// Actual browser screenshots, isolated demo content. No credentials in frames.
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,rm,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createApp} from '../server/app.mjs';
import {launchBrowser} from './browser.mjs';
const prefix=process.argv[2]||'glass';if(!/^[a-z-]+$/.test(prefix))throw new Error('Invalid screenshot prefix');
const directory=await mkdtemp(join(tmpdir(),'aperture-glass-')),origin='http://127.0.0.1:4362';
const app=await createApp({directory,origin,ownerKey:'glass-demo-synthetic-1234567890123'});
await new Promise(r=>app.server.listen(4362,'127.0.0.1',r));
const browser=await launchBrowser();
try{
 await mkdir('docs/images',{recursive:true});
 const page=await browser.newPage({viewport:{width:1920,height:1080}});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(origin+'/home?demo=1');await page.evaluate(()=>document.fonts.ready);await page.locator('#entry-live').focus();
 const typography=await page.evaluate(()=>({clockPx:parseFloat(getComputedStyle(document.querySelector('#clock')).fontSize),datePx:parseFloat(getComputedStyle(document.querySelector('#date-day')).fontSize),weekdayPx:parseFloat(getComputedStyle(document.querySelector('#date-week')).fontSize),clockFontLoaded:document.fonts.check('92px \"Aperture Clock\"','0123456789:'),dateFontLoaded:document.fonts.check('32px \"Aperture Song\"','年月日星期一二三四五六')}));
 assert.ok(typography.clockFontLoaded&&typography.dateFontLoaded,'local display fonts loaded');
 await page.screenshot({path:'docs/images/'+prefix+'-overview-1920.png'});
 for(const section of ['photos','data','information','tools']){
  await page.locator('.section-nav [data-section='+section+']').click();await page.mouse.move(0,0);
  await page.screenshot({path:'docs/images/'+prefix+'-'+section+'-1920.png'});
  for(const [width,height] of [[1280,720],[1920,1080]]){await page.setViewportSize({width,height});assert.equal(await page.evaluate(()=>document.documentElement.scrollHeight>innerHeight||document.documentElement.scrollWidth>innerWidth),false,section+' '+width);}
 }
 await page.locator('.section-nav [data-section=overview]').click();await page.locator('#entry-cinema').click();await page.screenshot({path:'docs/images/'+prefix+'-cinema-1920.png'});
 await page.goto(origin+'/home?edition=parents&demo=1');await page.screenshot({path:'docs/images/'+prefix+'-parents-1920.png'});
 await page.goto(origin+'/home?demo=1');await page.setViewportSize({width:390,height:844});await page.screenshot({path:'docs/images/'+prefix+'-phone-390.png',fullPage:true});
 await mkdir('artifacts',{recursive:true});await writeFile('artifacts/'+prefix+'-capture.json',JSON.stringify({method:'actual Chromium screenshots',syntheticHouseholdData:true,aiLandscape:true,realTV:false,apkUpdated:false,typography,errors},null,2));
 if(errors.length)throw new Error(errors.join('\n'));console.log('8 actual screenshots captured');
}finally{await browser.close();await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});}
