// Spatial D-pad navigation: no hover dependency, and dialogs trap focus.
export function installRemote({back,root=()=>document,gridSelector}) {
  const visible=node=>!node.disabled && node.getClientRects().length && getComputedStyle(node).visibility!=='hidden';
  const items=()=>[...root().querySelectorAll('button,a[href],input,select,textarea,[tabindex="0"]')].filter(visible);
  function handler(event) {
    if(event.key==='Escape'||event.key==='BrowserBack'){if(back()){event.preventDefault();}return;}
    if(event.key==='Tab' && root()!==document){const all=items(),index=all.indexOf(document.activeElement);event.preventDefault();all[(index+(event.shiftKey?-1:1)+all.length)%all.length]?.focus();return;}
    if(event.key==='Enter' && ['BUTTON','A'].includes(event.target.tagName)&&!event.target.disabled){event.preventDefault();event.target.click();return;}
    if(!['ArrowLeft','ArrowRight','ArrowUp','ArrowDown'].includes(event.key))return;
    event.preventDefault();const all=items(),active=document.activeElement;if(!all.includes(active)){all[0]?.focus();return;}
    const grid=gridSelector&&active.closest(gridSelector);
    if(grid){
      const cells=[...grid.querySelectorAll('button')].filter(visible),index=cells.indexOf(active);
      const columns=getComputedStyle(grid).gridTemplateColumns.split(' ').length,column=index%columns;
      const target=event.key==='ArrowLeft'?(column>0?index-1:index):event.key==='ArrowRight'?(column+1<columns?index+1:index):event.key==='ArrowDown'?index+columns:event.key==='ArrowUp'&&index>=columns?index-columns:null;
      if(target!==null){const next=cells[target]||active;next.focus();next.scrollIntoView({block:'nearest'});return;}
    }
    const a=active.getBoundingClientRect(),ax=a.x+a.width/2,ay=a.y+a.height/2;
    const horizontal=event.key==='ArrowLeft'||event.key==='ArrowRight',direction=event.key==='ArrowLeft'||event.key==='ArrowUp'?-1:1;
    const candidates=all.filter(n=>n!==active).map(node=>{const b=node.getBoundingClientRect(),dx=b.x+b.width/2-ax,dy=b.y+b.height/2-ay;const main=(horizontal?dx:dy)*direction,side=Math.abs(horizontal?dy:dx);return {node,main,score:main+side*2.4};}).filter(x=>x.main>4).sort((a,b)=>a.score-b.score);
    if(candidates[0]){candidates[0].node.focus();candidates[0].node.scrollIntoView({block:'nearest'});}
  }
  const previous=window.apertureRemoteKey;const nativeKey=key=>{let handled=false;handler({key,target:document.activeElement||document,shiftKey:false,preventDefault(){handled=true;}});return handled;};window.apertureRemoteKey=nativeKey;
  document.addEventListener('keydown',handler);return ()=>{document.removeEventListener('keydown',handler);if(window.apertureRemoteKey===nativeKey){if(previous)window.apertureRemoteKey=previous;else delete window.apertureRemoteKey;}};
}
