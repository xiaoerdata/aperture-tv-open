// Sampled browser walkthrough of real launcher UI. No native NAS, session, QR or private media.
import {mkdtemp,mkdir,writeFile,rm} from 'node:fs/promises';import {tmpdir} from 'node:os';import {join} from 'node:path';import {existsSync} from 'node:fs';import {spawn} from 'node:child_process';import {once} from 'node:events';import assert from 'node:assert/strict';
import {createApp} from '../server/app.mjs';import {launchBrowser} from './browser.mjs';
const directory=await mkdtemp(join(tmpdir(),'aperture-nas-entry-film-')),origin='http://127.0.0.1:4366',app=await createApp({directory,origin,ownerKey:'synthetic-nas-film-owner-123456789012345'});
await new Promise(r=>app.server.listen(4366,'127.0.0.1',r));const browser=await launchBrowser(),errors=[];let encoder;
try{
 await mkdir('media/output',{recursive:true});await mkdir('docs/images',{recursive:true});
 const page=await browser.newPage({viewport:{width:1920,height:1080}});page.on('pageerror',e=>errors.push(e.message));
 await page.goto(origin+'/home?edition=parents&demo=1&quick1='+encodeURIComponent('视频应用 · 示例')+'&quick2='+encodeURIComponent('音乐 · 示例')+'&quick3='+encodeURIComponent('健身 · 示例')+'&quick4='+encodeURIComponent('戏曲 · 示例'));
 await page.evaluate(()=>document.fonts.ready);await page.evaluate(()=>{apertureNasLabels('NAS · 周末电影','NAS · 一起追的剧');document.querySelector('#entry-live').focus();var label=document.createElement('div');label.textContent='浏览器界面演示 · 合成目录 · 未连接电视 / NAS';label.style.cssText='position:fixed;top:16px;left:50%;transform:translateX(-50%);z-index:20;background:#111c2de8;color:#eaf1ff;border:1px solid #b5c7e56b;border-radius:20px;padding:8px 18px;font:16px Arial,sans-serif';document.body.appendChild(label);});
 const runtime='/tmp/aperture-media-runtime/imageio_ffmpeg/binaries/ffmpeg-linux-x86_64-v7.0.2',ffmpeg=process.env.FFMPEG_PATH||(existsSync(runtime)?runtime:'ffmpeg'),output='media/output/parents-nas-entries-1920x1080.mp4',fps=12,seconds=12;
 encoder=spawn(ffmpeg,['-hide_banner','-loglevel','error','-y','-f','image2pipe','-vcodec','png','-framerate',String(fps),'-i','-','-an','-c:v','libx264','-preset','fast','-crf','21','-pix_fmt','yuv420p','-movflags','+faststart',output],{stdio:['pipe','ignore','pipe']});let error='';encoder.stderr.on('data',b=>error+=b);const done=new Promise((r,j)=>{encoder.on('error',j);encoder.on('close',code=>code===0?r():j(new Error(error)));});done.catch(()=>{});
 for(let frame=0;frame<fps*seconds;frame++){
  if(frame===30||frame===60)await page.evaluate(()=>apertureRemoteKey('ArrowRight'));
  if(frame===72)await page.screenshot({path:'docs/images/s24-parents-nas-entry-preview-1920.png'});
  if(frame===90){await page.locator('#settings-open').click();await page.evaluate(()=>{apertureRemoteKey('ArrowDown');apertureRemoteKey('ArrowDown');apertureRemoteKey('ArrowDown');});}
  if(frame===126)await page.evaluate(()=>apertureBack());
  const bytes=await page.screenshot({type:'png'});if(!encoder.stdin.write(bytes))await once(encoder.stdin,'drain');
  if(frame%48===0)console.log('Sampled '+frame/fps+'s / '+seconds+'s');
 }
 encoder.stdin.end();await done;assert.deepEqual(errors,[]);
 await writeFile('media/output/parents-nas-entries-manifest.json',JSON.stringify({file:output,width:1920,height:1080,fps,seconds,audio:false,method:'144 browser screenshots sampled into a walkthrough, not a wall-clock latency or TV performance measurement',syntheticNasLabels:true,realNasConnected:false,androidActivity:false,xiaomiDevice:false,credentialsOrQrInFrames:false,errors},null,2)+'\n');console.log(output);
}finally{encoder?.kill();await browser.close();await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});}
