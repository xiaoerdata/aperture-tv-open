import test from 'node:test';
import assert from 'node:assert/strict';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {mkdtemp,rm,writeFile,mkdir} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {nasClasspath} from '../scripts/nas-classpath.mjs';
import {nasTestJson} from '../scripts/nas-test-json.mjs';
const run=promisify(execFile);
test('production native cinema parser and HTTP client interoperate with an isolated source',{timeout:90000},async()=>{
  const directory=await mkdtemp(join(tmpdir(),'kaishi-cinema-'));
  const jdk=process.env.JAVA_HOME||'/tmp/aperture-android-runtime/jdk17';
  try{
    const cp=(await nasClasspath())+':'+await nasTestJson();
    const source='android/app/src/main/java/org/zhixiaoce/aperture/';
    await run(join(jdk,'bin/javac'),['-cp',cp,'-d',directory,source+'CinemaCatalog.java',source+'CinemaHttp.java',source+'CinemaCipher.java','tests/java/EmbeddedCinemaTest.java']);
    const {stdout}=await run(join(jdk,'bin/java'),['-cp',directory+':'+cp,'org.zhixiaoce.aperture.EmbeddedCinemaTest'],{timeout:30000});
    assert.match(stdout,/assertions passed/);
    await mkdir('docs/verification',{recursive:true});
    await writeFile('docs/verification/s46-embedded-cinema.json',JSON.stringify({productionJavaParser:true,productionOkHttpClient:true,isolatedLocalHttpFixtures:true,androidActivitiesExecuted:false,androidKeyStoreTested:false,mediaDecoded:false,xiaomiTested:false,output:stdout.trim()},null,2)+'\n');
    console.log(stdout.trim());
  }finally{await rm(directory,{recursive:true,force:true});}
});
