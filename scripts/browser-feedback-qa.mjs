// V17 hardware feedback regression in Chromium with television CSS densities; not physical Xiaomi proof.
import assert from 'node:assert/strict';
import {readFile,readdir,writeFile,mkdir} from 'node:fs/promises';
import {launchBrowser} from './browser.mjs';
const base='https://appassets.androidplatform.net',root=process.env.KAISHI_LAUNCHER_ROOT||'public/launcher/';
const names=(await readdir(root,{recursive:true})).filter(n=>/\.(html|css|js|json|svg|jpg|webp|png|woff2)$/.test(n));
const files=new Map(await Promise.all(names.map(async n=>[n,await readFile(root+n)])));
const mime=n=>({'js':'text/javascript','json':'application/json','html':'text/html','css':'text/css','svg':'image/svg+xml','png':'image/png','jpg':'image/jpeg','webp':'image/webp','woff2':'font/woff2'})[n.split('.').at(-1)];
const board={entries:[{id:'metric-test',type:'metric',title:'温度',value:22.5,target:30,unit:'°C',source:'合成传感器',date:'2026-10-25'},...Array.from({length:8},(_,i)=>({type:'note',id:'n'+i,title:i%2?'到家先喝口水':'晚饭后散步',body:'20:00 出门\n相机充好电。',author:'合成测试'}))],photos:[]};
const browser=await launchBrowser(),checks=[],images=[],errors=[];
const press=(page,key)=>page.evaluate(k=>apertureRemoteKey(k),key);
async function frame(page,selectors){
 const m=await page.evaluate(selectors=>({viewport:{w:innerWidth,h:innerHeight},scroll:{x:scrollX,y:scrollY,lobbyX:document.querySelector('#lobby').scrollLeft,lobbyY:document.querySelector('#lobby').scrollTop},rects:selectors.map(s=>{const e=document.querySelector(s),r=e.getBoundingClientRect();return {s,x:r.x,y:r.y,w:r.width,h:r.height,right:r.right,bottom:r.bottom};})}),selectors);
 assert.deepEqual(m.scroll,{x:0,y:0,lobbyX:0,lobbyY:0});
 for(const r of m.rects){assert.ok(r.w>0&&r.h>0,r.s+' visible');assert.ok(r.x>=m.viewport.w*.025-1&&r.right<=m.viewport.w*.975+1&&r.y>=m.viewport.h*.02-1&&r.bottom<=m.viewport.h*.98+1,JSON.stringify(r));}
 return m;
}
try{
 await mkdir('docs/images',{recursive:true});
 for(const [width,height,density] of [[1920,1080,1],[960,540,2],[1280,720,1.5]]){
  const context=await browser.newContext({viewport:{width,height},deviceScaleFactor:density,reducedMotion:'reduce',timezoneId:'Asia/Shanghai'}),page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
  // Exercise a focus implementation that ignores preventScroll, as embedded engines may do.
  await page.addInitScript(()=>{const focus=HTMLElement.prototype.focus;HTMLElement.prototype.focus=function(){return focus.call(this);};});
  await page.route('**/*',async route=>{const u=new URL(route.request().url()),name=u.pathname.slice(8);if(u.origin===base&&u.pathname.startsWith('/assets/')&&files.has(name))return route.fulfill({body:files.get(name),contentType:mime(name)});if(u.pathname==='/api/board')return route.fulfill({json:board});return route.fulfill({status:404,body:''});});
  await page.goto(base+'/assets/index.html');await page.evaluate(()=>document.fonts.ready);await page.evaluate(()=>apertureNativeFocus(true));await page.waitForFunction(()=>document.querySelector('#personal-kind').textContent==='数据');
  const home=await frame(page,['.wordmark','.header-time','#clock','#date-day','#date-week','#home-watch','#home-dock','#personal-panel']);
  // Menus must scroll their content while the television frame remains stationary.
  await page.locator('#settings-open').click();for(let i=0;i<18;i++)await press(page,'ArrowDown');await frame(page,['.entry-pane']);await press(page,'Escape');
  await page.locator('[data-home-screen="2"]').click();await page.locator('#entry-information').click();
  const messages=await frame(page,['#collection-title','#collection-body .collection-tile:first-child','#collection-body .collection-tile:nth-child(2)']);
  for(let i=0;i<12;i++)await press(page,i%2?'ArrowLeft':'ArrowRight');await frame(page,['#collection-body .collection-tile:first-child','#collection-body .collection-tile:nth-child(2)']);
  if(density===2){const path='docs/images/s53-feedback-messages.png';await page.screenshot({path});images.push(path);}
  await press(page,'Escape');await press(page,'Escape');await press(page,'Escape');await frame(page,['.header-time','#personal-panel','#home-watch']);
  if(density===2){const path='docs/images/s53-feedback-home.png';await page.screenshot({path});images.push(path);}
  checks.push({logicalViewport:[width,height],deviceScaleFactor:density,home,messages,ignorePreventScroll:true});await context.close();
 }
 assert.deepEqual(errors,[]);await writeFile('docs/verification/s53-feedback-browser.json',JSON.stringify({checks,images,errors,method:'Real launcher DOM in Chromium, synthetic shared content, 1x/1.5x/2x television viewport and focus option ignored',realXiaomi:false},null,2)+'\n');console.log({checks:checks.length,images});
}finally{await browser.close();}
