// A/B test at Android's actual packaged HTTPS origin. Desktop Chromium has no
// Android WebViewClient: custom navigation is observed, not a native TV launch.
import assert from 'node:assert/strict';
import {mkdtemp, mkdir, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join, resolve, sep} from 'node:path';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {launchBrowser} from './browser.mjs';

const apk = resolve(process.env.BASELINE_APK || 'artifacts/s89-v33-final-standard/kaishi-v33-standard.apk');
const dir = await mkdtemp(join(tmpdir(), 'kaishi-v33-startup-'));
const extracted = spawnSync('python3', ['-c',
  'import pathlib,sys,zipfile\nroot=pathlib.Path(sys.argv[2])\nwith zipfile.ZipFile(sys.argv[1]) as z:\n for name in z.namelist():\n  if not name.startswith("assets/") or name.endswith("/"): continue\n  rel=pathlib.PurePosixPath(name[7:])\n  if ".." in rel.parts: raise ValueError("Unsafe ZIP path")\n  out=root.joinpath(*rel.parts); out.parent.mkdir(parents=True,exist_ok=True); out.write_bytes(z.read(name))',
  apk, dir], {encoding:'utf8'});
if (extracted.status !== 0) { await rm(dir,{recursive:true,force:true}); throw new Error(extracted.stderr); }
const browser = await launchBrowser(), results = [];
const origin = 'https://appassets.androidplatform.net';
const mime = name => name.endsWith('.html') ? 'text/html' : name.endsWith('.js') ? 'text/javascript' : name.endsWith('.css') ? 'text/css' : name.endsWith('.json') ? 'application/json' : 'application/octet-stream';
try {
  for (const version of ['v33-apk', 'v33-with-ambient-ready-guard', 'working-tree']) {
    const root = version.startsWith('v33-') ? dir : resolve('public/launcher');
    const page = await browser.newPage({viewport:{width:1920,height:1080},reducedMotion:'reduce'});
    const cdp = await page.context().newCDPSession(page); await cdp.send('Page.enable');
    const navigations = [], errors = [], requests = [];
    cdp.on('Page.frameRequestedNavigation', event => navigations.push(event.url));
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      requests.push(url.pathname);
      if (url.origin !== origin || !url.pathname.startsWith('/assets/')) return route.fulfill({status:503,contentType:'application/json',body:'{}'});
      const file = resolve(root, url.pathname.slice('/assets/'.length));
      if (!file.startsWith(root + sep)) return route.fulfill({status:403,body:''});
      const served = version === 'v33-with-ambient-ready-guard' && url.pathname === '/assets/ambient.js' ? resolve('public/launcher/ambient.js') : file;
      try { await route.fulfill({body:await readFile(served),contentType:mime(file)}); }
      catch { await route.fulfill({status:404,body:''}); }
    });
    await page.goto(origin+'/assets/index.html', {waitUntil:'commit'});
    // A bounded wait preserves the failed state rather than a misleading test timeout.
    let startupReady = true;
    try { await page.waitForFunction(() => document.readyState === 'complete' && typeof window.apertureRemoteKey === 'function', null, {timeout:2500}); }
    catch { startupReady = false; }
    const beforeHandshake = await page.evaluate(() => ({
      state:document.readyState,clock:document.getElementById('clock').textContent,
      remote:typeof window.apertureRemoteKey,viewport:typeof window.apertureSetViewport,
      cards:['live','series','movies','variety'].map(name => ({id:'home-'+name,binding:typeof document.getElementById('home-'+name).onclick})),
      scale:document.documentElement.style.getPropertyValue('--tv-scale'),active:document.activeElement.id
    }));
    const startupNavigations = [...navigations];
    const row = {version,startupReady,beforeHandshake,startupNavigations,errors,requests};
    if (version === 'v33-apk') {
      assert.ok(startupNavigations.includes('aperture://action/ambient-awake-off'), 'baseline must exhibit the premature native action');
      assert.equal(startupReady, false, 'baseline reproduces the incomplete packaged startup in this Chromium harness');
      assert.equal(beforeHandshake.clock, '--:--');
      assert.equal(beforeHandshake.viewport, 'undefined');
      assert.equal(beforeHandshake.remote, 'undefined');
      assert.ok(beforeHandshake.cards.every(card => card.binding !== 'function'));
    } else {
      assert.equal(startupReady, true);
      assert.deepEqual(startupNavigations, [], 'no custom scheme may navigate before the home scripts finish');
      assert.match(beforeHandshake.clock, /^\d{2}:\d{2}$/);
      assert.equal(beforeHandshake.viewport, 'function');
      assert.ok(beforeHandshake.cards.every(card => card.binding === 'function'));
      await page.evaluate(() => window.apertureNativeFocus(true));
      await page.waitForTimeout(50);
      assert.deepEqual(navigations, ['aperture://action/ambient-awake-on']);
      await page.evaluate(() => window.apertureNativeFocus(true));
      await page.waitForTimeout(50);
      assert.equal(navigations.length, 1, 'unchanged native focus must not navigate again');
      for (const name of ['live','series','movies','variety']) {
        await page.evaluate(name => { document.getElementById('home-'+name).focus(); window.apertureRemoteKey('Enter'); }, name);
        await page.waitForTimeout(50);
        assert.equal(navigations.at(-1), 'aperture://action/family-'+name);
      }
      row.afterHandshakeNavigations = [...navigations];
    }
    results.push(row); await page.close();
  }
  await mkdir('docs/verification', {recursive:true});
  await writeFile('docs/verification/s90-packaged-startup-browser.json', JSON.stringify({baselineApkSha256:createHash('sha256').update(await readFile(apk)).digest('hex'),origin,results,allNetworkRequestsStubbed:true,baselineScriptsUnmodified:true,actualAndroidWebView:false,realXiaomi:false,limitations:'Desktop Chromium reproduces interrupted startup at the packaged HTTPS origin; Android WebViewClient interception and Xiaomi device confirmation still require device verification.'},null,2)+'\n');
  console.log('Packaged HTTPS origin: unmodified v33 APK fails startup; changing only ambient.js and the full working tree both finish startup and all four home actions respond. Desktop Chromium only.');
} finally { await browser.close(); await rm(dir,{recursive:true,force:true}); }
