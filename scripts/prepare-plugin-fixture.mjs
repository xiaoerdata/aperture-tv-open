// Compile only the provider-free test plugin, including DEX for Android execution.
import {mkdir,readFile,mkdtemp,rm,copyFile} from 'node:fs/promises';
import {join,resolve} from 'node:path';import {tmpdir} from 'node:os';import {execFileSync} from 'node:child_process';
import {nasClasspath} from './nas-classpath.mjs';import {nasTestJson} from './nas-test-json.mjs';
const runtime=process.env.APERTURE_ANDROID_RUNTIME||'/tmp/aperture-android-runtime',jdk=process.env.JAVA_HOME||join(runtime,'jdk17'),sdk=process.env.ANDROID_HOME||join(runtime,'android-sdk');
const temporary=await mkdtemp(join(tmpdir(),'kaishi-plugin-fixture-')),output=resolve('artifacts/plugin-fixtures');
try{
 await mkdir(output,{recursive:true});await mkdir(join(temporary,'host'));await mkdir(join(temporary,'plugin'));await mkdir(join(temporary,'dex'));
 const cp=await nasClasspath(),android=join(sdk,'platforms/android-35/android.jar'),json=await nasTestJson();
 const javac=join(jdk,'bin/javac'),host=join(temporary,'host');
 execFileSync(javac,['--release','8','-cp',android+':'+cp+':'+json,'-d',host,'android/app/src/main/java/com/github/catvod/crawler/Spider.java','android/app/src/main/java/org/zhixiaoce/aperture/CinemaPluginAbi.java','tests/java/PluginExecutionTest.java']);
 execFileSync(javac,['--release','8','-cp',host+':'+android+':'+cp+':'+json,'-d',join(temporary,'plugin'),'tests/fixtures/plugins/Fixture.java']);
 execFileSync(join(jdk,'bin/jar'),['cf',join(output,'fixture.jar'),'-C',join(temporary,'plugin'),'.']);
 execFileSync(join(sdk,'build-tools/35.0.0/d8'),['--lib',android,'--classpath',host,'--min-api','26','--output',join(temporary,'dex'),join(output,'fixture.jar')],{env:{...process.env,JAVA_HOME:jdk}});
 execFileSync(join(jdk,'bin/jar'),['uf',join(output,'fixture.jar'),'-C',join(temporary,'dex'),'classes.dex']);
 process.stdout.write(execFileSync(join(jdk,'bin/java'),['-cp',host+':'+json+':'+android+':'+cp,'org.zhixiaoce.aperture.PluginExecutionTest',join(output,'fixture.jar')],{encoding:'utf8'}));
 for(const name of ['catalogue.js','records.js','catalogue.py'])await copyFile(join('tests/fixtures/plugins',name),join(output,name));
 console.log('Original DEX/JAR and JS/Python fixtures: '+output);
}finally{await rm(temporary,{recursive:true,force:true});}
