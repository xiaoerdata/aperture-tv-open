import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {resolve,extname} from 'node:path';
import {launchBrowser} from './browser.mjs';

// Exercise shipped DOM/scripts and remote paths. All photos/apps are synthetic;
// requests stay inside Playwright, without a household/upload server.
const out=process.env.KAISHI_LAYOUT_IMAGES||'docs/images/s115-home-weather';await mkdir(out,{recursive:true});
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
  await page.goto('https://appassets.androidplatform.net/assets/'+document);
  if(document==='gallery.html'){await page.waitForFunction(()=>!!window.KaishiGallerySettings&&document.getElementById('gallery-count').textContent.includes('/'));return page;}
  await page.waitForFunction(()=>!!window.apertureRemoteKey&&!!window.apertureHomeApps);
  await page.evaluate(()=>{apertureNativeFocus(true);apertureHomeApps({apps:[],slots:[{slot:'quick1',id:'bilibili',name:'哔哩哔哩'},{slot:'quick2',id:'tencent',name:'腾讯视频'}]});});
  return page;
}
function contained(inner,outer){return inner.x>=outer.x-.5&&inner.y>=outer.y-.5&&inner.x+inner.width<=outer.x+outer.width+.5&&inner.y+inner.height<=outer.y+outer.height+.5;}
try{
 for(const [width,height,dpr] of [[960,540,4],[1280,720,1],[1920,1080,1],[3840,2160,2]]){
  const page=await open(width,height,dpr);await page.waitForFunction(()=>document.querySelector('#weather-summary').dataset.state==='ready');
  assert.match(await page.locator('#weather-summary').innerText(),/杭州.*24/);assert.match(await page.locator('#weather-summary').innerText(),/多云/);
  assert.equal(await page.locator('#weather-home strong').textContent(),'看天气');assert.equal(await page.locator('#weather-home small').count(),0);
  const summary=await page.locator('#weather-summary').boundingBox(),clock=await page.locator('.header-time').boundingBox(),button=await page.locator('#weather-home').boundingBox(),variety=await page.locator('#home-variety').boundingBox();
  assert.ok(contained(summary,{x:0,y:0,width,height}));assert.ok(summary.x>=clock.x+clock.width&&summary.y<clock.y+clock.height);assert.ok(button.x>=variety.x+variety.width);
  const fonts=await page.locator('#weather-summary').evaluate(n=>({main:parseFloat(getComputedStyle(n.querySelector('strong')).fontSize),detail:parseFloat(getComputedStyle(n.querySelector('.weather-summary-copy>span')).fontSize),focusable:n.hasAttribute('tabindex')}));assert.ok(fonts.main>=38&&fonts.detail>=28);assert.equal(fonts.focusable,false);
  await page.locator('#home-variety').focus();await page.keyboard.press('ArrowRight');assert.equal(await page.evaluate(()=>document.activeElement.id),'weather-home');await page.keyboard.press('Enter');assert.equal(await page.locator('#weather-overlay').isVisible(),true);assert.equal(await page.locator('.weather-hour-strip .weather-cell').count(),24);await page.keyboard.press('Escape');assert.equal(await page.evaluate(()=>document.activeElement.id),'weather-home');await page.keyboard.press('ArrowLeft');assert.equal(await page.evaluate(()=>document.activeElement.id),'home-variety');
  await page.evaluate(()=>apertureHomeSources({nasCount:2}));await page.waitForFunction(()=>!document.querySelector('#personal-panel').hidden);
  const panel=await page.locator('#personal-panel').boundingBox();assert.ok(summary.x+summary.width<=panel.x||summary.y+summary.height<=panel.y||summary.y>=panel.y+panel.height,'weather must leave the configured NAS panel clear');
  if(width===1920){await page.screenshot({path:out+'/home-weather-nas.png'});await page.evaluate(()=>apertureHomeSources({nasCount:0}));await page.locator('#home-live').focus();await page.screenshot({path:out+'/home-weather.png'});}
  await page.locator('#home-live').focus();await page.keyboard.press('ArrowDown');await page.keyboard.press('ArrowDown');assert.equal(await page.locator('#weather-summary').isVisible(),false);await page.keyboard.press('ArrowUp');assert.equal(await page.locator('#weather-summary').isVisible(),true);
  checks.push({width,height,dpr,summaryByClock:true,largeFonts:fonts,watchWeatherEntry:true,remoteOpenAndReturn:true,nasPanelClear:true});await page.close();
 }
 weatherMode='offline';const offline=await open(1920,1080,1);await offline.waitForFunction(()=>document.querySelector('#weather-summary').dataset.state==='unavailable');assert.match(await offline.locator('#weather-summary').innerText(),/天气暂不可用/);assert.equal(await offline.locator('#weather-home strong').textContent(),'看天气');assert.doesNotMatch(await offline.locator('#weather-summary').innerText(),/24°/);await offline.screenshot({path:out+'/home-weather-offline.png'});
 weatherMode='ready';await offline.evaluate(()=>window.dispatchEvent(new Event('online')));await offline.waitForFunction(()=>document.querySelector('#weather-summary').dataset.state==='ready');await offline.close();
 const disabled=await open(1920,1080,1);await disabled.evaluate(()=>localStorage.setItem('kaishi-weather-enabled','off'));await disabled.reload();await disabled.waitForFunction(()=>document.querySelector('#weather-summary').dataset.state==='disabled');assert.match(await disabled.locator('#weather-summary').innerText(),/天气已关闭/);assert.equal(await disabled.locator('#weather-home strong').textContent(),'看天气');await disabled.close();
 assert.deepEqual(errors,[]);const sources={};for(const name of ['index.html','launcher.js','style.css','weather.js','weather-model.js'])sources['public/launcher/'+name]=createHash('sha256').update(await readFile('public/launcher/'+name)).digest('hex');
 await writeFile('docs/verification/s115-home-weather-browser.json',JSON.stringify({passed:true,checks,offlineAndRecovery:true,disabledSummary:true,syntheticWeatherAndNas:true,realNetworkForecast:false,realXiaomi:false,errors,sources},null,2)+'\n');console.log(JSON.stringify({passed:true,cases:checks.length,offlineAndRecovery:true}));
}finally{await browser.close();}
