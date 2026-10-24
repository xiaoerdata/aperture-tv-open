// Real-TV regression: overlapping hero controls and cut-off footer. Browser fixtures only.
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,rm,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createApp} from '../server/app.mjs';
import {launchBrowser} from './browser.mjs';
const directory=await mkdtemp(join(tmpdir(),'kaishi-viewport-'));
const app=await createApp({directory,origin:'http://127.0.0.1:4396',ownerKey:'synthetic-viewport-123456789012345'});
await new Promise(r=>app.server.listen(4396,'127.0.0.1',r));
const browser=await launchBrowser(),errors=[],measurements=[],checks=[];
const sizes=[[960,540],[1024,576],[1066,600],[1280,720],[1920,1080],[3840,2160],[960,500],[1280,640]];
try{
  await mkdir('docs/images',{recursive:true});
  for(const edition of ['standard','parents']){
    const page=await browser.newPage();page.on('pageerror',e=>errors.push(e.message));
    for(const [width,height] of sizes){
      await page.setViewportSize({width,height});
      await page.goto('http://127.0.0.1:4396/home?edition='+edition+'&system='+encodeURIComponent('桌面')+'&quick1='+encodeURIComponent('常用视频应用的较长名称'));
      await page.evaluate(()=>document.fonts.ready);
      const measure=await page.evaluate(()=>{
        const box=n=>{const r=n.getBoundingClientRect();return {left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height};};
        const hero=box(document.querySelector('#hero-open')),controls=box(document.querySelector('.carousel-controls'));
        const shown=[...document.querySelectorAll('.lobby-header a,.lobby-header button,.destinations button,.shortcuts-row button,.lobby-footer')].filter(n=>n.offsetWidth&&n.offsetHeight);
        return {heroControlsOverlap:hero.right>controls.left&&hero.left<controls.right&&hero.bottom>controls.top&&hero.top<controls.bottom,
          controls:shown.map(n=>({id:n.id||n.className,...box(n)})),scrollWidth:document.documentElement.scrollWidth,scrollHeight:document.documentElement.scrollHeight,
          duplicateDesktop:document.querySelector('#system-label').textContent,wordmark:document.querySelector('.wordmark').textContent};
      });
      assert.ok(!measure.heroControlsOverlap,edition+' hero controls overlap '+width);
      assert.ok(measure.scrollWidth<=width&&measure.scrollHeight<=height,edition+' canvas overflow '+width);
      assert.match(measure.wordmark,/开视/);assert.equal(measure.duplicateDesktop,'');
      const scale=Math.min(width/1920,height/1080),left=(width-1920*scale)/2+90*scale,top=(height-1080*scale)/2+48*scale;
      for(const rect of measure.controls){assert.ok(rect.left>=left-1&&rect.right<=width-left+1,edition+' horizontal safe area '+rect.id+' '+width);assert.ok(rect.top>=top-1&&rect.bottom<=height-top+1,edition+' vertical safe area '+rect.id+' '+width);}
      measurements.push({edition,width,height,...measure});
      if([960,1920].includes(width)&&height===width*9/16)await page.screenshot({path:'docs/images/s45-'+edition+'-'+width+'.png'});
      await page.locator('#entry-live').focus();
      for(const key of ['ArrowRight','ArrowDown','ArrowUp','ArrowLeft'])await page.evaluate(key=>apertureRemoteKey(key),key);
      assert.equal(await page.evaluate(()=>scrollX===0&&scrollY===0),true,'remote moved the canvas');
      await page.locator('#settings-open').click();
      for(let j=0;j<12;j++)await page.evaluate(()=>apertureRemoteKey('ArrowDown'));
      assert.equal(await page.evaluate(()=>{const r=document.activeElement.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight;}),true,'menu focus clipped');
      await page.evaluate(()=>apertureRemoteKey('Escape'));
      assert.equal(await page.evaluate(()=>document.activeElement.id),'settings-open');
      assert.equal(await page.evaluate(()=>scrollX===0&&scrollY===0),true,'dialog moved the canvas');
    }
    if(edition==='standard'){
      await page.setViewportSize({width:960,height:540});
      for(const section of ['photos','data','information','tools']){
        await page.locator('#entry-'+section).click();
        assert.equal(await page.evaluate(()=>{const r=document.querySelector('#collection-body').getBoundingClientRect();return r.left>=0&&r.right<=innerWidth&&r.bottom<=innerHeight;}),true,section+' collection clipped');
        await page.screenshot({path:'docs/images/s45-'+section+'-960.png'});
        await page.evaluate(()=>apertureRemoteKey('Escape'));
        assert.equal(await page.evaluate(()=>document.activeElement.id),'entry-'+section);
      }
      await page.locator('#slide-next').click();await page.locator('#slide-next').click();
      await page.evaluate(()=>document.querySelector('#hero-description').textContent='这是一条较长的家庭留言，用于检查两行正文与查看按钮、轮播控件之间的间距。');
      assert.equal(await page.evaluate(()=>document.querySelector('#hero-open').getBoundingClientRect().bottom<=document.querySelector('.carousel-controls').getBoundingClientRect().top),true);
    }
    await page.close();
  }
  const offline=await browser.newPage({viewport:{width:960,height:540}});offline.on('pageerror',e=>errors.push(e.message));
  await offline.goto(pathToFileURL(join(process.cwd(),'public/launcher/index.html')).href);
  assert.match(await offline.title(),/开视/);
  assert.equal(await offline.evaluate(()=>document.querySelector('#hero-open').getBoundingClientRect().bottom<=document.querySelector('.carousel-controls').getBoundingClientRect().top),true);
  await offline.screenshot({path:'docs/images/s45-offline-960.png'});
  checks.push('16 edition/viewport combinations: safe area, no overlap/scroll overflow, display name and duplicate-label regression','remote navigation, long settings list, return focus and stationary canvas','four collections at 960x540, two-line message and packaged-file offline layout');
  assert.deepEqual(errors,[]);
  await writeFile('docs/verification/s45-tv-viewport.json',JSON.stringify({checks,measurements,errors,method:'desktop Chromium; isolated empty household and synthetic labels',realXiaomiRetest:false,physicalRemote:false,reference:'User supplied an installed-TV photo; original has personal metadata and is not copied into public materials.'},null,2)+'\n');
  console.log({checks,errors});
}finally{await browser.close();await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});}
