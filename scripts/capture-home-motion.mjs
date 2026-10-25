// Real UI animation captured by Chromium. Synthetic installed-app/NAS state only.
import {mkdtemp,mkdir,rm,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';import {join} from 'node:path';
import {createApp} from '../server/app.mjs';import {launchBrowser} from './browser.mjs';
const directory=await mkdtemp(join(tmpdir(),'kaishi-motion-')),origin='http://127.0.0.1:4418';
const app=await createApp({directory,origin,ownerKey:'synthetic-motion-local-owner-123456789'});
await new Promise(r=>app.server.listen(4418,'127.0.0.1',r));const browser=await launchBrowser();let context;
try{
 await mkdir('media/captures',{recursive:true});
 context=await browser.newContext({viewport:{width:1920,height:1080},recordVideo:{dir:directory,size:{width:1920,height:1080}},reducedMotion:'no-preference'});
 const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));await page.goto(origin+'/home');
 await page.evaluate(()=>{apertureHomeApps({apps:['bilibili','tencent','netflix','appletv'].map(id=>({id,installed:true})),slots:[['bilibili','哔哩哔哩'],['tencent','腾讯视频'],['netflix','Netflix'],['appletv','Apple TV']].map((a,i)=>({id:a[0],name:a[1],slot:'quick'+(i+1)}))});apertureHomeSources({nasCount:1,nasPhotos:true});});
 await page.locator('#settings-open').click();await page.locator('[data-action=wallpapers]').click();await page.locator('[data-wallpaper=dusk]').click();await page.waitForFunction(()=>document.querySelector('#pane-backdrop').hidden);
 await page.locator('#home-live').focus();await page.waitForTimeout(700);
 await page.evaluate(()=>apertureRemoteKey('ArrowDown'));await page.waitForTimeout(500);await page.evaluate(()=>apertureRemoteKey('ArrowDown'));await page.waitForTimeout(1000);
 await page.evaluate(()=>{apertureRemoteKey('ArrowRight');apertureRemoteKey('ArrowRight');});await page.waitForTimeout(600);
 await page.evaluate(()=>apertureBack());await page.waitForTimeout(800);
 await page.locator('#dock-toggle').click();await page.waitForTimeout(600);await page.locator('#home-app-add').click();await page.waitForFunction(()=>document.querySelectorAll('.preset-grid button').length===12);await page.waitForTimeout(1200);
 await page.evaluate(()=>apertureBack());await page.waitForTimeout(500);
 const video=page.video();await page.close();await context.close();context=null;await video.saveAs('media/captures/s50-fold-and-art.webm');
 if(errors.length)throw new Error(errors.join('\n'));
 await writeFile('docs/verification/s50-motion-capture.json',JSON.stringify({file:'media/captures/s50-fold-and-art.webm',method:'Chromium video capture of live DOM/CSS; synthetic app and NAS callbacks; no physical TV',errors,oneShotAnimations:true,voiceAndDeviceTested:false},null,2)+'\n');
 console.log('Saved live browser animation: media/captures/s50-fold-and-art.webm');
}finally{if(context)await context.close();await browser.close();await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});}
