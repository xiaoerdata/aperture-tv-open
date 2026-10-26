// Local browser evidence for compact TV layouts; never Xiaomi hardware acceptance.
import assert from 'node:assert/strict';
import {mkdtemp,rm,mkdir,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';import {join} from 'node:path';import {createServer} from 'node:http';
import {createApp} from '../server/app.mjs';import {launchBrowser} from './browser.mjs';
const directory=await mkdtemp(join(tmpdir(),'kaishi-s58-'));
const reserve=createServer();await new Promise(r=>reserve.listen(0,'127.0.0.1',r));const port=reserve.address().port;await new Promise(r=>reserve.close(r));
const origin=`http://127.0.0.1:${port}`,app=await createApp({directory,origin,ownerKey:'s58-synthetic-owner-1234567890123456'});
await new Promise(r=>app.server.listen(port,'127.0.0.1',r));const browser=await launchBrowser();const checks=[],errors=[],sizes=[];
await mkdir('docs/images',{recursive:true});await mkdir('docs/verification',{recursive:true});
try{
 const page=await browser.newPage({viewport:{width:1920,height:1080}});page.on('pageerror',e=>errors.push(e.message));
 async function shot(name){await page.screenshot({path:`docs/images/s58-${name}.png`,animations:'disabled'});}
 await page.goto(origin+'/home?demo=1');await page.locator('#home-live').waitFor();await page.waitForFunction(()=>document.activeElement.id==='home-live');
 await page.evaluate(()=>window.apertureHomeApps({apps:[],slots:[{slot:'quick1',id:'tencent',name:'腾讯视频'},{slot:'quick2',id:'bilibili',name:'哔哩哔哩'},{slot:'quick3',id:'netflix',name:'Netflix'}]}));await shot('home');await page.screenshot({path:'public/guide/standard-preview.jpg',type:'jpeg',quality:88,animations:'disabled'});
 await page.locator('#personal-open').click();assert.equal(await page.evaluate(()=>document.activeElement.id),'pane-close');await shot('summary-detail');await page.keyboard.press('Escape');await page.locator('#home-live').focus();
 sizes.push(await page.locator('#home-live').boundingBox());assert.ok(sizes[0].height<=80);
 await page.keyboard.press('ArrowDown');assert.equal(await page.evaluate(()=>document.activeElement.id),'dock-toggle');await page.keyboard.press('ArrowDown');assert.equal(await page.locator('#dock-toggle').getAttribute('aria-expanded'),'true');await shot('apps-expanded');await page.keyboard.press('Escape');assert.equal(await page.locator('#dock-toggle').getAttribute('aria-expanded'),'false');
 await page.locator('#page-down').click();await shot('apps');await page.locator('#apps-next').click();await shot('family-home');assert.equal(await page.locator('.data-card,.welcome-space,.content-links,.dashboard').count(),0);await page.locator('#entry-information').click();await shot('messages');await page.keyboard.press('Escape');assert.equal(await page.evaluate(()=>document.activeElement.id),'entry-information');
 await page.locator('#entry-photos').click();await shot('photos-empty');await page.keyboard.press('Escape');
 await page.keyboard.press('F10');await shot('settings');await page.locator('[data-action=wallpapers]').click();await shot('wallpapers');await page.keyboard.press('ArrowRight');assert.ok(await page.evaluate(()=>document.activeElement.classList.contains('wallpaper-option')));await page.keyboard.press('Escape');await page.keyboard.press('F10');await page.locator('[data-action=more-settings]').click();await shot('settings-more');await page.locator('[data-action=presets]').click();await shot('app-presets');assert.ok(await page.locator('.preset-grid img.app-real-icon').count()>3);await page.keyboard.press('Escape');checks.push('home/app fold/page navigation/family reduction/settings/wallpaper/presets/summary and exact back focus');
 await page.goto(origin+'/home?edition=parents');await page.locator('#entry-live').waitFor();await shot('parents');await page.screenshot({path:'public/guide/parents-preview.jpg',type:'jpeg',quality:88,animations:'disabled'});const parentCard=await page.locator('#entry-live').boundingBox();assert.ok(parentCard.height<=174);sizes.push(parentCard);await page.keyboard.press('ArrowDown');assert.match(await page.evaluate(()=>document.activeElement.id),/^quick/);checks.push('parents compact four viewing entries and D-pad shortcuts');
 await page.goto(origin+'/tv?demo=1');await page.locator('#notes .note').waitFor();await shot('family');assert.equal(await page.locator('#scene-canvas,.scene-nav,#metrics,.life-tv').count(),0);await page.locator('#connect-button').click();await page.waitForFunction(()=>document.querySelector('#pair-code').textContent.match(/\d/));await shot('pairing');await page.keyboard.press('Escape');assert.equal(await page.evaluate(()=>document.activeElement.id),'connect-button');checks.push('family photos/agenda/notes only; actual pairing start and modal back focus');
 for(const width of [960,1280,1920,3840,390]){
  await page.setViewportSize({width,height:width===390?844:Math.round(width*9/16)});
  for(const path of ['/home?demo=1','/home?edition=parents','/tv?demo=1']){await page.goto(origin+path);await page.waitForTimeout(150);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false,`${path}@${width}`);}
 }
 await shot('family-phone');checks.push('home, parents, family no horizontal overflow at 960/1280/1920/3840/390');
 assert.deepEqual(errors,[]);await writeFile('docs/verification/s58-ui-browser.json',JSON.stringify({checks,sizes,errors,localBrowser:true,syntheticData:true,nativeActivityExecuted:false,xiaomiHardware:false},null,2)+'\n');console.log(JSON.stringify({checks,errors}));
}finally{await browser.close();await new Promise(r=>app.server.close(r));app.cinema?.close?.();await rm(directory,{recursive:true,force:true});}
