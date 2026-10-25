// Real launcher DOM/assets in Chromium; installed-state and PackageManager icon responses are fixtures.
import assert from 'node:assert/strict';
import {readFile,readdir,writeFile,mkdir} from 'node:fs/promises';
import {launchBrowser} from './browser.mjs';
const stage=process.env.KAISHI_QA_STAGE||'s53';
const root='public/launcher/',base='https://appassets.androidplatform.net';
const names=(await readdir(root,{recursive:true})).filter(n=>/\.(html|css|js|json|svg|jpg|webp|png|woff2)$/.test(n));
const files=new Map(await Promise.all(names.map(async n=>[n,await readFile(root+n)])));
const mime=n=>({'js':'text/javascript','json':'application/json','html':'text/html','css':'text/css','svg':'image/svg+xml','png':'image/png','jpg':'image/jpeg','webp':'image/webp','woff2':'font/woff2'})[n.split('.').at(-1)];
const presets=JSON.parse(files.get('app-presets.json')),checks=[],errors=[],requests=[],images=[];
const browser=await launchBrowser();
let nativeFails=false,presetFails=false;
const sample={apps:presets.map(p=>({id:p.id,installed:p.id==='bilibili',icon:p.id==='bilibili'?'/apps/icons/bilibili.png':''})),slots:[['bilibili','哔哩哔哩'],['tencent','腾讯视频'],['netflix','Netflix'],['appletv','Apple TV']].map(([id,name],i)=>({id,name,slot:'quick'+(i+1),icon:id==='bilibili'?'/apps/icons/quick1.png':''}))};
const loaded=async(page,selector)=>page.waitForFunction(sel=>[...document.querySelectorAll(sel)].length>0&&[...document.querySelectorAll(sel)].every(n=>n.complete&&n.naturalWidth>0),selector);
async function capture(page,name){const path='docs/images/'+stage+'-logos-'+name+'.png';await page.screenshot({path,fullPage:true});images.push(path);}
try{
 await mkdir('docs/images',{recursive:true});await mkdir('docs/verification',{recursive:true});
 for(const edition of ['standard','parents']){
  const page=await browser.newPage({viewport:{width:1920,height:1080},reducedMotion:'reduce',timezoneId:'Asia/Shanghai'});
  page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',async route=>{
   const u=new URL(route.request().url()),name=u.pathname.slice(8);requests.push(u.href);
   if(u.origin===base&&u.pathname.startsWith('/apps/icons/'))return nativeFails?route.fulfill({status:404,body:''}):route.fulfill({body:files.get('app-icons/bilibili.jpg'),contentType:'image/jpeg'});
   if(u.origin===base&&u.pathname.startsWith('/assets/')&&files.has(name))return presetFails&&name==='app-icons/bilibili.jpg'?route.fulfill({status:404,body:''}):route.fulfill({body:files.get(name),contentType:mime(name)});
   if(u.origin===base&&u.pathname==='/api/board')return route.fulfill({json:{entries:[],photos:[]}});
   return route.fulfill({status:403,body:''});
  });
  await page.goto(base+'/assets/index.html?edition='+edition);await page.evaluate(()=>document.fonts.ready);await page.waitForFunction(()=>window.apertureHomeApps&&document.querySelector('#home-apps'));
  await page.waitForTimeout(100);await page.evaluate(s=>apertureHomeApps(s),sample);await loaded(page,'.app-real-icon');
  assert.equal(await page.locator('.app-wordmark,.app-compact').count(),0);
  assert.equal(await page.locator('#dock-quick1 .app-mark').getAttribute('data-icon-source'),'installed');
  assert.equal(await page.locator('#quick1 .app-mark').getAttribute('data-icon-source'),'installed');
  if(edition==='standard'){await page.locator('#dock-toggle').click();await capture(page,'standard-expanded');}
  else await capture(page,'parents');
  await page.locator('#settings-open').click();await page.locator('[data-action=presets]').click();await loaded(page,'.preset-grid img');
  const brands=await page.locator('.preset-grid .app-mark').evaluateAll(nodes=>nodes.map(n=>({id:n.dataset.brand,source:n.dataset.iconSource,src:n.querySelector('img').getAttribute('src'),width:n.querySelector('img').naturalWidth,height:n.querySelector('img').naturalHeight,text:n.textContent})));
  assert.equal(brands.length,12);assert.ok(brands.every(b=>b.width>=144&&b.height>=144&&b.text===''));assert.equal(brands[0].source,'installed');assert.ok(brands.slice(1).every(b=>b.source==='preset'));
  await capture(page,edition+'-presets-top');await page.locator('[data-action=preset-kodi]').focus();await capture(page,edition+'-presets-bottom');
  // Native failure must reveal the packaged official logo. A second failure ends at a neutral graphic.
  nativeFails=true;await page.keyboard.press('Escape');await page.evaluate(s=>apertureHomeApps(s),sample);await loaded(page,'.app-real-icon');
  assert.equal(await page.locator('#dock-quick1 .app-mark').getAttribute('data-icon-source'),'preset');
  presetFails=true;await page.evaluate(s=>apertureHomeApps(s),sample);await loaded(page,'.app-real-icon');
  assert.equal(await page.locator('#quick1 .app-mark').getAttribute('data-icon-source'),'generic');assert.equal(await page.locator('#quick1 .app-real-icon').getAttribute('src'),'./app-icons/application.svg');
  const unknown={apps:[],slots:[{id:'unknown',name:'我的应用',slot:'quick1',icon:'https://tracking.invalid/icon.png'}]};
  await page.evaluate(s=>apertureHomeApps(s),unknown);await loaded(page,'.app-real-icon');assert.equal(await page.locator('#quick1 .app-mark').getAttribute('data-icon-source'),'generic');assert.equal(await page.locator('#quick1 strong').textContent(),'我的应用');
  checks.push({edition,brands,installedFirst:true,installedFailureUsesPreset:true,doubleFailureUsesGeneric:true,unknownUsesGraphic:true,noTextLogo:true});nativeFails=false;presetFails=false;await page.close();
 }
 assert.deepEqual(errors,[]);assert.ok(requests.every(u=>new URL(u).origin===base),'no external logo/image requests');
 await writeFile('docs/verification/'+stage+'-app-logos.json',JSON.stringify({checks,images,errors,externalRequests:0,method:'Chromium real launcher with packaged official assets; synthetic installed-state and native icon bytes',television:false,apkBuilt:false},null,2)+'\n');console.log({checks:checks.length,images,errors});
}finally{await browser.close();}
