// Real launcher scripts in Chromium; custom-scheme delivery is an explicit native transport double.
import assert from 'node:assert/strict';import {readFile,writeFile} from 'node:fs/promises';import {createHash} from 'node:crypto';import {launchBrowser} from './browser.mjs';
const browser=await launchBrowser(),errors=[],paths=['public/launcher/launcher.js','public/launcher/gallery.js','public/launcher/index.html','scripts/s136-home-album-browser.mjs'],checks=[];
try{for(const width of [960,1920,3840]){
 const p=await browser.newPage({viewport:{width,height:width*9/16}});p.on('pageerror',e=>errors.push(e.message));await p.addInitScript(()=>window.__nativeActions=[]);
 await p.route('https://appassets.androidplatform.net/**',async route=>{const u=new URL(route.request().url());if(u.pathname.startsWith('/assets/')){const name=u.pathname.slice(8);if(!/^[a-zA-Z0-9_./-]+$/.test(name)||name.includes('..'))return route.abort();try{let bytes=await readFile('public/launcher/'+name);if(name==='launcher.js'){const script=bytes.toString();assert.ok(script.includes("location.href = 'aperture://action/' + action;"));bytes=Buffer.from(script.replace("location.href = 'aperture://action/' + action;","window.__nativeActions.push(action);"));}const type=name.endsWith('.html')?'text/html':name.endsWith('.js')?'application/javascript':name.endsWith('.css')?'text/css':name.endsWith('.json')?'application/json':name.endsWith('.jpg')?'image/jpeg':name.endsWith('.webp')?'image/webp':'application/octet-stream';return route.fulfill({contentType:type,body:bytes});}catch{return route.fulfill({status:404,body:''});}}
 if(u.pathname==='/api/gallery')return route.fulfill({contentType:'application/json',body:JSON.stringify({photos:[{id:'1'.repeat(32),src:'/never-read-photo.jpg'}],template:'fade',seconds:12})});return route.fulfill({status:503,contentType:'application/json',body:'{}'});});
 await p.goto('https://appassets.androidplatform.net/assets/index.html');await p.waitForFunction(()=>window.apertureSetHomePage);await p.evaluate(()=>apertureNativeFocus(true));
 for(const target of [1,3,0]){
  await p.evaluate(()=>{apertureSetHomePage(1);document.getElementById('apps-next').focus();apertureRemoteKey('ArrowDown',0,0);});assert.equal(await p.locator('#lobby').getAttribute('data-home-page'),'2');assert.equal(await p.locator('#gallery-home-scene').isVisible(),false);
  const before=await p.evaluate(()=>__nativeActions.filter(x=>x==='home-album').length);assert.equal(before,checks.filter(x=>x.width===width).length+1);
  await p.evaluate(()=>{apertureNativeFocus(false);apertureNativeFocus(true);});assert.equal(await p.evaluate(()=>__nativeActions.filter(x=>x==='home-album').length),before);
  await p.evaluate(target=>apertureSetHomePage(target),target);assert.equal(await p.locator('#lobby').getAttribute('data-home-page'),String(target));
  checks.push({width,target,nativeRequestOnce:true,noWebPhotoLayer:true,focusRestoreDoesNotReenter:true});
 }
 await p.close();}
 assert.deepEqual(errors,[]);const sources={};for(const p of paths)sources[p]=createHash('sha256').update(await readFile(p)).digest('hex');const receipt={passed:true,checks,errors,realLauncherBrowser:true,nativeTransportDouble:true,nativeActivityAndImageRendering:false,hisenseHardware:false,sources};await writeFile('docs/verification/s136-home-album-browser.json',JSON.stringify(receipt,null,2)+'\n');console.log(JSON.stringify(receipt));
}finally{await browser.close();}
