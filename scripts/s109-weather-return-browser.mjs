// Browser JS paths only; weather data is synthetic and no Android task is running.
import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {resolve,extname} from 'node:path';
import {createHash} from 'node:crypto';
import {launchBrowser} from './browser.mjs';
const browser=await launchBrowser(),checks=[],errors=[],images='docs/images/s109';await mkdir(images,{recursive:true});
const mime={'.js':'text/javascript','.css':'text/css','.json':'application/json','.html':'text/html','.jpg':'image/jpeg','.png':'image/png','.webp':'image/webp','.svg':'image/svg+xml','.woff2':'font/woff2'};
async function setup(path,offline=false){
 const page=await browser.newPage({viewport:{width:1920,height:1080},reducedMotion:'reduce'}),navigation=[];page.on('pageerror',e=>errors.push(e.message));
 const cdp=await page.context().newCDPSession(page);await cdp.send('Page.enable');cdp.on('Page.frameRequestedNavigation',event=>navigation.push(event.url));
 await page.route('**/*',async route=>{const url=new URL(route.request().url());
  if(url.pathname.startsWith('/weather/')){
   if(offline)return route.fulfill({status:503,json:{}});
   const now=Date.now(),hour=Math.floor(now/3600000)*3600;
   const data=url.pathname==='/weather/location'?{city:'杭州',country:'中国',latitude:30.27,longitude:120.15,scope:'device-egress'}:{timezone:'Asia/Shanghai',current:{time:hour,temperature_2m:23,apparent_temperature:24,relative_humidity_2m:60,wind_speed_10m:8,weather_code:0,is_day:1},hourly:{time:Array.from({length:24},(_,i)=>hour+i*3600),temperature_2m:Array(24).fill(23),precipitation_probability:Array(24).fill(10),weather_code:Array(24).fill(0),is_day:Array(24).fill(1)},daily:{time:Array.from({length:7},(_,i)=>hour+i*86400),temperature_2m_min:Array(7).fill(16),temperature_2m_max:Array(7).fill(25),weather_code:Array(7).fill(0),precipitation_probability_max:Array(7).fill(10)}};
   return route.fulfill({json:{data,fetchedAt:now}});
  }
  if(url.pathname.startsWith('/api/'))return route.fulfill({status:404,json:{}});
  if(url.pathname.startsWith('/assets/')){const file=resolve('public/launcher',url.pathname.slice(8));assert.ok(file.startsWith(resolve('public/launcher')+'/'));return route.fulfill({body:await readFile(file),contentType:mime[extname(file)]||'application/octet-stream'});}
  throw Error('Unexpected test URL: '+url.pathname);
 });
 await page.addInitScript(()=>localStorage.setItem('kaishi-ambient-minutes','0'));
 await page.goto('https://appassets.androidplatform.net/assets/'+path);await page.waitForFunction(()=>window.apertureNativeFocus&&window.KaishiWeatherUI);await page.evaluate(()=>apertureNativeFocus(true));
 return {page,navigation};
}
try{
 const {page:home,navigation}=await setup('index.html');await home.waitForFunction(()=>KaishiWeatherUI.model());
 for(let i=0;i<10;i++){
  await home.locator('#weather-home').focus();await home.keyboard.press('Enter');assert.equal(await home.evaluate(()=>KaishiWeatherUI.isOpen()),true);
  await home.evaluate(()=>apertureRemoteKey('BrowserBack'));assert.equal(await home.evaluate(()=>KaishiWeatherUI.isOpen()),false);assert.equal(await home.evaluate(()=>document.activeElement.id),'weather-home');assert.ok(home.url().endsWith('/assets/index.html'));
 }
 assert.ok(!navigation.some(url=>url.includes('/ambient/close')));
 await home.locator('#weather-home').click();await home.getByRole('button',{name:'未来 7 天',exact:true}).click();assert.equal(await home.locator('.weather-day-row').count(),7);await home.getByRole('button',{name:'返回',exact:true}).click();assert.equal(await home.evaluate(()=>document.activeElement.id),'weather-home');
 await home.screenshot({path:images+'/home-after-weather-return.png'});
 checks.push('Embedded home: ten native-bridge Back cycles restore weather focus without navigation; 7-day view and on-screen Return also retain app home');
 const {page:offline,navigation:offlineNav}=await setup('index.html',true);await offline.locator('#weather-home').focus();await offline.keyboard.press('Enter');await offline.evaluate(()=>apertureRemoteKey('BrowserBack'));assert.equal(await offline.evaluate(()=>KaishiWeatherUI.isOpen()),false);assert.equal(await offline.evaluate(()=>document.activeElement.id),'weather-home');assert.ok(!offlineNav.some(url=>url.includes('/ambient/close')));checks.push('Offline weather closes to the same home and keeps focus');
 for(const button of [false,true]){
  const {page:panel,navigation:panelNav}=await setup('ambient.html?panel=weather');await panel.waitForFunction(()=>KaishiWeatherUI.isOpen());
  if(button)await panel.getByRole('button',{name:'返回',exact:true}).click();else await panel.evaluate(()=>apertureBack());
  await panel.waitForFunction(()=>!KaishiWeatherUI.isOpen());await panel.evaluate(()=>{apertureBack();KaishiWeatherUI.close();});
  assert.equal(panelNav.filter(url=>url==='aperture://ambient/close').length,1);assert.ok(!panelNav.some(url=>/open-system|\/action\//.test(url)));
  await panel.evaluate(()=>apertureNativeFocus(false));await panel.close();
 }
 checks.push('Standalone weather: browser/native JS Return emits one child-close action only, never an original-desktop action; repeated close is ignored');
 assert.deepEqual(errors,[]);const sources={};for(const file of ['public/launcher/weather.js','public/launcher/ambient.js','public/launcher/ambient.html','public/launcher/launcher.js'])sources[file]=createHash('sha256').update(await readFile(file)).digest('hex');
 await writeFile('docs/verification/s109-weather-return-browser.json',JSON.stringify({passed:true,checks,errors,sources,syntheticWeather:true,realAndroidExecuted:false,realXiaomi:false},null,2)+'\n');console.log(JSON.stringify({checks,errors},null,2));
}finally{await browser.close();}
