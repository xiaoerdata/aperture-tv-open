import {dateInZone} from '../shared/model.mjs';
// Presentation adapter only; roles, writes and private data remain in the shared service.
export function liteDisplay(board,now=new Date()){
 const date=dateInZone(now,board.settings.timeZone),zone=board.settings.timeZone;
 const notes=board.entries.filter(e=>e.type==='note').reverse().sort((a,b)=>Number(b.pinned)-Number(a.pinned));
 return {revision:board.revision,name:board.settings.name,scene:board.settings.scene,headline:board.settings.headline||'日子很平常。一起就很好。',
  date,time:new Intl.DateTimeFormat('en-GB',{timeZone:zone,hour:'2-digit',minute:'2-digit',hour12:false}).format(now),timeZone:zone,
  note:notes[0]||null,events:board.entries.filter(e=>e.type==='event'&&e.date===date).sort((a,b)=>a.time.localeCompare(b.time)).slice(0,4),
  metrics:board.entries.filter(e=>e.type==='metric').slice(0,3),photos:board.photos.map(p=>({id:p.id,title:p.title,caption:p.caption,url:p.asset?'/media/'+p.asset:p.url})),
  life:(board.life||[]).slice(0,6).map(s=>({...s,value:Date.parse(s.expiresAt)<=now.getTime()?null:s.value,expired:Date.parse(s.expiresAt)<=now.getTime()})),
  cinemaEnabled:!!board.cinema?.enabled};
}
