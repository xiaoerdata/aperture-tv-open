import assert from 'node:assert/strict';
import {mkdtemp, mkdir, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createApp} from '../server/app.mjs';
import {launchBrowser} from './browser.mjs';
async function viewport(page,size){await page.setViewportSize(size);await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));}
const imagePrefix=process.env.APERTURE_QA_IMAGE_PREFIX||'s22';
const directory=await mkdtemp(join(tmpdir(),'aperture-launcher-')),origin='http://127.0.0.1:4357';
const app=await createApp({directory,origin,ownerKey:'launcher-fixture-owner-1234567890'});
await new Promise(r=>app.server.listen(4357,'127.0.0.1',r));
const browser=await launchBrowser(),checks=[],errors=[];
try {
 await mkdir('docs/images',{recursive:true});await mkdir('artifacts',{recursive:true});
 const page=await browser.newPage({viewport:{width:1920,height:1080}});page.on('pageerror',e=>errors.push(e.message));
 await page.goto(origin+'/?demo=1');await page.waitForFunction(()=>typeof window.apertureRemoteKey==='function');
 assert.equal(new URL(page.url()).pathname,'/launcher/index.html');
 assert.equal(await page.locator('.destinations>.destination:visible').count(),8);
 await page.screenshot({path:'docs/images/'+imagePrefix+'-s18-glass-standard-qa-1920.png'});
 await page.evaluate(()=>{window.KeyboardEvent=undefined;document.querySelector('#entry-live').focus();});
 for(const [key,id] of [['ArrowRight','entry-cinema'],['ArrowRight','entry-photos'],['ArrowRight','entry-family'],['ArrowDown','entry-system'],['ArrowLeft','entry-tools'],['ArrowLeft','entry-information'],['ArrowLeft','entry-data'],['ArrowUp','entry-live']]){await page.evaluate(key=>window.apertureRemoteKey(key),key);assert.equal(await page.evaluate(()=>document.activeElement.id),id);}
 await page.locator('#entry-live').focus();await page.evaluate(()=>window.apertureRemoteKey('Enter'));await page.locator('#pane-backdrop').waitFor();
 assert.equal(await page.evaluate(()=>document.activeElement.dataset.action),'open-live');
 await page.keyboard.press('Tab');assert.equal(await page.evaluate(()=>!!document.activeElement.closest('#pane-backdrop')),true);
 await page.locator('[data-action=open-live]').click();assert.match(await page.locator('#action-status').textContent(),/电视/);
 await page.screenshot({path:'docs/images/'+imagePrefix+'-s18-glass-live-1920.png'});
 await page.evaluate(()=>window.apertureRemoteKey('Escape'));assert.equal(await page.evaluate(()=>document.activeElement.id),'entry-live');
 checks.push('eight first-level entries, native key-name navigation without KeyboardEvent, dialog trap/return and honest browser-only app state');
 await page.locator('#entry-cinema').click();await page.screenshot({path:'docs/images/'+imagePrefix+'-s18-glass-cinema-qa-1920.png'});await page.keyboard.press('Escape');
 await page.locator('#entry-system').click();assert.match(await page.locator('#pane-title').textContent(),/原桌面/);await page.screenshot({path:'docs/images/'+imagePrefix+'-s18-glass-system-1920.png'});await page.keyboard.press('Escape');
 for(const [w,h] of [[1280,720],[1920,1080],[3840,2160],[320,844],[390,844],[430,900],[760,900],[761,900]]){
  await viewport(page,{width:w,height:h});
  const box=await page.evaluate(()=>({x:document.documentElement.scrollWidth>innerWidth,y:document.documentElement.scrollHeight>innerHeight,rects:[...document.querySelectorAll('.destinations>.destination')].map(n=>{const r=n.getBoundingClientRect();return {top:r.top,bottom:r.bottom};})}));
  assert.equal(box.x,false,'standard width '+w);if(w>=1280)assert.equal(box.y,false,'standard height '+h);
  if(w===390)await page.screenshot({path:'docs/images/'+imagePrefix+'-s18-glass-standard-qa-phone.png',fullPage:true});
 }
 checks.push('standard UI at 720p/1080p/4K and 320/390/430/760/761 widths');
 // Content tabs and return work with the native key-name contract; no view requires typing.
 await viewport(page,{width:1920,height:1080});
 for(const section of ['photos','data','information','tools']) {
  await page.locator('#entry-'+section).focus();await page.evaluate(()=>window.apertureRemoteKey('Enter'));
  assert.equal(await page.locator('#collection-view').isVisible(),true);assert.ok(await page.locator('#collection-body .collection-tile').count()>0);
  await page.evaluate(()=>window.apertureRemoteKey('Escape'));assert.equal(await page.evaluate(()=>document.activeElement.id),'entry-'+section);
 }
 for(let index=0;index<3;index++)await page.locator('#slide-next').click();assert.equal(await page.locator('#scene-number').textContent(),'4 / 4');
 assert.match(await page.locator('#hero-open').textContent(),/工具/);
 await page.locator('#slide-play').focus();await page.evaluate(()=>apertureRemoteKey('Enter'));assert.equal(await page.locator('#slide-play').getAttribute('aria-pressed'),'true');
 await page.evaluate(()=>window.apertureRemoteKey('ArrowLeft'));assert.equal(await page.locator('#slide-play').getAttribute('aria-pressed'),'false');
 checks.push('four populated content sections, native-key entry/back focus, four manual carousel slides and user-input pause');
 for(const [width,height] of [[1280,720],[1920,1080]]){
  await viewport(page,{width,height});await page.locator('#entry-cinema').click();assert.equal(await page.locator('[data-action=nas]').count(),1);await page.locator('[data-action=nas]').focus();await page.screenshot({path:'docs/images/'+imagePrefix+'-nas-entry-'+width+'.png'});await page.evaluate(()=>apertureBack());
  await page.locator('#entry-tools').click();assert.equal(await page.locator('#collection-body button').count(),8);
  assert.equal(await page.evaluate(()=>document.querySelector('#collection-body').getBoundingClientRect().bottom<=innerHeight),true,'eight tools fit above shortcuts');
  await page.evaluate(()=>apertureBack());await page.locator('#entry-photos').click();assert.equal(await page.locator('#collection-nas').isVisible(),true);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await page.screenshot({path:'docs/images/'+imagePrefix+'-nas-photos-'+width+'.png'});await page.evaluate(()=>apertureBack());
 }
 checks.push('NAS direct action in cinema, eight tools fit 720p/1080p and photos retain a NAS entry with populated/empty collections');
 const parents=await browser.newPage({viewport:{width:1920,height:1080},reducedMotion:'reduce'});parents.on('pageerror',e=>errors.push(e.message));
 await parents.goto(origin+'/home?edition=parents');await parents.waitForFunction(()=>typeof window.apertureRemoteKey==='function');
 assert.equal(await parents.locator('.destinations>.destination:visible').count(),3);assert.equal(await parents.evaluate(()=>document.activeElement.id),'entry-live');
 assert.equal(await parents.locator('.mood-switch').isVisible(),false);assert.match(await parents.locator('#lobby').getAttribute('class'),/solid/);
 assert.ok(await parents.locator('#entry-live strong').evaluate(n=>parseFloat(getComputedStyle(n).fontSize))>=36);
 assert.deepEqual(await parents.locator('.destinations>.destination:visible strong').allTextContents(),['看电视','看电视剧','看电影']);
 await parents.screenshot({path:'docs/images/'+imagePrefix+'-parents-shortcuts-empty-1920.png'});
 for(const [id,view] of [['live','live'],['series','series'],['cinema','movies']]){await parents.locator('#entry-'+id).click();await parents.waitForURL('**/cinema?from=home&edition=parents&view='+view);await parents.locator('#cinema-home').click();await parents.waitForFunction(()=>typeof window.apertureRemoteKey==='function');}
 await parents.locator('#settings-open').click();assert.match(await parents.locator('#pane-title').textContent(),/设置/);
 await parents.locator('[data-action=family]').click();await parents.waitForURL('**/tv-lite?edition=parents');await parents.locator('body.parents-lite').waitFor();
 await parents.locator('#lobby-home').click();await parents.waitForURL('**/launcher/index.html?edition=parents');
 await parents.goto(origin+'/home?edition=parents&demo=1&quick1='+encodeURIComponent('视频应用 · 示例')+'&quick2='+encodeURIComponent('音乐 · 示例')+'&quick3='+encodeURIComponent('健身 · 示例')+'&quick4='+encodeURIComponent('戏曲 · 示例'));
 await parents.waitForFunction(()=>typeof window.apertureRemoteKey==='function');assert.equal(await parents.locator('#parents-shortcuts').isVisible(),true);assert.equal(await parents.locator('.shortcuts-row button:visible').count(),5);assert.match(await parents.locator('#quick1').textContent(),/视频应用/);
 assert.ok(await parents.locator('#quick1').evaluate(n=>n.getBoundingClientRect().height)<await parents.locator('#entry-live').evaluate(n=>n.getBoundingClientRect().height));await parents.locator('#entry-live').focus();await parents.evaluate(()=>window.apertureRemoteKey('ArrowDown'));assert.match(await parents.evaluate(()=>document.activeElement.id),/^quick/);await parents.evaluate(()=>window.apertureRemoteKey('Enter'));await parents.locator('#pane-backdrop').waitFor();assert.match(await parents.locator('#pane-title').textContent(),/常用应用/);await parents.keyboard.press('Escape');assert.match(await parents.evaluate(()=>document.activeElement.id),/^quick/);await parents.screenshot({path:'docs/images/'+imagePrefix+'-parents-shortcuts-1920.png'});
 checks.push('parents: three core entries plus four smaller pinned apps and all-apps; key navigation/dialog return and edition preserved');
 await parents.locator('#entry-series').focus();
 await parents.evaluate(()=>apertureNasLabels('NAS · 周末电影','NAS · 一起追的剧'));
 assert.equal(await parents.evaluate(()=>document.activeElement.id),'entry-series');
 assert.equal(await parents.locator('#cinema-label').textContent(),'NAS · 周末电影');assert.equal(await parents.locator('#series-label').textContent(),'NAS · 一起追的剧');
 for(const [width,height]of [[1280,720],[1920,1080],[390,844]]){await viewport(parents,{width,height});await parents.screenshot({path:'docs/images/'+imagePrefix+'-parents-nas-'+width+'.png',fullPage:width<761});assert.equal(await parents.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);if(width>=1280)assert.equal(await parents.evaluate(()=>document.documentElement.scrollHeight<=innerHeight),true);}
 await parents.evaluate(()=>apertureNasLabels('<img src=x onerror=alert(1)>','NAS · 一起追的剧'));assert.equal(await parents.locator('#cinema-label img').count(),0);assert.match(await parents.locator('#cinema-label').textContent(),/<img/);await parents.evaluate(()=>apertureNasLabels('NAS · 周末电影','NAS · 一起追的剧'));
 await viewport(parents,{width:1280,height:720});await parents.locator('#settings-open').click();await parents.evaluate(()=>{apertureRemoteKey('ArrowDown');apertureRemoteKey('ArrowDown');apertureRemoteKey('ArrowDown');});assert.equal(await parents.evaluate(()=>document.activeElement.dataset.action),'nas-photos');assert.equal(await parents.evaluate(()=>getComputedStyle(document.activeElement).outlineStyle),'solid');assert.ok(await parents.evaluate(()=>parseFloat(getComputedStyle(document.activeElement).outlineWidth))>=3);assert.ok(await parents.evaluate(()=>parseFloat(getComputedStyle(document.activeElement).outlineOffset))<0);assert.equal(await parents.locator('[data-action=nas-photos]').count(),1);assert.equal(await parents.evaluate(()=>{var r=document.activeElement.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight;}),true);await parents.screenshot({path:'docs/images/'+imagePrefix+'-parents-nas-settings-1280.png'});await parents.keyboard.press('Escape');
 checks.push('parents NAS source labels update as safe text without resetting focus; NAS photo settings reachable with visible native-key focus at 720p; browser simulation does not test Android source dispatch');

 for(const [w,h] of [[1280,720],[1920,1080],[3840,2160],[390,844],[760,900],[761,900]]){
  await viewport(parents,{width:w,height:h});assert.equal(await parents.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,'parents width '+w);
  if(w>=1280)assert.equal(await parents.evaluate(()=>document.documentElement.scrollHeight>innerHeight),false,'parents height '+h);
  if(w===1280)await parents.screenshot({path:'docs/images/'+imagePrefix+'-parents-shortcuts-1280.png'});
  if(w===390)await parents.screenshot({path:'docs/images/'+imagePrefix+'-s18-glass-parents-phone.png',fullPage:true});
 }
 checks.push('parents UI at 720p/1080p/4K and phone boundaries with large text');
 await viewport(page,{width:1920,height:1080});await page.reload();await page.waitForFunction(()=>typeof window.apertureRemoteKey==='function');assert.equal(await page.evaluate(()=>localStorage.getItem('aperture-launcher-solid')),'0');
 const offline=await browser.newPage({viewport:{width:1280,height:720}}),requests=[];offline.on('request',r=>requests.push(r.url()));offline.on('pageerror',e=>errors.push(e.message));
 await offline.goto(pathToFileURL(resolve('public/launcher/index.html')).href+'?edition=parents&live='+encodeURIComponent('<img src=x onerror=alert(1)>'));
 await offline.waitForFunction(()=>typeof window.apertureRemoteKey==='function');
 assert.equal(await offline.locator('#live-label img').count(),0);assert.equal(await offline.locator('#live-label').textContent(),'');
 await offline.goto(pathToFileURL(resolve('public/launcher/index.html')).href+'?edition=parents&quick1='+encodeURIComponent('<img src=x onerror=alert(1)>')+'&focus=quick1');await offline.waitForFunction(()=>typeof window.apertureRemoteKey==='function');assert.equal(await offline.locator('#quick1 img').count(),0);assert.match(await offline.locator('#quick1 strong').textContent(),/<img/);assert.equal(await offline.evaluate(()=>document.activeElement.id),'quick1');
 await offline.locator('#settings-open').click();await offline.evaluate(()=>window.apertureRemoteKey('Escape'));
 assert.equal(await offline.evaluate(()=>document.activeElement.id),'settings-open');assert.equal(requests.filter(u=>/^https?:/.test(u)).length,0);
 checks.push('packaged-file page works without network, ES5 local interaction and safe text labels; actual Android intents remain a device test');
 const member=await browser.newPage({viewport:{width:1920,height:1080}});member.on('pageerror',e=>errors.push(e.message));
 const login=await member.request.post(origin+'/api/login',{data:{key:'launcher-fixture-owner-1234567890'}});assert.equal(login.status(),200);
 await app.store.transact(s=>{s.board.entries=[{id:'qa-m',type:'metric',title:'家庭记录',value:23,target:30,unit:'次',source:'合成专项',date:'2001-01-01'},{id:'qa-n',type:'note',title:'已授权合成留言',body:'只在隔离家庭会话中展示',author:'测试家人'}];s.board.life=[{id:'qa-old',title:'过期设备快照',value:'99',unit:'℃',source:'合成专项',expiresAt:'2001-01-01T00:00:00Z'}];});
 await member.goto(origin+'/home');await member.waitForFunction(()=>document.querySelector('#metric-value').textContent==='23');
 assert.match(await member.locator('#data-detail').textContent(),/历史数据/);
 await member.locator('#entry-data').click();assert.match(await member.locator('#collection-body').textContent(),/待更新/);assert.ok(!(await member.locator('#collection-body').textContent()).includes('99'));
 await member.request.post(origin+'/api/logout',{data:{}});
 await member.evaluate(()=>document.dispatchEvent(new Event('visibilitychange')));
 await member.waitForFunction(()=>document.querySelector('#metric-value').textContent==='—');
 assert.ok(!(await member.locator('#collection-body').textContent()).includes('家庭记录'));
 const unauth=await browser.newPage();await unauth.goto(origin+'/home');await unauth.waitForFunction(()=>typeof apertureRemoteKey==='function');assert.equal(await unauth.locator('#metric-value').textContent(),'—');
 checks.push('authenticated household read, historical-date label, expired snapshots masked and content cleared on next read after logout; anonymous empty state');
 assert.deepEqual(errors,[]);
 await writeFile('artifacts/launcher-browser-report.json',JSON.stringify({checks,errors,actualAndroidIntentsTested:false,actualXiaomiHardwareTested:false,parentsUserStudyCompleted:false},null,2));console.log({checks,errors});
}finally{await browser.close();await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});}
