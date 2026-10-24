// Editorial animation driven by actual production-controller JVM traces. Not a TV/app capture.
import {readFile, mkdir, writeFile} from 'node:fs/promises';
import {existsSync} from 'node:fs';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import assert from 'node:assert/strict';
import {launchBrowser} from './browser.mjs';

const report = JSON.parse(await readFile('artifacts/playback-session-report.json', 'utf8'));
assert.equal(report.productionJava, true); assert.equal(report.realDevice, false);
const states = report.traces.find(t => t.startsWith('TRACE prepare-loss ')).slice('TRACE prepare-loss '.length).split(' -> ');
assert.deepEqual(states, ['PREPARING', 'INTERRUPTED', 'INTERRUPTED', 'PLAYING']);
const image = (await readFile('public/launcher/alpine-dusk.png')).toString('base64');
const html = `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>开窗 · 播放中断逻辑演示</title>
<style>
*{box-sizing:border-box}body{margin:0;width:1920px;height:1080px;background:#0b131a;color:#edf1f3;font-family:"Noto Sans CJK SC",sans-serif}
body:before{content:"";position:absolute;inset:0;background:linear-gradient(90deg,#08121bea,#08121b8c),url(data:image/png;base64,${image}) center/cover;opacity:.8}
main{position:relative;padding:64px 92px}header{display:flex;justify-content:space-between;align-items:center;font-size:24px;color:#c7d2d9}
.boundary{padding:13px 22px;border:1px solid #93a6b851;border-radius:30px;background:#12212abf}h1{font:500 72px/1.4 "Noto Serif CJK SC",serif;margin:74px 0 12px}p{font-size:28px;color:#c7d2d9;margin:0}
.glass{margin-top:46px;padding:46px 50px;border-radius:32px;background:linear-gradient(120deg,#c5e2ee1c,#91b8c707);border:1px solid #d4e8f131;box-shadow:inset 0 1px #f1f9ff1f;backdrop-filter:blur(24px)}
.screen{display:flex;align-items:center;gap:48px;height:180px}.symbol{height:130px;width:130px;display:grid;place-items:center;background:#d2e9f00d;border:1px solid #d2e9f045;border-radius:50%;font-size:64px}.status{font-size:45px;margin-bottom:12px}.detail{font-size:25px;color:#c4d1da}
.rail{display:grid;grid-template-columns:repeat(4,1fr);gap:20px;margin-top:36px;border-top:1px solid #d2e9f026;padding-top:32px}.step{padding-left:22px;border-left:3px solid #d2e9f02b;opacity:.58}.step.current{border-color:#e5ecda;opacity:1}.step b{display:block;font-size:25px;font-weight:500}.step span{display:block;font-size:20px;color:#c3d0d9;margin-top:12px}.proof{display:flex;justify-content:space-between;font-size:21px;color:#b8c7d1;margin-top:32px;gap:30px}footer{font-size:21px;color:#b8c7d1;margin-top:28px}
</style><main><header><span>开窗 · Aperture TV / 开发记录 015</span><span class="boundary">逻辑演示 · 合成音频事件 · 非小爱实机画面</span></header>
<h1>先听你说，再接着看。</h1><p>声音让给小爱；按下播放，再继续影片。</p>
<section class="glass"><div class="screen"><div class="symbol" id="symbol"></div><div><div class="status" id="status"></div><div class="detail" id="detail"></div></div></div>
<div class="rail">${['打开影片','音频被占用','影片准备完成','主动按播放'].map((t,i)=>'<div class="step" id="step'+i+'"><b>'+String(i+1).padStart(2,'0')+'　'+t+'</b><span>'+['取得音频焦点，开始准备','暂停并撤销待播放意图','仍保持暂停','重新申请音频焦点'][i]+'</span></div>').join('')}</div></section>
<div class="proof"><span>来源：生产 PlaybackSession 的 JVM 场景输出</span><span id="trace"></span></div>
<footer>未运行 Android AudioManager / MediaPlayer；小米电视、小爱与 NAS 硬件尚待验证。时间轴仅用于讲解，不代表实测耗时。</footer></main>
<script>const states=${JSON.stringify(states)};const titles=['正在准备影片','声音先让出来','准备好了，也先等你','按下播放，接着看'];const details=['用户选择影片后申请焦点；只有准备完成才能出声。','即使电视页面没失焦，收到音频中断也会暂停。','迟到的准备回调不会自动播放；回到页面也不会。','只有新的明确播放操作，才重新申请并开始播放。'];window.showStep=i=>{document.getElementById('status').textContent=titles[i];document.getElementById('detail').textContent=details[i];document.getElementById('symbol').textContent=i===3?'▷':i===0?'…':'Ⅱ';document.getElementById('trace').textContent='逻辑状态：'+states[i];document.querySelectorAll('.step').forEach((e,n)=>e.classList.toggle('current',i===n));};showStep(0);</script></html>`;

await mkdir('media/output', {recursive:true}); await mkdir('docs/images', {recursive:true});
await writeFile('media/output/playback-focus-story.html', html);
const browser = await launchBrowser(), errors = []; let encoder;
try {
  const page = await browser.newPage(); await page.setViewportSize({width:1920,height:1080});
  page.on('pageerror', e => errors.push(e.message)); await page.setContent(html); await page.evaluate(() => document.fonts.ready);
  const runtime='/tmp/aperture-media-runtime/imageio_ffmpeg/binaries/ffmpeg-linux-x86_64-v7.0.2';
  const ffmpeg=process.env.FFMPEG_PATH||(existsSync(runtime)?runtime:'ffmpeg');
  const output='media/output/playback-focus-logic-1920x1080.mp4', fps=12, seconds=12;
  encoder=spawn(ffmpeg,['-hide_banner','-loglevel','error','-y','-f','image2pipe','-vcodec','png','-framerate',String(fps),'-i','-','-an','-c:v','libx264','-preset','fast','-crf','21','-pix_fmt','yuv420p','-movflags','+faststart',output],{stdio:['pipe','ignore','pipe']});
  let error=''; encoder.stderr.on('data', b => error+=b);
  const done=new Promise((r,j)=>{encoder.on('error',j);encoder.on('close',code=>code===0?r():j(new Error(error)));}); done.catch(()=>{});
  for(let frame=0;frame<fps*seconds;frame++) {
    if(frame%36===0) await page.evaluate(i => showStep(i), Math.floor(frame/36));
    if(frame===72||frame===108) await page.screenshot({path:'docs/images/s26-playback-focus-'+(frame===72?'paused':'continued')+'-1920.png'});
    const bytes=await page.screenshot({type:'png'}); if(!encoder.stdin.write(bytes)) await once(encoder.stdin,'drain');
    if(frame%48===0) console.log('Sampled '+frame/fps+'s / '+seconds+'s');
  }
  encoder.stdin.end(); await done; assert.deepEqual(errors, []);
  await writeFile('media/output/playback-focus-manifest.json',JSON.stringify({file:output,width:1920,height:1080,fps,seconds,audio:false,states,sourceReport:'artifacts/playback-session-report.json',method:'144 sampled editorial frames driven by production JVM trace; not a UI recording or latency measurement',androidAudioManagerExecuted:false,androidMediaPlayerExecuted:false,xiaomiDevice:false,realNasConnected:false,credentialsOrQrInFrames:false,errors},null,2)+'\n');
  console.log(output);
} finally { encoder?.kill(); await browser.close(); }
