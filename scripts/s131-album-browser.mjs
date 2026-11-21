// Full launcher in Chromium, synthetic photographs; no Hisense/Xiaomi renderer proof.
import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir,mkdtemp,rm} from 'node:fs/promises';
import {createHash} from 'node:crypto';import {tmpdir} from 'node:os';import {join} from 'node:path';
import {createServer} from 'node:http';
import {createApp} from '../server/app.mjs';import {launchBrowser} from './browser.mjs';
const dir=await mkdtemp(join(tmpdir(),'kaishi-s131-')),reserve=createServer();await new Promise(r=>reserve.listen(0,'127.0.0.1',r));const port=reserve.address().port;await new Promise(r=>reserve.close(r));const origin='http://127.0.0.1:'+port,app=await createApp({directory:dir,origin,ownerKey:'s131-synthetic-owner-12345678901234567890'});
await new Promise(r=>app.server.listen(port,'127.0.0.1',r));const browser=await launchBrowser(),checks=[],errors=[];const out='docs/images/s131-album';await mkdir(out,{recursive:true});
try{
 for(const width of [960,1920,3840]){
  const p=await browser.newPage({viewport:{width,height:width*9/16},reducedMotion:'reduce'});p.on('pageerror',e=>{errors.push(e.message);console.error(e.message)});let photos=[];
  await p.route('**/api/gallery',r=>r.fulfill({contentType:'application/json',body:JSON.stringify({photos,template:'fade',seconds:12})}));
  await p.route('**/synthetic-photo.svg*',r=>r.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="900"><rect width="1600" height="900" fill="#173c52"/><circle cx="1100" cy="300" r="170" fill="#ecd7a1"/><path d="M0 900V700L500 300 1100 900" fill="#276075"/></svg>'}));
  await p.route('**/bad-photo.jpg',r=>r.fulfill({status:404,body:''}));
  await p.goto(origin+'/home');await p.waitForFunction(()=>window.KaishiGallery&&window.KaishiAmbient);
  await p.evaluate(()=>{document.querySelector('[data-home-screen="2"]').click();});
  await p.waitForFunction(()=>document.getElementById('gallery-home-count').textContent==='0 张照片');
  photos=[{id:'1'.repeat(32),src:'/synthetic-photo.svg'}];
  // Advance only the real gallery poll; no lifecycle toggle or manual refresh.
  await p.waitForFunction(()=>!!document.querySelector('#gallery-home-scene .current img'),{},{timeout:12000});
  await p.waitForFunction(()=>{let n=document.querySelector('#gallery-home-scene .current img');return n&&n.naturalWidth>0&&getComputedStyle(n.parentNode).opacity==='1';});
  const bounds=await p.locator('#gallery-home-scene .current img').boundingBox();assert.equal(bounds.width,width);assert.equal(bounds.height,width*9/16);
  assert.equal(await p.locator('#gallery-home-scene .gallery-empty').isVisible(),false);
  await p.evaluate(()=>{let n=document.createElement('div');n.textContent='浏览器示意 · 合成照片 · 非电视截图';n.style.cssText='position:fixed;top:12px;left:12px;z-index:999;color:white;background:#000a;padding:8px;font:18px sans-serif';document.body.appendChild(n)});
  await p.screenshot({path:`${out}/home-${width}.png`});
  if(width===1920){photos.push({id:'2'.repeat(32),src:'/synthetic-photo.svg?2'});await p.evaluate(()=>KaishiGallery.refresh());await p.waitForFunction(()=>document.getElementById('gallery-home-count').textContent==='2 张照片');await p.waitForFunction(()=>document.querySelector('#gallery-home-scene .current img')?.getAttribute('src').includes('?2'),{},{timeout:15000});}
  photos=[{id:'2'.repeat(32),src:'/bad-photo.jpg'}];await p.evaluate(()=>KaishiGallery.refresh());await p.waitForTimeout(200);
  assert.ok((await p.locator('#gallery-home-scene .current img').getAttribute('src')).includes('synthetic-photo'));
  photos=[];await p.evaluate(()=>KaishiGallery.refresh());await p.waitForFunction(()=>!document.querySelector('#gallery-home-scene .current img'));
  checks.push({width,firstUploadViaPoll:true,visibleWithoutOpacityAnimation:true,fullViewport:bounds,failedImagePreservesFrame:true,lastDeletionClearsFrame:true});
  await p.close();
 }
 const p=await browser.newPage();p.on('pageerror',e=>errors.push(e.message));
 for(const method of ['button','back','preview']){await p.goto(origin+'/launcher/ambient.html?panel=settings');await p.waitForFunction(()=>window.KaishiAmbient&&KaishiAmbient.state().settings);
  if(method==='button')await p.getByRole('button',{name:'返回',exact:true}).click();else if(method==='back')await p.keyboard.press('Escape');else{await p.getByRole('button',{name:'预览',exact:true}).click();await p.keyboard.press('Escape');}
  await p.waitForURL(/\/launcher\/index\.html$/);
 }
 await p.close();assert.deepEqual(errors,[]);const paths=['public/launcher/launcher.js','public/launcher/gallery.js','public/launcher/gallery.css','public/launcher/ambient.js','scripts/s131-album-browser.mjs'];const sources={};for(const path of paths)sources[path]=createHash('sha256').update(await readFile(path)).digest('hex');
 const receipt={passed:true,checks,standaloneSettingsButtonBackPreviewReturn:true,rotationContinuesThroughUnchangedPolling:true,errors,syntheticPhotos:true,fullLauncherBrowser:true,realAndroid:false,hisenseTested:false,sources};await writeFile('docs/verification/s131-album-browser.json',JSON.stringify(receipt,null,2)+'\n');console.log(JSON.stringify(receipt));
}finally{await browser.close();await new Promise(r=>app.server.close(r));await rm(dir,{recursive:true,force:true});}
