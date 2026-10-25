// Packaged HTTPS simulated in Chromium. Synthetic household/app data; no real television.
import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {launchBrowser} from './browser.mjs';
const base='https://appassets.androidplatform.net';
const names=['brand/kaishi-icon-v2.webp','art/dusk-lake-v1.webp','art/dusk-lake-thumb-v1.webp','art/memory-archive-v1.webp','art/glass-swallow-v1.webp','index.html','launcher.js','home-personal.js','app-presets.json','style.css','carezza-4k.jpg','namib-4k.jpg','fuji-4k.jpg','lofoten-4k.jpg','namib-thumb.jpg','fuji-thumb.jpg','lofoten-thumb.jpg','fonts/clock-reader.woff2','fonts/aperture-song.woff2'];
const files=new Map(await Promise.all(names.map(async n=>[n,await readFile(resolve('public/launcher',n))])));
const mime=n=>n.endsWith('.webp')?'image/webp':n.endsWith('.js')?'text/javascript':n.endsWith('.json')?'application/json':n.endsWith('.html')?'text/html':n.endsWith('.css')?'text/css':n.endsWith('.woff2')?'font/woff2':'image/jpeg';
const browser=await launchBrowser(),errors=[],checks=[],images=[];
let board={entries:[],photos:[]},authorized=true;
const sample={apps:['bilibili','tencent','netflix','appletv'].map(id=>({id,installed:true,icon:''})),slots:[['bilibili','哔哩哔哩'],['tencent','腾讯视频'],['netflix','Netflix'],['appletv','Apple TV']].map(([id,name],i)=>({id,name,slot:'quick'+(i+1),icon:''}))};
const press=(page,key)=>page.evaluate(k=>apertureRemoteKey(k),key);
try{
 await mkdir('docs/images',{recursive:true});await mkdir('docs/verification',{recursive:true});
 for(const edition of ['standard','parents']){
  const page=await browser.newPage({viewport:{width:1920,height:1080},timezoneId:'Asia/Shanghai',reducedMotion:'reduce'});
  page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',async route=>{const u=new URL(route.request().url()),name=u.pathname.slice(8);if(u.origin===base&&u.pathname.startsWith('/assets/')&&files.has(name))return route.fulfill({body:files.get(name),contentType:mime(name)});if(u.origin===base&&u.pathname==='/api/board')return route.fulfill({status:authorized?200:403,json:board});return route.fulfill({status:403,body:''});});
  await page.goto(base+'/assets/index.html?edition='+edition);await page.evaluate(()=>document.fonts.ready);
  await page.waitForFunction(()=>typeof apertureHomeApps==='function');
  const resize=async(width,height)=>{await page.setViewportSize({width,height});await page.waitForFunction(()=>Math.abs(Number(document.documentElement.style.getPropertyValue('--tv-scale'))-Math.min(innerWidth/1920,innerHeight/1080))<0.00001);};
  const capture=async name=>{const path='docs/images/s50-'+name+'.png';await page.screenshot({path,fullPage:true});images.push(path);};
  const refresh=async()=>{await page.evaluate(()=>apertureNativeFocus(true));await page.waitForTimeout(80);};
  await refresh();
  if(edition==='standard'){
   assert.equal(await page.locator('#personal-panel').isVisible(),false);await capture('standard-empty');
  }
  await page.evaluate(s=>apertureHomeApps(s),sample);
  await page.locator('#settings-open').click();await page.locator('[data-action=wallpapers]').click();await page.locator('[data-wallpaper=dusk]').click();await page.waitForFunction(()=>document.querySelector('#pane-backdrop').hidden);
  if(edition==='standard'){
   await page.locator('#page-down').focus();await capture('standard-apps');
   await press(page,'ArrowLeft');assert.equal(await page.evaluate(()=>document.activeElement.id),'dock-toggle');assert.equal(await page.locator('#dock-quick1').isVisible(),false);await press(page,'ArrowDown');assert.equal(await page.evaluate(()=>document.activeElement.id),'dock-quick1');await capture('standard-apps-expanded');
   await press(page,'ArrowRight');assert.equal(await page.evaluate(()=>document.activeElement.id),'dock-quick2');
   checks.push('collapsed group hides individual targets; Down/Enter unfolds; arrows select; Back collapses');
   await press(page,'Escape');assert.equal(await page.evaluate(()=>document.activeElement.id),'dock-toggle');assert.equal(await page.locator('#dock-quick2').isVisible(),false);await press(page,'Enter');
   await press(page,'ArrowDown');assert.equal(await page.evaluate(()=>document.activeElement.id),'page-down');await press(page,'ArrowDown');assert.equal(await page.locator('#lobby').getAttribute('data-home-page'),'1');await press(page,'Escape');
   await page.evaluate(()=>apertureHomeSources({nasCount:1,nasPhotos:true}));assert.equal(await page.locator('#personal-count').textContent(),'1 / 1');assert.equal(await page.locator('#personal-next').isVisible(),false);await capture('standard-nas');
   await resize(390,844);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await capture('standard-portrait');await resize(1920,1080);
   board={entries:[{type:'metric',title:'今日步数',value:6842,unit:'步',target:8000,date:'2026-10-25',source:'合成测试数据'},{type:'note',title:'周末去走走',body:'相机充好电，明早一起出发。',author:'合成留言'}],photos:[]};await refresh();assert.equal(await page.locator('#personal-count').textContent(),'3 / 3');
   await page.locator('#personal-next').click();await capture('standard-personal');
   await page.locator('#personal-open').click();assert.equal(await page.locator('#collection-view').isVisible(),true);await press(page,'Escape');assert.equal(await page.locator('#lobby').getAttribute('data-home-page'),'0');
   for(const [width,height] of [[960,540],[1280,720],[1920,1080]]){
    await resize(width,height);const rects=await page.evaluate(()=>['.header-time','#home-watch','#personal-panel','#home-dock','#page-down'].map(s=>{const r=document.querySelector(s).getBoundingClientRect();return {s,x:r.x,y:r.y,r:r.right,b:r.bottom};}));
    for(const a of rects)assert.ok(a.x>=0&&a.y>=0&&a.r<=width+1&&a.b<=height+1,JSON.stringify(a));for(let i=0;i<rects.length;i++)for(let j=i+1;j<rects.length;j++){const a=rects[i],b=rects[j];assert.ok(a.x>=b.r||b.x>=a.r||a.y>=b.b||b.y>=a.b,'overlap '+a.s+' '+b.s);}
   }
   // Polling must not keep postponing the carousel. A virtual clock avoids a real wait.
   await page.clock.install();await page.emulateMedia({reducedMotion:'no-preference'});await page.locator('#page-down').focus();await refresh();
   const before=await page.locator('#personal-count').textContent();await page.clock.fastForward(13000);assert.notEqual(await page.locator('#personal-count').textContent(),before);
   await page.locator('#personal-open').focus();const paused=await page.locator('#personal-count').textContent();await page.clock.fastForward(26000);assert.equal(await page.locator('#personal-count').textContent(),paused);
   await page.evaluate(()=>apertureNativeFocus(false));await page.locator('#page-down').focus();await page.clock.fastForward(26000);assert.equal(await page.locator('#personal-count').textContent(),paused);
   await page.emulateMedia({reducedMotion:'reduce'});await page.clock.resume();checks.push('12s carousel survives 5s polling; focus/native-overlay pause; reduced-motion fallback');
   authorized=false;await refresh();assert.equal(await page.locator('#personal-count').textContent(),'1 / 1');assert.equal(await page.locator('#personal-kind').textContent(),'NAS');await page.locator('#personal-open').focus();await page.evaluate(()=>apertureHomeSources({}));assert.equal(await page.evaluate(()=>document.activeElement.id),'home-live');assert.equal(await page.locator('#personal-panel').isVisible(),false);assert.equal(await page.locator('#personal-title').textContent(),'');assert.equal(await page.locator('#personal-photo').getAttribute('src'),null);authorized=true;
   await refresh();await page.locator('#personal-open').click();assert.equal(await page.locator('#collection-view').isVisible(),true);authorized=false;await refresh();await press(page,'Escape');assert.equal(await page.evaluate(()=>document.activeElement.id),'home-live');authorized=true;
   checks.push('summary removed while its collection is open returns to a visible primary entry');checks.push('empty/single/multiple summaries; NAS saved state; access removal clears household; 540p/720p/1080p no collisions; D-pad dock and page return');
  } else {await capture('parents-apps');assert.equal(await page.locator('.destination:visible').count(),3);}
  await page.locator('#settings-open').click();await page.locator('[data-action=presets]').click();await page.waitForFunction(()=>document.querySelectorAll('.preset-grid .pane-action').length===12);await capture(edition+'-presets');
  assert.equal(await page.evaluate(()=>{const a=document.querySelector('#pane-swallow').getBoundingClientRect(),b=document.querySelector('#pane-close').getBoundingClientRect();return a.bottom<=b.top||a.top>=b.bottom||a.right<=b.left||a.left>=b.right;}),true,'illustration does not cover return');
  assert.match(await page.locator('[data-action=preset-bilibili]').textContent(),/已安装/);assert.match(await page.locator('[data-action=preset-kodi]').textContent(),/未安装/);
  await page.locator('[data-action=preset-bilibili]').focus();await press(page,'ArrowDown');assert.equal(await page.evaluate(()=>document.activeElement.dataset.action),'preset-youku');
  await press(page,'Escape');assert.equal(await page.evaluate(()=>document.activeElement.id),'settings-open');await page.evaluate(()=>apertureHomeApps({apps:[],slots:[]}));assert.equal(await page.locator('#home-apps button').count(),0);assert.equal(await page.locator('#quick1 strong').textContent(),'添加应用');checks.push(edition+': 12 app presets, installed distinction and D-pad grid, removing apps clears both surfaces');
  await page.locator('#settings-open').click();await page.locator('[data-action=hub]').click();assert.equal(await page.locator('[data-action=hub-connect]').isDisabled(),true);await capture(edition+'-hub-entry');await press(page,'Escape');checks.push(edition+': Hub entry reserved, login explicitly unavailable');
  await page.locator('#settings-open').click();await page.locator('[data-action=motion]').click();assert.equal(await page.evaluate(()=>localStorage.getItem('kaishi-motion')),'off');await press(page,'Escape');
  await page.close();board={entries:[],photos:[]};
 }
 assert.deepEqual(errors,[]);await writeFile('docs/verification/s50-home-personal.json',JSON.stringify({checks,images,errors,method:'Chromium packaged HTTPS mock; synthetic household and installed-state callbacks; textual brand placeholders only',nativePackageManager:false,television:false},null,2)+'\n');console.log({checks,images});
}finally{await browser.close();}
