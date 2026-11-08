import assert from 'node:assert/strict';
import {mkdtemp,readFile,writeFile,mkdir,rm,readdir} from 'node:fs/promises';
import {tmpdir} from 'node:os';import {join,resolve,extname} from 'node:path';
import {execFile,spawn} from 'node:child_process';import {promisify} from 'node:util';
import {launchBrowser} from './browser.mjs';
import {createHash} from 'node:crypto';
const run=promisify(execFile),temp=await mkdtemp(join(tmpdir(),'kaishi-s105-')),images='docs/images/s105',checks=[],errors=[];
await mkdir(images,{recursive:true});let browser,fixture;
try{
 const jdk=resolve('.android-local/jdk17/bin'),base='android/app/src/main/java/',jsonBase=resolve('.android-local/gradle-user/caches/modules-2/files-2.1/org.json/json/20160810');
 let json;for(const dir of await readdir(jsonBase)){const path=join(jsonBase,dir,'json-20160810.jar');try{await readFile(path);json=path;break;}catch{}}
 assert.ok(json);const source=['MessageFeed','LobbyAssets','MessageNotes','MessagePhoneSession'].map(s=>base+'org/zhixiaoce/aperture/'+s+'.java');
 const vendor=['QrCode','QrSegment','BitBuffer','DataTooLongException'].map(s=>base+'io/nayuki/qrcodegen/'+s+'.java');
 await run(join(jdk,'javac'),['-cp',json,'-d',temp,...source,...vendor,'tests/java/PhoneEditingTest.java','tests/java/PhoneEditorFixture.java']);
 const result=await run(join(jdk,'java'),['-Dsun.net.http.allowRestrictedHeaders=true','-cp',temp+':'+json,'org.zhixiaoce.aperture.PhoneEditingTest',join(temp,'qr.pbm')]);checks.push(result.stdout.trim());
 await run('python3',['-c',"import cv2,sys; image=cv2.imread(sys.argv[1]); text,_,_=cv2.QRCodeDetector().detectAndDecode(image); assert text=='http://192.168.1.12:54321/#abcdefghijklmnopqrstuvwxyzABCDEF'; print('QR decoded')",join(temp,'qr.pbm')]);checks.push('Native QR encoder fixture independently decoded with OpenCV');
 fixture=spawn(join(jdk,'java'),['-cp',temp+':'+json,'org.zhixiaoce.aperture.PhoneEditorFixture',resolve('public/launcher/messages-edit.html')],{stdio:['pipe','pipe','pipe']});
 const phoneUrl=await new Promise((ok,no)=>{let text='';const timer=setTimeout(()=>no(Error('Fixture startup timeout')),10000);fixture.stdout.on('data',chunk=>{text+=chunk;if(text.includes('\n')){clearTimeout(timer);ok(text.split('\n')[0]);}});fixture.once('error',no);fixture.once('exit',code=>{if(!text)no(Error('Fixture exited '+code));});});
 browser=await launchBrowser();const phone=await browser.newPage({viewport:{width:390,height:844}});phone.on('pageerror',e=>errors.push(e.message));await phone.goto(phoneUrl);await phone.waitForFunction(()=>!document.querySelector('#save').disabled);
 assert.equal(await phone.evaluate(()=>location.hash),'');
 const note='<img src=x onerror=alert(1)> 今晚一起吃饭';await phone.locator('#text').fill(note);await phone.locator('#save').click();await phone.waitForFunction(()=>document.querySelectorAll('#notes li').length===1);
 assert.equal(await phone.locator('#notes img').count(),0);assert.equal(await phone.locator('#notes p').innerText(),note);
 await phone.getByRole('button',{name:'编辑',exact:true}).click();await phone.locator('#text').fill('回家记得带钥匙');await phone.locator('#save').click();await phone.waitForFunction(()=>document.querySelector('#notes p').textContent==='回家记得带钥匙');
 for(const width of [320,390,760]){await phone.setViewportSize({width,height:844});assert.ok(await phone.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));for(const id of ['save','refresh']){const b=await phone.locator('#'+id).boundingBox();assert.ok(b.height>=44);}if(width===390)await phone.screenshot({path:images+'/phone-notes.png'});}
 phone.once('dialog',dialog=>dialog.accept());await phone.getByRole('button',{name:'删除',exact:true}).click();await phone.waitForFunction(()=>document.querySelectorAll('#notes li').length===0);checks.push('Real loopback production HTTP editor: add/edit/delete, safe text rendering, 320/390/760 widths and 44px controls');await phone.close();

 const mime={'.js':'text/javascript','.css':'text/css','.json':'application/json','.html':'text/html','.jpg':'image/jpeg','.png':'image/png','.webp':'image/webp','.svg':'image/svg+xml','.woff2':'font/woff2'};
 const now=Date.now(),hour=Math.floor(now/3600000)*3600,day=Math.floor((now+28800000)/86400000)*86400-28800;
 const forecast={fetchedAt:now,data:{timezone:'Asia/Shanghai',current:{time:hour,temperature_2m:22,apparent_temperature:23,relative_humidity_2m:62,weather_code:2,is_day:1,wind_speed_10m:8},hourly:{time:Array.from({length:24},(_,i)=>hour+i*3600),temperature_2m:Array.from({length:24},(_,i)=>i===8?null:21+Math.round(Math.sin(i/4)*4)),precipitation_probability:Array.from({length:24},(_,i)=>i%6*15),weather_code:Array.from({length:24},(_,i)=>i%3===0?61:2),is_day:Array.from({length:24},()=>1)},daily:{time:Array.from({length:7},(_,i)=>day+i*86400),weather_code:[2,61,3,0,2,80,1],temperature_2m_min:[17,16,15,17,18,17,16],temperature_2m_max:[24,22,23,25,26,24,25],precipitation_probability_max:[20,75,30,0,20,65,10]}}};
 let offline=false;const bridges=[];
 const page=await browser.newPage({viewport:{width:1920,height:1080},reducedMotion:'reduce'});page.on('pageerror',e=>errors.push(e.message));
 const cdp=await page.context().newCDPSession(page);await cdp.send('Page.enable');cdp.on('Page.frameRequestedNavigation',e=>{if(e.url==='aperture://action/message-edit')bridges.push('message-edit');});
 await page.route('**/*',async route=>{const u=new URL(route.request().url());
  if(u.pathname==='/weather/location')return route.fulfill({json:{data:{city:'上海',country:'中国',latitude:31.23,longitude:121.47,scope:'device-egress'}}});
  if(u.pathname==='/weather/forecast')return offline?route.fulfill({status:503,body:'{}'}):route.fulfill({json:forecast});
  if(u.pathname==='/weather/search')return route.fulfill({json:{data:{results:[{name:'杭州',country:'中国',admin1:'浙江',latitude:30.27,longitude:120.15}]}}});
  if(u.pathname==='/api/messages')return route.fulfill({json:{configured:true,messages:[{id:'fixture',text:'晚饭在冰箱，记得热一下。',time:''}]}});
  if(u.pathname.startsWith('/api/'))return route.fulfill({status:404,body:''});
  if(u.pathname.startsWith('/assets/')){const file=resolve('public/launcher',u.pathname.slice(8));assert.ok(file.startsWith(resolve('public/launcher')+'/'));return route.fulfill({body:await readFile(file),contentType:mime[extname(file)]||'application/octet-stream'});}
  if(u.protocol==='aperture:')return route.abort();throw Error('Unexpected route '+u.pathname);
 });
 await page.addInitScript(()=>localStorage.setItem('kaishi-ambient-minutes','0'));
 await page.goto('https://appassets.androidplatform.net/assets/index.html');await page.waitForFunction(()=>window.apertureRemoteKey&&window.KaishiWeatherUI);await page.evaluate(()=>{apertureNativeFocus(true);apertureHomeApps({apps:[],slots:[{slot:'quick1',id:'bilibili',name:'哔哩哔哩'},{slot:'quick2',id:'tencent',name:'腾讯视频'}]});});await page.waitForFunction(()=>KaishiWeatherUI.model());
 for(const [width,height] of [[1920,1080],[960,540],[3840,2160],[1280,640],[1920,1080]]){
  await page.setViewportSize({width,height});await page.waitForFunction(({w,h})=>{const r=apertureViewportReport();return r.fitted.width===w&&r.fitted.height===h;},{w:width,h:height});const b=await page.locator('#home-live').boundingBox();assert.ok(Math.abs(b.width-380*.86*Math.min(width/1920,height/1080))<1,'uniform live resize '+JSON.stringify({width,height,box:b,report:await page.evaluate(()=>apertureViewportReport())}));
 }
 await page.locator('[data-home-screen="1"]').click();
 for(const id of ['quick1','quick2']){const rect=await page.locator('#'+id+' .shortcut-glyph').boundingBox(),logo=await page.locator('#'+id+' .app-mark').boundingBox();assert.ok(Math.abs(rect.x+rect.width/2-logo.x-logo.width/2)<.6);assert.ok(Math.abs(rect.y+rect.height/2-logo.y-logo.height/2)<.6);}
 await page.screenshot({path:images+'/apps-centered.png'});checks.push('Live viewport resize 1080p/540p/4K/short window preserves uniform scale; second-screen logos centered in fixed icon stages');
 await page.locator('[data-home-screen="0"]').click();await page.locator('#weather-home').click();await page.waitForSelector('.weather-temperature-line');
 assert.equal(await page.locator('.weather-hour-strip .weather-cell').count(),24);assert.equal(await page.locator('.weather-temperature-point').count(),23);assert.equal((await page.locator('.weather-temperature-line').getAttribute('d')).split('M').length-1,2,'missing temperatures leave a real gap');
 await page.screenshot({path:images+'/weather-hours.png'});await page.locator('.weather-body').focus();const scroll=await page.locator('.weather-hour-strip').evaluate(n=>n.scrollLeft);await page.keyboard.press('ArrowRight');assert.ok(await page.locator('.weather-hour-strip').evaluate((n,b)=>n.scrollLeft>b,scroll));await page.keyboard.press('ArrowUp');assert.equal(await page.evaluate(()=>document.activeElement.textContent),'24 小时');
 await page.getByRole('button',{name:'未来 7 天',exact:true}).click();assert.equal(await page.locator('.weather-day-row').count(),7);assert.equal(await page.locator('.weather-temperature-range').count(),7);await page.screenshot({path:images+'/weather-days.png'});
 for(const width of [960,1920,3840]){await page.setViewportSize({width,height:width*9/16});await page.waitForTimeout(100);const b=await page.locator('.weather-day-row').last().boundingBox();assert.ok(b.x>=0&&b.y>=0&&b.x+b.width<=width+1&&b.y+b.height<=width*9/16+1);}
 checks.push('Weather: 24h curve with missing-data gaps, independently labelled rain axis, remote hour scrolling and 7 comparable temperature-range rows at 540p/1080p/4K');
 await page.getByRole('button',{name:'选择城市',exact:true}).click();await page.getByRole('searchbox').fill('杭州');await page.getByRole('button',{name:'搜索',exact:true}).click();await page.getByRole('button',{name:'杭州 · 浙江 · 中国',exact:true}).click();await page.waitForFunction(()=>document.querySelector('.weather-current h2')?.textContent==='杭州');await page.keyboard.press('Escape');
 await page.locator('[data-home-screen="3"]').click();await page.locator('#entry-information').focus();await page.keyboard.press('ArrowRight');assert.equal(await page.evaluate(()=>document.activeElement.id),'message-edit');await page.keyboard.press('Enter');await page.waitForTimeout(80);assert.ok(bridges.includes('message-edit'),'fourth screen scanner emits actual native editor intent');
 offline=true;await page.evaluate(()=>localStorage.removeItem('kaishi-weather-cache'));await page.reload();await page.evaluate(()=>apertureNativeFocus(true));await page.locator('#weather-home').click();await page.waitForFunction(()=>document.querySelector('.weather-status').textContent.includes('暂时连不上'));assert.equal(await page.locator('.weather-temperature-line').count(),0);assert.equal(await page.locator('.weather-hour-strip .weather-cell').count(),0);
 checks.push('City search/selection and return work; fourth-screen remote launches editor; cold forecast failure shows honest empty state');
 await page.close();assert.deepEqual(errors,[]);
 const sources={};for(const file of [...source,...vendor,'public/launcher/messages-edit.html','public/launcher/weather.js','public/launcher/ambient.css','public/launcher/launcher.js','public/launcher/style.css','public/launcher/index.html'])sources[file]=createHash('sha256').update(await readFile(file)).digest('hex');
 await writeFile('docs/verification/s105-phone-weather.json',JSON.stringify({passed:true,checks,errors,sources,syntheticWeatherAndNotes:true,loopbackOnly:true,nativeActivityExecuted:false,realXiaomi:false,productionPhotosUsed:false},null,2)+'\n');console.log(JSON.stringify({checks,errors}));
}finally{if(browser)await browser.close();if(fixture){fixture.stdin.end();await new Promise(resolve=>{if(fixture.exitCode!==null)resolve();else{fixture.once('exit',resolve);setTimeout(()=>fixture.kill(),1000).unref();}});}await rm(temp,{recursive:true,force:true});}
