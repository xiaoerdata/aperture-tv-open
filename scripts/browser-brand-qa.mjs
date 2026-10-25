// S54: local visual check only. Does not build an APK, commit or publish.
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,readFile,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createServer} from 'node:net';
import {createApp} from '../server/app.mjs';
import {launchBrowser} from './browser.mjs';
const reserve=createServer();await new Promise(r=>reserve.listen(0,'127.0.0.1',r));
const port=reserve.address().port;await new Promise(r=>reserve.close(r));
const directory=await mkdtemp(join(tmpdir(),'kaishi-brand-')),origin='http://127.0.0.1:'+port;
let app,browser;const checks=[],errors=[];
try{
 await mkdir('docs/images',{recursive:true});await mkdir('docs/verification',{recursive:true});
 app=await createApp({directory,origin,ownerKey:'s54-synthetic-local-brand-check-only'});
 await new Promise(r=>app.server.listen(port,'127.0.0.1',r));browser=await launchBrowser();
 for(const edition of ['standard','parents']){
  const page=await browser.newPage({viewport:{width:1920,height:1080},reducedMotion:'reduce'});
  page.on('pageerror',e=>errors.push(e.message));
  await page.goto(origin+'/home?demo=1&edition='+edition);
  await page.waitForFunction(()=>typeof apertureRemoteKey==='function');await page.evaluate(()=>document.fonts.ready);
  const image=edition==='parents'?'kaishi-parents-v2.webp':'kaishi-icon-v2.webp';
  assert.ok((await page.locator('.arch-logo').evaluate(n=>getComputedStyle(n).backgroundImage)).includes(image));
  assert.ok((await page.locator('[data-kaishi-favicon]').getAttribute('href')).endsWith(image));
  await page.evaluate(async image=>{const i=new Image();i.src='brand/'+image;await i.decode();},image);
  await page.screenshot({path:'docs/images/s54-brand-'+edition+'.png'});
  await page.evaluate(()=>apertureRemoteKey('ArrowRight'));assert.ok(await page.evaluate(()=>document.activeElement.getBoundingClientRect().width>0));
  for(const route of ['/tv?demo=1','/tv-lite?demo=1','/guide','/studio','/cinema','/film']){
   await page.goto(origin+route+(route.includes('?')?'&':'?')+'edition='+edition);
   await page.waitForFunction(()=>[...document.querySelectorAll('[data-kaishi-brand]')].length>0&&[...document.querySelectorAll('[data-kaishi-brand]')].every(n=>n.complete&&n.naturalWidth>0));
   const sources=await page.locator('[data-kaishi-brand]').evaluateAll(nodes=>nodes.map(n=>n.src));
   assert.ok(sources.every(s=>s.endsWith(image)),edition+' '+route);
   assert.equal(await page.locator('.brand-mark,.window-icon').count(),0);
   checks.push({edition,route,brand:image,loaded:true});
  }
  await page.close();
 }
 // Export the Android TV banner by laying out the actual icon and real text.
 const panel=await browser.newPage({viewport:{width:640,height:360},deviceScaleFactor:1});
 const standardIcon='data:image/png;base64,'+(await readFile('android/app/src/main/res/drawable-nodpi/kaishi_icon_v2.png')).toString('base64');
 const parentsIcon='data:image/png;base64,'+(await readFile('android/app/src/parents/res/drawable-nodpi/kaishi_icon_v2.png')).toString('base64');
 await panel.setContent('<style>*{box-sizing:border-box}body{margin:0;background:#202020;color:#f7f7f7;font-family:"Noto Sans CJK SC",sans-serif}main{height:360px;display:flex;align-items:center;gap:38px;padding:40px}img{width:272px;height:272px;flex:none}h1{font-size:68px;line-height:1.15;font-weight:400;margin:0;white-space:nowrap}p{font-size:32px;line-height:1.3;color:#c5c9ca;margin:10px 0 0}</style><main><img src="'+parentsIcon+'"><div><h1>开视</h1><p>爸妈版</p></div></main>');
 await panel.evaluate(()=>document.fonts.ready);await panel.locator('img').evaluate(n=>n.decode());
 await panel.screenshot({path:'android/app/src/parents/res/drawable-nodpi/kaishi_banner_v2.png'});
 await panel.setViewportSize({width:900,height:480});
 await panel.setContent('<style>body{margin:0;background:#17191c;color:white;font-family:"Noto Sans CJK SC",sans-serif;display:flex;justify-content:center;gap:80px;padding:48px}figure{margin:0;text-align:center}img{width:280px;height:280px;border-radius:32px}figcaption{font-size:26px;margin-top:22px}</style><figure><img src="'+standardIcon+'"><figcaption>标准版</figcaption></figure><figure><img src="'+parentsIcon+'"><figcaption>爸妈版</figcaption></figure>');
 await panel.evaluate(()=>document.fonts.ready);await panel.locator('img').evaluateAll(nodes=>Promise.all(nodes.map(n=>n.decode())));
 await panel.screenshot({path:'design-assets/s54/logo-comparison.png'});await panel.close();
 const nativeAllowlist=await readFile('android/app/src/main/java/org/zhixiaoce/aperture/LobbyAssets.java','utf8');
 for(const name of ['brand/kaishi-parents-v2.webp','brand/identity.css','brand/identity.js'])assert.ok(nativeAllowlist.includes('"'+name+'"'));
 assert.deepEqual(errors,[]);
 await writeFile('docs/verification/s54-brand.json',JSON.stringify({stage:'S54',standardUsesSecondLogo:true,parentsUsesEmojiVariant:true,checks,errors,androidFlavorIconOverride:true,nativeAllowlistChecked:true,bannerExported:[640,360],apkBuilt:false,uploaded:false,committed:false,televisionTested:false},null,2)+'\n');
 console.log('Both editions and 12 companion-page checks passed; banner and preview exported. No APK build or upload.');
}finally{await browser?.close();if(app)await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});}
