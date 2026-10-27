import test from 'node:test';
import assert from 'node:assert/strict';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {mkdtemp, rm} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {nasTestJson} from '../scripts/nas-test-json.mjs';

const run=promisify(execFile);
test('production related films use exact available metadata and honest bounded fallback', {timeout:60000}, async()=>{
  const directory=await mkdtemp(join(tmpdir(),'kaishi-related-'));
  const jdk=process.env.JAVA_HOME||join(process.cwd(),'.android-local/jdk17');
  try{
    const cp=await nasTestJson(),src='android/app/src/main/java/org/zhixiaoce/aperture/';
    await run(join(jdk,'bin/javac'),['-cp',cp,'-d',directory,...['CinemaCatalog','CinemaResourcePack','CinemaFormats','CinemaRelated'].map(n=>src+n+'.java'),'tests/java/CinemaRelatedTest.java']);
    const {stdout}=await run(join(jdk,'bin/java'),['-cp',directory+':'+cp,'org.zhixiaoce.aperture.CinemaRelatedTest']);
    assert.match(stdout,/14 production related-film assertions passed/);console.log(stdout.trim());
  }finally{await rm(directory,{recursive:true,force:true});}
});
