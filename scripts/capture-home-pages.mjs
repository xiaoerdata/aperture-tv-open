// Reproducible browser stills, empty isolated household and synthetic shortcut labels.
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,rm,writeFile} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {createApp} from '../server/app.mjs';
import {launchBrowser} from './browser.mjs';
const directory=await mkdtemp(join(tmpdir(),'kaishi-s48-capture-')),origin='http://127.0.0.1:4407';
const app=await createApp({directory,origin,ownerKey:'s48-capture-synthetic-owner-1234567890'});
await new Promise(r=>app.server.listen(4407,'127.0.0.1',r));
const browser=await launchBrowser(),errors=[],images=[];
try{
 await mkdir('docs/images',{recursive:true});
 for(const edition of ['standard','parents']){
  const page=await browser.newPage({viewport:{width:1920,height:1080},timezoneId:'Asia/Shanghai',reducedMotion:'reduce'});page.on('pageerror',e=>errors.push(e.message));
  await page.goto(origin+'/home?edition='+edition+'&quick1='+encodeURIComponent('视频')+'&quick2='+encodeURIComponent('音乐')+'&quick3='+encodeURIComponent('健身')+'&quick4='+encodeURIComponent('戏曲'));
  await page.evaluate(()=>document.fonts.ready);
  const capture=async name=>{const path='docs/images/s48-'+name+'.png';await page.screenshot({path});images.push(path);};
  await capture(edition+'-home');
  if(edition==='standard'){
   await page.evaluate(()=>apertureRemoteKey('ArrowDown'));await capture('standard-apps');
   await page.evaluate(()=>{apertureRemoteKey('ArrowDown');apertureRemoteKey('ArrowDown');});await capture('standard-family');
   await page.locator('#settings-open').click();await page.locator('[data-action=wallpapers]').click();await capture('wallpapers');
   await page.evaluate(()=>apertureRemoteKey('Escape'));await page.locator('#brand-home').click();
  }
  for(const id of edition==='standard'?['namib','fuji','lofoten']:['lofoten']){
   await page.locator('#settings-open').click();await page.locator('[data-action=wallpapers]').click();await page.locator('[data-wallpaper='+id+']').click();
   await page.waitForFunction(()=>document.querySelector('#pane-backdrop').hidden);await capture(edition+'-'+id);
  }
  await page.close();
 }
 assert.deepEqual(errors,[]);await writeFile('docs/verification/s48-captures.json',JSON.stringify({images,errors,method:'desktop Chromium; 1920x1080; empty isolated household; synthetic application names; reduced motion',television:false},null,2)+'\n');console.log({images,errors});
}finally{await browser.close();await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});}
