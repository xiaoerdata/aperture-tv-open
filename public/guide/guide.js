const scenes={morning:['07:30 / 出门前','今天，带把伞。','16:30 接小满放学','今日阅读 20 分钟'],home:['18:30 / 回家时','家里，刚刚好。','客厅温度 24.6°C','今晚吃番茄牛腩'],cinema:['20:30 / 全家观影','今晚，重温那个夏天。','来自：家庭旅行视频','家庭影院 · 按需开启']};
const house=document.querySelector('.house-scene');
for(const button of document.querySelectorAll('[data-day]'))button.onclick=()=>{house.dataset.day=button.dataset.day;for(const b of document.querySelectorAll('[data-day]'))b.setAttribute('aria-pressed',String(b===button));const data=scenes[button.dataset.day];['mini-kicker','mini-title','mini-detail-1','mini-detail-2'].forEach((id,i)=>document.getElementById(id).textContent=data[i]);};
const motion=document.querySelector('#guide-motion'),reduced=matchMedia('(prefers-reduced-motion: reduce)');let paused=false,visible=true;
function renderMotion(){house.classList.toggle('paused',paused||reduced.matches||!visible);motion.disabled=reduced.matches;motion.textContent=reduced.matches?'系统减少动态':paused?'播放动效':'暂停动效';motion.setAttribute('aria-pressed',String(paused||reduced.matches));}
motion.onclick=()=>{paused=!paused;renderMotion();};reduced.addEventListener('change',renderMotion);renderMotion();
document.addEventListener('visibilitychange',()=>document.body.classList.toggle('backgrounded',document.hidden));
const observer=new IntersectionObserver(([entry])=>{visible=entry.isIntersecting;renderMotion();});observer.observe(house);window.addEventListener('pagehide',()=>observer.disconnect());
