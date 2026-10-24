// Resolve pinned Gradle runtime dependencies only. No compile, APK build or device operation.
import {spawn} from 'node:child_process';
import {mkdir,readFile} from 'node:fs/promises';
import {join,resolve} from 'node:path';
export async function nasClasspath(){
 const runtime=process.env.APERTURE_ANDROID_RUNTIME||'/tmp/aperture-android-runtime',jdk=process.env.JAVA_HOME||join(runtime,'jdk-unpack/jdk-17.0.21+9'),sdk=process.env.ANDROID_HOME||join(runtime,'android-sdk'),output=resolve('artifacts/nas-classpath.txt');
 await mkdir('artifacts',{recursive:true});const args=['--no-daemon','--console=plain','-q','-p','android',':app:nasClasspath','-PnasClasspathFile='+output];
 for(const [scheme,name]of [['https','HTTPS_PROXY'],['http','HTTP_PROXY']]){const value=process.env[name]||process.env[name.toLowerCase()];if(value){const proxy=new URL(value);if(proxy.username||proxy.password)throw new Error('Configure authenticated proxy privately');args.push('-D'+scheme+'.proxyHost='+proxy.hostname,'-D'+scheme+'.proxyPort='+(proxy.port||'80'));}}
 await new Promise((done,fail)=>{const child=spawn(process.env.GRADLE_BIN||join(runtime,'gradle-8.9/bin/gradle'),args,{env:{...process.env,JAVA_HOME:jdk,ANDROID_HOME:sdk,GRADLE_USER_HOME:join(runtime,'gradle-user')},stdio:['ignore','pipe','pipe']});let error='';child.stdout.resume();child.stderr.on('data',b=>error+=b);child.on('error',fail);child.on('exit',code=>code===0?done():fail(new Error('NAS dependency resolution failed: '+error.slice(-3000))));});
 return (await readFile(output,'utf8')).trim();
}
