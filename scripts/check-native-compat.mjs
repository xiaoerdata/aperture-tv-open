// Targeted javac/API check, not Gradle, APK packaging, emulator or Xiaomi acceptance.
import {existsSync} from 'node:fs';
import {mkdtemp,mkdir,writeFile,rm,readFile,readdir} from 'node:fs/promises';
import {tmpdir} from 'node:os';import {join,resolve} from 'node:path';import {spawnSync} from 'node:child_process';
import {nasClasspath} from './nas-classpath.mjs';
const root=process.cwd(),directory=await mkdtemp(join(tmpdir(),'aperture-native-check-'));
const isolated='/tmp/aperture-android-runtime/jdk-unpack/jdk-17.0.21+9';
const jdk=process.env.APERTURE_CHECK_JDK||process.env.JAVA_HOME||(existsSync(join(isolated,'bin/javac'))?isolated:null);
const executable=name=>jdk?join(jdk,'bin',name):name;
const sdkJar=process.env.APERTURE_CHECK_ANDROID_JAR||join(process.env.ANDROID_HOME||process.env.ANDROID_SDK_ROOT||'/tmp/aperture-android-runtime/android-sdk','platforms/android-35/android.jar');
const run=(file,args)=>{const r=spawnSync(file,args,{encoding:'utf8'});if(r.status!==0)throw new Error((r.stderr||r.stdout||'failed').trim());return r.stdout;};
try{
 const dependencies=await nasClasspath();
 const source=join(root,'android/app/src/main/java'),files=(await readdir(source,{recursive:true})).filter(f=>f.endsWith('.java')).map(f=>join(source,f));
 await mkdir(join(directory,'classes'));
 for(const parents of [false,true]){
  const config=join(directory,'BuildConfig.java');await writeFile(config,'package org.zhixiaoce.aperture;final class BuildConfig {static final boolean DEBUG=true;static final boolean PARENTS_EDITION='+parents+';}');
  run(executable('javac'),['-classpath',sdkJar+':'+dependencies,'-d',join(directory,'classes'),...files,config,resolve('tests/java/NativeCompatibilityTest.java')]);
 }
 const output=run(executable('java'),['-cp',join(directory,'classes'),'org.zhixiaoce.aperture.NativeCompatibilityTest']);
 const manifest=await readFile('android/app/src/main/AndroidManifest.xml','utf8');
 if(/android\.permission\.(RECORD_AUDIO|BIND_VOICE_INTERACTION|MANAGE_VOICE_KEYPHRASES|CAPTURE_AUDIO_HOTWORD)/.test(manifest))throw new Error('Unexpected microphone or privileged assistant permission');
 await mkdir('artifacts',{recursive:true});await writeFile('artifacts/native-compat-report.json',JSON.stringify({standardAndParentsApiCompilation:true,policyHarnessPassed:true,microphonePermission:false,apkBuilt:false,deviceVoiceKeysTested:false,xiaomiFarFieldTested:false,standbyWakeTested:false,output:output.trim()},null,2));console.log(output.trim());console.log('Both editions compile against Android 35 API; no APK built and no device claims');
}finally{await rm(directory,{recursive:true,force:true});}
