// Real authorization UI and backend discovery; no WorkBuddy application or TV installer.
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,rm,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createApp} from '../server/app.mjs';
import {launchBrowser} from './browser.mjs';
const directory=await mkdtemp(join(tmpdir(),'aperture-app-ui-')),origin='http://127.0.0.1:4388',ownerKey='app-ui-synthetic-owner-1234567890';
const app=await createApp({directory,origin,ownerKey,localAutomation:true});await new Promise(r=>app.server.listen(4388,'127.0.0.1',r));const browser=await launchBrowser(),errors=[];
try{
 await mkdir('docs/images',{recursive:true});
 const pair=await(await fetch(origin+'/api/local-pair/start',{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'})).json();
 const page=await browser.newPage({viewport:{width:390,height:844}});page.on('pageerror',e=>errors.push(e.message));
 await page.request.post(origin+'/api/login',{data:{key:ownerKey}});await page.goto(pair.approvalUrl);await page.locator('#assistant-approve').waitFor({state:'visible'});
 for(const field of ['install','uninstall'])assert.equal(await page.locator('#assistant-approve [name='+field+']').isChecked(),false);
 await page.locator('#assistant-approve [name=install]').check();
 await page.locator('#assistant-approve [name=code]').evaluate(input=>input.value='示例');
 for(const [width,height]of[[390,844],[1440,1000]]){
  await page.setViewportSize({width,height});await page.locator('#local-assistant').scrollIntoViewIfNeeded();
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  await page.locator('#local-assistant').screenshot({path:'docs/images/s39-workbuddy-app-permissions-'+width+'.png',animations:'disabled'});
 }
 await page.locator('#assistant-approve [name=code]').evaluate((input,code)=>input.value=code,pair.code);
 await page.locator('#assistant-approve button').click();await page.waitForFunction(()=>document.querySelector('#assistant-status').textContent.includes('已批准'));
 const grant=await(await fetch(origin+'/api/local-pair/poll',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(pair)})).json();
 const discovery=await(await fetch(origin+'/api/local-agent/tools',{headers:{Authorization:'Bearer '+grant.token}})).json();
 assert.ok(discovery.tools.some(t=>t.name==='aperture_install_send'));assert.ok(!discovery.tools.some(t=>t.name==='aperture_uninstall_send'));
 await page.goto(origin+'/guide');assert.match(await page.locator('.hero-caption').textContent(),/仅适配小米电视/);
 for(const width of [390,1920]){await page.setViewportSize({width,height:1080});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);await page.locator('.guide-hero').screenshot({path:'docs/images/s39-xiaomi-guide-'+width+'.png',animations:'disabled'});}
 assert.deepEqual(errors,[]);
 const report={defaultAppsScopesUnchecked:true,installGrantedThroughActualUI:true,uninstallNotImplied:true,discoveryRevalidated:true,scopeNotice:'本项目仅适配小米电视',widths:[390,1440,1920],errors,workBuddyApplicationTested:false,xiaomiHardwareTested:false,androidInstallerExecuted:false};
 await writeFile('docs/verification/app-management-browser.json',JSON.stringify(report,null,2)+'\n');console.log(report);
}finally{await browser.close();await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});}
