// Real page navigation with synthetic household/media. Desktop Chromium is not a television.
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {parse} from 'acorn';
import {readFile} from 'node:fs/promises';
import {createApp} from '../server/app.mjs';
import {launchBrowser} from './browser.mjs';
parse(await readFile('public/launcher/launcher.js','utf8'),{ecmaVersion:5});
const directory=await mkdtemp(join(tmpdir(),'aperture-tv-controls-')),videos=join(directory,'videos');
await mkdir(videos);for(let i=1;i<=8;i++)await writeFile(join(videos,'布局样例 '+i+'.mp4'),'LAYOUT FIXTURE - NOT PLAYABLE');
const origin='http://127.0.0.1:4371',ownerKey='tv-layout-synthetic-owner-1234567890';
const app=await createApp({directory,origin,ownerKey,mediaConfig:{sources:[{id:'family',name:'布局样例',type:'local',path:videos}],allowedOrigins:[]}});
await new Promise(r=>app.server.listen(4371,'127.0.0.1',r));
const browser=await launchBrowser(),checks=[],errors=[],measurements=[];
await mkdir('docs/images',{recursive:true});await mkdir('artifacts',{recursive:true});
async function press(page,key){await page.evaluate(key=>apertureRemoteKey(key),key);}
async function active(page){return page.evaluate(()=>document.activeElement.id||document.activeElement.dataset.action);}
async function geometry(page,label){
 const result=await page.evaluate(()=>{
  const rows=[...document.querySelectorAll('button:not(:disabled),a[href]')].filter(n=>n.offsetWidth&&n.offsetHeight).map(n=>{const r=n.getBoundingClientRect(),c=getComputedStyle(n);return {id:n.id||n.dataset.action||n.textContent.trim(),width:r.width,height:r.height,font:parseFloat(c.fontSize)};});
  return {overflow:document.documentElement.scrollWidth>innerWidth||document.documentElement.scrollHeight>innerHeight,width:innerWidth,height:innerHeight,scrollHeight:document.documentElement.scrollHeight,scrollWidth:document.documentElement.scrollWidth,canvasScale:document.querySelector('#lobby')&&innerWidth/innerHeight>4/3?Math.min(innerWidth/1920,innerHeight/1080):1,rows};
 });
 if(result.overflow) await page.screenshot({path:'artifacts/s31-overflow-'+label.replace(/[^a-z0-9]/g,'-')+'.png',fullPage:true});
 assert.equal(result.overflow,false,label+' viewport '+JSON.stringify({height:result.height,scrollHeight:result.scrollHeight,width:result.width,scrollWidth:result.scrollWidth}));
 for(const row of result.rows){assert.ok(row.height/result.canvasScale>=63,label+' small control '+JSON.stringify(row));assert.ok(row.width/result.canvasScale>=63,label+' narrow control '+JSON.stringify(row));}
 measurements.push({label,canvasScale:result.canvasScale,normalizedTo1920x1080Canvas:true,minimumControlHeight:Math.min(...result.rows.map(n=>n.height)),minimumControlWidth:Math.min(...result.rows.map(n=>n.width))});
}
try{
 const page=await browser.newPage({viewport:{width:1920,height:1080}});page.on('pageerror',e=>errors.push(e.message));
 for(const [width,height] of [[1280,720],[1920,1080],[3840,2160]]){
  await page.setViewportSize({width,height});await page.goto(origin+'/home?demo=1');await page.waitForFunction(()=>typeof apertureRemoteKey==='function');await page.evaluate(()=>document.fonts.ready);
  await geometry(page,'standard '+width);
  const backdrop=await page.evaluate(()=>new Promise((resolve,reject)=>{
   var image=new Image();image.onload=function(){resolve({width:image.naturalWidth,height:image.naturalHeight,background:getComputedStyle(document.body).backgroundImage,size:getComputedStyle(document.body).backgroundSize});};image.onerror=reject;image.src=new URL('./carezza-4k.jpg',location.href).href;
  }));
  assert.equal(backdrop.width,3840);assert.equal(backdrop.height,2160);assert.ok(backdrop.background.includes('carezza-4k.jpg'));assert.ok(backdrop.size.split(',').every(value=>value.trim()==='cover'));
  await page.screenshot({path:'docs/images/s31-standard-'+width+'.png'});
  const ids=await page.locator('.destinations>.destination:visible').evaluateAll(nodes=>nodes.map(n=>n.id));
  assert.deepEqual(ids,['entry-live','entry-cinema','entry-photos','entry-family','entry-data','entry-information','entry-tools','entry-system']);
  for(let i=0;i<ids.length;i++){
   await page.locator('#'+ids[i]).focus();await press(page,'ArrowRight');assert.equal(await active(page),ids[i%4<3?i+1:i]);
   await page.locator('#'+ids[i]).focus();await press(page,'ArrowLeft');assert.equal(await active(page),ids[i%4>0?i-1:i]);
   await page.locator('#'+ids[i]).focus();await press(page,i<4?'ArrowDown':'ArrowUp');assert.equal(await active(page),ids[i<4?i+4:i-4]);
  }
  // Reach settings without pointer, Menu or DOM focus changes from the normal initial entry.
  await page.locator('#entry-live').focus();await press(page,'ArrowUp');assert.equal(await active(page),'hero-open');
  await press(page,'ArrowUp');for(let i=0;i<5;i++)await press(page,'ArrowRight');assert.equal(await active(page),'settings-open');
  await press(page,'Enter');assert.equal(await active(page),'configure');
  await press(page,'ArrowUp');assert.equal(await active(page),'pane-close');await press(page,'ArrowDown');assert.equal(await active(page),'configure');
  const actions=await page.locator('#pane-actions>button').evaluateAll(nodes=>nodes.map(n=>n.dataset.action));
  for(const action of actions.slice(1)){await press(page,'ArrowDown');assert.equal(await active(page),action);}
  await press(page,'ArrowDown');assert.equal(await active(page),actions.at(-1));
  await press(page,'Escape');assert.equal(await active(page),'settings-open');
  for(const section of ['photos','data','information','tools']){
   await page.locator('#entry-'+section).focus();await press(page,'Enter');assert.equal(await page.locator('#lobby').getAttribute('data-section'),section);
   assert.equal(await page.locator('.destinations').isVisible(),false);await geometry(page,section+' '+width);
   await press(page,'Escape');assert.equal(await active(page),'entry-'+section);
  }
 }
 checks.push('standard 720p/1080p/4K: eight entries in two rows, same-column vertical moves, row edges stay put, settings reachable without Menu/pointer, linear menu and back focus');
 for(const [width,height] of [[1280,720],[1920,1080],[3840,2160]]){
  await page.setViewportSize({width,height});await page.goto(origin+'/home?edition=parents&demo=1&quick1='+encodeURIComponent('视频 · 示例')+'&quick2='+encodeURIComponent('音乐 · 示例'));
  await page.waitForFunction(()=>typeof apertureRemoteKey==='function');await geometry(page,'parents '+width);
  await page.screenshot({path:'docs/images/s31-parents-'+width+'.png'});
  assert.equal(await active(page),'entry-live');await press(page,'ArrowRight');assert.equal(await active(page),'entry-series');await press(page,'ArrowRight');assert.equal(await active(page),'entry-cinema');await press(page,'ArrowRight');assert.equal(await active(page),'entry-cinema');
  await page.locator('#entry-live').focus();await press(page,'ArrowDown');assert.equal(await active(page),'quick1');await press(page,'ArrowUp');assert.equal(await active(page),'entry-live');
  await press(page,'ArrowUp');assert.equal(await active(page),'settings-open');await press(page,'Enter');assert.equal(await active(page),'configure');await press(page,'Escape');assert.equal(await active(page),'settings-open');
 }
 checks.push('parents 720p/1080p/4K: three core entries, five smaller application controls, explicit core/shortcut/header paths and retained settings return focus');
 const login=await page.request.post(origin+'/api/login',{data:{key:ownerKey}});assert.equal(login.status(),200);
 assert.equal((await page.request.post(origin+'/api/cinema/settings',{data:{enabled:true,sourceIds:['family'],execution:{key:'layout-enable',revision:0}}})).status(),200);
 for(const edition of ['standard','parents']){
  await page.setViewportSize({width:1920,height:1080});await page.goto(origin+'/cinema?from=home&view=library'+(edition==='parents'?'&edition=parents':''));
  await page.locator('.cinema-item').first().waitFor();assert.equal(await page.locator('.cinema-item').count(),8);
  const cols=edition==='parents'?3:4;
  await page.locator('.cinema-item').first().focus();await press(page,'ArrowDown');assert.equal(await page.locator('.cinema-item').nth(cols).evaluate(n=>n===document.activeElement),true);
  await press(page,'ArrowUp');assert.equal(await page.locator('.cinema-item').first().evaluate(n=>n===document.activeElement),true);
  await page.locator('.cinema-item').nth(cols-1).focus();await press(page,'ArrowRight');assert.equal(await page.locator('.cinema-item').nth(cols-1).evaluate(n=>n===document.activeElement),true);
 }
 checks.push('real configured library with eight non-playable layout fixtures: standard four-column / parents three-column remote grid, same-column moves and no row-edge diagonal jumps; no decoding claim');
 assert.deepEqual(errors,[]);
 await writeFile('docs/verification/tv-controls.json',JSON.stringify({checks,measurements,errors,es5Parsed:true,browser:'desktop Chromium',realTelevision:false,physicalRemote:false,nativeControlsRendered:false,mediaDecoded:false,realNas:false},null,2)+'\n');
 console.log({checks,measurements,errors});
}finally{await browser.close();await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});}
