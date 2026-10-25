// Original JAR + local source. Separate from the plugin trust/regression fixture.
import {mkdtemp,mkdir,rm,readFile} from 'node:fs/promises';
import {join,resolve} from 'node:path';
import {tmpdir} from 'node:os';
import {execFileSync} from 'node:child_process';
import {createServer} from 'node:http';
import {nasClasspath} from './nas-classpath.mjs';
const runtime=process.env.APERTURE_ANDROID_RUNTIME||'/tmp/aperture-android-runtime',jdk=join(runtime,'jdk17'),sdk=join(runtime,'android-sdk'),android=join(sdk,'platforms/android-35/android.jar');
const work=await mkdtemp(join(tmpdir(),'kaishi-playback-')),out=resolve('artifacts/s50-plugin-playback');
const dependencies=await nasClasspath();
await mkdir(out,{recursive:true});
try {
 await mkdir(join(work,'host'));await mkdir(join(work,'plugin'));await mkdir(join(work,'dex'));
 execFileSync(join(jdk,'bin/javac'),['--release','8','-cp',android+':'+dependencies,'-d',join(work,'host'),'android/app/src/main/java/com/github/catvod/crawler/Spider.java']);
 execFileSync(join(jdk,'bin/javac'),['--release','8','-cp',android+':'+dependencies+':'+join(work,'host'),'-d',join(work,'plugin'),'tests/fixtures/plugins/Playback.java']);
 execFileSync(join(jdk,'bin/jar'),['cf',join(out,'playback.jar'),'-C',join(work,'plugin'),'.']);
 execFileSync(join(sdk,'build-tools/35.0.0/d8'),['--lib',android,'--classpath',join(work,'host'),'--min-api','26','--output',join(work,'dex'),join(out,'playback.jar')],{env:{...process.env,JAVA_HOME:jdk}});
 execFileSync(join(jdk,'bin/jar'),['uf',join(out,'playback.jar'),'-C',join(work,'dex'),'classes.dex']);
}finally{await rm(work,{recursive:true,force:true});}
const jar=await readFile(join(out,'playback.jar'));
const profile={spider:'http://10.0.2.2:4401/playback.jar',sites:[{key:'native-playback',name:'原生插件播放测试',type:3,api:'csp_Playback',ext:'http://10.0.2.2:4398/video.mp4'}],lives:[{name:'直播清单',url:'http://10.0.2.2:4398/channels.m3u'}]};
const server=createServer((req,res)=>{if(req.url==='/profile.json'){res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify(profile));}else if(req.url==='/playback.jar'){res.writeHead(200,{'Content-Type':'application/java-archive'});res.end(jar);}else{res.writeHead(404);res.end();}});
await new Promise(done=>server.listen(4401,'127.0.0.1',done));console.log('Native plugin playback fixture ready on loopback 4401');
function stop(){server.closeAllConnections();server.close(()=>process.exit(0));}process.once('SIGINT',stop);process.once('SIGTERM',stop);
