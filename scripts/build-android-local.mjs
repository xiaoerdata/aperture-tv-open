// Uses official locally installed SDK/JDK/Gradle. No GitHub runner, billing or upload.
import {spawn} from 'node:child_process';
import {mkdir,writeFile,copyFile,chmod,access,readFile} from 'node:fs/promises';
import {join,resolve,delimiter} from 'node:path';
import {fileURLToPath} from 'node:url';
import {androidSource,sourceProvenance} from './android-source.mjs';
import {editions} from './android-editions.mjs';
import {androidAbis} from './android-abis.mjs';
import {preserveDebugKey} from './signing-backup.mjs';
const selected=editions(process.argv.slice(2)),selectedAbis=androidAbis();
const root=fileURLToPath(new URL('../',import.meta.url)),runtime=resolve(process.env.APERTURE_ANDROID_RUNTIME||join(root,'.android-local'));
const sdk=process.env.ANDROID_HOME||process.env.ANDROID_SDK_ROOT;
if(!sdk||!process.env.JAVA_HOME)throw new Error('Set JAVA_HOME to JDK17 and ANDROID_HOME to Android SDK. See docs/ANDROID-LOCAL.md.');
const java=join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'),gradle=process.env.GRADLE_BIN||'gradle',buildTools=join(sdk,'build-tools','35.0.0');
await mkdir(runtime,{recursive:true,mode:0o700});if(process.platform!=='win32')await chmod(runtime,0o700);
const env={...process.env,ANDROID_HOME:sdk,ANDROID_SDK_ROOT:sdk,ANDROID_USER_HOME:join(runtime,'android-user'),GRADLE_USER_HOME:join(runtime,'gradle-user'),PATH:join(process.env.JAVA_HOME,'bin')+delimiter+process.env.PATH};
await mkdir(env.ANDROID_USER_HOME,{recursive:true,mode:0o700});
function run(command,args,{capture=false}={}){return new Promise((done,fail)=>{const p=spawn(command,args,{cwd:root,env,stdio:capture?['ignore','pipe','pipe']:'inherit'});let output='';if(capture){p.stdout.on('data',b=>output+=b);p.stderr.on('data',b=>output+=b);}p.on('error',fail);p.on('exit',code=>code===0?done(output):fail(new Error(`${command} exited ${code}${capture?'\n'+output:''}`)));});}
const previousCertificates=[];
for(const edition of selected){try{const manifest=JSON.parse(await readFile(join(root,'artifacts',edition==='standard'?'android-local':'android-local-parents','manifest.json'),'utf8'));if(manifest.certificateSha256)previousCertificates.push(manifest.certificateSha256);}catch(error){if(error.code!=='ENOENT')throw error;}}
const backupDirectory=join(root,'.android-local','signing');
const signing=await preserveDebugKey({runtimeDirectory:runtime,backupDirectory,existingSignedCandidate:previousCertificates.length>0});
const version=await run(java,['-version'],{capture:true});if(!/version "17\./.test(version))throw new Error('JDK17 is required.');
await access(join(sdk,'platforms','android-35','android.jar'));await access(join(buildTools,process.platform==='win32'?'apksigner.bat':'apksigner'));
const gradleVersion=await run(gradle,['--version'],{capture:true});if(!/Gradle 8\.9(?:\s|$)/.test(gradleVersion))throw new Error('Use Gradle8.9 for this Android plugin.');
if(signing.present&&previousCertificates.length){
 const keyInfo=await run(join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'keytool.exe':'keytool'),['-list','-v','-keystore',join(env.ANDROID_USER_HOME,'debug.keystore'),'-storepass','android','-alias','androiddebugkey'],{capture:true});
 const certificate=/SHA256:\s*([A-Fa-f0-9:]{64,})/.exec(keyInfo)?.[1].replace(/:/g,'').toLowerCase();
 if(!certificate||previousCertificates.some(previous=>previous!==certificate))throw new Error('Signing certificate differs from retained candidates; refusing to overwrite them.');
}
const inputs=await androidSource(root),provenance=await sourceProvenance(root,inputs),sourceHash=inputs.sha256;
const commit=provenance.sourceCommit,dirty=provenance.workingTreeDirty;
const args=['--no-daemon','--stacktrace'];
// Java does not consume HTTP(S)_PROXY automatically. Do not write global JVM settings.
for(const [scheme,name] of [['https','HTTPS_PROXY'],['http','HTTP_PROXY']]){const value=process.env[name]||process.env[name.toLowerCase()];if(value){const proxy=new URL(value);if(proxy.username||proxy.password)throw new Error('Authenticated proxy must be configured privately in Gradle; refusing to put credentials in process arguments.');args.push(`-D${scheme}.proxyHost=${proxy.hostname}`,`-D${scheme}.proxyPort=${proxy.port||'80'}`);}}
args.push('-PkaishiAbis='+selectedAbis.join(','),'-p','android',...selected.map(edition=>':app:assemble'+(edition==='parents'?'Parents':'Standard')+'Debug'));await run(gradle,args);
for(const edition of selected){
const output=resolve(root,'artifacts',edition==='standard'?'android-local':'android-local-parents');await mkdir(output,{recursive:true});await copyFile(join(root,'android','app','build','outputs','apk',edition,'debug','app-'+edition+'-debug.apk'),join(output,'aperture-tv-debug.apk'));
await writeFile(join(output,'signature.txt'),await run(join(buildTools,process.platform==='win32'?'apksigner.bat':'apksigner'),['verify','--verbose','--print-certs',join(output,'aperture-tv-debug.apk')],{capture:true}));
await writeFile(join(output,'package.txt'),await run(join(buildTools,process.platform==='win32'?'aapt.exe':'aapt'),['dump','badging',join(output,'aperture-tv-debug.apk')],{capture:true}));
Object.assign(env,{SOURCE_SHA:commit||'',BUILD_SHA:provenance.buildCommit||'',BUILD_ENVIRONMENT:'local-free',WORKTREE_DIRTY:String(dirty),ANDROID_SOURCE_SHA256:sourceHash,SOURCE_PROVENANCE:JSON.stringify(provenance),ANDROID_EDITION:edition});
await run(process.env.PYTHON_BIN||'python3',['scripts/android-artifact.py','--directory',output]);
await writeFile(join(output,'build-environment.json'),JSON.stringify({edition,java:version.trim(),gradle:'8.9',compileSdk:35,buildTools:'35.0.0',sourceCommit:commit,sourceProvenance:provenance,androidSourceSha256:sourceHash,abis:selectedAbis,workingTreeDirty:dirty,githubHostedRunnerUsed:false,uploaded:false},null,2));
await preserveDebugKey({runtimeDirectory:runtime,backupDirectory,existingSignedCandidate:true});
console.log('Private testing APK: '+join(output,'aperture-tv-debug.apk')+'\nDebug key remains in the private local runtime. No release was published.');
}
