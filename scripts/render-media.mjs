import {mkdir,writeFile} from 'node:fs/promises';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {launchBrowser} from './browser.mjs';
const origin=process.env.APERTURE_URL || 'http://127.0.0.1:4318';
const ffmpeg=process.env.FFMPEG_PATH || 'ffmpeg';
await mkdir('media/output',{recursive:true});const browser=await launchBrowser();
const page=await browser.newPage({viewport:{width:1080,height:1920},deviceScaleFactor:1});
try{
  await page.goto(origin+'/film?capture=1');await page.waitForFunction(()=>!!window.renderFilm);await page.evaluate(()=>document.fonts.ready);
  for(const [name,time] of [['cover',1],['01-family',5],['02-memories',9],['03-islands',13],['04-open-source',17]]){await page.evaluate(t=>window.renderFilm(t),time);await page.screenshot({path:`media/output/${name}-1080x1920.png`});}
  await page.setViewportSize({width:1080,height:1440});await page.evaluate(()=>window.renderFilm(1));await page.screenshot({path:'media/output/xhs-cover-1080x1440.png'});
  await page.setViewportSize({width:1080,height:1920});
  const output='media/output/aperture-intro-1080x1920.mp4',fps=24,seconds=20;
  const encoder=spawn(ffmpeg,['-hide_banner','-loglevel','error','-y','-f','image2pipe','-vcodec','png','-framerate',String(fps),'-i','-','-an','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',output],{stdio:['pipe','ignore','pipe']});
  let stderr='';encoder.stderr.on('data',b=>stderr+=b.toString());const completion=new Promise((resolve,reject)=>{encoder.on('error',reject);encoder.on('close',code=>code===0?resolve():reject(new Error(stderr||'encoder failed')));});
  for(let i=0;i<fps*seconds;i++){await page.evaluate(t=>window.renderFilm(t),i/fps);const bytes=await page.screenshot({type:'png'});if(!encoder.stdin.write(bytes))await once(encoder.stdin,'drain');if(i%96===0)console.log(`Rendered ${i/fps}s / ${seconds}s`);}
  encoder.stdin.end();await completion;
  await page.setViewportSize({width:1920,height:1080});await page.evaluate(()=>window.renderFilm(1));await page.screenshot({path:'media/output/landscape-title-1920x1080.png'});
  await writeFile('media/output/manifest.json',JSON.stringify({version:'0.1.0-alpha.1',created:'2026-10-23',kind:'deterministic-code-render',video:{file:output,width:1080,height:1920,fps,duration:seconds,audio:false},source:'/public/film.js',usesRealFamilyData:false,hardwareFootage:false,license:'MIT for original code and procedural art'},null,2));
  console.log(output);
}finally{await browser.close();}
