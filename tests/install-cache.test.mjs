import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
const run=promisify(execFile);
test('production installer staging rejects unsafe/partial files and closes resources',async t=>{
 const directory=await mkdtemp(join(tmpdir(),'aperture-install-cache-'));t.after(()=>rm(directory,{recursive:true,force:true}));
 const jdk=process.env.JAVA_HOME||'/tmp/aperture-android-runtime/jdk17';
 await run(join(jdk,'bin/javac'),['-d',directory,resolve('android/app/src/main/java/org/zhixiaoce/aperture/InstallCache.java'),resolve('tests/java/InstallCacheTest.java')]);
 const result=await run(join(jdk,'bin/java'),['-cp',directory,'org.zhixiaoce.aperture.InstallCacheTest',join(directory,'cache')]);
 assert.match(result.stdout,/checks passed/);console.log(result.stdout.trim());
});
