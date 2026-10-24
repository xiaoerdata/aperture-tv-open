import {api,el} from './client.js';

// Reuses the family viewer handshake; the claim secret never leaves this page's memory.
export function mountTvPairing({onConnected,onOpen=()=>{}}){
 const backdrop=el('div','tv-pair-backdrop');backdrop.id='tv-pair-dialog';backdrop.hidden=true;
 const panel=el('section','tv-pair-panel'),title=el('h2','','让这台电视，连上家。');title.id='tv-pair-title';panel.setAttribute('role','dialog');panel.setAttribute('aria-modal','true');panel.setAttribute('aria-labelledby',title.id);
 const code=el('strong','tv-pair-code','··· ···'),status=el('p','tv-pair-status'),address=el('p','tv-pair-address'),actions=el('div','tv-pair-actions'),retry=el('button','','重新生成连接码'),back=el('button','','返回片库');
 code.id='tv-pair-code';status.id='tv-pair-status';status.setAttribute('role','status');retry.id='tv-pair-retry';back.id='tv-pair-close';retry.type=back.type='button';
 address.textContent=location.origin+'/studio';actions.append(back,retry);panel.append(title,el('p','','请家人用已登录管理员的手机打开工作台，在「连接与窗景」批准下面的六位码。'),code,status,address,el('small','','五分钟内有效，只连接这台电视用于查看与播放。无需把管理员口令填进电视。'),actions);backdrop.append(panel);document.body.append(backdrop);
 let generation=0,timer=null,request=null,challenge=null,prior=null,disposed=false;
 const open=()=>!backdrop.hidden;
 function stop(){generation++;clearTimeout(timer);timer=null;request?.abort();request=null;challenge=null;code.textContent='··· ···';retry.disabled=false;}
 function close(){stop();backdrop.hidden=true;if(prior?.isConnected&&!prior.hidden)prior.focus();}
 async function call(path,body){const controller=new AbortController();request=controller;try{return await api(path,body,{signal:controller.signal});}finally{if(request===controller)request=null;}}
 function expired(){challenge=null;code.textContent='··· ···';status.textContent='连接码已过期，请按「重新生成连接码」。';}
 async function poll(turn){
  if(disposed||!open()||turn!==generation||!challenge||document.hidden)return;
  if(challenge.expiresAt<=Date.now()){expired();return;}
  try{const result=await call('/api/pair/poll',{code:challenge.code,secret:challenge.secret});if(disposed||turn!==generation||!open())return;
   if(result.status==='connected'){close();await onConnected();return;}
   timer=setTimeout(()=>poll(turn),2500);
  }catch(e){if(turn===generation&&!disposed&&open()){challenge=null;code.textContent='··· ···';status.textContent=e.code==='pair_expired'?'连接码已过期，请重新生成。':'连接暂时中断，请家人检查网络，再重新生成连接码。';}}
 }
 async function start(){
  stop();if(disposed||!open())return;const turn=generation;retry.disabled=true;status.textContent='正在生成连接码…';
  try{const result=await call('/api/pair/start',{role:'viewer'});if(disposed||turn!==generation||!open())return;
   if(!/^\d{6}$/.test(result.code)||typeof result.secret!=='string'||!Number.isFinite(result.expiresAt))throw new Error('invalid pairing response');
   challenge=result;code.textContent=result.code.slice(0,3)+' '+result.code.slice(3);status.textContent='等家人批准后，会自动回到当前片库。';timer=setTimeout(()=>poll(turn),2000);
  }catch(e){if(turn===generation&&!disposed&&open())status.textContent='暂时无法生成连接码，请检查家庭服务后重试。';}
  finally{if(turn===generation)retry.disabled=false;}
 }
 function visibility(){if(!open()||!challenge)return;clearTimeout(timer);if(document.hidden)return;if(challenge.expiresAt<=Date.now())expired();else if(!request)poll(generation);}
 back.onclick=close;retry.onclick=start;document.addEventListener('visibilitychange',visibility);
 return {open(){if(disposed||open())return;onOpen();prior=document.activeElement;backdrop.hidden=false;back.focus();start();},close,isOpen:open,root:()=>panel,dispose(){disposed=true;close();document.removeEventListener('visibilitychange',visibility);backdrop.remove();}};
}
