import {mkdir} from 'node:fs/promises';
import {spawn} from 'node:child_process';
import {launchBrowser} from './browser.mjs';
const browser=await launchBrowser();await mkdir('media/output',{recursive:true});
const context=await browser.newContext({viewport:{width:1920,height:1080},recordVideo:{dir:'artifacts/recording',size:{width:1920,height:1080}}});const page=await context.newPage(),video=page.video();
try{
  await page.goto((process.env.APERTURE_URL||'http://127.0.0.1:4318')+'/tv?demo=1');await page.waitForTimeout(2000);
  await page.locator('button[data-scene=daylight]').focus();await page.keyboard.press('ArrowRight');await page.keyboard.press('Enter');await page.waitForTimeout(2500);
  await page.keyboard.press('ArrowRight');await page.keyboard.press('Enter');await page.waitForTimeout(2500);
  await page.locator('#motion-button').click();await page.waitForTimeout(1500);await page.locator('#motion-button').click();
  await page.locator('button[data-scene=daylight]').click();await page.locator('#immersive-button').click();await page.waitForTimeout(2500);await page.keyboard.press('Escape');await page.waitForTimeout(1000);
}finally{await context.close();await browser.close();}
const input=await video.path(),output='media/output/browser-walkthrough-1920x1080.mp4';
await new Promise((resolve,reject)=>{const p=spawn(process.env.FFMPEG_PATH||'ffmpeg',['-hide_banner','-loglevel','error','-y','-i',input,'-an','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',output],{stdio:'inherit'});p.on('error',reject);p.on('exit',code=>code===0?resolve():reject(new Error('encoding failed')));});
console.log(output);
