import test from 'node:test';
import assert from 'node:assert/strict';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {mkdtemp,rm} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {nasClasspath} from '../scripts/nas-classpath.mjs';
import {nasTestJson} from '../scripts/nas-test-json.mjs';

const run=promisify(execFile);
test('production playlist/config logos survive redirects and cache without a render base', {timeout:120000}, async()=>{
  const directory=await mkdtemp(join(tmpdir(),'kaishi-logo-address-'));
  const jdk=process.env.JAVA_HOME||join(process.cwd(),'.android-local/jdk17');
  try {
    const cp=(await nasClasspath())+':'+await nasTestJson(),src='android/app/src/main/java/org/zhixiaoce/aperture/';
    await run(join(jdk,'bin/javac'),['-cp',cp,'-d',directory,...['CinemaCatalog','CinemaFormats','CinemaResourcePack','CinemaHttp'].map(n=>src+n+'.java'),'tests/java/CinemaLogoAddressTest.java']);
    const {stdout}=await run(join(jdk,'bin/java'),['-cp',directory+':'+cp,'org.zhixiaoce.aperture.CinemaLogoAddressTest']);
    assert.match(stdout,/14 production logo address assertions passed/);console.log(stdout.trim());
  } finally {await rm(directory,{recursive:true,force:true});}
});
