import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {resolve,extname} from 'node:path';
import {launchBrowser} from './browser.mjs';

// Exercise shipped DOM/scripts and remote paths. All photos/apps are synthetic;
// requests stay inside Playwright, without a household/upload server.
const out=process.env.KAISHI_LAYOUT_IMAGES||'docs/images/s117-parents';await mkdir(out,{recursive:true});
const browser=await launchBrowser(),checks=[],errors=[],bridges=[];
let weatherMode='ready';
const mime={'.js':'text/javascript','.css':'text/css','.json':'application/json','.html':'text/html','.jpg':'image/jpeg','.png':'image/png','.webp':'image/webp','.svg':'image/svg+xml','.woff2':'font/woff2'};
async function open(width,height,dpr,count=2,document='index.html'){
  const page=await browser.newPage({viewport:{width,height},deviceScaleFactor:dpr,reducedMotion:'reduce'});
  page.on('pageerror',e=>errors.push(e.message));
  const cdp=await page.context().newCDPSession(page);await cdp.send('Page.enable');cdp.on('Page.frameRequestedNavigation',e=>{if(e.url.startsWith('aperture:'))bridges.push(new URL(e.url).pathname);});
  await page.route('**/*',async route=>{
    const u=new URL(route.request().url());
    if(u.protocol==='aperture:'){bridges.push(u.pathname);return route.abort();}
    if(u.pathname.startsWith('/weather/')){
      if(weatherMode==='offline')return route.fulfill({status:503,body:'{}'});
      const at=Date.now(),hour=Math.floor(at/3600000)*3600;
      const data=u.pathname.endsWith('location')?{city:'杭州',country:'中国',latitude:30.25,longitude:120.17,scope:'device-egress'}:{timezone:'Asia/Shanghai',current:{time:hour,temperature_2m:24,weather_code:2,is_day:1},hourly:{time:Array.from({length:48},(_,i)=>hour+i*3600),temperature_2m:Array.from({length:48},(_,i)=>24+Math.sin(i)*3)},daily:{time:Array.from({length:7},(_,i)=>hour+i*86400),temperature_2m_min:[16,17,18,18,19,18,17],temperature_2m_max:[24,25,26,25,24,23,24]}};
      return route.fulfill({json:{data,fetchedAt:at}});
    }
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
  await page.goto('https://appassets.androidplatform.net/assets/'+document+'?edition=parents');
  if(document==='gallery.html'){await page.waitForFunction(()=>!!window.KaishiGallerySettings&&document.getElementById('gallery-count').textContent.includes('/'));return page;}
  await page.waitForFunction(()=>!!window.apertureRemoteKey&&!!window.apertureHomeApps);
  await page.evaluate(()=>{apertureNativeFocus(true);apertureHomeApps({apps:[],slots:[{slot:'quick1',id:'bilibili',name:'哔哩哔哩'},{slot:'quick2',id:'tencent',name:'腾讯视频'}]});});
  return page;
}
function contained(inner,outer){return inner.x>=outer.x-.5&&inner.y>=outer.y-.5&&inner.x+inner.width<=outer.x+outer.width+.5&&inner.y+inner.height<=outer.y+outer.height+.5;}
try{
 for(const [width,height,dpr] of [[960,540,4],[1280,720,1],[1920,1080,1],[3840,2160,2]]){
  const page=await open(width,height,dpr);await page.waitForFunction(()=>KaishiWeatherUI.model());assert.equal(await page.locator('body').evaluate(n=>n.classList.contains('parents')),true);assert.equal(await page.locator('#lobby').getAttribute('data-home-page'),'1');
  const entries=['entry-live','entry-series','entry-cinema','entry-variety'];
  for(const id of entries)assert.ok(contained(await page.locator('#'+id).boundingBox(),{x:0,y:0,width,height}),id);
  const first=await page.locator('#entry-live').boundingBox();assert.ok(Math.abs(first.width/width-.15365)<.02);
  assert.match(await page.locator('#weather-home').innerText(),/杭州.*24/);assert.equal(await page.locator('#weather-summary').isVisible(),false);
  assert.match(await page.locator('.arch-logo').evaluate(n=>getComputedStyle(n).backgroundImage),/kaishi-parents-v2/);
  for(const selector of ['#quick1','#quick2']){const icon=await page.locator(selector+' .app-mark').boundingBox(),tile=await page.locator(selector).boundingBox();assert.ok(contained(icon,tile));}
  await page.locator('#entry-live').focus();for(const id of entries.slice(1)){await page.keyboard.press('ArrowRight');assert.equal(await page.evaluate(()=>document.activeElement.id),id);}
  await page.keyboard.press('ArrowDown');assert.ok(/^quick|shortcut/.test(await page.evaluate(()=>document.activeElement.id)));await page.keyboard.press('ArrowUp');assert.ok(entries.includes(await page.evaluate(()=>document.activeElement.id)));
  await page.locator('#weather-home').click();assert.equal(await page.locator('.weather-hour-strip .weather-cell').count(),24);await page.keyboard.press('Escape');assert.equal(await page.evaluate(()=>document.activeElement.id),'weather-home');
  await page.locator('#settings-open').click();await page.keyboard.press('Escape');assert.equal(await page.locator('#pane-backdrop').isVisible(),false);
  if(width===1920){await page.locator('#entry-live').focus();await page.screenshot({path:out+'/home.png'});}
  checks.push({width,height,dpr,fourViewingEntries:true,installedAppSnapshot:true,weather:true,remoteDirections:true,parentsLogo:true});await page.close();
 }
 const bridge=await open(1920,1080,1);await bridge.locator('#entry-variety').focus();await bridge.keyboard.press('Enter');await bridge.waitForTimeout(50);assert.ok(bridges.includes('/family-variety'));await bridge.close();
 assert.deepEqual(errors,[]);const sources={};for(const name of ['index.html','launcher.js','style.css','home-personal.js','weather.js'])sources['public/launcher/'+name]=createHash('sha256').update(await readFile('public/launcher/'+name)).digest('hex');
 await writeFile('docs/verification/s117-parents-browser.json',JSON.stringify({passed:true,checks,confirmVarietyEmitsNativeIntent:true,syntheticWeatherAndApps:true,realXiaomi:false,errors,sources},null,2)+'\n');console.log(JSON.stringify({passed:true,cases:checks.length,parents:true}));
}finally{await browser.close();}
