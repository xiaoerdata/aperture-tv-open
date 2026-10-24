// Browser/UI contract only: no Android package installation is performed.
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,rm,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createApp} from '../server/app.mjs';
import {launchBrowser} from './browser.mjs';
const directory=await mkdtemp(join(tmpdir(),'aperture-installation-'));
const origin='http://127.0.0.1:4387';
const app=await createApp({directory,origin,ownerKey:'installation-fixture-owner-1234567890'});
await new Promise(r=>app.server.listen(4387,'127.0.0.1',r));
const browser=await launchBrowser(),checks=[],errors=[];
try {
 await mkdir('docs/images',{recursive:true});
 const page=await browser.newPage({viewport:{width:1920,height:1080}});
 page.on('pageerror',e=>errors.push(e.message));
 for (const edition of ['standard','parents']) {
  await page.goto(origin+'/home?demo=1'+(edition==='parents'?'&edition=parents':''));
  await page.waitForFunction(()=>typeof window.apertureRemoteKey==='function');
  await page.locator('#settings-open').click();
  const button=page.locator('[data-action="install-apps"]');
  await button.waitFor({state:'visible'});
  await button.focus();await page.evaluate(()=>window.apertureRemoteKey('Enter'));
  assert.match(await page.locator('#action-status').textContent(),/电视/);
  await page.screenshot({path:'docs/images/s37-installation-'+edition+'.png'});
  await page.evaluate(()=>window.apertureRemoteKey('Escape'));
  assert.equal(await page.evaluate(()=>document.activeElement.id),'settings-open');
  checks.push(edition+': installation entry in settings; native key activation; browser-only status; Back restores focus');
 }
 await page.goto(origin+'/guide#app-install');
 for(const [width,height] of [[1920,1080],[390,844]]) {
  await page.setViewportSize({width,height});
  await page.locator('#app-install').scrollIntoViewIfNeeded();
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  assert.match(await page.locator('#app-install').innerText(),/覆盖升级/);
  await page.locator('#app-install').screenshot({path:'docs/images/s37-installation-guide-'+width+'.png',animations:'disabled'});
 }
 assert.deepEqual(errors,[]);
 checks.push('guide: own APK, system installation route, in-app binding, in-place upgrades; 1920/390 widths');
 await writeFile('docs/verification/installation-browser.json',JSON.stringify({checks,errors,androidInstallationTested:false,xiaomiTVTested:false},null,2)+'\n');
 console.log(JSON.stringify({checks,errors}));
} finally {await browser.close();await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});}
