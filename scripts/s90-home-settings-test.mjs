// Browser UI evidence only. Default HOME, cold boot and OEM key dispatch need the television.
import assert from 'node:assert/strict';
import {mkdtemp,rm,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createServer} from 'node:http';
import {createApp} from '../server/app.mjs';
import {launchBrowser} from './browser.mjs';
const directory=await mkdtemp(join(tmpdir(),'kaishi-home-settings-'));
const reserve=createServer();await new Promise(resolve=>reserve.listen(0,'127.0.0.1',resolve));const port=reserve.address().port;await new Promise(resolve=>reserve.close(resolve));
const origin='http://127.0.0.1:'+port,app=await createApp({directory,origin,ownerKey:'synthetic-home-settings-test-1234567890'});
await new Promise(resolve=>app.server.listen(port,'127.0.0.1',resolve));
let browser;const checks=[];
try{
  browser=await launchBrowser();
  for(const width of [1280,1920]){
    const page=await browser.newPage({viewport:{width,height:width*9/16},reducedMotion:'reduce'});
    await page.goto(origin+'/home?pane=settings&demo=1');
    const entry=page.locator('#pane-actions button').first();
    assert.equal(await entry.getAttribute('data-action'),'home-settings');
    assert.equal((await entry.textContent()).trim(),'默认桌面与开机');
    assert.equal(await page.evaluate(()=>document.activeElement.getAttribute('data-action')),'home-settings');
    const box=await entry.boundingBox();assert.ok(box.width>100&&box.height>36&&box.y>=0&&box.y+box.height<=width*9/16);
    await page.keyboard.press('Enter');
    assert.equal(await page.locator('#action-status').textContent(),'请在电视上操作');
    await page.keyboard.press('Escape');assert.equal(await page.locator('#pane-backdrop').isVisible(),false);
    checks.push({width,firstAction:'home-settings',focus:true,remoteEnter:true,remoteBack:true});
    await page.close();
  }
  await writeFile('docs/verification/s90-home-settings-browser.json',JSON.stringify({checks,androidDefaultHome:false,coldBoot:false,xiaomiHardware:false},null,2)+'\n');
  console.log('Standard settings first entry, focus, Enter and Back passed at 1280/1920; Android HOME and boot not exercised.');
}finally{if(browser)await browser.close();await new Promise(resolve=>app.server.close(resolve));await rm(directory,{recursive:true,force:true});}
