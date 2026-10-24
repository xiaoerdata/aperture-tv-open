// Real mobile approval UI. No QR, credential, request id or token is captured in the report.
import {mkdtemp,rm,mkdir,writeFile} from 'node:fs/promises';import {tmpdir} from 'node:os';import {join} from 'node:path';import assert from 'node:assert/strict';
import {createApp} from '../server/app.mjs';import {launchBrowser} from './browser.mjs';
const directory=await mkdtemp(join(tmpdir(),'aperture-nas-grant-')),origin='http://127.0.0.1:4391',key='synthetic-grant-owner-123456789012345';
const app=await createApp({directory,origin,ownerKey:key,localAutomation:true});await new Promise(r=>app.server.listen(4391,'127.0.0.1',r));const browser=await launchBrowser();
const api=async(path,body,token)=>{
 const r=await fetch(origin+path,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body)});
 assert.equal(r.status,200);return r.json();
};
try{
 const pair=await api('/api/local-pair/start',{}),page=await browser.newPage();await page.setViewportSize({width:390,height:844});await page.goto(pair.approvalUrl);
 await page.locator('#owner-key').fill(key);await page.locator('#login-form button').click();await page.locator('#assistant-approve').waitFor({state:'visible'});
 const checkbox=page.locator('#assistant-approve [name=nas]');assert.equal(await checkbox.isChecked(),false);await checkbox.check();await page.locator('#assistant-approve button').click();
 await page.waitForFunction(()=>document.querySelector('#assistant-status').textContent.includes('已批准'));
 const connected=await api('/api/local-pair/poll',pair),list=await api('/api/local-agent/tools',undefined,connected.token);
 assert.deepEqual(list.tools.map(t=>t.name),['aperture_home','aperture_receipt','aperture_nas_requests','aperture_nas_status','aperture_nas_send']);
 assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 await mkdir('artifacts',{recursive:true});await writeFile('artifacts/nas-setup-grant-report.json',JSON.stringify({actualStudioBrowser:true,width:390,nasConfigureDefault:false,explicitNasCheckboxApproved:true,discoveredExactlyReadAndNasTools:true,sceneAndContentNotGranted:true,credentialsOrQrCaptured:false,realWorkBuddyApplication:false},null,2)+'\n');
 console.log('NAS permission defaults off; explicit mobile approval grants read + NAS tools only');
}finally{await browser.close();app.server.closeAllConnections();await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});}
