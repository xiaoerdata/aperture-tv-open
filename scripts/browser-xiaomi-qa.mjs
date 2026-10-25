// Runs the real Java asset policy behind a simulated local HTTPS WebView origin.
// Browser/native-contract evidence only: no real Xiaomi, microphone, OEM hotword or Android intents.
import {existsSync} from 'node:fs';
import assert from 'node:assert/strict';import {mkdtemp,rm,readFile,mkdir,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';import {join,resolve} from 'node:path';import {execFile} from 'node:child_process';import {promisify} from 'node:util';
import {launchBrowser} from './browser.mjs';
const run=promisify(execFile),directory=await mkdtemp(join(tmpdir(),'aperture-xiaomi-qa-'));
const isolated='/tmp/aperture-android-runtime/jdk-unpack/jdk-17.0.21+9';
const jdk=process.env.APERTURE_CHECK_JDK||process.env.JAVA_HOME||(existsSync(join(isolated,'bin/javac'))?isolated:null);
const executable=name=>jdk?join(jdk,'bin',name):name;
const source=resolve('android/app/src/main/java/org/zhixiaoce/aperture'),base='https://appassets.androidplatform.net',checks=[],resources=[],errors=[];
let browser;
try{
 await run(executable('javac'),['-d',directory,join(source,'LobbyAssets.java'),join(source,'DisplayLifecycle.java'),resolve('tests/java/NativeCompatibilityTest.java')]);
 const policy=await run(executable('java'),['-cp',directory,'org.zhixiaoce.aperture.NativeCompatibilityTest']);assert.match(policy.stdout,/passed/);checks.push('Java lifecycle and exact allowlist tests include all new wallpaper files and reject unknown/traversal paths');
 browser=await launchBrowser();const page=await browser.newPage({viewport:{width:1920,height:1080}});page.on('pageerror',e=>errors.push(e.message));
 await page.route('**/*',async route=>{
  const url=route.request().url(),result=await run(executable('java'),['-cp',directory,'org.zhixiaoce.aperture.NativeCompatibilityTest',url]);
  if(result.stdout==='BLOCKED'){resources.push({url,allowed:false});await route.fulfill({status:403,body:'Blocked'});return;}
  const [file,mime]=result.stdout.split('\n');resources.push({url,allowed:true,file,mime});await route.fulfill({status:200,contentType:mime,body:await readFile(resolve('public/launcher',file))});
 });
 await page.goto(base+'/assets/index.html');await page.evaluate(()=>document.fonts.ready);
 assert.equal(await page.locator('#environment').textContent(),'');assert.match(await page.title(),/开视/);
 assert.equal(await page.evaluate(()=>document.fonts.check('92px "Aperture Clock"','0123456789:')&&document.fonts.check('32px "Aperture Song"','年月日星期')),true);
 assert.ok(resources.some(r=>r.file==='carezza-4k.jpg'));assert.ok(resources.some(r=>r.file==='fonts/clock-reader.woff2'));assert.equal(resources.some(r=>r.url.includes('/api/')),false);
 checks.push('actual Java whitelist serves offline HTML/CSS/JS/JPEG/WOFF2 from local HTTPS; fonts load with no household API request');
 await page.locator('#settings-open').click();await page.locator('[data-action=wallpapers]').click();await page.locator('[data-wallpaper=namib]').click();await page.waitForFunction(()=>document.querySelector('#pane-backdrop').hidden);assert.ok(resources.some(r=>r.file==='namib-4k.jpg'));assert.ok(resources.some(r=>r.file==='lofoten-thumb.jpg'));
 const focused=await page.evaluate(()=>document.activeElement.id);
 for(const name of ['VoiceAssist','Assist','Search','AudioVolumeUp','AudioVolumeDown'])assert.equal(await page.evaluate(n=>window.apertureRemoteKey(n),name),false);
 assert.equal(await page.evaluate(()=>document.activeElement.id),focused);
 checks.push('shared web remote contract does not consume voice/assist/search/volume key names or move focus');
 await page.locator('[data-home-screen="2"]').click();
 await page.locator('#slide-play').click();assert.equal(await page.locator('#slide-play').getAttribute('aria-pressed'),'true');
 const url=page.url(),focus=await page.evaluate(()=>document.activeElement.id);await page.evaluate(()=>window.apertureNativeFocus(false));
 assert.equal(await page.locator('#slide-play').getAttribute('aria-pressed'),'false');await page.evaluate(()=>window.apertureNativeFocus(true));assert.equal(await page.locator('#slide-play').getAttribute('aria-pressed'),'false');assert.equal(page.url(),url);assert.equal(await page.evaluate(()=>document.activeElement.id),focus);
 checks.push('native focus contract pauses carousel without navigation/reload/focus loss and does not restart automatically');
 await page.locator('#entry-tools').click();await page.getByRole('button',{name:'小爱同学',exact:true}).click();
 assert.match(await page.locator('#pane-title').textContent(),/小爱/);assert.equal(await page.locator('[data-action=open-assistant]').count(),1);assert.equal(await page.locator('[data-action=voice-help]').count(),1);assert.equal(await page.locator('[data-action=voice-settings]').count(),1);
 await mkdir('docs/images',{recursive:true});await page.screenshot({path:'docs/images/s48-xiaomi-voice-browser-1920.png'});
 await page.evaluate(()=>window.apertureBack());assert.equal(await page.locator('#collection-view').isVisible(),true);
 checks.push('voice guidance opens from tools and returns to current section; wake state explicitly depends on firmware');
 for(const path of ['/api/board','/assets/fonts/Newsreader-OFL.txt','/assets/missing.js'])assert.equal(await page.evaluate(async p=>(await fetch(p)).status,path),403);
 checks.push('private/unknown/undeclared resource paths are denied by the real Java policy, without network fallback');
 assert.deepEqual(errors,[]);await mkdir('artifacts',{recursive:true});await writeFile('artifacts/xiaomi-browser-report.json',JSON.stringify({checks,resources,errors,localHttpsSimulated:true,actualAndroidWebView:false,xiaomiHotwordTested:false,physicalVoiceKeyTested:false,apkBuilt:false},null,2));console.log({checks,errors});
}finally{await browser?.close();await rm(directory,{recursive:true,force:true});}
