// Dedicated emulator fixture. Loopback-only Samba; no changes to system NAS services.
import {mkdir,mkdtemp,rm,copyFile,writeFile} from 'node:fs/promises';
import {existsSync} from 'node:fs';
import {spawn,spawnSync} from 'node:child_process';
import {resolve,join} from 'node:path';
import {userInfo} from 'node:os';
const directory=resolve('artifacts/s50-nas-runtime'),temporary=await mkdtemp('/tmp/kaishi-nas-'),share=join(temporary,'share'),port=Number(process.env.APERTURE_NAS_FIXTURE_PORT||4458);
if(!Number.isInteger(port)||port<1024||port>65535)throw new Error('Use an unprivileged fixture port');
await mkdir(directory,{recursive:true});
await mkdir(join(share,'电影','周末'),{recursive:true});
const video=join(share,'电影','周末','家庭测试.mp4');
for(let i=0;i<22;i++)await writeFile(join(share,'电影','周末','A'+String(i).padStart(2,'0')+'.mp4'),'synthetic unselected placeholder');
if(existsSync('artifacts/s47-runtime/fixture/video.mp4'))await copyFile('artifacts/s47-runtime/fixture/video.mp4',video);
else {
 const generated=spawnSync(process.env.FFMPEG_PATH||'ffmpeg',['-hide_banner','-loglevel','error','-y','-threads','2','-f','lavfi','-i','testsrc2=size=320x180:rate=12','-f','lavfi','-i','sine=frequency=400:sample_rate=48000','-t','120','-c:v','libx264','-profile:v','baseline','-level','3.0','-preset','ultrafast','-pix_fmt','yuv420p','-g','48','-c:a','aac','-b:a','32k','-ac','1','-movflags','+faststart',video],{encoding:'utf8'});
 if(generated.status!==0)throw new Error(generated.stderr||'Synthetic video generation failed');
}
const manifest={loopbackOnly:true,serverForEmulator:`smb://10.0.2.2:${port}/Family`,user:userInfo().username,password:'synthetic-nas-only',relativePath:'电影/周末/家庭测试.mp4',root:'电影',synthetic:true,readOnlyShare:true};
await writeFile(join(directory,'fixture.json'),JSON.stringify(manifest,null,2)+'\n');
const child=spawn(process.env.APERTURE_NAS_TEST_PYTHON||'/tmp/aperture-nas-runtime/venv/bin/python',[resolve('tests/fixtures/nas-samba.py'),String(port),share,process.env.APERTURE_NAS_SAMBA_ROOT||'/tmp/aperture-nas-runtime/samba','SMB3_11'],{detached:true,stdio:['ignore','inherit','inherit']});
console.log('S50 synthetic NAS fixture: 127.0.0.1:'+port+'; emulator connection details in artifacts/s50-nas-runtime/fixture.json');
process.once('SIGINT',()=>child.kill('SIGTERM'));process.once('SIGTERM',()=>child.kill('SIGTERM'));child.on('exit',async code=>{await rm(temporary,{recursive:true,force:true});process.exit(code||0);});child.on('error',error=>{console.error(error.message);process.exit(1);});
