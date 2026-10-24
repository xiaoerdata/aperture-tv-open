import {$,el,api} from '../ui/client.js';
import {mountTvPairing} from '../ui/tv-pairing.js';
import {installRemote} from '../ui/remote.js';
let items=[],hls=null,prior=null,playing=false,polling=false,generation=0,connectionEpoch=0;
const query=new URLSearchParams(location.search);let filter=['live','library','series','movies'].includes(query.get('view'))?query.get('view'):'all';
const home=query.get('from')==='home'?'/home'+(query.get('edition')==='parents'?'?edition=parents':''):'/tv';
$('#cinema-home').href=home;$('#cinema-home').textContent=query.get('from')==='home'?'返回':'返回';
const parents=query.get('edition')==='parents';if(parents)document.body.classList.add('parents-cinema');
const video=$('#video');
const pairing=mountTvPairing({onOpen:()=>{if(playing)close();},onConnected:async()=>{connectionEpoch++;await refresh();($('#cinema-grid .cinema-item')||$('#cinema-refresh')).focus();}});
$('#cinema-connect').onclick=()=>pairing.open();
function close(){generation++;hls?.destroy();hls=null;video.pause();video.removeAttribute('src');video.load();$('#cinema-player').hidden=true;playing=false;prior?.focus();}
async function play(item){
  close();prior=document.activeElement;$('#cinema-player').hidden=false;$('#playing-title').textContent=item.name;$('#close-player').focus();$('#player-status').textContent='正在打开视频…';playing=true;
  const current=generation,src='/api/cinema/play/'+item.id;
  if(item.format==='hls'&&!video.canPlayType('application/vnd.apple.mpegurl')){
    try{const {default:Hls}=await import('/vendor/hls.mjs');if(!playing||current!==generation)return;if(!Hls.isSupported())throw new Error();hls=new Hls({enableWorker:false,maxBufferLength:30});hls.on(Hls.Events.ERROR,(_,data)=>{if(data.fatal)$('#player-status').textContent='直播流不可用或编码不兼容，请检查来源。';});hls.loadSource(src);hls.attachMedia(video);}catch{$('#player-status').textContent='当前内核不支持此直播格式，请更新 WebView 或使用已安装的播放器。';return;}
  }else video.src=src;
  video.play().catch(()=>{$('#player-status').textContent='请按「播放 / 暂停」开始；如仍无法播放，请核对编码和来源。';});
}
function render(){
 const labels={live:'看电视',series:'看电视剧',movies:'看电影',library:'视频片库',all:'家庭影院'};
 $('.cinema-intro h1').textContent=labels[filter];
 for(const b of document.querySelectorAll('[data-media-filter]'))b.setAttribute('aria-pressed',String(b.dataset.mediaFilter===filter));const term=$('#cinema-search').value.toLowerCase();$('#cinema-grid').replaceChildren();for(const [i,item] of items.filter(i=>(filter==='all'||(filter==='live'?i.kind==='channel':filter==='series'?i.viewKind==='series':filter==='movies'?i.viewKind==='movie':i.kind!=='channel'))&&(i.name+' '+i.sourceName).toLowerCase().includes(term)).entries()){const button=el('button','cinema-item'),art=el('span','cinema-art');art.append(el('span','',String(i+1).padStart(2,'0')),el('span','cinema-play-icon','▷'));button.append(art,el('strong','',item.name),el('small','',item.sourceName+' · '+(item.health?.status==='readable'?'媒体可读取':item.health?.status==='checking'?'正在检测':item.health?.status&&item.health.status!=='untested'?item.health.detail:item.format))); button.onclick=()=>play(item);$('#cinema-grid').append(button);}if(!$('#cinema-grid').children.length)$('#cinema-grid').append(el('p','empty',term?'无搜索结果':filter==='live'?'暂无频道':'暂无影片'));}
async function refresh(){const turn=connectionEpoch;try{const result=await api('/api/cinema/library');if(turn!==connectionEpoch)return;items=result.items;$('#cinema-connect').hidden=true;$('#cinema-grid').hidden=false;render();$('#cinema-status').textContent=`${items.length} 项`+(result.unavailable.length?' · 暂时无法读取：'+result.unavailable.join('、'):'');}catch(e){if(turn!==connectionEpoch)return;items=[];render();close();$('#cinema-connect').hidden=e.status!==401;$('#cinema-grid').hidden=true;$('#cinema-status').textContent=e.status===401?'未连接':e.code==='cinema_disabled'?'片库未开启':e.message;}}
for(const b of document.querySelectorAll('[data-media-filter]'))b.onclick=()=>{filter=b.dataset.mediaFilter;render();};
$('#cinema-refresh').onclick=refresh;$('#cinema-search').oninput=render;$('#close-player').onclick=close;
$('#play-pause').onclick=()=>video.paused?video.play().catch(()=>{$('#player-status').textContent='无法播放此视频。请检查来源或格式。';}):video.pause();
$('#seek-back').onclick=()=>{if(Number.isFinite(video.duration))video.currentTime=Math.max(0,video.currentTime-10);};$('#seek-forward').onclick=()=>{if(Number.isFinite(video.duration))video.currentTime=Math.min(video.duration,video.currentTime+10);};
video.onplaying=()=>{$('#player-status').textContent='正在播放 · '+$('#playing-title').textContent;};video.onerror=()=>{$('#player-status').textContent=parents?'这个节目暂时打不开，请返回选择别的，或请家人检查后台来源。':'视频无法播放。来源可能已离线、未开启共享，或当前内核不支持该编码。';};
window.apertureBack=()=>{if(pairing.isOpen()){pairing.close();return true;}if(playing){close();return true;}location.href=home;return true;};const dispose=installRemote({back:window.apertureBack,gridSelector:'.cinema-grid',root:()=>pairing.isOpen()?pairing.root():playing?$('#cinema-player'):document});
const timer=setInterval(async()=>{
 if(polling||document.hidden)return;polling=true;const turn=connectionEpoch;
 try{const board=await api('/api/board');if(turn!==connectionEpoch)return;
  $('#cinema-connect').hidden=true;
  if(!board.cinema?.enabled){close();items=[];render();$('#cinema-grid').hidden=true;$('#cinema-status').textContent='家庭影院已关闭。';}
  else if($('#cinema-grid').hidden&&!pairing.isOpen())await refresh();
 }catch(e){if(turn!==connectionEpoch)return;close();items=[];render();$('#cinema-connect').hidden=e.status!==401;$('#cinema-grid').hidden=true;$('#cinema-status').textContent=e.status===401?'连接已失效':'连接中断';}
 finally{polling=false;}
},5000);
window.addEventListener('pagehide',()=>{pairing.dispose();close();dispose();clearInterval(timer);});window.addEventListener('pageshow',event=>{if(event.persisted)location.reload();});refresh();
