// Actual phone page + household service + production Java receiver. Success ack is explicitly synthetic.
import {mkdtemp,mkdir,writeFile,rm} from 'node:fs/promises';import {tmpdir} from 'node:os';import {join} from 'node:path';import assert from 'node:assert/strict';
import {createApp} from '../server/app.mjs';import {launchBrowser} from './browser.mjs';import {nasSetupHarness} from './nas-setup-harness.mjs';
const directory=await mkdtemp(join(tmpdir(),'aperture-setup-browser-')),origin='http://127.0.0.1:4388',ownerKey='synthetic-setup-browser-owner-123456789012345';
const app=await createApp({directory,origin,ownerKey,localAutomation:true});await new Promise(r=>app.server.listen(4388,'127.0.0.1',r));
const browser=await launchBrowser(),h=await nasSetupHarness(),checks=[],errors=[];let page;
const api=async(path,body,cookie)=>{const r=await fetch(origin+path,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json',...(cookie?{Cookie:cookie}:{})},body:body===undefined?undefined:JSON.stringify(body)});return {status:r.status,cookie:r.headers.get('set-cookie')?.split(';')[0],data:await r.json()};};
try{
 await mkdir('docs/images',{recursive:true});await mkdir('artifacts',{recursive:true});
 const owner=(await api('/api/login',{key:ownerKey})).cookie,pair=(await api('/api/pair/start',{role:'viewer'})).data;await api('/api/pair/approve',{code:pair.code},owner);const viewer=(await api('/api/pair/poll',pair)).cookie;
 const request=await h.call({start:{origin,cookie:viewer}});page=await browser.newPage();page.on('pageerror',e=>errors.push(e.message));await page.setViewportSize({width:390,height:844});
 await page.goto(request.approvalUrl);await page.locator('#login').waitFor({state:'visible'});
 await page.locator('#login-form input').fill(ownerKey);await page.locator('#login-form button').click();await page.locator('#nas-form').waitFor({state:'visible'});
 checks.push('real owner login continues the original TV request; full fingerprint is verified');
 await page.evaluate(()=>{const label=document.createElement('p');label.className='notice';label.textContent='开发演示 · 合成资料 · JVM接收端 · 非电视/NAS实机';document.querySelector('main').prepend(label);});
 await page.locator('[name=brand]').selectOption({label:'Xiaomi 智能存储 · 新款 NAS'});await page.locator('[name=role]').selectOption('movies');
 await page.locator('[name=name]').fill('周末电影 · 示例');await page.locator('[name=root]').fill('smb://192.168.1.20/Family/电影');
 for(const width of [320,390,430,760,761,1280]){await page.setViewportSize({width,height:844});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert.equal(await page.locator('[name=name]').inputValue(),'周末电影 · 示例');}
 await page.locator('details summary').click();await page.setViewportSize({width:390,height:844});assert.ok((await page.locator('#send').boundingBox()).height>=44);await page.screenshot({path:'docs/images/s28-nas-setup-phone-390.png',fullPage:true});
 await page.setViewportSize({width:1280,height:960});await page.screenshot({path:'docs/images/s28-nas-setup-desktop-1280.png',fullPage:true});
 checks.push('320/390/430/760/761/1280px retain the draft without horizontal overflow; expanded domain control is visible');
 await page.setViewportSize({width:390,height:844});
 const syntheticPassword='synthetic-browser-password-only',syntheticUser='synthetic-reader';await page.locator('[name=user]').fill(syntheticUser);await page.locator('[name=password]').fill(syntheticPassword);
 let sends=0,wire;
 await page.route('**/api/nas-setup/send',async route=>{sends++;wire=JSON.parse(route.request().postData());await route.fetch();await route.abort('failed');});
 await page.locator('#send').click();await page.waitForFunction(()=>document.querySelector('#status').textContent.includes('查询交接结果'));
 assert.equal(sends,1);assert.ok(!JSON.stringify(wire).includes(syntheticPassword)&&!JSON.stringify(wire).includes(syntheticUser));assert.equal(await page.locator('[name=password]').inputValue(),'');
 await page.locator('#check').click();await page.waitForFunction(()=>document.querySelector('#receipt-text').textContent.includes('等待电视测试'));
 assert.equal(sends,1);const polled=await h.call({poll:true});assert.equal((await h.call({envelope:polled.envelope})).valid,true);
 checks.push('lost send response is resolved by querying original receipt, without a second POST; wire and persistent service state exclude plaintext credentials');
 await page.screenshot({path:'docs/images/s28-nas-setup-awaiting-390.png',fullPage:true});
 await h.call({finish:'saved'});await page.locator('#check').click();await page.waitForFunction(()=>document.querySelector('#receipt-text').textContent.includes('已保存在电视'));
 await page.screenshot({path:'docs/images/s28-nas-setup-receipt-390.png',fullPage:true});
 checks.push('synthetic native saved acknowledgement is distinct from awaiting_tv; no actual Android save is claimed');
 const bad=await browser.newPage();await bad.setViewportSize({width:390,height:844});await bad.goto(request.approvalUrl.replace(request.fingerprint,'0'.repeat(64)));await bad.waitForFunction(()=>document.querySelector('#status').dataset.error==='true');assert.equal(await bad.locator('#nas-form').isVisible(),false);await bad.close();
 checks.push('mismatched scanned fingerprint never reveals the NAS input form');
 const state=JSON.stringify(app.store.read());assert.ok(!state.includes(syntheticPassword)&&!state.includes(wire.envelope.ciphertext));assert.deepEqual(errors,[]);
 await writeFile('artifacts/nas-setup-browser-report.json',JSON.stringify({checks,errors,realBrowserPage:true,realServiceAuthentication:true,realWebCrypto:true,productionJavaDecrypt:true,lostResponseResolvedWithoutResend:true,syntheticSavedAcknowledgement:true,androidActivity:false,androidKeyStore:false,realNas:false,realXiaomi:false,screenshotsContainCredentialsOrQr:false},null,2)+'\n');console.log(checks.join('\n'));
}finally{await h.close();await browser.close();app.server.closeAllConnections();await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});}
