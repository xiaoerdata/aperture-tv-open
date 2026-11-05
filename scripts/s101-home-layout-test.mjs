import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {resolve,extname} from 'node:path';
import {launchBrowser} from './browser.mjs';

// Exercise shipped DOM/scripts and remote paths. All photos/apps are synthetic;
// requests stay inside Playwright, without a household/upload server.
const out=process.env.KAISHI_LAYOUT_IMAGES||'docs/images/s101-home';await mkdir(out,{recursive:true});
const browser=await launchBrowser(),checks=[],errors=[],bridges=[];
const mime={'.js':'text/javascript','.css':'text/css','.json':'application/json','.html':'text/html','.jpg':'image/jpeg','.png':'image/png','.webp':'image/webp','.svg':'image/svg+xml','.woff2':'font/woff2'};
async function open(width,height,dpr,count=2,document='index.html'){
  const page=await browser.newPage({viewport:{width,height},deviceScaleFactor:dpr,reducedMotion:'reduce'});
  page.on('pageerror',e=>errors.push(e.message));
  const cdp=await page.context().newCDPSession(page);await cdp.send('Page.enable');cdp.on('Page.frameRequestedNavigation',e=>{if(e.url.startsWith('aperture:'))bridges.push(new URL(e.url).pathname);});
  await page.route('**/*',async route=>{
    const u=new URL(route.request().url());
    if(u.protocol==='aperture:'){bridges.push(u.pathname);return route.abort();}
    if(u.pathname==='/api/gallery')return route.fulfill({json:{photos:Array.from({length:count},(_,i)=>({id:String(i+1).padStart(32,'0'),src:`/fixture-${i}.svg`,thumb:`/fixture-${i}.svg`})),seconds:8,template:'fade',maxCount:300,bytes:0}});
    if(/^\/fixture-\d+.svg$/.test(u.pathname))return route.fulfill({contentType:'image/svg+xml',body:`<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="900"><rect width="1600" height="900" fill="${u.pathname.includes('0')?'#28505d':'#7d5140'}"/><circle cx="1100" cy="300" r="160" fill="#ead8b1"/><path d="M0 900 500 370 1000 900 1400 500 1600 900" fill="#193941"/><text x="90" y="100" fill="white" font-size="36">合成测试照片 ${u.pathname.includes('0')?'1':'2'}</text></svg>`});
    if(u.pathname==='/api/messages')return route.fulfill({json:{configured:false,messages:[]}});
    if(u.pathname.startsWith('/api/')||u.pathname.startsWith('/weather/'))return route.fulfill({status:404,body:''});
    if(u.pathname.startsWith('/assets/')){
      const file=resolve('public/launcher',u.pathname.slice(8));assert.ok(file.startsWith(resolve('public/launcher')+'/'));
      return route.fulfill({body:await readFile(file),contentType:mime[extname(file)]||'application/octet-stream'});
    }
    throw Error('Unexpected request '+u.pathname);
  });
  await page.addInitScript(()=>localStorage.setItem('kaishi-ambient-minutes','0'));
  await page.clock.install();
  await page.goto('https://appassets.androidplatform.net/assets/'+document);
  if(document==='gallery.html'){await page.waitForFunction(()=>!!window.KaishiGallerySettings&&document.getElementById('gallery-count').textContent.includes('/'));return page;}
  await page.waitForFunction(()=>!!window.apertureRemoteKey&&!!window.apertureHomeApps);
  await page.evaluate(()=>{apertureNativeFocus(true);apertureHomeApps({apps:[],slots:[{slot:'quick1',id:'bilibili',name:'哔哩哔哩'},{slot:'quick2',id:'tencent',name:'腾讯视频'}]});});
  return page;
}
function contained(inner,outer){return inner.x>=outer.x-.5&&inner.y>=outer.y-.5&&inner.x+inner.width<=outer.x+outer.width+.5&&inner.y+inner.height<=outer.y+outer.height+.5;}
try{
  for(const [width,height,dpr] of [[640,360,3],[960,540,1],[960,540,2],[960,540,4],[1024,576,1],[1280,720,1.5],[1920,1080,1],[1920,1080,2],[3840,2160,1],[3840,2160,2],[1280,640,1],[960,500,2]]){
    const page=await open(width,height,dpr);
    const watch=await page.locator('#home-live').boundingBox();
    assert.ok(Math.abs(watch.width-(380*.86*Math.min(width/1920,height/1080)))<1,'controls must be smaller at every density');
    for(const id of ['home-live','home-series','home-movies','home-variety'])assert.ok(contained(await page.locator('#'+id).boundingBox(),{x:0,y:0,width,height}),id);
    for(const selector of ['#dock-quick1','#dock-quick2']){
      const box=await page.locator(selector).boundingBox(),icon=await page.locator(selector+' .app-mark').boundingBox();assert.ok(contained(icon,box),'home icon overflow');
    }
    await page.locator('#home-live').focus();await page.keyboard.press('ArrowDown');await page.keyboard.press('ArrowDown');
    assert.equal(await page.locator('#lobby').getAttribute('data-home-page'),'1');
    assert.equal(await page.locator('#home-app-list').isVisible(),false); // first screen is hidden, never folded
    for(const id of ['quick1','quick2'])assert.ok(contained(await page.locator('#'+id+' .app-mark').boundingBox(),await page.locator('#'+id).boundingBox()),'apps icon overflow');
    await page.keyboard.press('ArrowDown');
    assert.equal(await page.locator('#lobby').getAttribute('data-home-page'),'2');
    assert.equal(await page.evaluate(()=>document.activeElement.id),'gallery-home-settings');
    await page.waitForSelector('#gallery-home-scene .current img');
    const album=await page.locator('#gallery-home-scene').boundingBox();assert.deepEqual(album,{x:0,y:0,width,height});
    assert.equal(await page.locator('#pane-backdrop').isVisible(),false);
    if(width===1920&&dpr===1){
      await page.screenshot({path:out+'/album.png'});
      const first=await page.locator('#gallery-home-scene .current img').getAttribute('src');
      // Keep real image loading, advance only the clock after the first photo.
      await page.clock.runFor(8500);
      await page.waitForFunction(src=>document.querySelector('#gallery-home-scene .current img').getAttribute('src')!==src,first);
      const second=await page.locator('#gallery-home-scene .current img').getAttribute('src');assert.notEqual(second,first);
      await page.evaluate(()=>apertureNativeFocus(false));await page.clock.runFor(17000);
      assert.equal(await page.locator('#gallery-home-scene .current img').getAttribute('src'),second,'background playback must stop');
      await page.evaluate(()=>apertureNativeFocus(true));
    }
    assert.equal(await page.evaluate(()=>document.activeElement.id),'gallery-home-settings');
    await page.keyboard.press('ArrowDown');assert.equal(await page.locator('#lobby').getAttribute('data-home-page'),'3');
    assert.equal(await page.locator('#content-home').isVisible(),true);assert.equal(await page.locator('#gallery-home-scene').isVisible(),false);
    await page.keyboard.press('ArrowUp');assert.equal(await page.locator('#lobby').getAttribute('data-home-page'),'2');
    await page.waitForSelector('#gallery-home-scene .current img');
    await page.keyboard.press('ArrowUp');assert.equal(await page.locator('#lobby').getAttribute('data-home-page'),'1');
    await page.keyboard.press('ArrowUp');assert.equal(await page.locator('#lobby').getAttribute('data-home-page'),'0');
    await page.locator('#settings-open').click();
    const pane=await page.locator('.entry-pane').boundingBox();assert.ok(pane.x>=width*.6&&pane.width<=width*.3,'settings must be a compact right drawer');
    const buttons=page.locator('#pane-actions button');for(let n=0;n<await buttons.count();n++)await page.keyboard.press('ArrowDown');
    assert.equal(await page.evaluate(()=>document.activeElement.getAttribute('data-action')),'more-settings');
    const last=await page.locator('#pane-actions button').last().boundingBox();assert.ok(contained(last,pane),'settings must scroll to the final entry');
    await page.keyboard.press('Escape');assert.equal(await page.locator('#pane-backdrop').isVisible(),false);
    await page.locator('#home-app-add').click();
    const mark=page.locator('.preset-grid .app-mark').first();await mark.waitFor();assert.ok(contained(await mark.boundingBox(),await page.locator('.preset-grid button').first().boundingBox()));
    await page.keyboard.press('Escape');
    if(width===1920&&dpr===1){await page.screenshot({path:out+'/home.png'});await page.locator('#settings-open').click();await page.screenshot({path:out+'/settings.png'});}
    const group=await page.locator('#home-watch').boundingBox();assert.ok(group.width<width*.74,'watch cards must leave scenery visible');
    checks.push({width,height,dpr,watchWidthRatio:watch.width/width,albumFullViewport:true,autoPlayWithoutConfirm:true,pageOrderAndUpDown:true,iconsContained:true,settingsRightAndScrollable:true});await page.close();
  }
  const empty=await open(1920,1080,2,0);await empty.locator('[data-home-screen="2"]').click();
  assert.equal(await empty.locator('#gallery-home-scene .gallery-empty').isVisible(),true);
  assert.equal(await empty.locator('#gallery-home-scene img').count(),0);assert.deepEqual(bridges,[],'changing home pages must not open management/upload');await empty.close();
  const confirm=await open(1920,1080,1);await confirm.locator('[data-home-screen="2"]').click();await confirm.keyboard.press('Enter');
  await assert.doesNotReject(async()=>{for(let i=0;i<30&&!bridges.includes('/gallery');i++)await new Promise(r=>setTimeout(r,20));assert.ok(bridges.includes('/gallery'));});await confirm.close();
  const mismatch=await open(1920,1080,2);await mismatch.evaluate(()=>apertureSetViewport(960,540));
  assert.ok(Math.abs((await mismatch.locator('#home-live').boundingBox()).width-380*.43)<1);await mismatch.close();
  for(const width of [960,1920,3840]){
    const manager=await open(width,width*9/16,1,13,'gallery.html');
    const sheet=await manager.locator('.gallery-settings-sheet').boundingBox();assert.ok(sheet.x>width*.5&&sheet.width<width*.45);
    assert.equal(await manager.evaluate(()=>document.activeElement.id),'gallery-heading');
    for(let i=0;i<2;i++)await manager.keyboard.press('Enter');assert.equal(bridges.filter(p=>p.includes('upload')).length,0);
    await manager.locator('#gallery-grid button').first().focus();for(let i=0;i<3;i++)await manager.keyboard.press('ArrowDown');
    const focused=await manager.evaluate(()=>({tag:document.activeElement.tagName,rect:document.activeElement.getBoundingClientRect().toJSON()}));assert.equal(focused.tag,'BUTTON');assert.ok(contained(focused.rect,sheet));
    if(width===1920)await manager.screenshot({path:out+'/album-settings.png'});
    await manager.close();
  }
  assert.deepEqual(errors,[]);
  const inputs={};for(const file of ['index.html','launcher.js','style.css','gallery.js','gallery.css','gallery.html','gallery-settings.js'])inputs['public/launcher/'+file]=createHash('sha256').update(await readFile('public/launcher/'+file)).digest('hex');
  const receipt={passed:true,inputs,checks,confirmEmitsAlbumManagementIntent:true,albumManagementRightDrawer:true,emptyAlbum:true,nativeCssViewportMismatch:true,errors,realXiaomi:false,nativeActivityExecuted:false,networkListenerStarted:false};
  await writeFile(process.env.KAISHI_LAYOUT_RECEIPT||'docs/verification/s101-home-browser.json',JSON.stringify(receipt,null,2)+'\n');console.log(JSON.stringify(receipt));
}finally{await browser.close();}
