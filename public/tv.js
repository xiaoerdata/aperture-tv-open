import {demoBoard,emptyBoard,dateInZone} from '/shared/model.mjs';
import {$,el,api,photoUrl,showToast} from './ui/client.js';
import {installRemote} from './ui/remote.js';
const params=new URLSearchParams(location.search);
let isDemo=params.get('demo')==='1',board=emptyBoard(),photoIndex=0,connected=false,lastSync=0,pairTimer=0,pairGeneration=0,priorFocus=null,fetching=false,renderedDay='',eventStream=null;
const shell=$('#tv-shell');
if(params.get('edition')==='parents')document.documentElement.classList.add('parents');
$('#native-menu').hidden=params.get('native')!=='1';
function clock(now=new Date()){
 const zone=board.settings.timeZone;
 $('#clock').textContent=new Intl.DateTimeFormat('en-GB',{timeZone:zone,hour:'2-digit',minute:'2-digit',hour12:false}).format(now);
 $('#date-label').textContent=new Intl.DateTimeFormat('zh-CN',{timeZone:zone,month:'long',day:'numeric',weekday:'long'}).format(now);
}
function renderPhoto(){
 const photos=board.photos,hasPhoto=photos.length>0;
 shell.classList.toggle('has-photo',hasPhoto);
 $('#memory-frame').hidden=!hasPhoto;$('#photo-empty').hidden=hasPhoto;$('#photo-count').textContent=hasPhoto?`${photoIndex%photos.length+1} / ${photos.length}`:'';
 if(!hasPhoto){$('#hero-photo').removeAttribute('src');$('#hero-photo').alt='';$('#photo-title').textContent='';return;}
 const p=photos[photoIndex%photos.length];$('#hero-photo').src=photoUrl(p);$('#hero-photo').alt=p.caption||p.title;$('#photo-title').textContent=p.title;$('#next-photo').hidden=photos.length<2;
}
function render(){
 renderedDay=dateInZone(new Date(),board.settings.timeZone);
 $('#home-name').textContent=board.settings.name||'家庭';$('#demo-label').textContent=isDemo?'示例内容':'';
 $('#connect-button').textContent=connected?'连接信息':'连接家庭';
 $('#connection-status').textContent=isDemo?'演示模式':connected?'已同步':'尚未连接';
 const events=board.entries.filter(e=>e.type==='event'&&e.date===renderedDay).sort((a,b)=>Number(a.done)-Number(b.done)||a.time.localeCompare(b.time));
 $('#agenda').replaceChildren();$('#agenda-count').textContent=events.length>3?`3 / ${events.length} 项`:events.length?`${events.length} 项`:'';
 for(const e of events.slice(0,3)){const row=el('li',e.done?'done':'');row.append(el('time','',e.time),el('strong','',e.title),el('span','',e.done?'已完成':e.person));$('#agenda').append(row);}
 if(!events.length)$('#agenda').append(el('li','empty','暂无安排'));
 $('#notes').replaceChildren();const notes=board.entries.filter(e=>e.type==='note').reverse().sort((a,b)=>Number(b.pinned)-Number(a.pinned));
 for(const n of notes.slice(0,1)){const item=el('article','note');item.append(el('p','',n.body),el('small','',n.time||''));$('#notes').append(item);}
 if(!notes.length)$('#notes').append(el('p','empty','暂无留言'));
 renderPhoto();clock();
}
async function refresh(){
 if(fetching||document.hidden||isDemo)return;fetching=true;
 try{const next=await api('/api/board');if(isDemo)return;connected=true;
  if(!eventStream||eventStream.readyState===EventSource.CLOSED){eventStream=new EventSource('/api/events');eventStream.addEventListener('revision',refresh);}
  lastSync=Date.now();shell.classList.remove('offline');if(board.revision!==next.revision||board.demo||!board.updatedAt){board=next;render();}
  $('#connection-status').textContent='已同步';api('/api/display/ack',{revision:board.revision,scene:board.settings.scene,presentation:'family-v2'}).catch(()=>{});
 }catch(e){if(isDemo)return;if(e.status===401){connected=false;eventStream?.close();eventStream=null;board=emptyBoard();photoIndex=0;render();$('#connection-status').textContent='连接已失效，请重新连接';}else{shell.classList.add('offline');$('#connection-status').textContent=lastSync?'连接中断 · 保留上次内容':'无法连接服务';}}finally{fetching=false;}
}
function closeDialog(){pairGeneration++;clearTimeout(pairTimer);$('#dialog-backdrop').hidden=true;priorFocus?.focus();}
async function startPair(){
 const generation=++pairGeneration;clearTimeout(pairTimer);$('#pair-code').textContent='··· ···';$('#pair-status').textContent='正在生成连接码';
 try{const pair=await api('/api/pair/start',{role:'viewer'});if(generation!==pairGeneration)return;
  $('#pair-code').textContent=pair.code.slice(0,3)+' '+pair.code.slice(3);$('#pair-status').textContent='5分钟内有效 · 只读家庭内容';
  const poll=async()=>{if(generation!==pairGeneration)return;try{const result=await api('/api/pair/poll',{code:pair.code,secret:pair.secret});if(generation!==pairGeneration)return;
   if(result.status==='connected'){isDemo=false;const next=new URL(location.href);next.searchParams.delete('demo');history.replaceState(null,'',next.pathname+next.search);closeDialog();await refresh();showToast('已连接');return;}pairTimer=setTimeout(poll,2500);
  }catch(e){if(generation===pairGeneration)$('#pair-status').textContent=e.message;}};pairTimer=setTimeout(poll,2000);
 }catch(e){if(generation===pairGeneration)$('#pair-status').textContent=e.message;}
}
$('#connect-button').onclick=()=>{priorFocus=document.activeElement;$('#dialog-backdrop').hidden=false;$('#pair-origin').textContent=location.origin+'/studio';$('#close-dialog').focus();$('#retry-pair').hidden=connected;if(connected){$('#pair-code').textContent='已连接';$('#pair-status').textContent=board.settings.name+' · 在工作台移除此设备';}else startPair();};
$('#close-dialog').onclick=closeDialog;$('#retry-pair').onclick=startPair;
$('#demo-button').onclick=()=>{isDemo=true;connected=false;eventStream?.close();eventStream=null;board=demoBoard();const next=new URL(location.href);next.searchParams.set('demo','1');history.replaceState(null,'',next.pathname+next.search);closeDialog();render();};
$('#dialog-backdrop').addEventListener('click',e=>{if(e.target===$('#dialog-backdrop'))closeDialog();});
$('#next-photo').onclick=()=>{photoIndex++;renderPhoto();};
window.apertureBack=()=>{if(!$('#dialog-backdrop').hidden){closeDialog();return true;}return false;};
const disposeRemote=installRemote({back:window.apertureBack,root:()=>$('#dialog-backdrop').hidden?document:$('#dialog-backdrop')});
if(isDemo){board=demoBoard();render();}else{render();refresh();}
const syncTimer=setInterval(refresh,5000),clockTimer=setInterval(()=>{if(renderedDay!==dateInZone(new Date(),board.settings.timeZone)){if(isDemo)board=demoBoard();render();}else clock();},15000);
document.addEventListener('visibilitychange',()=>{if(!document.hidden){clock();refresh();}});
window.addEventListener('pagehide',()=>{eventStream?.close();pairGeneration++;clearInterval(syncTimer);clearInterval(clockTimer);clearTimeout(pairTimer);disposeRemote();});
window.addEventListener('pageshow',event=>{if(event.persisted)location.reload();});
