// Desktop Chromium exercises the unchanged packaged page's local action branch.
// It observes requested custom-scheme navigation; no Android Activity is launched.
import assert from 'node:assert/strict';
import {mkdir, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {launchBrowser} from './browser.mjs';

const browser = await launchBrowser(), checks = [], errors = [];
const entries = ['live', 'series', 'movies', 'variety'];
try {
  for (const viewport of [{width:1920,height:1080},{width:960,height:540}]) {
    for (const input of ['keyboard', 'native-js-bridge']) {
      for (let target = 0; target < entries.length; target++) {
        const page = await browser.newPage({viewport, reducedMotion:'reduce'});
        const cdp = await page.context().newCDPSession(page);
        await cdp.send('Page.enable');
        const navigations = [], diagnostic = [];
        cdp.on('Page.frameRequestedNavigation', event => navigations.push(event.url));
        page.on('pageerror', error => errors.push(error.message));
        page.on('console', message => {if (message.text().startsWith('[KaishiHome] ')) diagnostic.push(JSON.parse(message.text().slice(13)));});
        await page.goto(pathToFileURL(resolve('public/launcher/index.html')).href);
        await page.waitForFunction(() => typeof window.apertureRemoteKey === 'function');
        const press = async key => input === 'keyboard'
          ? page.keyboard.press(key)
          : page.evaluate(key => window.apertureRemoteKey(key), key);
        for (let i = 0; i < target; i++) await press('ArrowRight');
        const id = 'home-' + entries[target];
        assert.equal(await page.evaluate(() => document.activeElement.id), id);
        const box = await page.locator('#' + id).boundingBox();
        assert.ok(box.width > 0 && box.height > 0);
        await press('Enter');
        await page.waitForTimeout(50);
        assert.deepEqual(navigations, ['aperture://action/family-' + entries[target]]);
        assert.equal(diagnostic.length, 1, 'one confirmation must emit one bounded diagnostic');
        assert.equal(diagnostic[0].active, id);
        assert.equal(diagnostic[0].inItems, true);
        checks.push({viewport,input,id,box,action:navigations[0],diagnostic:diagnostic[0]});
        await page.close();
      }
    }
  }
  assert.deepEqual(errors, []);
  await mkdir('docs/verification', {recursive:true});
  await writeFile('docs/verification/s90-home-watch-confirm-browser.json', JSON.stringify({checks,errors,packagedLocalBranch:true,actualAndroidWebView:false,nativeActivityLaunched:false,realXiaomi:false,conclusion:'The four unchanged local action routes respond in Chromium; the reported Xiaomi-only variety confirmation failure remains unconfirmed here.'},null,2)+'\n');
  console.log(`${checks.length} home entries / key paths passed; no Xiaomi WebView acceptance claimed`);
} finally { await browser.close(); }
