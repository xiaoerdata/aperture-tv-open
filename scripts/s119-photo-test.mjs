import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {launchBrowser} from './browser.mjs';
const out='docs/images/s119-photo-fix';await mkdir(out,{recursive:true});
const css=await readFile('public/launcher/gallery.css','utf8');
const old=await readFile('tests/fixtures/photo-v45/gallery.css','utf8');
const js=await readFile('public/launcher/gallery.js','utf8');
const browser=await launchBrowser();const checks=[];
try{
 for(const width of [960,1920,3840])for(const variant of ['before','after']){
  const page=await browser.newPage({reducedMotion:'reduce',viewport:{width,height:width*9/16}});let photos=[];
  await page.route('**/*',r=>{const u=new URL(r.request().url());
   if(u.pathname==='/api/gallery')return r.fulfill({contentType:'application/json',body:JSON.stringify({photos,template:'fade',seconds:12})});
   if(u.pathname==='/photo.svg')return r.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="160" height="90"><rect width="160" height="90" fill="#2a687d"/></svg>'});
   return r.abort();
  });
  // Emulate a WebView which ignores CSS inset; this is not an actual old Android device.
  const style=(variant==='before'?old:css).replace(/inset\s*:\s*[^;}]+;?/g,'');
  await page.setContent(`<base href="https://appassets.androidplatform.net/"><style>${style}</style><div id="gallery-home-scene" class="home-album-backdrop"></div><button id="gallery-home-settings"></button><small id="gallery-home-count"></small>`);
  await page.addScriptTag({content:js});
  await page.evaluate(()=>{KaishiGallery.mount(document.getElementById('gallery-home-scene'),'home');KaishiGallery.homePage(true)});
  await page.waitForFunction(()=>document.getElementById('gallery-home-count').textContent==='0 张照片');
  photos=[{src:'/photo.svg',thumb:'/photo.svg',id:'1'.repeat(32)}];
  await page.evaluate(()=>KaishiGallery.active(true));
  await page.waitForFunction(()=>document.getElementById('gallery-home-count').textContent==='1 张照片');
  await page.waitForFunction(()=>!!document.querySelector('.gallery-frame.current img'));
  const rect=await page.locator('.gallery-stage').evaluate(n=>{const r=n.getBoundingClientRect();return {width:r.width,height:r.height}});
  if(variant==='before')assert.ok(rect.width===0||rect.height===0,JSON.stringify(rect));
  else {
   assert.equal(rect.width,width);assert.equal(rect.height,width*9/16);
   await page.evaluate(()=>{KaishiGallery.homePage(false);KaishiGallery.homePage(true)});
   await page.waitForFunction(()=>!!document.querySelector('.gallery-frame.current img'));
   photos.push({src:'/photo.svg',thumb:'/photo.svg',id:'2'.repeat(32)});
   await page.evaluate(()=>KaishiGallery.refresh());
   await page.waitForFunction(()=>document.getElementById('gallery-home-count').textContent==='2 张照片');
   await page.waitForFunction(()=>{const i=document.querySelector('.gallery-frame.current img');return i&&i.complete&&i.naturalWidth>0&&getComputedStyle(i.parentNode).opacity==='1'&&getComputedStyle(document.querySelector('.gallery-empty')).display==='none'});
   await page.screenshot({path:`${out}/home-${width}.png`});
  }
  checks.push({width,variant,rect,oldInsetEmulated:true});await page.close();
 }
 const native=await readFile('android/app/src/main/java/org/zhixiaoce/aperture/NativeGallery.java','utf8');
 for(const required of ['native-gallery-manage','this::manage','confirmDelete(photoId)','setNegativeButton("保留"','store.delete(id);refresh()','dismissManage();uploadClosed()'])assert.ok(native.includes(required),required);
 const sources={};for(const path of ['public/launcher/gallery.css','public/launcher/gallery.js','tests/fixtures/photo-v45/gallery.css','android/app/src/main/java/org/zhixiaoce/aperture/NativeGallery.java'])sources[path]=createHash('sha256').update(await readFile(path)).digest('hex');
 const receipt={passed:true,sources,checks,nativeManagementSourceChecks:true,nativeUIExecuted:false,realTV:false,photosSynthetic:true,networkListener:false};
 await writeFile('docs/verification/s119-photo-browser.json',JSON.stringify(receipt,null,2)+'\n');console.log(JSON.stringify(receipt));
}finally{await browser.close()}
