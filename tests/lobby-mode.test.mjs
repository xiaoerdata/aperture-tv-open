import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, mkdir, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';

test('home routing keeps legacy engines usable and stops renderer recreation until a fresh Activity', async t => {
  const directory = await mkdtemp(join(tmpdir(), 'aperture-home-mode-'));
  t.after(() => rm(directory, {recursive:true, force:true}));
  const jdk = process.env.JAVA_HOME || '/tmp/aperture-android-runtime/jdk-unpack/jdk-17.0.21+9';
  const exec = promisify(execFile);
  await exec(join(jdk, 'bin/javac'), ['-d', directory,
    resolve('android/app/src/main/java/org/zhixiaoce/aperture/LobbyMode.java'),
    resolve('tests/java/LobbyModeTest.java')]);
  const {stdout} = await exec(join(jdk, 'bin/java'), ['-cp', directory, 'org.zhixiaoce.aperture.LobbyModeTest']);
  const checks = stdout.trim().split('\n').filter(line => line.startsWith('PASS '));
  assert.equal(checks.length, 4);
  await mkdir('artifacts', {recursive:true});
  await writeFile('artifacts/lobby-mode-report.json', JSON.stringify({
    checks, traces:stdout.trim().split('\n').filter(line => line.startsWith('TRACE ')),
    productionJava:true, androidActivityExecuted:false, actualRendererKilled:false,
    actualLegacyWebViewTested:false, realDevice:false
  }, null, 2) + '\n');
  console.log(stdout.trim());
});
