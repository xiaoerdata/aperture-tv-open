import assert from 'node:assert/strict';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {resolve,extname} from 'node:path';
import {launchBrowser} from './browser.mjs';
// Every response is fulfilled in-process. No HTTP server or upload listener is created.
const out='artifacts/s95-browser';await mkdir(out,{recursive:true});const browser=await launchBrowser(),checks=[],errors=[];
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html'};
try{
 for(const width of [1920,960])for(const count of [0,2]){
  const page=await browser.newPage({viewport:{width,height:width*9/16}}),bridge=[];page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',async route=>{
   const url=new URL(route.request().url());
   if(url.pathname==='/api/gallery')return route.fulfill({contentType:'application/json',body:JSON.stringify({photos:Array.from({length:count},(_,i)=>({id:String(i+1).padStart(32,'0'),src:'/fixture.svg',thumb:'/fixture.svg'})),template:'fade',seconds:12,maxCount:300,bytes:0})});
   if(url.pathname==='/fixture.svg')return route.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="128" height="72"><rect width="128" height="72" fill="#24515b"/></svg>'});
   if(url.protocol==='aperture:'){bridge.push(url.pathname);return route.abort();}
   if(url.hostname==='appassets.androidplatform.net'&&url.pathname.startsWith('/assets/')){const name=url.pathname.slice(8),file=resolve('public/launcher',name);assert.ok(file.startsWith(resolve('public/launcher')+'/'));return route.fulfill({body:await readFile(file),contentType:mime[extname(file)]||'application/octet-stream'});}
   throw new Error('Unexpected request '+url.origin);
  });
  // Native bridge navigation is observed without navigating away or connecting anywhere.
  page.on('request',r=>{if(r.url().startsWith('aperture:'))bridge.push(new URL(r.url()).pathname);});
  await page.goto('https://appassets.androidplatform.net/assets/gallery.html');
  await page.waitForFunction(()=>!!window.KaishiGallerySettings&&document.getElementById('gallery-count').textContent.includes('/'));
  assert.equal(await page.evaluate(()=>document.activeElement.id),'gallery-heading');await page.keyboard.press('Enter');await page.keyboard.press('Enter');assert.deepEqual(bridge,[]);
  await page.keyboard.press('ArrowDown');const first=await page.evaluate(()=>document.activeElement.id);assert.notEqual(first,'gallery-heading');assert.ok(first);
  // Read rendered API readiness and neutral focus; native confirmation is tested by Android probe.
  await page.screenshot({path:`${out}/gallery-${width}-${count}.png`});
  checks.push({width,syntheticPhotos:count,neutralInitialFocus:true,doubleEnterNoBridge:true,remoteDirectionLeavesHeading:true,firstDirectionalFocus:first});await page.close();
 }
 assert.deepEqual(errors,[]);const receipt={passed:true,checks,errors,serverStarted:false,uploadListenerStarted:false,nativeDialogExecuted:false,realXiaomi:false};await writeFile('docs/verification/s95-browser.json',JSON.stringify(receipt,null,2)+'\n');console.log(JSON.stringify(receipt));
}finally{await browser.close();}
