import {mkdtemp,mkdir,rm,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {createApp} from '../server/app.mjs';
import {launchBrowser} from './browser.mjs';
const directory=await mkdtemp(join(tmpdir(),'aperture-lobby-film-')),origin='http://127.0.0.1:4359';
const app=await createApp({directory,origin,ownerKey:'lobby-film-synthetic-owner-1234567'});await new Promise(r=>app.server.listen(4359,'127.0.0.1',r));
await mkdir('media/output',{recursive:true});const browser=await launchBrowser();let context;
try{
 context=await browser.newContext({viewport:{width:1280,height:720},recordVideo:{dir:resolve('artifacts/glass-film-raw'),size:{width:1280,height:720}}});
 const page=await context.newPage(),pause=ms=>page.waitForTimeout(ms);await page.goto(origin+'/home?demo=1');await page.waitForFunction(()=>typeof window.apertureRemoteKey==='function');await page.mouse.move(0,0);await pause(900);
 for(const index of [1,2,3,0]){await page.locator('[data-slide=\"'+index+'\"]').click();await page.mouse.move(0,0);await pause(800);}
 for(const id of ['live','cinema','system']){await page.locator('#entry-'+id).focus();await pause(550);await page.evaluate(()=>window.apertureRemoteKey('Enter'));await pause(1200);await page.evaluate(()=>window.apertureRemoteKey('Escape'));}
 await page.goto(origin+'/home?edition=parents&demo=1');await page.waitForFunction(()=>typeof window.apertureRemoteKey==='function');await pause(1000);await page.evaluate(()=>window.apertureRemoteKey('ArrowRight'));await pause(700);await page.evaluate(()=>window.apertureRemoteKey('ArrowRight'));await pause(700);await page.locator('#settings-open').click();await pause(1200);await page.keyboard.press('Escape');await pause(600);
 const video=page.video();await context.close();context=null;const output=resolve('media/output/dark-glass-walkthrough-1280.webm');await video.saveAs(output);
 await writeFile('media/output/dark-glass-recording.json',JSON.stringify({output,width:1280,height:720,method:'actual browser recording',syntheticUI:true,androidAppSwitching:false,realTelevision:false,parentsUserStudy:false,credentialsInFrames:false,aiLandscape:true,syntheticHouseholdData:true,design:'dark-glass'},null,2));console.log(output);
}finally{await context?.close();await browser.close();await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});}
