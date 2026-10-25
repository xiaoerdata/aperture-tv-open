// Browser fallback with synthetic media records; no stream fetch or Android claim.
import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {resolve,sep} from 'node:path';
import {launchBrowser} from './browser.mjs';
const browser=await launchBrowser(),errors=[],checks=[],root=resolve('public');
try{
 for(const edition of ['standard','parents']){
  const page=await browser.newPage({viewport:{width:1920,height:1080}});page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',async route=>{const u=new URL(route.request().url());if(u.pathname==='/api/cinema/library')return route.fulfill({json:{items:[{id:'a',name:'家庭综艺',sourceName:'合成清单',kind:'video',viewKind:'variety'},{id:'b',name:'家庭电影',sourceName:'合成清单',kind:'video',viewKind:'movie'},{id:'c',name:'家庭剧集',sourceName:'合成清单',kind:'video',viewKind:'series'}],unavailable:[]}});if(u.pathname==='/api/board')return route.fulfill({json:{cinema:{enabled:true}}});const filename=resolve(root,u.pathname==='/cinema'?'cinema/index.html':'.'+u.pathname);if(!filename.startsWith(root+sep))return route.fulfill({status:403,body:''});try{return route.fulfill({body:await readFile(filename),contentType:filename.endsWith('.js')?'text/javascript':filename.endsWith('.css')?'text/css':filename.endsWith('.html')?'text/html':'application/octet-stream'});}catch{return route.fulfill({status:404,body:''});}});
  await page.goto('https://preview.invalid/cinema?view=variety&edition='+edition);await page.waitForFunction(()=>document.querySelector('#cinema-status').textContent.includes('3 项'));assert.equal(await page.locator('.cinema-intro h1').textContent(),'看综艺');assert.deepEqual(await page.locator('.cinema-item strong').allTextContents(),['家庭综艺']);assert.equal(await page.locator('[data-media-filter=variety]').getAttribute('aria-pressed'),'true');
  await page.locator('[data-media-filter=movies]').click();assert.deepEqual(await page.locator('.cinema-item strong').allTextContents(),['家庭电影']);await page.locator('[data-media-filter=variety]').click();assert.deepEqual(await page.locator('.cinema-item strong').allTextContents(),['家庭综艺']);checks.push(edition+': variety entry, genre isolation, active tab and return after movie selection');await page.close();
 }
 assert.deepEqual(errors,[]);await writeFile('docs/verification/s51-variety-player.json',JSON.stringify({checks,errors,syntheticMedia:true,mediaPlayed:false,androidActivityTested:false},null,2)+'\n');console.log({checks,errors});
}finally{await browser.close();}
