// CSS viewport checks, including an inflated innerWidth and an incorrect density hint.
// This deliberately distinguishes CSS/DPR simulation from OEM WebView validation.
import assert from 'node:assert/strict';
import {writeFile, mkdir} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {resolve} from 'node:path';
import {launchBrowser} from './browser.mjs';

const browser=await launchBrowser(), results=[];
try {
  for (const [width,height,dpr,inflated] of [[1920,1080,1,false],[960,540,2,false],[960,540,2,true],[480,270,4,true],[1920,1080,2,false]]) {
    const page=await browser.newPage({viewport:{width,height},deviceScaleFactor:dpr,reducedMotion:'reduce'});
    if(inflated)await page.addInitScript(()=>{
      Object.defineProperty(window,'innerWidth',{get:()=>1920});
      Object.defineProperty(window,'innerHeight',{get:()=>1080});
    });
    await page.goto(pathToFileURL(resolve('public/launcher/index.html')).href);
    // The old Android density conversion could give an oversized hint. DOM layout
    // and the visual viewport must constrain it without depending on that hint.
    await page.evaluate(()=>window.apertureSetViewport(1920,1080));
    const report=await page.evaluate(()=>window.apertureViewportReport());
    assert.equal(report.fitted.width,width);
    assert.equal(report.fitted.height,height);
    for(const rect of report.entries){
      assert.ok(rect.width>0&&rect.height>0);
      assert.ok(rect.x>=0&&rect.y>=0&&rect.x+rect.width<=width+.1&&rect.y+rect.height<=height+.1,JSON.stringify(rect));
    }
    const live=report.entries.find(x=>x.id==='home-live');
    const variety=report.entries.find(x=>x.id==='home-variety');
    assert.ok(Math.abs(live.width/width-380/1920)<.001);
    assert.ok(variety.x+variety.width<=width*.96);
    await page.locator('#settings-open').click();
    const settings=await page.locator('.entry-pane').boundingBox();
    assert.ok(settings.width>0&&settings.x+settings.width<=width&&settings.y+settings.height<=height);
    await page.keyboard.press('Escape');
    await page.locator('#home-live').focus();
    await page.keyboard.press('ArrowRight');await page.keyboard.press('ArrowRight');await page.keyboard.press('ArrowRight');
    assert.equal(await page.evaluate(()=>document.activeElement.id),'home-variety');
    results.push({width,height,dpr,inflated,report,settings});
    if(width===960&&!inflated){await mkdir('artifacts/s90-screens',{recursive:true});await page.screenshot({path:'artifacts/s90-screens/home-dpr2.png'});}
    await page.close();
  }
  await writeFile('docs/verification/s90-viewport-browser.json',JSON.stringify({actualXiaomi:false,actualAndroidWebView:false,results},null,2)+'\n');
  console.log('5 CSS viewport cases passed; four cards and settings fit, including inflated innerWidth');
} finally {await browser.close();}
