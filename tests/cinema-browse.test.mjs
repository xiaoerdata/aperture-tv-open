import test from 'node:test';
import assert from 'node:assert/strict';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {mkdtemp, rm} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {nasTestJson} from '../scripts/nas-test-json.mjs';

const run=promisify(execFile);
test('loaded-content filters and stable title relevance never invent source-wide counts', {timeout:60000}, async()=>{
  const directory=await mkdtemp(join(tmpdir(),'kaishi-related-'));
  const jdk=process.env.JAVA_HOME||join(process.cwd(),'.android-local/jdk17');
  try{
    const cp=await nasTestJson(),src='android/app/src/main/java/org/zhixiaoce/aperture/';
    await run(join(jdk,'bin/javac'),['-cp',cp,'-d',directory,...['CinemaCatalog','CinemaResourcePack','CinemaFormats','CinemaBrowse'].map(n=>src+n+'.java'),'tests/java/CinemaBrowseTest.java']);
    const {stdout}=await run(join(jdk,'bin/java'),['-cp',directory+':'+cp,'org.zhixiaoce.aperture.CinemaBrowseTest']);
    assert.match(stdout,/9 production browse assertions passed/);console.log(stdout.trim());
  }finally{await rm(directory,{recursive:true,force:true});}
});
