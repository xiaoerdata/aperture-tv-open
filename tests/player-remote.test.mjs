import {test} from 'node:test';import assert from 'node:assert/strict';import {execFile} from 'node:child_process';import {promisify} from 'node:util';import {mkdtemp,writeFile,rm} from 'node:fs/promises';import {tmpdir} from 'node:os';import {join} from 'node:path';
const run=promisify(execFile);
test('confirm ownership rejects orphan/canceled/stale key-up; photo policy differs between shipping and development',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'kaishi-s74-')),jdk=process.env.JAVA_HOME||join(process.cwd(),'.android-local/jdk17');
 try{for(const debug of [false,true]){await writeFile(join(dir,'BuildConfig.java'),'package org.zhixiaoce.aperture;final class BuildConfig{static final boolean DEBUG='+debug+';}');await run(join(jdk,'bin/javac'),['-d',dir,join(dir,'BuildConfig.java'),...['ConfirmPress','FeaturePolicy'].map(n=>'android/app/src/main/java/org/zhixiaoce/aperture/'+n+'.java'),'tests/java/ConfirmPressTest.java']);const {stdout}=await run(join(jdk,'bin/java'),['-cp',dir,'org.zhixiaoce.aperture.ConfirmPressTest']);assert.match(stdout,/11 confirmation\/policy assertions passed/);console.log(stdout.trim());}}finally{await rm(dir,{recursive:true,force:true});}
});
