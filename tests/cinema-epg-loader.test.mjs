import test from 'node:test';
import assert from 'node:assert/strict';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {mkdtemp, rm, readFile} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {nasTestJson} from '../scripts/nas-test-json.mjs';
import {nasClasspath} from '../scripts/nas-classpath.mjs';

const run = promisify(execFile);
test('production EPG retries, real failed-cache expiry, callback ownership and cancellation', {timeout:120000}, async () => {
  const directory = await mkdtemp(join(tmpdir(), 'kaishi-epg-loader-'));
  const jdk = process.env.JAVA_HOME || join(process.cwd(), '.android-local/jdk17');
  try {
    const cp = (await nasClasspath()) + ':' + await nasTestJson();
    const production = 'android/app/src/main/java/org/zhixiaoce/aperture/';
    await run(join(jdk, 'bin/javac'), ['-cp', cp, '-d', directory,
      ...['CinemaCatalog', 'CinemaResourcePack', 'CinemaFormats', 'CinemaEpg', 'CinemaHttp', 'CinemaEpgLoader'].map(name => production + name + '.java'),
      'tests/fixtures/epg-callbacks/android/os/Handler.java', 'tests/fixtures/epg-callbacks/android/os/Looper.java',
      'tests/java/CinemaEpgLoaderTest.java']);
    const reportPath = join(directory, 'report.json');
    const {stdout} = await run(join(jdk, 'bin/java'), ['-cp', directory + ':' + cp, 'org.zhixiaoce.aperture.CinemaEpgLoaderTest', reportPath], {timeout:85000});
    const report = JSON.parse(await readFile(reportPath, 'utf8'));
    assert.equal(report.passed, true);
    assert.equal(report.checks.length, 16);
    assert.ok(report.failedCacheRecoveryWaitMillis >= 30000);
    console.log(stdout.trim());
    console.log(JSON.stringify(report));
  } finally { await rm(directory, {recursive:true, force:true}); }
});
