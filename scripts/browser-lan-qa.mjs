import assert from 'node:assert/strict';
import {mkdtemp, mkdir, readFile, writeFile, rm} from 'node:fs/promises';
import {tmpdir, networkInterfaces} from 'node:os';
import {join} from 'node:path';
import {createServer, request as httpRequest} from 'node:http';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {lanAddresses} from './lan-options.mjs';
import {launchBrowser} from './browser.mjs';

const directory = await mkdtemp(join(tmpdir(), 'aperture-lan-qa-'));
const address = process.env.APERTURE_QA_LAN_ADDRESS || lanAddresses(networkInterfaces()).find(item => !/tun|vpn|docker|veth/i.test(item.name))?.address;
if (!address) throw new Error('No testable private IPv4 interface; set APERTURE_QA_LAN_ADDRESS to an actual local address');
const reservation = createServer();
await new Promise((resolve, reject) => {reservation.once('error', reject); reservation.listen(0, address, resolve);});
const port = reservation.address().port;
await new Promise(resolve => reservation.close(resolve));
const origin = `http://${address}:${port}`, checks = [], errors = [];
let child, browser, log = '';
try {
  child = spawn(process.execPath, ['scripts/start-lan.mjs', '--address', address, '--port', String(port)], {
    env: {...process.env, HOST: '', PUBLIC_ORIGIN: '', APERTURE_DATA: join(directory, 'family'), APERTURE_OWNER_KEY: '', APERTURE_CONNECTIONS: '', APERTURE_MEDIA_CONFIG: '', APERTURE_LOCAL_AUTOMATION: '0', APERTURE_TRUSTED_PROXIES: ''},
    stdio: ['ignore', 'pipe', 'pipe']
  });
  child.stdout.on('data', data => log += data); child.stderr.on('data', data => log += data);
  let ready = false;
  for (let i = 0; i < 200; i++) {
    if (child.exitCode !== null) throw new Error('Launcher stopped: ' + log);
    if (log.includes('电视 APK 输入：' + origin)) {ready = true; break;}
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  assert.equal(ready, true, log);
  const key = (await readFile(join(directory, 'family', 'owner-key'), 'utf8')).trim();
  assert.ok(!log.includes(key));
  checks.push('actual launcher starts on one selected local interface; ready banner follows server listen; logs contain no owner key');
  const call = async (path, body, cookie) => {
    const response = await fetch(origin + path, {method: body ? 'POST' : 'GET', headers: {'Content-Type': 'application/json', ...(cookie ? {cookie} : {})}, body: body ? JSON.stringify(body) : undefined});
    return {status: response.status, data: await response.json(), cookie: response.headers.get('set-cookie')?.split(';')[0]};
  };
  assert.equal((await call('/api/tv-lite/state')).status, 401);
  assert.equal((await call('/api/local-pair/start', {})).status, 503);
  const wrongHost = await new Promise((resolve, reject) => {
    const req = httpRequest(origin + '/api/health', {headers: {Host: 'wrong.example'}}, response => {response.resume(); resolve(response.statusCode);});
    req.on('error', reject); req.end();
  });
  assert.equal(wrongHost, 421);
  checks.push('LAN mode retains private-state auth, exact Host check and disabled-by-default automation');
  const owner = (await call('/api/login', {key})).cookie;
  browser = await launchBrowser();
  const tv = await browser.newPage({viewport: {width: 1280, height: 720}});
  tv.on('pageerror', error => errors.push(error.message));
  await tv.goto(origin + '/tv-lite');
  await tv.locator('#connect').click();
  await tv.waitForFunction(() => /^\d{3} \d{3}$/.test(document.querySelector('#pair-code').textContent));
  const code = (await tv.locator('#pair-code').textContent()).replace(/ /g, '');
  assert.equal((await call('/api/pair/approve', {code}, owner)).status, 200);
  await tv.locator('#pair-dialog').waitFor({state: 'hidden'});
  const state = await call('/api/board', undefined, owner);
  const written = await call('/api/write', {operation: 'entry.add', input: {type: 'note', title: '局域网试用：晚饭后散步', body: '合成验收资料', author: '内测', pinned: true}, execution: {key: 'lan-smoke-note', revision: state.data.revision}}, owner);
  assert.equal(written.status, 200, JSON.stringify(written.data));
  await tv.waitForFunction(() => document.querySelector('#note-title').textContent === '局域网试用：晚饭后散步', {}, {timeout: 9000});
  await mkdir('docs/images', {recursive: true});
  await tv.screenshot({path: 'docs/images/lan-lite-1280.png'});
  checks.push('a browser at the actual selected interface completes viewer pairing and receives a synthetic note');
  const guide = await browser.newPage({viewport: {width: 390, height: 844}});
  guide.on('pageerror', error => errors.push(error.message));
  await guide.goto(origin + '/guide#start');
  await guide.locator('#start summary').first().click();
  assert.match(await guide.locator('#start').textContent(), /npm run start:lan/);
  assert.equal(await guide.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
  const boundaries = await guide.locator('#start .guide-steps article').first().evaluate(article => {
    const outer = article.getBoundingClientRect();
    return [...article.querySelectorAll('div,p,pre,h3')].every(node => {
      const rect = node.getBoundingClientRect(); return rect.left >= outer.left - 1 && rect.right <= outer.right + 1;
    });
  });
  assert.equal(boundaries, true, 'expanded instructions must fit inside their step, not merely avoid page scroll');
  await guide.locator('#start').screenshot({path: 'docs/images/lan-guide-phone.png'});
  checks.push('phone guide exposes LAN startup instructions without horizontal overflow; synthetic screenshots retained');
  assert.deepEqual(errors, []);
  const stopped = once(child, 'exit'); child.kill('SIGTERM'); await stopped;
  child = undefined;
  await assert.rejects(fetch(origin + '/api/health'));
  checks.push('stopping launcher stops child server and releases listener');
  await mkdir('artifacts', {recursive: true});
  await writeFile('artifacts/lan-start-report.json', JSON.stringify({checks, errors, network: 'same Linux host via its private NIC; not a household network', hardwareTested: false, workbuddyApplicationTested: false}, null, 2));
  console.log({checks, errors});
} finally {
  await browser?.close();
  if (child && child.exitCode === null) {const stopped = once(child, 'exit'); child.kill('SIGTERM'); await stopped;}
  await rm(directory, {recursive: true, force: true});
}
