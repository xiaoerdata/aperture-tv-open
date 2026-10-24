import {$,el} from '../ui/client.js';
export function mountLifeTV(){
  const section=el('section','life-tv');section.id='life-tv';section.hidden=true;section.setAttribute('aria-label','家庭生活快照');
  const heading=el('div','life-tv-heading'),text=el('div');text.append(el('p','','家的另一面'),el('h2','','今天的家，一眼就知道。'),el('p','life-tv-explainer','由家人选择分享 · 手动更新的快照'));heading.append(text,el('span','life-tv-window','⌂'));
  const grid=el('div','life-tv-grid');grid.id='life-tv-grid';section.append(heading,grid);$('.main-window').after(section);
  const button=el('button','','生活快照');button.id='life-tv-button';button.setAttribute('aria-pressed','false');$('.toolbar').prepend(button);
  let visible=false;
  function toggle(next){visible=next;section.hidden=!visible;$('.main-window').hidden=visible;button.setAttribute('aria-pressed',String(visible));button.textContent=visible?'返回窗景':'生活快照';}
  button.onclick=()=>toggle(!visible);
  return {back(){if(visible){toggle(false);button.focus();return true;}return false;},render(board){
    grid.replaceChildren();const signals=board.life||[];
    for(const s of signals){const stale=Date.parse(s.expiresAt)<=Date.now(),tile=el('article','life-tv-tile'+(stale?' stale':'')),value=el('strong','life-tv-value',stale?'待更新':s.value);if(!stale)value.append(el('small','',s.unit));
      const when=s.observedAt||s.fetchedAt,formatted=new Date(when).toLocaleString('zh-CN',{timeZone:board.settings.timeZone,month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit',hour12:false});
      tile.append(el('h3','',s.title),value,el('p','',s.source),el('small','',stale?'快照已过期 · 请在手机重新读取':`${s.observedAt?'数据':'读取'}于 ${formatted}${s.observedAt?'':' · 源时间未知'}`));grid.append(tile);
    }
    if(!signals.length)grid.append(el('p','empty','还没有分享生活数据。\n在手机工作台的「生活连接」里，选一些值得一起看的事。'));
  }};
}
