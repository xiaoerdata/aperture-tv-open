// Actual production Java transport in a JVM; not Android/WebView/device evidence.
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';import {join,resolve} from 'node:path';
import {existsSync} from 'node:fs';import {execFile,spawn} from 'node:child_process';import {promisify} from 'node:util';import {createInterface} from 'node:readline';
export async function householdHarness(){
 const directory=await mkdtemp(join(tmpdir(),'aperture-household-java-')),isolated='/tmp/aperture-android-runtime/jdk-unpack/jdk-17.0.21+9',jdk=process.env.APERTURE_CHECK_JDK||process.env.JAVA_HOME||(existsSync(join(isolated,'bin/javac'))?isolated:null),exe=name=>jdk?join(jdk,'bin',name):name;
 try{await promisify(execFile)(exe('javac'),['-d',directory,resolve('android/app/src/main/java/org/zhixiaoce/aperture/LobbyAssets.java'),resolve('android/app/src/main/java/org/zhixiaoce/aperture/HouseholdProxy.java'),resolve('tests/java/HouseholdProxyHarness.java')]);}catch(e){await rm(directory,{recursive:true,force:true});throw e;}
 const child=spawn(exe('java'),['-cp',directory,'org.zhixiaoce.aperture.HouseholdProxyHarness'],{stdio:['pipe','pipe','pipe']}),pending=new Map();let next=0,closed=false;
 const fail=()=>{for(const p of pending.values()){clearTimeout(p.timer);p.reject(new Error('Java transport harness stopped'));}pending.clear();};child.once('error',fail);child.once('exit',fail);child.stderr.resume();
 const lines=createInterface({input:child.stdout});lines.on('line',line=>{const [id,status,mime,encoded]=line.split(' '),p=pending.get(id);if(!p)return;pending.delete(id);clearTimeout(p.timer);p.resolve(status==='OK'?{}:{status:Number(status),mime,body:encoded?Buffer.from(encoded,'base64'):Buffer.alloc(0)});});
 function command(value){if(closed)throw new Error('harness closed');const id=String(++next);return new Promise((resolve,reject)=>{const timer=setTimeout(()=>{pending.delete(id);reject(new Error('Java transport observation timed out'));},15000);pending.set(id,{resolve,reject,timer});child.stdin.write(id+' '+value+'\n');});}
 const b64=value=>value?Buffer.from(value).toString('base64'):'-';
 return {set:async(origin,cookie,debug=true)=>{await command('SET '+b64(origin)+' '+b64(cookie)+' '+debug);await command('ACTIVE true');},active:value=>command('ACTIVE '+value),read:(url,method='GET',mainFrame=false)=>command('GET '+b64(url)+' '+method+' '+mainFrame),cancel:()=>command('CLOSE'),async close(){if(closed)return;closed=true;child.stdin.end();await new Promise(resolve=>{if(child.exitCode!==null)return resolve();child.once('exit',resolve);});lines.close();await rm(directory,{recursive:true,force:true});}};
}
