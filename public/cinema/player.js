import {$,el,api} from '../ui/client.js';
import {mountTvPairing} from '../ui/tv-pairing.js';
import {installRemote} from '../ui/remote.js';
let items=[],hls=null,prior=null,playing=false,polling=false,generation=0,connectionEpoch=0;
const query=new URLSearchParams(location.search);let filter=['live','library','series','movies','variety'].includes(query.get('view'))?query.get('view'):'all';
const home=query.get('from')==='home'?'/home'+(query.get('edition')==='parents'?'?edition=parents':''):'/tv';
$('#cinema-home').href=home;$('#cinema-home').textContent=query.get('from')==='home'?'返回':'返回';
const parents=query.get('edition')==='parents';if(parents)document.body.classList.add('parents-cinema');
let detail=null,detailPrior=null;const infoCache=new Map(),infoPending=new Map();let infoQueue=[],infoActive=0;
const video=$('#video');
const pairing=mountTvPairing({onOpen:()=>{if(playing)close();closeDetail();},onConnected:async()=>{connectionEpoch++;await refresh();($('#cinema-grid .cinema-item')||$('#cinema-refresh')).focus();}});
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
function posterInto(node,url,fallback){let img=node.querySelector('img');if(!url)return;if(!img){img=el('img','');img.alt='';img.loading='lazy';img.referrerPolicy='no-referrer';node.prepend(img);}img.src=url;img.onerror=()=>{if(fallback&&img.getAttribute('src')!==fallback){img.src=fallback;return;}img.remove();node.classList.remove('has-art');};img.onload=()=>node.classList.add('has-art');}
function loadInfo(item){if(infoCache.has(item.id))return Promise.resolve(infoCache.get(item.id));if(infoPending.has(item.id))return infoPending.get(item.id);if(infoQueue.length>=20)return Promise.resolve(null);const pending=new Promise(resolve=>{infoQueue.push({item,resolve,epoch:connectionEpoch});drainInfo();});infoPending.set(item.id,pending);return pending;}
function resetInfo(){for(const q of infoQueue)q.resolve(null);infoQueue=[];infoCache.clear();infoPending.clear();}
async function drainInfo(){if(infoActive>=2||!infoQueue.length)return;const {item,resolve,epoch}=infoQueue.shift();infoActive++;try{const data=await api('/api/cinema/metadata/'+item.id);if(epoch===connectionEpoch){infoCache.set(item.id,data);if(data?.match?.provider==='TMDB')$('#tmdb-credits').hidden=false;}resolve(epoch===connectionEpoch?data:null);}catch{resolve(null);}finally{if(epoch===connectionEpoch)infoPending.delete(item.id);infoActive--;drainInfo();}}
function closeDetail(){if(!detail)return;detail=null;$('#cinema-detail').hidden=true;detailPrior?.focus();}
function applyDetail(item,data){if(detail?.id!==item.id)return;const match=data?.match;$('#detail-meta').textContent=[item.sourceName,item.year||match?.year,item.genre||match?.genre].filter(Boolean).join('  ·  ');$('#detail-summary').textContent=item.summary||match?.summary||'暂无简介';$('#detail-credit').textContent=match?`资料：${match.provider} · ${match.url}`:'资料：媒体来源';const poster=item.poster||match?.poster;$('#detail-poster').hidden=!poster;$('#detail-poster').dataset.fallback=match?.poster||'';if(poster)$('#detail-poster').src=poster;$('#detail-correct').hidden=!data?.candidates?.length;$('#detail-state').textContent=data?.state==='unavailable'?'联网资料暂时不可用，仍可播放':data?.state==='needs_tmdb'?'可在家庭服务中配置电影资料':' ';}
async function openDetail(item){if(item.kind==='channel'){play(item);return;}detailPrior=document.activeElement;detail=item;$('#cinema-detail').hidden=false;$('#detail-candidates').hidden=true;$('#detail-title').textContent=item.name;$('#detail-play').onclick=()=>play(item);$('#detail-play').focus();applyDetail(item,infoCache.get(item.id));const data=await loadInfo(item);applyDetail(item,data);$('#detail-correct').onclick=()=>{if(detail?.id!==item.id)return;const holder=$('#detail-candidates');holder.replaceChildren();for(const candidate of data?.candidates||[]){const b=el('button','',candidate.title+' · '+candidate.year);b.onclick=()=>{const selected={...data,match:candidate};infoCache.set(item.id,selected);applyDetail(item,selected);holder.hidden=true;$('#detail-play').focus();};holder.append(b);}holder.hidden=false;holder.querySelector('button')?.focus();};}
$('#detail-close').onclick=closeDetail;$('#detail-poster').onerror=()=>{const p=$('#detail-poster');if(p.dataset.fallback&&p.getAttribute('src')!==p.dataset.fallback){p.src=p.dataset.fallback;return;}p.hidden=true;};
function render(){
 const labels={live:'看电视',series:'看电视剧',movies:'看电影',variety:'看综艺',library:'视频片库',all:'家庭影院'};
 $('.cinema-intro h1').textContent=labels[filter];
 for(const b of document.querySelectorAll('[data-media-filter]'))b.setAttribute('aria-pressed',String(b.dataset.mediaFilter===filter));
 const term=$('#cinema-search').value.toLowerCase(),shown=items.filter(i=>(filter==='all'||(filter==='live'?i.kind==='channel':filter==='series'?i.viewKind==='series':filter==='movies'?i.viewKind==='movie':filter==='variety'?i.viewKind==='variety':i.kind!=='channel'))&&(i.name+' '+i.sourceName).toLowerCase().includes(term));
 $('#cinema-status').textContent=shown.length+' 项';$('#cinema-grid').replaceChildren();$('#cinema-grid').classList.toggle('live-grid',filter==='live');
 for(const [i,item] of shown.entries()){
  const button=el('button','cinema-item'),art=el('span','cinema-art');button.setAttribute('aria-label',item.name);art.append(el('span','art-placeholder',item.kind==='channel'?'◉':'▱'));posterInto(art,item.poster);button.append(art,el('strong','',item.name),el('small','',item.sourceName));button.onclick=()=>openDetail(item);$('#cinema-grid').append(button);
  if(item.kind!=='channel'&&i<12){const epoch=connectionEpoch;loadInfo(item).then(data=>{if(epoch!==connectionEpoch||!button.isConnected)return;posterInto(art,item.poster||data?.match?.poster,data?.match?.poster);});}
 }
 if(!shown.length)$('#cinema-grid').append(el('p','empty',term?'无搜索结果':filter==='live'?'暂无频道':'暂无影片'));
 const featured=shown.find(i=>i.kind!=='channel');$('#cinema-feature').hidden=!featured||filter==='live'||!!term;
 if(featured){$('#feature-title').textContent=featured.name;$('#feature-meta').textContent=featured.sourceName;$('#feature-summary').textContent=featured.summary||'';$('#feature-image').hidden=true;$('#feature-open').onclick=()=>openDetail(featured);const epoch=connectionEpoch;loadInfo(featured).then(data=>{if(epoch!==connectionEpoch||$('#feature-title').textContent!==featured.name)return;const match=data?.match;$('#feature-summary').textContent=featured.summary||match?.summary||'';$('#feature-meta').textContent=[featured.sourceName,featured.year||match?.year].filter(Boolean).join(' · ');const image=featured.poster||match?.poster;if(image){$('#feature-image').src=image;$('#feature-image').hidden=false;}});}
}
async function refresh(){const turn=++connectionEpoch;resetInfo();try{const result=await api('/api/cinema/library');if(turn!==connectionEpoch)return;items=result.items;infoCache.clear();$('#cinema-connect').hidden=true;$('#cinema-grid').hidden=false;render();$('#cinema-status').textContent=`${$('#cinema-grid .cinema-item')?$('#cinema-grid').querySelectorAll('.cinema-item').length:0} 项`+(result.unavailable.length?' · 暂时无法读取：'+result.unavailable.join('、'):'');}catch(e){if(turn!==connectionEpoch)return;items=[];render();close();$('#cinema-connect').hidden=e.status!==401;$('#cinema-grid').hidden=true;$('#cinema-status').textContent=e.status===401?'未连接':e.code==='cinema_disabled'?'片库未开启':e.message;}}
for(const b of document.querySelectorAll('[data-media-filter]'))b.onclick=()=>{filter=b.dataset.mediaFilter;render();};
$('#cinema-refresh').onclick=refresh;$('#cinema-search').oninput=render;$('#close-player').onclick=close;
$('#play-pause').onclick=()=>video.paused?video.play().catch(()=>{$('#player-status').textContent='无法播放此视频。请检查来源或格式。';}):video.pause();
$('#seek-back').onclick=()=>{if(Number.isFinite(video.duration))video.currentTime=Math.max(0,video.currentTime-10);};$('#seek-forward').onclick=()=>{if(Number.isFinite(video.duration))video.currentTime=Math.min(video.duration,video.currentTime+10);};
video.onplaying=()=>{$('#player-status').textContent='正在播放 · '+$('#playing-title').textContent;};video.onerror=()=>{$('#player-status').textContent=parents?'这个节目暂时打不开，请返回选择别的，或请家人检查后台来源。':'视频无法播放。来源可能已离线、未开启共享，或当前内核不支持该编码。';};
window.apertureBack=()=>{if(pairing.isOpen()){pairing.close();return true;}if(playing){close();return true;}if(detail){closeDetail();return true;}location.href=home;return true;};const dispose=installRemote({back:window.apertureBack,gridSelector:'.cinema-grid',root:()=>pairing.isOpen()?pairing.root():playing?$('#cinema-player'):detail?$('#cinema-detail'):document});
const timer=setInterval(async()=>{
 if(polling||document.hidden)return;polling=true;const turn=connectionEpoch;
 try{const board=await api('/api/board');if(turn!==connectionEpoch)return;
  $('#cinema-connect').hidden=true;
  if(!board.cinema?.enabled){connectionEpoch++;resetInfo();close();closeDetail();items=[];render();$('#cinema-grid').hidden=true;$('#cinema-status').textContent='家庭影院已关闭。';}
  else if($('#cinema-grid').hidden&&!pairing.isOpen())await refresh();
 }catch(e){if(turn!==connectionEpoch)return;connectionEpoch++;resetInfo();close();closeDetail();items=[];render();$('#cinema-connect').hidden=e.status!==401;$('#cinema-grid').hidden=true;$('#cinema-status').textContent=e.status===401?'连接已失效':'连接中断';}
 finally{polling=false;}
},5000);
window.addEventListener('pagehide',()=>{pairing.dispose();close();closeDetail();connectionEpoch++;for(const q of infoQueue)q.resolve(null);infoQueue=[];dispose();clearInterval(timer);});window.addEventListener('pageshow',event=>{if(event.persisted)location.reload();});refresh();
