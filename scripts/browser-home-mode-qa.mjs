// Desktop Chromium with the packaged HTTPS origin simulated. No Android Activity runs here.
import assert from 'node:assert/strict';
import {mkdir, readFile, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {launchBrowser} from './browser.mjs';

const base = 'https://appassets.androidplatform.net';
const assets = new Map([
  ['index.html','text/html'], ['launcher.js','text/javascript'], ['style.css','text/css'],
  ['carezza-4k.jpg','image/jpeg'], ['fonts/clock-reader.woff2','font/woff2'],
  ['fonts/aperture-song.woff2','font/woff2']
]);
const browser = await launchBrowser(), checks = [], errors = [], screenshots = [];
try {
  await mkdir('docs/images', {recursive:true}); await mkdir('artifacts', {recursive:true});
  for (const edition of ['standard','parents']) {
    const page = await browser.newPage({viewport:{width:1280,height:720}, reducedMotion:'reduce'});
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', async route => {
      const url = new URL(route.request().url()), file = url.pathname.slice('/assets/'.length);
      if (url.origin === base && url.pathname.startsWith('/assets/') && assets.has(file)) {
        return route.fulfill({contentType:assets.get(file), body:await readFile(resolve('public/launcher',file))});
      }
      return route.fulfill({status:403, body:''});
    });
    const cdp = await page.context().newCDPSession(page), actions = [];
    await cdp.send('Page.enable');
    cdp.on('Page.frameRequestedNavigation', event => {
      if (event.url.startsWith('aperture://')) actions.push(event.url);
    });
    await page.goto(base+'/assets/index.html?edition='+edition);
    await page.waitForFunction(() => typeof apertureRemoteKey === 'function');
    assert.equal(await page.locator('.destinations>.destination:visible').count(), edition==='parents'?3:8);
    for (const [width,height] of [[1280,720],[1920,1080],[320,844],[390,844],[430,900],[760,900],[761,900]]) {
      await page.setViewportSize({width,height});
      await page.locator('#settings-open').click();
      if (await page.locator('#pane-backdrop').isHidden()) {
        await page.screenshot({path:'artifacts/s31-settings-did-not-open-'+edition+'-'+width+'.png'});
        throw new Error(edition+' '+width+' settings did not open after pointer click');
      }
      const trace=[];
      for (let step=0;step<14;step++) {
        trace.push(await page.evaluate(() => document.activeElement.dataset.action||document.activeElement.id));
        if (await page.evaluate(() => document.activeElement.dataset.action==='home-mode')) break;
        await page.evaluate(() => apertureRemoteKey('ArrowDown'));
      }
      const current=await page.evaluate(() => document.activeElement.dataset.action);
      if (current!=='home-mode') await page.screenshot({path:'artifacts/s31-home-mode-failed-'+edition+'-'+width+'.png'});
      assert.equal(current,'home-mode',edition+' '+width+' focus trace '+trace.join(' > '));
      const focus = await page.evaluate(() => {
        const node=document.activeElement, rect=node.getBoundingClientRect(), css=getComputedStyle(node);
        const box=node.parentElement.getBoundingClientRect();
        // scrollTop is integer CSS pixels; layout edges can be fractional at the 761px boundary.
        return {visible:rect.top>=box.top-1&&rect.bottom<=box.bottom+1&&rect.top>=-1&&rect.bottom<=innerHeight+1,
          outline:css.outlineStyle, size:parseFloat(css.outlineWidth), offset:parseFloat(css.outlineOffset), top:rect.top,bottom:rect.bottom,parentTop:box.top,parentBottom:box.bottom,height:innerHeight};
      });
      if (!focus.visible) await page.screenshot({path:'artifacts/s31-focus-clipped-'+edition+'-'+width+'.png'});
      assert.ok(focus.visible,edition+' '+width+' '+JSON.stringify(focus)); assert.equal(focus.outline,'solid'); assert.ok(focus.size>=3); assert.ok(focus.offset<0);
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth>innerWidth), false);
      if ([1280,1920,390].includes(width)) {
        const path='docs/images/s31-home-mode-'+edition+'-'+width+'.png';
        await page.screenshot({path}); screenshots.push(path);
      }
      await page.evaluate(() => apertureBack());
      assert.equal(await page.evaluate(() => document.activeElement.id),'settings-open');
    }
    // Desktop Chrome may show an external-protocol prompt that consumes later pointer events.
    // Exercise that navigation once, after layout/return checks, then close this page.
    await page.locator('#settings-open').click();
    for (let step=0;step<14;step++) {
      if (await page.evaluate(() => document.activeElement.dataset.action==='home-mode')) break;
      await page.evaluate(() => apertureRemoteKey('ArrowDown'));
    }
    assert.equal(await page.evaluate(() => document.activeElement.dataset.action),'home-mode');
    const navigation = new Promise((resolve,reject) => {
      const timeout=setTimeout(() => reject(new Error('No native action requested')),3000);
      cdp.on('Page.frameRequestedNavigation', event => {
        if (event.url==='aperture://action/home-mode') { clearTimeout(timeout); resolve(); }
      });
    });
    await page.evaluate(() => apertureRemoteKey('Enter')); await navigation;
    assert.deepEqual(actions,['aperture://action/home-mode']);
    checks.push(edition+': 720p/1080p and 320/390/430/760/761 widths; settings reachable with native key names, visible unclipped focus, one fixed native action and return focus');
    await page.close();
  }
  assert.deepEqual(errors,[]);
  const report={checks,screenshots,errors,simulatedPackagedHttps:true,actualAndroidIntents:false,
    nativeSimpleHomeRendered:false,actualRendererKilled:false,legacyWebViewExecuted:false,realDevice:false};
  await writeFile('artifacts/home-mode-browser-report.json',JSON.stringify(report,null,2)+'\n');
  console.log(report);
} finally { await browser.close(); }
