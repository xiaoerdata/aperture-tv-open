import {drawDaylight} from './daylight.js';
import {drawGallery} from './gallery.js';
import {drawLandscape} from './landscape.js';
export const sceneRegistry={
  daylight:{name:'日光庭院', draw:drawDaylight, title:'日子很平常。\n一起就很好。',description:'今天的安排，想说的话，和想留住的瞬间。',art:'日光庭院 · 程序艺术'},
  gallery:{name:'记忆展厅',draw:drawGallery,title:'好时光，\n值得常常看。',description:'让手机里的照片，回到家人眼前。',art:'记忆展厅 · 家庭相册'},
  landscape:{name:'数据群岛',draw:drawLandscape,title:'小小坚持，慢慢成山。',description:'山体高度对应目标进度；具体数值以下方标注为准。',art:'数据群岛 · 目标进度的艺术表达'}
};
export function createSceneEngine(canvas) {
  const ctx=canvas.getContext('2d',{alpha:false});let board={entries:[]},scene='daylight',paused=false,last=0,elapsed=0,raf=0,disposed=false;
  const reduce=matchMedia('(prefers-reduced-motion: reduce)');
  function draw(time=elapsed){const r=canvas.getBoundingClientRect(),dpr=Math.min(devicePixelRatio||1,1.25);const w=Math.round(r.width*dpr),h=Math.round(r.height*dpr);if(!w||!h)return;if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}ctx.setTransform(dpr,0,0,dpr,0,0);sceneRegistry[scene].draw(ctx,r.width,r.height,time,board);}
  function tick(now){if(disposed||document.hidden||paused||reduce.matches){raf=0;return;}if(!document.hidden&&!paused&&!reduce.matches&&now-last>=1000/24){elapsed+=Math.min((now-last)/1000,.1);last=now;draw();}raf=requestAnimationFrame(tick);}
  function visibility(){if(document.hidden){cancelAnimationFrame(raf);raf=0;}else{last=performance.now();if(!raf&&!paused&&!reduce.matches)raf=requestAnimationFrame(tick);draw();}}
  const resize=()=>draw();const motionChange=()=>{cancelAnimationFrame(raf);raf=0;if(!document.hidden&&!paused&&!reduce.matches)raf=requestAnimationFrame(tick);draw();};window.addEventListener('resize',resize);document.addEventListener('visibilitychange',visibility);reduce.addEventListener?.('change',motionChange);
  raf=requestAnimationFrame(tick);
  return {set(next,which){board=next;scene=which;draw();},pause(value){paused=value;motionChange();},capture(time){draw(time);},dispose(){disposed=true;cancelAnimationFrame(raf);window.removeEventListener('resize',resize);document.removeEventListener('visibilitychange',visibility);reduce.removeEventListener?.('change',motionChange);}};
}
