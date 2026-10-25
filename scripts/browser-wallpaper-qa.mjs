// Packaged-origin browser simulation: allowlisted local assets, no Android execution.
import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {parse} from 'acorn';
import {launchBrowser} from './browser.mjs';
parse(await readFile('public/launcher/launcher.js','utf8'),{ecmaVersion:5});
const base='https://appassets.androidplatform.net',errors=[],checks=[],requests=[];
const types=new Map([['index.html','text/html'],['launcher.js','text/javascript'],['home-personal.js','text/javascript'],['app-presets.json','application/json'],['art/dusk-lake-v1.webp','image/webp'],['art/dusk-lake-thumb-v1.webp','image/webp'],['art/memory-archive-v1.webp','image/webp'],['art/glass-swallow-v1.webp','image/webp'],['style.css','text/css'],...['carezza-4k.jpg','namib-4k.jpg','fuji-4k.jpg','lofoten-4k.jpg','namib-thumb.jpg','fuji-thumb.jpg','lofoten-thumb.jpg'].map(n=>[n,'image/jpeg']),...['clock-reader.woff2','aperture-song.woff2'].map(n=>['fonts/'+n,'font/woff2'])]);
const browser=await launchBrowser();
try{
 const context=await browser.newContext({viewport:{width:1920,height:1080},reducedMotion:'reduce'});
 let failFuji=false;
 await context.route('**/*',async route=>{
  const url=new URL(route.request().url()),file=url.pathname.slice('/assets/'.length);requests.push(url.href);
  if(url.origin!==base||!types.has(file))return route.fulfill({status:403,body:''});
  if(failFuji&&file==='fuji-4k.jpg')return route.fulfill({status:404,body:''});
  return route.fulfill({contentType:types.get(file),body:await readFile(resolve('public/launcher',file))});
 });
 const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
 const press=key=>page.evaluate(key=>apertureRemoteKey(key),key);
 const open=async()=>{await page.locator('#settings-open').click();await page.locator('[data-action=wallpapers]').click();};
 await page.goto(base+'/assets/index.html');await page.waitForFunction(()=>typeof apertureRemoteKey==='function');
 assert.equal(await page.evaluate(()=>document.activeElement.id),'home-live');assert.equal(await page.locator('.home-screen button:visible').count(),5);
 assert.equal(await page.locator('.glass:visible').count(),4,'three compact viewing cards and settings, without unconfigured summary cards');
 await press('ArrowDown');assert.equal(await page.evaluate(()=>document.activeElement.id),'dock-toggle');
 await press('ArrowDown');assert.equal(await page.evaluate(()=>document.activeElement.id),'home-app-add');
 await press('ArrowDown');assert.equal(await page.evaluate(()=>document.activeElement.id),'page-down');
 await press('ArrowDown');assert.equal(await page.evaluate(()=>document.activeElement.id),'entry-live');
 await press('ArrowDown');assert.equal(await page.evaluate(()=>document.activeElement.id),'quick1');
 await press('ArrowRight');await press('ArrowDown');assert.equal(await page.evaluate(()=>document.activeElement.id),'entry-photos');
 await press('Escape');assert.equal(await page.evaluate(()=>document.activeElement.id),'quick2');
 await press('Escape');assert.equal(await page.evaluate(()=>document.activeElement.id),'page-down');
 checks.push('first screen has three compact viewing cards; Down traverses app group, then reaches library/apps and household; Back restores previous shortcut');
 await open();assert.equal(await page.locator('[data-wallpaper]').count(),5);
 await press('ArrowDown');assert.equal(await page.evaluate(()=>document.activeElement.dataset.wallpaper),'fuji');
 assert.equal(await page.evaluate(()=>{const r=document.activeElement.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight;}),true);
 await press('ArrowUp');await press('ArrowRight');assert.equal(await page.evaluate(()=>document.activeElement.dataset.wallpaper),'carezza');
 await press('ArrowRight');await press('Enter');await page.waitForFunction(()=>document.querySelector('#pane-backdrop').hidden);assert.equal(await page.evaluate(()=>localStorage.getItem('kaishi-wallpaper-standard')),'namib');
 assert.equal(await page.locator('#pane-actions img').count(),0,'closed picker releases decoded preview images');
 await page.reload();await page.waitForFunction(()=>document.querySelector('#wallpaper-place').textContent.includes('纳米比亚'));
 await open();await press('ArrowRight');await press('Escape');assert.equal(await page.evaluate(()=>localStorage.getItem('kaishi-wallpaper-standard')),'namib','navigation/cancel does not change wallpaper');
 failFuji=true;await open();await page.locator('[data-wallpaper=fuji]').click();await page.waitForFunction(()=>document.querySelector('#action-status').textContent.includes('无法读取'));
 assert.equal(await page.evaluate(()=>localStorage.getItem('kaishi-wallpaper-standard')),'namib');assert.match(await page.locator('#wallpaper-place').textContent(),/纳米比亚/);await press('Escape');failFuji=false;
 checks.push('5 wallpaper choices (4 CC0 photos, 1 generated) reachable by remote; selection persists; cancel/fetch failure retain previous photo; thumbnails released when closed');
 await page.locator('#settings-open').click();await page.locator('[data-action=material]').click();assert.match(await page.locator('body').getAttribute('class'),/reduced-transparency/);
 assert.equal(await page.locator('.scenic-blur').evaluate(n=>getComputedStyle(n).backdropFilter),'none');await press('Escape');
 await page.reload();assert.match(await page.locator('#lobby').getAttribute('class'),/solid/);
 await page.goto(base+'/assets/index.html?edition=parents');await page.waitForFunction(()=>typeof apertureRemoteKey==='function');
 assert.doesNotMatch(await page.locator('#lobby').getAttribute('class'),/solid/);assert.equal(await page.evaluate(()=>localStorage.getItem('kaishi-wallpaper-parents')),null);
 await open();await page.locator('[data-wallpaper=lofoten]').click();await page.waitForFunction(()=>document.querySelector('#pane-backdrop').hidden);
 assert.equal(await page.evaluate(()=>localStorage.getItem('kaishi-wallpaper-parents')),'lofoten');assert.equal(await page.evaluate(()=>localStorage.getItem('kaishi-wallpaper-standard')),'namib');
 await page.locator('#settings-open').click();await page.locator('[data-action=material]').click();await press('Escape');await page.reload();assert.match(await page.locator('#lobby').getAttribute('class'),/solid/);
 checks.push('wallpaper and transparency preferences independent per edition, persistent after reload; reduced transparency removes lower blur');
 await page.evaluate(()=>localStorage.setItem('kaishi-wallpaper-parents','../../private/unknown.jpg'));
 await page.reload();await page.waitForFunction(()=>typeof apertureRemoteKey==='function');assert.match(await page.locator('#wallpaper-place').textContent(),/卡雷扎/);
 assert.ok(!requests.some(u=>u.includes('unknown.jpg')));assert.ok(requests.every(u=>u.startsWith(base+'/assets/')));
 for(const id of ['carezza','namib','fuji','lofoten']){
  const dimensions=await page.evaluate(id=>new Promise((resolve,reject)=>{const img=new Image();img.onload=()=>resolve([img.naturalWidth,img.naturalHeight]);img.onerror=reject;img.src='./'+id+'-4k.jpg';}),id);
  assert.ok(dimensions[0]>=3840&&dimensions[1]>=2160,id+' resolution');
 }
 assert.equal(await page.locator('#apps-home').evaluate(n=>getComputedStyle(n).animationName),'none');
 checks.push('invalid stored ID never becomes a request; all resources local; 4 original/derivative photos at least 3840 by 2160; reduced-motion removes screen animation');
 assert.deepEqual(errors,[]);
 await writeFile('docs/verification/s50-wallpaper-browser.json',JSON.stringify({checks,errors,es5:true,simulatedPackagedOrigin:true,actualAndroid:false,realTelevision:false,localImageEditing:false},null,2)+'\n');console.log({checks,errors});
}finally{await browser.close()}
