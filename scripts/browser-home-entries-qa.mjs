// S51: real launcher HTML/CSS in Chromium; synthetic app/household callbacks, no television.
import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {parse} from 'acorn';
import {launchBrowser} from './browser.mjs';
const base='https://appassets.androidplatform.net';
const stage=process.env.KAISHI_QA_STAGE||'s52';
const presets=JSON.parse(await readFile('public/launcher/app-presets.json','utf8'));
const names=['art/dusk-lake-v1.webp','art/dusk-lake-thumb-v1.webp','art/memory-archive-v1.webp','art/glass-swallow-v1.webp','index.html','launcher.js','home-personal.js','app-presets.json','style.css','carezza-4k.jpg','namib-4k.jpg','fuji-4k.jpg','lofoten-4k.jpg','namib-thumb.jpg','fuji-thumb.jpg','lofoten-thumb.jpg','fonts/clock-reader.woff2','fonts/aperture-song.woff2'];
names.push('brand/kaishi-icon-v2.webp',...presets.map(entry=>entry.icon),'app-icons/application.svg');
const files=new Map(await Promise.all(names.map(async n=>[n,await readFile(resolve('public/launcher',n))])));
for(const name of ['launcher.js','home-personal.js'])parse(files.get(name).toString(),{ecmaVersion:5});
const mime=n=>n.endsWith('.png')?'image/png':n.endsWith('.svg')?'image/svg+xml':n.endsWith('.webp')?'image/webp':n.endsWith('.js')?'text/javascript':n.endsWith('.json')?'application/json':n.endsWith('.html')?'text/html':n.endsWith('.css')?'text/css':n.endsWith('.woff2')?'font/woff2':'image/jpeg';
const browser=await launchBrowser(),errors=[],checks=[],images=[],measurements=[];
const sample={apps:['bilibili','tencent','netflix','appletv'].map(id=>({id,installed:true,icon:''})),slots:[['bilibili','哔哩哔哩'],['tencent','腾讯视频'],['netflix','Netflix'],['appletv','Apple TV']].map(([id,name],i)=>({id,name,slot:'quick'+(i+1),icon:''}))};
const press=(page,key)=>page.evaluate(k=>apertureRemoteKey(k),key);
const active=page=>page.evaluate(()=>document.activeElement.id);
const capture=async(page,name)=>{const path='docs/images/'+stage+'-home-'+name+'.png';await page.screenshot({path,fullPage:true});images.push(path);};
async function layout(page,edition,screen,expanded){
 const state=await page.evaluate(()=>{
  const box=s=>{const n=typeof s==='string'?document.querySelector(s):s,r=n.getBoundingClientRect();return {id:typeof s==='string'?s:(n.id||n.getAttribute('aria-label')||n.textContent),x:r.x,y:r.y,right:r.right,bottom:r.bottom,w:r.width,h:r.height};};
  const main=['.wordmark','.header-time','#home-watch','#home-dock','#personal-panel','#page-down','.landscape-caption'].map(box).filter(r=>r.w&&r.h);
  return {width:innerWidth,height:innerHeight,main,controls:[...document.querySelectorAll('.lobby-header a,.lobby-header button,.home-screen button,.screen-dots button')].filter(n=>n.offsetWidth&&n.offsetHeight&&n.tabIndex!==-1).map(box).filter(r=>r.w&&r.h),overflow:document.documentElement.scrollWidth>innerWidth||document.documentElement.scrollHeight>innerHeight};
 });
 assert.equal(state.overflow,false,edition+' '+screen+' overflow');
 const scale=Math.min(state.width/1920,state.height/1080),left=(state.width-1920*scale)/2+90*scale,top=(state.height-1080*scale)/2+48*scale;
 for(const r of state.controls){assert.ok(r.x>=left-1&&r.right<=state.width-left+1&&r.y>=top-1&&r.bottom<=state.height-top+1,'safe area '+JSON.stringify(r));assert.ok(r.w/scale>=63&&r.h/scale>=63,'remote target '+JSON.stringify(r));}
 if(edition==='standard'&&screen===0){for(let i=0;i<state.main.length;i++)for(let j=i+1;j<state.main.length;j++){const a=state.main[i],b=state.main[j];assert.ok(a.x>=b.right||b.x>=a.right||a.y>=b.bottom||b.y>=a.bottom,'overlap '+a.id+' '+b.id);}
  const row=state.main.find(n=>n.id==='#home-watch');assert.ok(row.w*row.h/(state.width*state.height)<.06,'compact watching row');
 }
 measurements.push({edition,screen,expanded,...state});
}
try{
 await mkdir('docs/images',{recursive:true});await mkdir('docs/verification',{recursive:true});
 for(const edition of ['standard','parents']){
  const page=await browser.newPage({viewport:{width:1920,height:1080},timezoneId:'Asia/Shanghai',reducedMotion:'reduce'});page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',async route=>{const u=new URL(route.request().url()),name=u.pathname.slice(8);if(u.origin===base&&u.pathname.startsWith('/assets/')&&files.has(name))return route.fulfill({body:files.get(name),contentType:mime(name)});if(u.origin===base&&u.pathname==='/api/board')return route.fulfill({json:{entries:[],photos:[]}});return route.fulfill({status:403,body:''});});
  await page.goto(base+'/assets/index.html?edition='+edition);await page.evaluate(()=>document.fonts.ready);await page.waitForFunction(()=>typeof apertureHomeApps==='function');
  const resize=async(width,height)=>{await page.setViewportSize({width,height});await page.waitForFunction(()=>Math.abs(Number(document.documentElement.style.getPropertyValue('--tv-scale'))-Math.min(innerWidth/1920,innerHeight/1080))<.00001);};
  if(edition==='standard'){
   assert.equal(await active(page),'home-live');assert.deepEqual(await page.locator('#home-watch button').allTextContents(),['看电视','看电视剧','看电影','看综艺']);assert.equal(await page.locator('#personal-panel').isVisible(),false);await capture(page,'standard-empty');
   await press(page,'ArrowLeft');assert.equal(await active(page),'home-live');await press(page,'ArrowRight');assert.equal(await active(page),'home-series');await press(page,'ArrowRight');assert.equal(await active(page),'home-movies');await press(page,'ArrowRight');assert.equal(await active(page),'home-variety');await press(page,'ArrowRight');assert.equal(await active(page),'home-variety');
   await press(page,'ArrowDown');assert.equal(await active(page),'dock-toggle');assert.equal(await page.locator('#home-app-add').isVisible(),false);await press(page,'ArrowDown');assert.equal(await active(page),'home-app-add');
   await press(page,'Enter');assert.equal(await page.locator('#pane-title').textContent(),'添加应用');await press(page,'Escape');assert.equal(await active(page),'home-app-add');await press(page,'Escape');assert.equal(await active(page),'dock-toggle');await press(page,'ArrowUp');assert.equal(await active(page),'home-variety');
   checks.push('empty configuration: four direct viewing entries; initial focus on television; empty app group opens presets; Back/Up preserve focus');
   await page.evaluate(s=>apertureHomeApps(s),sample);await page.locator('#settings-open').click();await page.locator('[data-action=wallpapers]').click();await page.locator('[data-wallpaper=dusk]').click();await page.waitForFunction(()=>document.querySelector('#pane-backdrop').hidden);
   await page.evaluate(()=>apertureHomeSources({nasCount:1,nasPhotos:true}));await page.locator('#home-live').focus();await capture(page,'standard-ready');
   await press(page,'ArrowDown');await press(page,'ArrowDown');assert.equal(await active(page),'dock-quick1');await press(page,'ArrowRight');assert.equal(await active(page),'dock-quick2');await capture(page,'standard-expanded');
   for(const [w,h] of [[960,540],[1280,720],[1920,1080],[3840,2160],[960,500]]){await resize(w,h);await layout(page,edition,0,true);}await resize(1920,1080);
   await press(page,'ArrowDown');assert.equal(await active(page),'page-down');await press(page,'ArrowDown');assert.equal(await page.locator('#lobby').getAttribute('data-home-page'),'1');assert.equal(await active(page),'entry-live');await capture(page,'standard-library');
   await press(page,'ArrowDown');assert.equal(await active(page),'quick1');await press(page,'ArrowRight');assert.equal(await active(page),'quick2');await press(page,'ArrowDown');assert.equal(await page.locator('#lobby').getAttribute('data-home-page'),'2');assert.equal(await active(page),'entry-photos');await capture(page,'standard-family');
   await press(page,'Escape');assert.equal(await active(page),'quick2');await press(page,'Escape');assert.equal(await active(page),'page-down');await press(page,'ArrowUp');assert.equal(await active(page),'dock-toggle');
   checks.push('vertical sequence: primary viewing / folded apps / expanded apps / next screen / library and apps / family; Back restores last visible target');
   // Clicking a screen dot while an app is focused folds the group. The remembered child must not become hidden focus.
   await press(page,'Enter');await press(page,'ArrowRight');await page.locator('[data-home-screen="1"]').evaluate(n=>n.click());await press(page,'Escape');assert.equal(await active(page),'dock-toggle');assert.equal(await page.locator('#dock-quick2').isVisible(),false);checks.push('return after leaving expanded group restores visible group, never hidden child');
   for(const screen of [0,1,2]){await page.locator('[data-home-screen="'+screen+'"]').click();for(const [w,h] of [[960,540],[1280,720],[1920,1080],[3840,2160]]){await resize(w,h);await layout(page,edition,screen,false);}}
   await resize(1920,1080);await page.locator('[data-home-screen="0"]').click();await page.locator('#home-variety').focus();await press(page,'ArrowRight');assert.equal(await active(page),'personal-open');await press(page,'ArrowLeft');assert.equal(await active(page),'home-variety');
   await page.locator('#settings-open').focus();await press(page,'ArrowDown');assert.equal(await active(page),'home-variety');
   await resize(390,844);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await capture(page,'portrait');await page.locator('#page-down').focus();await press(page,'ArrowUp');assert.equal(await active(page),'dock-toggle');assert.equal(await page.evaluate(()=>{var r=document.activeElement.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight;}),true,'portrait bottom controls remain scrollable');await resize(1920,1080);
   checks.push('configured summary reachable from viewing row; header returns to remembered viewing entry; portrait has no horizontal overflow');
  }else{
   await page.evaluate(s=>apertureHomeApps(s),sample);assert.equal(await page.locator('#home-watch').isVisible(),false);assert.equal(await page.locator('.destinations button:visible').count(),4);assert.equal(await active(page),'entry-live');await press(page,'ArrowRight');assert.equal(await active(page),'entry-series');await press(page,'ArrowRight');assert.equal(await active(page),'entry-cinema');await press(page,'ArrowRight');assert.equal(await active(page),'entry-variety');await press(page,'ArrowDown');assert.equal(await active(page),'shortcut-all');await press(page,'ArrowUp');assert.equal(await active(page),'entry-variety');await capture(page,'parents');
   for(const [w,h] of [[960,540],[1280,720],[1920,1080],[3840,2160]]){await resize(w,h);await layout(page,edition,1,false);}checks.push('parents retain four principal entries and smaller installed-app shortcuts');
  }
  await page.close();
 }
 // Browser preview uses the same four viewing categories. Native dispatch stays in MainActivity.
 const preview=await browser.newPage({viewport:{width:1920,height:1080},reducedMotion:'reduce'});
 await preview.route('**/*',async route=>{const u=new URL(route.request().url()),name=u.pathname.slice(8);if(u.pathname.startsWith('/assets/')&&files.has(name))return route.fulfill({body:files.get(name),contentType:mime(name)});if(u.pathname==='/api/board')return route.fulfill({json:{entries:[],photos:[]}});return route.fulfill({contentType:'text/html',body:'<!doctype html><title>Navigation destination fixture</title>'});});
 for(const [id,view] of [['home-live','live'],['home-series','series'],['home-movies','movies'],['home-variety','variety']]){await preview.goto('https://preview.invalid/assets/index.html');await preview.locator('#'+id).click();await preview.waitForURL(u=>u.pathname==='/cinema');assert.equal(new URL(preview.url()).searchParams.get('view'),view);}
 await preview.close();checks.push('all four first-screen viewing entries navigate to their live/series/movies/variety destination in browser preview');
 assert.deepEqual(errors,[]);await writeFile('docs/verification/'+stage+'-home-entries.json',JSON.stringify({checks,images,measurements,errors,es5:true,method:'Chromium real packaged launcher HTML/CSS with synthetic callbacks; bundled official brand assets, synthetic installed-state callbacks',television:false,physicalRemote:false,nativeMediaLaunchTested:false},null,2)+'\n');console.log({checks,images,measurements:measurements.length,errors});
}finally{await browser.close();}
