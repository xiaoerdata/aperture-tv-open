import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, rm, mkdir, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';

test('native playback production controller handles audio/window/prepare races without implicit resume', async t => {
  const directory = await mkdtemp(join(tmpdir(), 'aperture-playback-'));
  t.after(() => rm(directory, {recursive: true, force: true}));
  const jdk = process.env.JAVA_HOME || '/tmp/aperture-android-runtime/jdk-unpack/jdk-17.0.21+9';
  const exec = promisify(execFile);
  await exec(join(jdk, 'bin/javac'), ['-d', directory,
    resolve('android/app/src/main/java/org/zhixiaoce/aperture/PlaybackSession.java'),
    resolve('tests/java/PlaybackSessionTest.java')]);
  const {stdout} = await exec(join(jdk, 'bin/java'), ['-cp', directory, 'org.zhixiaoce.aperture.PlaybackSessionTest']);
  const lines = stdout.trim().split('\n');
  const checks = lines.filter(line => line.startsWith('PASS '));
  const traces = lines.filter(line => line.startsWith('TRACE '));
  assert.equal(checks.length, 10); assert.equal(traces.length, 3);
  await mkdir('artifacts', {recursive: true});
  await writeFile('artifacts/playback-session-report.json', JSON.stringify({
    checks, traces, productionJava: true, syntheticAudioAndPlayer: true,
    androidAudioManagerExecuted: false, androidMediaPlayerExecuted: false,
    xiaomiAssistantTested: false, realDevice: false
  }, null, 2) + '\n');
  console.log(stdout.trim());
});
