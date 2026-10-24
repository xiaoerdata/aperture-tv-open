import {demoBoard} from '/shared/model.mjs';
import {sceneRegistry} from './scenes/engine.js';
const board=demoBoard(),canvas=document.querySelector('canvas'),ctx=canvas.getContext('2d',{alpha:false});
const chapters=[
  {scene:'daylight',kicker:'让电视重新回到生活里',title:'电视，能不能\n更像一个家？',subtitle:'我们正在做一块，属于一家人的共享屏。',caption:'日程 · 留言 · 相册 · 数据'},
  {scene:'daylight',kicker:'01 / 想说的话，不必等到见面',title:'手机留一句话。\n客厅，多一点牵挂。',subtitle:'家人编辑，电视展示。内容只留在自己的家。',caption:'一张便签，就是一件小小的在意'},
  {scene:'gallery',kicker:'02 / 让好时光，回到眼前',title:'相册不该只在\n手机里睡着。',subtitle:'上传照片，用遥控器慢慢看。',caption:'记忆展厅 · 每个家都有自己的收藏'},
  {scene:'landscape',kicker:'03 / 把日常，慢慢堆成山',title:'一起做的小事，\n也有自己的风景。',subtitle:'目标完成的比例，长成三座小小的岛。',caption:'示例数据 · 山体高度对应目标进度'},
  {scene:'daylight',kicker:'智小策 · 首个拟开源项目',title:'给生活，\n开一扇窗。',subtitle:'场景源码、内容服务和电视外壳，准备一起开放。',caption:'开视 Aperture TV · 开发预览'}
];
const $=s=>document.querySelector(s);
function render(time){
  const duration=20,t=Math.max(0,Math.min(duration-.001,time)),index=Math.floor(t/4),chapter=chapters[index],phase=(t%4)/4;
  $('#film').dataset.scene=chapter.scene;$('#film-kicker').textContent=chapter.kicker;$('#film-title').textContent=chapter.title;$('#film-subtitle').textContent=chapter.subtitle;$('#film-caption').textContent=chapter.caption;$('#film-counter').textContent=String(index+1).padStart(2,'0')+' / 05';$('#film-progress-fill').style.width=(t/duration*100)+'%';$('.film-metrics').hidden=chapter.scene!=='landscape';
  const enter=Math.min(1,phase*7),ease=1-Math.pow(1-enter,3);$('.film-copy').style.transform=`translateY(${(1-ease)*20}px)`;$('.film-copy').style.opacity=String(.3+.7*ease);
  const r=canvas.getBoundingClientRect();if(canvas.width!==Math.round(r.width)||canvas.height!==Math.round(r.height)){canvas.width=Math.round(r.width);canvas.height=Math.round(r.height);}sceneRegistry[chapter.scene].draw(ctx,r.width,r.height,t,board);
}
let raf=0,manual=new URLSearchParams(location.search).has('capture'),start=performance.now();
function tick(now){render(((now-start)/1000)%20);if(!document.hidden&&!manual)raf=requestAnimationFrame(tick);}
window.renderFilm=t=>{manual=true;cancelAnimationFrame(raf);render(t);};render(0);if(!manual)raf=requestAnimationFrame(tick);
document.addEventListener('visibilitychange',()=>{cancelAnimationFrame(raf);if(!document.hidden&&!manual)raf=requestAnimationFrame(tick);});
