import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm,mkdir,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join,resolve,delimiter} from 'node:path';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {nasTestJson} from '../scripts/nas-test-json.mjs';

test('NAS shared shelf transaction, stable route, resume, favorite and encryption',{timeout:60000},async t=>{
 const directory=await mkdtemp(join(tmpdir(),'kaishi-nas-viewing-'));t.after(()=>rm(directory,{recursive:true,force:true}));
 const cp=await nasTestJson(),jdk=process.env.JAVA_HOME||'/tmp/aperture-android-runtime/jdk17',exec=promisify(execFile),source=resolve('android/app/src/main/java/org/zhixiaoce/aperture');
 await exec(join(jdk,'bin/javac'),['-cp',cp,'-d',directory,...['CinemaCatalog','CinemaResourcePack','CinemaFormats','CinemaShelf','CinemaShelfStore','CinemaCipher','NasLocation','NasNavigation','NasLibrary','NasDraft','NasWatchRoute'].map(n=>join(source,n+'.java')),resolve('tests/java/NasViewingTest.java')]);
 const {stdout}=await exec(join(jdk,'bin/java'),['-cp',directory+delimiter+cp,'org.zhixiaoce.aperture.NasViewingTest']);assert.match(stdout,/assertions passed/);
 await mkdir('docs/verification',{recursive:true});await writeFile('docs/verification/s53-nas-viewing.json',JSON.stringify({productionJavaModels:true,transactionConcurrency:true,stableRelativeNasRoute:true,jvmAesGcmRoundtrip:true,androidKeyStore:false,activityExecuted:false,mediaDecoded:false,realXiaomi:false,output:stdout.trim()},null,2)+'\n');console.log(stdout.trim());
});
