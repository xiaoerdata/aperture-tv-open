import {installRemote} from '../ui/remote.js';
// Documentation preview only; no live household data or automatic motion.
const image=document.querySelector('#guide-preview'),link=document.querySelector('#open-preview');
for(const button of document.querySelectorAll('[data-edition]'))button.onclick=()=>{
 const parents=button.dataset.edition==='parents';
 for(const other of document.querySelectorAll('[data-edition]'))other.setAttribute('aria-pressed',String(other===button));
 image.src=parents?'/guide/parents-preview.jpg':'/guide/standard-preview.jpg';image.alt=parents?'开视爸妈版开发界面':'开视标准版开发界面';
 link.href=parents?'/home?edition=parents&demo=1':'/home?demo=1';
};

const disposeRemote=installRemote({back:()=>false});window.addEventListener('pagehide',disposeRemote);
