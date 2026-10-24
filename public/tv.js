import {mountLifeTV} from './life/tv.js';
import {sampleSignals} from './life/catalog.js';
import {demoBoard,emptyBoard,dateInZone} from '/shared/model.mjs';
import {$,el,api,photoUrl,showToast} from './ui/client.js';
import {createSceneEngine,sceneRegistry} from './scenes/engine.js';
import {installRemote} from './ui/remote.js';
const params=new URLSearchParams(location.search);let isDemo=params.get('demo')==='1',board=emptyBoard(),scene='daylight',paused=matchMedia('(prefers-reduced-motion: reduce)').matches,photoIndex=0,connected=false,lastSync=0,pairTimer=0,pairGeneration=0,priorFocus=null,fetching=false,sceneOverride=false,renderedDay='',eventStream=null;
const lifeTV=mountLifeTV();
$('#native-menu').hidden=params.get('native')!=='1';
const engine=createSceneEngine($('#scene-canvas'));const shell=$('#tv-shell');
const reducedMotion=matchMedia('(prefers-reduced-motion: reduce)');
function updateMotion(){const restricted=reducedMotion.matches||!board.settings.motion;engine.pause(paused||restricted);$('#motion-button').disabled=restricted;$('#motion-button').textContent=reducedMotion.matches?'系统减少动态':!board.settings.motion?'窗景为静态':paused?'播放动效':'暂停动效';$('#motion-button').setAttribute('aria-pressed',String(paused||restricted));}
reducedMotion.addEventListener('change',updateMotion);
function clock(now=new Date()){
  const zone=board.settings.timeZone;
  $('#clock').textContent=new Intl.DateTimeFormat('en-GB',{timeZone:zone,hour:'2-digit',minute:'2-digit',hour12:false}).format(now);
  $('#date-label').textContent=new Intl.DateTimeFormat('zh-CN',{timeZone:zone,month:'long',day:'numeric',weekday:'long'}).format(now);
  $('#timezone-label').textContent=zone;
}
function renderPhoto(){
  const photos=board.photos;
  if(!photos.length){$('#memory-frame').hidden=true;$('#next-photo').hidden=true;return;}
  $('#memory-frame').hidden=false;$('#next-photo').hidden=false;const p=photos[photoIndex%photos.length];$('#hero-photo').src=photoUrl(p);$('#hero-photo').alt=p.caption || p.title;$('#photo-title').textContent=p.title;$('#photo-count').textContent=String(photoIndex%photos.length+1).padStart(2,'0')+' / '+String(photos.length).padStart(2,'0');
}
function render(){
  renderedDay=dateInZone(new Date(),board.settings.timeZone);
  $('#cinema-link').hidden=!board.cinema?.enabled;
  $('#home-name').textContent=board.settings.name;$('#demo-label').textContent=isDemo?'示例家庭 · 非真实资料':'家庭内容 · 仅已连接设备可见';
  $('#connect-button').firstChild.textContent=connected?'连接信息 ':'连接我的家 ';
  $('#connection-status').textContent=isDemo?'演示模式':connected?'已同步 · r'+board.revision:'尚未连接';
  $('#agenda').replaceChildren();const date=dateInZone(new Date(),board.settings.timeZone),events=board.entries.filter(e=>e.type==='event'&&e.date===date).sort((a,b)=>a.time.localeCompare(b.time));
  $('#agenda-count').textContent=events.length+' 个安排';
  for(const e of events){const li=el('li',e.done?'done':'');li.append(el('time','',e.time),el('strong','',e.title),el('span','',e.done?'已完成':e.person));$('#agenda').append(li);}
  if(!events.length)$('#agenda').append(el('p','empty','今天留一点空白。\n在手机上添加一个安排吧。'));
  const notes=board.entries.filter(e=>e.type==='note').reverse().sort((a,b)=>Number(b.pinned)-Number(a.pinned));const note=notes[0];
  $('#note-title').textContent=note?.title || '留一句话给家人';$('#note-body').textContent=note?.body || '在手机上写下想说的话。';$('#note-author').textContent=note?'— '+note.author:'来自我们';
  $('#metrics').replaceChildren();$('#landscape-labels').replaceChildren();
  for(const m of board.entries.filter(e=>e.type==='metric').slice(0,3)){
    const ratio=Math.min(100,100*m.value/m.target);const node=el('div','metric'),top=el('div','metric-top'),number=el('strong','',String(m.value));number.append(el('small','',` / ${m.target} ${m.unit}`));top.append(el('span','',m.title),number);
    const bar=el('span','metric-bar'),fill=el('i');fill.style.width=ratio+'%';bar.append(fill);node.append(top,bar,el('span','metric-source',m.source+' · '+m.date));$('#metrics').append(node);
    const label=el('div','',m.title);label.append(el('strong','',`${m.value} / ${m.target}`),el('small','',`${m.unit} · ${Math.round(100*m.value/m.target)}%`));$('#landscape-labels').append(label);
  }
  if(!$('#metrics').children.length)$('#metrics').append(el('p','empty','记录阅读、散步，或者你们自己的小目标。'));
  lifeTV.render(board);renderPhoto();clock();setScene(scene);updateMotion();
}
function setScene(next){
  scene=next;const spec=sceneRegistry[scene];$('.scene-stage').dataset.scene=scene;
  $('#scene-title').replaceChildren();(board.settings.headline||spec.title).split('\n').forEach((s,i)=>{if(i)$('#scene-title').append(document.createElement('br'));$('#scene-title').append(document.createTextNode(s));});
  $('#scene-description').textContent=spec.description;$('#art-label').textContent=spec.art;$('#landscape-labels').hidden=scene!=='landscape';
  for(const button of document.querySelectorAll('button[data-scene]'))button.setAttribute('aria-pressed',String(button.dataset.scene===scene));
  engine.set(board,scene);
}
async function refresh(){
  if(fetching||document.hidden||isDemo)return;fetching=true;
  try{const next=await api('/api/board');connected=true;if(!eventStream||eventStream.readyState===EventSource.CLOSED){eventStream=new EventSource('/api/events');eventStream.addEventListener('revision',()=>{sceneOverride=false;refresh();});}lastSync=Date.now();shell.classList.remove('offline');if(board.revision!==next.revision||board.demo||!board.updatedAt){board=next;if(!sceneOverride)scene=board.settings.scene;render();}$('#connection-status').textContent='已同步 · r'+board.revision;api('/api/display/ack',{revision:board.revision,scene}).catch(()=>{});}
  catch(e){if(e.status===401){connected=false;board=emptyBoard();photoIndex=0;render();$('#connection-status').textContent='连接已失效，请重新连接';}else{shell.classList.add('offline');$('#connection-status').textContent=lastSync?'连接中断 · 保留 '+new Date(lastSync).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})+' 的内容':'无法连接服务';}}finally{fetching=false;}
}
function closeDialog(){pairGeneration++;clearTimeout(pairTimer);$('#dialog-backdrop').hidden=true;priorFocus?.focus();}
async function startPair(){
  const generation=++pairGeneration;clearTimeout(pairTimer);$('#pair-code').textContent='··· ···';$('#pair-status').textContent='正在生成连接码';
  try{
    const pair=await api('/api/pair/start',{role:'viewer'});if(generation!==pairGeneration)return;
    $('#pair-code').textContent=pair.code.slice(0,3)+' '+pair.code.slice(3);$('#pair-status').textContent='5分钟内有效 · 只允许读取家庭屏内容';
    const poll=async()=>{
      if(generation!==pairGeneration)return;
      try{const result=await api('/api/pair/poll',{code:pair.code,secret:pair.secret});if(generation!==pairGeneration)return;
        if(result.status==='connected'){isDemo=false;sceneOverride=false;history.replaceState(null,'',params.get('native')==='1'?'/tv?native=1':'/tv');closeDialog();await refresh();showToast('已连接，欢迎回家');return;}
        pairTimer=setTimeout(poll,2500);
      }catch(e){if(generation===pairGeneration)$('#pair-status').textContent=e.message;}
    };pairTimer=setTimeout(poll,2000);
  }catch(e){if(generation===pairGeneration)$('#pair-status').textContent=e.message;}
}
$('#connect-button').onclick=()=>{priorFocus=document.activeElement;$('#dialog-backdrop').hidden=false;$('#pair-origin').textContent=location.origin+'/studio';$('#close-dialog').focus();$('#retry-pair').hidden=connected;if(connected){$('#pair-code').textContent='已连接';$('#pair-status').textContent='此设备已连接到 '+board.settings.name+'。如需移除，请在管理员工作台操作。';}else startPair();};
$('#close-dialog').onclick=closeDialog;$('#retry-pair').onclick=startPair;$('#demo-button').onclick=()=>{isDemo=true;connected=false;sceneOverride=false;board={...demoBoard(),life:sampleSignals()};history.replaceState(null,'','/tv?demo=1');closeDialog();render();};
$('#dialog-backdrop').addEventListener('click',e=>{if(e.target===$('#dialog-backdrop'))closeDialog();});
for(const b of document.querySelectorAll('button[data-scene]'))b.onclick=()=>{lifeTV.back();sceneOverride=true;setScene(b.dataset.scene);};
$('#next-photo').onclick=()=>{photoIndex++;renderPhoto();};
$('#motion-button').onclick=()=>{paused=!paused;updateMotion();};
if(paused){$('#motion-button').textContent='播放动效';$('#motion-button').setAttribute('aria-pressed','true');}
$('#immersive-button').onclick=()=>{lifeTV.back();shell.classList.add('immersive');engine.capture(0);showToast('按返回键退出沉浸');};
window.apertureBack=()=>{if(lifeTV.back())return true;if(!$('#dialog-backdrop').hidden){closeDialog();return true;}if(shell.classList.contains('immersive')){shell.classList.remove('immersive');$('#immersive-button').focus();engine.capture(0);return true;}return false;};
const disposeRemote=installRemote({back:window.apertureBack,root:()=>$('#dialog-backdrop').hidden?document:$('#dialog-backdrop')});
window.apertureCapture=(time,next)=>{if(next)setScene(next);engine.pause(true);engine.capture(time);};
window.apertureScene=()=>scene;
if(isDemo){board={...demoBoard(),life:sampleSignals()};render();}else{render();refresh();}
const syncTimer=setInterval(refresh,5000),clockTimer=setInterval(()=>{if(renderedDay!==dateInZone(new Date(),board.settings.timeZone)){if(isDemo)board={...demoBoard(),life:sampleSignals()};render();}else {clock();lifeTV.render(board);}},15000);
document.addEventListener('visibilitychange',()=>{if(!document.hidden){clock();refresh();}});
window.addEventListener('pagehide',()=>{eventStream?.close();clearInterval(syncTimer);clearInterval(clockTimer);clearTimeout(pairTimer);engine.dispose();disposeRemote();});

window.addEventListener('pageshow',event=>{if(event.persisted)location.reload();});
