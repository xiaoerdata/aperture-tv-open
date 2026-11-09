import assert from 'node:assert/strict';
import {mkdtemp,mkdir,readFile,writeFile,rm,readdir} from 'node:fs/promises';
import {join,resolve,extname} from 'node:path';
import {tmpdir} from 'node:os';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {createHash} from 'node:crypto';
import {launchBrowser} from './browser.mjs';
import {checkPack} from './resource-pack.mjs';

const run=promisify(execFile),temp=await mkdtemp(join(tmpdir(),'kaishi-s108-'));
const checks=[],errors=[],images='docs/images/s108',sources=[];let browser;
await mkdir(images,{recursive:true});
try{
 const jdk=resolve('.android-local/jdk17/bin'),jsonRoot=resolve('.android-local/gradle-user/caches/modules-2/files-2.1/org.json/json/20160810');let lib;
 for(const d of await readdir(jsonRoot)){const p=join(jsonRoot,d,'json-20160810.jar');try{await readFile(p);lib=p;break;}catch{}}
 const src='android/app/src/main/java/org/zhixiaoce/aperture/';
 sources.push(...['AppWall','CinemaOrder','CinemaCatalog','CinemaDates','CinemaResourcePack','CinemaFormats','GalleryUploadHttp'].map(x=>src+x+'.java'),...['HomeUpdatesTest','GalleryUploadHttpTest','GalleryUploadLimitTest'].map(x=>'tests/java/'+x+'.java'));
 await run(join(jdk,'javac'),['-encoding','UTF-8','-cp',lib,'-d',temp,...sources]);
 for(const name of ['HomeUpdatesTest','GalleryUploadHttpTest','GalleryUploadLimitTest'])checks.push((await run(join(jdk,'java'),['-cp',temp+':'+lib,'org.zhixiaoce.aperture.'+name])).stdout.trim());
 const pack=JSON.parse(await readFile('examples/resource-packs/family.kaishi.json','utf8'));
 const item=pack.resources.find(r=>r.type==='collection').items[0];item.updatedAt='2026-11-08T03:00:00Z';item.doubanRating=8.7;
 checkPack(Buffer.from(JSON.stringify(pack)));
 item.metadata={...(item.metadata||{}),updatedAt:'2026-11-01'};assert.throws(()=>checkPack(Buffer.from(JSON.stringify(pack))));
 checks.push('Authoring schema accepts update time and explicit Douban score; rejects conflicting top-level and metadata values');

 browser=await launchBrowser();
 const page=await browser.newPage({viewport:{width:1920,height:1080},reducedMotion:'reduce'});page.on('pageerror',e=>errors.push(e.message));
 const mime={'.js':'text/javascript','.css':'text/css','.json':'application/json','.html':'text/html','.jpg':'image/jpeg','.png':'image/png','.webp':'image/webp','.svg':'image/svg+xml','.woff2':'font/woff2'};
 const photo='<svg xmlns="http://www.w3.org/2000/svg" width="1920" height="1080"><path fill="#7090b0" d="M0 0h1920v1080H0z"/></svg>';
 await page.route('**/*',async route=>{
  const u=new URL(route.request().url());
  if(u.pathname==='/api/gallery')return route.fulfill({json:{photos:[{id:'fixture',src:'/fixture.svg'}],template:'fade',seconds:12}});
  if(u.pathname==='/fixture.svg')return route.fulfill({body:photo,contentType:'image/svg+xml'});
  if(u.pathname.startsWith('/apps/icons/'))return route.fulfill({body:photo,contentType:'image/svg+xml'});
  if(u.pathname.startsWith('/api/')||u.pathname.startsWith('/weather/'))return route.fulfill({status:404,body:'{}'});
  if(u.pathname.startsWith('/assets/')){const p=resolve('public/launcher',u.pathname.slice(8));assert.ok(p.startsWith(resolve('public/launcher')+'/'));return route.fulfill({body:await readFile(p),contentType:mime[extname(p)]||'application/octet-stream'});}
  if(u.protocol==='aperture:')return route.abort();
  throw Error('Unexpected fixture URL: '+u.pathname);
 });
 await page.addInitScript(()=>{localStorage.setItem('kaishi-ambient-minutes','0');localStorage.setItem('kaishi-ambient-mode','album');});
 await page.goto('https://appassets.androidplatform.net/assets/index.html');
 await page.waitForFunction(()=>window.apertureRemoteKey&&window.KaishiGallery&&window.KaishiAmbient);
 await page.evaluate(()=>apertureNativeFocus(true));
 const snapshot=n=>({apps:[],slots:[1,2,3,4].map(i=>({slot:'quick'+i,id:'bilibili',name:'哔哩哔哩'})),wall:Array.from({length:n},(_,i)=>({key:'wall'+i.toString(16).padStart(16,'0'),id:'bilibili',name:'应用 '+(i+1),icon:'/apps/icons/wall'+i.toString(16).padStart(16,'0')+'.png'}))});
 await page.evaluate(value=>apertureHomeApps(value),snapshot(50));
 assert.equal(await page.locator('#home-apps .dock-app').count(),4);
 await page.locator('#home-live').focus();await page.keyboard.press('ArrowDown');await page.keyboard.press('ArrowDown');
 await page.waitForFunction(()=>document.querySelector('#lobby').dataset.homePage==='1');
 assert.equal(await page.locator('.shortcuts-row button').count(),53);
 await page.locator('#wall-add').focus();
 for(let i=0;i<5;i++)await page.keyboard.press('ArrowDown');
 assert.equal(await page.evaluate(()=>document.activeElement.id),'wall-wall0000000000000027');
 assert.ok(await page.locator('.shortcuts-row').evaluate(n=>n.scrollTop)>0);
 assert.equal(await page.locator('#lobby').evaluate(n=>n.scrollTop),0);
 const icon=await page.locator('#wall-wall0000000000000027 .app-mark').boundingBox(),stage=await page.locator('#wall-wall0000000000000027 .shortcut-glyph').boundingBox();
 assert.ok(Math.abs(icon.x+icon.width/2-stage.x-stage.width/2)<1&&Math.abs(icon.y+icon.height/2-stage.y-stage.height/2)<1);
 await page.screenshot({path:images+'/app-wall.png'});
 await page.evaluate(value=>apertureHomeApps(value),snapshot(128));assert.equal(await page.locator('.shortcuts-row button').count(),131);
 assert.equal(await page.evaluate(()=>document.activeElement.id),'wall-wall0000000000000027');
 for(let i=0;i<5;i++)await page.keyboard.press('ArrowUp');
 assert.equal(await page.evaluate(()=>document.activeElement.id),'wall-add');
 for(let i=0;i<16;i++)await page.keyboard.press('ArrowDown');
 assert.equal(await page.evaluate(()=>document.activeElement.id),'wall-wall000000000000007f');
 await page.keyboard.press('ArrowRight');await page.keyboard.press('ArrowRight');assert.equal(await page.evaluate(()=>document.activeElement.id),'wall-manage');
 await page.keyboard.press('ArrowDown');assert.equal(await page.locator('#lobby').getAttribute('data-home-page'),'2');
 await page.keyboard.press('ArrowUp');assert.equal(await page.evaluate(()=>document.activeElement.id),'wall-manage');
 await page.locator('#wall-add').focus();await page.keyboard.press('ArrowUp');
 assert.equal(await page.locator('#lobby').getAttribute('data-home-page'),'0');
 checks.push('50/128 app snapshots render; four first-screen shortcuts remain independent; remote grid scrolls internally, centers icons and returns Up to first screen; refresh preserves focus');

 await page.clock.install();
 await page.evaluate(()=>document.querySelector('[data-home-screen="2"]').click());
 await page.clock.runFor(9900);assert.equal(await page.locator('#gallery-home-settings').evaluate(n=>n.classList.contains('hint-hidden')),false);
 await page.clock.runFor(200);assert.equal(await page.locator('#gallery-home-settings span').evaluate(n=>getComputedStyle(n).opacity),'0');
 assert.equal(await page.evaluate(()=>document.activeElement.id),'gallery-home-settings');
 await page.evaluate(()=>KaishiGallery.hintActivity('ArrowRight'));assert.equal(await page.locator('#gallery-home-settings').evaluate(n=>n.classList.contains('hint-hidden')),false);
 await page.clock.runFor(10001);assert.equal(await page.locator('#gallery-home-settings').evaluate(n=>n.classList.contains('hint-hidden')),true);
 await page.evaluate(()=>KaishiGallery.homePage(false));await page.clock.runFor(10001);
 checks.push('Album hint stays visible before ten seconds, hides afterwards, retains button focus and restarts on remote activity; leaving page clears hint');

 await page.evaluate(()=>{KaishiWeatherUI.model=()=>({place:{name:'杭州',scope:'manual'},code:0,night:false,temp:23,at:Date.now()});KaishiAmbient.show();});
 await page.waitForFunction(()=>document.querySelector('#ambient-view .gallery-frame.current img')?.complete);
 const album=await page.locator('#ambient-view').evaluate(root=>{const info=root.querySelector('.ambient-content').getBoundingClientRect(),weather=root.querySelector('.ambient-weather').getBoundingClientRect(),date=root.querySelector('.ambient-date').getBoundingClientRect();return {shade:getComputedStyle(root.querySelector('.ambient-shade')).display,canvas:getComputedStyle(root.querySelector('.ambient-canvas')).display,left:info.x,bottom:info.bottom,weatherTop:weather.y,dateBottom:date.bottom,text:root.querySelector('.ambient-weather').textContent};});
 await page.screenshot({path:images+'/album-ambient.png'});
 assert.equal(album.shade,'none');assert.equal(album.canvas,'none');assert.ok(album.left<100&&album.bottom>950&&album.weatherTop>=album.dateBottom,JSON.stringify(album));assert.match(album.text,/杭州.*23/);
 // Pixel readback of a synthetic solid-color photo checks the real browser compositor.
 const png=await page.screenshot({clip:{x:1400,y:400,width:1,height:1}});
 await page.route('**/pixel.png',route=>route.fulfill({body:png,contentType:'image/png'}));
 const pixel=await page.evaluate(async()=>{const image=new Image();image.src='/pixel.png';await image.decode();const canvas=document.createElement('canvas');canvas.width=canvas.height=1;const context=canvas.getContext('2d');context.drawImage(image,0,0);return Array.from(context.getImageData(0,0,1,1).data);});
 assert.deepEqual(pixel,[112,144,176,255]);
 await page.evaluate(()=>{KaishiAmbient.key('Escape');KaishiAmbient.releaseKey();localStorage.setItem('kaishi-ambient-mode','galaxy');KaishiAmbient.active(true);KaishiAmbient.show();});
 assert.notEqual(await page.locator('.ambient-shade').evaluate(n=>getComputedStyle(n).display),'none');
 checks.push('Album screen saver shows lower-left time/date/weather; overlay and sky canvas hidden; compositor RGB equals original synthetic photo; galaxy shade retained');

 const phone=await browser.newPage({viewport:{width:390,height:844}});phone.on('pageerror',e=>errors.push(e.message));let uploads=0;
 await phone.route('https://phone.example/**',async route=>{if(route.request().method()==='POST'){uploads++;assert.equal(route.request().postDataBuffer().length,20*1024*1024);return route.fulfill({json:{saved:true}});}return route.fulfill({contentType:'text/html',body:await readFile('public/launcher/gallery-upload.html')});});
 await phone.goto('https://phone.example/#abcdefghijklmnopqrstuvwxyzABCDEF');
 await phone.locator('#files').setInputFiles({name:'boundary.jpg',mimeType:'image/jpeg',buffer:Buffer.alloc(20*1024*1024,37)});await phone.locator('#send').click();await phone.waitForFunction(()=>document.querySelector('#status').textContent==='已保存 1 张到电视');assert.equal(uploads,1);
 await phone.locator('#files').setInputFiles({name:'over.jpg',mimeType:'image/jpeg',buffer:Buffer.alloc(20*1024*1024+1,37)});await phone.locator('#send').click();await phone.waitForFunction(()=>document.querySelector('#status').textContent.includes('照片超过20MB'));assert.equal(uploads,1);
 checks.push('Phone upload preflight accepts 20 MiB, reads and sends bytes; 20 MiB plus one rejected without POST (synthetic body, mocked HTTP response)');
 assert.deepEqual(errors,[]);
 sources.push('public/launcher/gallery-upload.html','public/launcher/gallery.js','public/launcher/gallery.css','public/launcher/ambient.js','public/launcher/ambient.css','public/launcher/home-personal.js','public/launcher/launcher.js','public/launcher/style.css',src+'CinemaActivity.java',src+'HomeAlbumView.java',src+'AppWallView.java',src+'DestinationApps.java',src+'GalleryStore.java','docs/contracts/kaishi-resource-pack-v1.schema.json','scripts/resource-pack.mjs');
 const hashes={};for(const path of sources)hashes[path]=createHash('sha256').update(await readFile(path)).digest('hex');
 await writeFile('docs/verification/s108-home-updates.json',JSON.stringify({passed:true,checks,errors,sources:hashes,syntheticAppsAndPhoto:true,loopbackTransport:true,nativeActivityExecuted:false,androidImageStoreExecuted:false,realXiaomi:false,apkBuilt:false,published:false},null,2)+'\n');
 console.log(JSON.stringify({checks,errors},null,2));
}finally{if(browser)await browser.close();await rm(temp,{recursive:true,force:true});}
