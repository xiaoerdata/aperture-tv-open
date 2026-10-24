// Synthetic JVM receiver for API/browser/protocol verification. Never used in the APK.
import {mkdtemp,rm,readFile} from 'node:fs/promises';import {existsSync} from 'node:fs';import {tmpdir} from 'node:os';import {join,resolve} from 'node:path';
import {spawn,execFile} from 'node:child_process';import {promisify} from 'node:util';import {createInterface} from 'node:readline';
import {nasClasspath} from './nas-classpath.mjs';import {nasTestJson} from './nas-test-json.mjs';
export async function nasSetupHarness(){
  const directory=await mkdtemp(join(tmpdir(),'aperture-setup-java-')),jdk=process.env.JAVA_HOME||'/tmp/aperture-android-runtime/jdk-unpack/jdk-17.0.21+9';
  let libraries;try{const cached=(await readFile('artifacts/nas-classpath.txt','utf8')).trim();if(cached.split(':').every(p=>existsSync(p)))libraries=cached;}catch{}
  libraries ||= await nasClasspath();
  const cp=await nasTestJson()+':'+libraries,source=resolve('android/app/src/main/java/org/zhixiaoce/aperture');
  let child,lines;
  try{
    await promisify(execFile)(join(jdk,'bin/javac'),['-cp',cp,'-d',directory,...['NasSetupCrypto','NasSetupClient','NasSetupImport','NasDraft','NasLocation','NasLibrary','NasCipher','NasReader','NasSocketFactory','SmbNasReader','DavNasReader','LobbyAssets'].map(n=>join(source,n+'.java')),resolve('tests/java/NasSetupHarness.java')]);
    child=spawn(join(jdk,'bin/java'),['-cp',directory+':'+cp,'org.zhixiaoce.aperture.NasSetupHarness'],{stdio:['pipe','pipe','pipe']});child.stderr.resume();
    lines=createInterface({input:child.stdout})[Symbol.asyncIterator]();
    async function read(){const line=await lines.next();if(line.done)throw new Error('Synthetic setup receiver stopped');return JSON.parse(line.value);}
    const hello=await read();
    return {hello,async call(command){child.stdin.write(JSON.stringify(command)+'\n');return read();},async close(){child.stdin.end();await new Promise(r=>{if(child.exitCode!==null)return r();child.once('exit',r);});await rm(directory,{recursive:true,force:true});}};
  }catch(error){child?.kill();await rm(directory,{recursive:true,force:true});throw error;}
}
