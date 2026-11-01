import assert from 'node:assert/strict';
import {mkdtemp,rm,writeFile,mkdir} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createServer} from 'node:http';
import {createApp} from '../server/app.mjs';
import {launchBrowser} from './browser.mjs';
const dir=await mkdtemp(join(tmpdir(),'kaishi-4k-')),reserve=createServer();await new Promise(r=>reserve.listen(0,'127.0.0.1',r));const port=reserve.address().port;await new Promise(r=>reserve.close(r));const origin='http://127.0.0.1:'+port,app=await createApp({directory:dir,origin,ownerKey:'synthetic-4k-test-12345678901234567890'});await new Promise(r=>app.server.listen(port,'127.0.0.1',r));const browser=await launchBrowser(),results=[];
try {
 for(const edition of ['standard','parents']) for(const [width,height,dpr] of [[1920,1080,1],[3840,2160,1],[1920,1080,2]]) {
  const ctx=await browser.newContext({viewport:{width,height},deviceScaleFactor:dpr,reducedMotion:'reduce'}), page=await ctx.newPage();
  await page.addInitScript(()=>localStorage.setItem('kaishi-apps-expanded','false'));await page.goto(origin+'/home?edition='+edition+'&demo=1');await page.evaluate(()=>apertureHomeApps({apps:[],slots:[{slot:'quick1',id:'',name:'测试应用'}]}));
  const get=async selector=>page.locator(selector).evaluate(el=>{let r=el.getBoundingClientRect();return {x:r.x/innerWidth,y:r.y/innerHeight,w:r.width/innerWidth,h:r.height/innerHeight}});
  const selector=edition==='parents'?'#entry-live':'#home-live';const watch=await get(selector);assert.ok(watch.x>=0&&watch.y>=0&&watch.x+watch.w<=1&&watch.y+watch.h<=1);
  if(edition==='standard'){await page.locator('#home-live').focus();await page.keyboard.press('ArrowDown');assert.equal(await page.evaluate(()=>document.activeElement.id),'dock-quick1');assert.equal(await page.locator('#home-app-list').isVisible(),true);await page.keyboard.press('ArrowDown');assert.equal(await page.evaluate(()=>document.activeElement.id),'quick1');await page.keyboard.press('Escape');assert.equal(await page.locator('#home-app-list').isVisible(),true);assert.equal(await page.evaluate(()=>document.activeElement.id),'dock-quick1');await page.keyboard.press('ArrowDown');assert.equal(await page.evaluate(()=>document.activeElement.id),'quick1');}
  const appRect=await get('#quick1'),labelRect=await get('#quick1 strong'),logoRect=await get('#quick1 .shortcut-glyph');assert.ok(appRect.x>=0&&appRect.y>=0&&appRect.x+appRect.w<=1&&appRect.y+appRect.h<=1);assert.ok(labelRect.y>=logoRect.y+logoRect.h-0.001);assert.ok(labelRect.y+labelRect.h<=appRect.y+appRect.h+0.001);
  results.push({edition,width,height,dpr,watch,appRect,labelRect,logoRect});if(width===3840){await mkdir('docs/images',{recursive:true});await page.screenshot({path:`docs/images/s87-4k-${edition}.png`});}await ctx.close();
 }
 const mismatch=await browser.newPage({viewport:{width:1920,height:1080},deviceScaleFactor:2,reducedMotion:'reduce'});await mismatch.goto(origin+'/home?demo=1');await mismatch.evaluate(()=>window.apertureSetViewport(960,540));
 for(const id of ['home-live','home-series','home-movies','home-variety']){const r=await mismatch.locator('#'+id).boundingBox();assert.ok(r.width>0&&r.height>0&&(r.x+r.width)*2<=1920&&(r.y+r.height)*2<=1080,JSON.stringify({id,r}));}
 const reference=results.find(x=>x.edition==='standard'&&x.width===1920&&x.dpr===1).watch;const mr=await mismatch.locator('#home-live').boundingBox();assert.ok(Math.abs(mr.width*2/1920-reference.w)<.003);await mismatch.close();
 for(const edition of ['standard','parents']){const variants=results.filter(x=>x.edition===edition);for(const variant of variants.slice(1))for(const name of ['watch','appRect','labelRect','logoRect'])for(const axis of ['x','y','w','h'])assert.ok(Math.abs(variant[name][axis]-variants[0][name][axis])<0.003,`${edition} ${name}.${axis} 4K differs`);}
 await writeFile('docs/verification/s87-4k-browser.json',JSON.stringify({results,syntheticApps:true,actualXiaomi:false},null,2)+'\n');console.log('6 viewport/density cases and mismatched WebView viewport simulation passed');
}finally{await browser.close();await new Promise(r=>app.server.close(r));await rm(dir,{recursive:true,force:true});}
