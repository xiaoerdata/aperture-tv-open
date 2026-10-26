// Verify a frozen archive in a new directory, following the public Linux quickstart.
// No Android build, television installation, WorkBuddy application or publication.
import assert from 'node:assert/strict';
import {mkdtemp,rm,mkdir,readFile,open,stat,readdir} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join,resolve,delimiter,sep} from 'node:path';
import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {createServer} from 'node:net';
import {fileURLToPath} from 'node:url';
import {launchBrowser} from './browser.mjs';

const root=fileURLToPath(new URL('../',import.meta.url));
const version=JSON.parse(await readFile(join(root,'package.json'),'utf8')).version;
const args=process.argv.slice(2),archive=resolve(args[0]||join(root,'artifacts','aperture-tv-v'+version+'-source.tar.gz'));
if(args.length!==0&&args.length!==1&&!(args.length===3&&args[1]==='--output'))throw new Error('Usage: node scripts/verify-source-install.mjs archive.tar.gz [--output artifacts/new-report.json]');
const archiveHash=createHash('sha256').update(await readFile(archive)).digest('hex');
const output=resolve(root,args[2]||'artifacts/source-install-'+archiveHash.slice(0,12)+'.json');
if(!output.startsWith(resolve(root,'artifacts')+sep)||!output.endsWith('.json'))throw new Error('Choose a new report inside artifacts');
await mkdir(resolve(output,'..'),{recursive:true});
const receipt=await open(output,'wx',0o600);
const directory=await mkdtemp(join(tmpdir(),'aperture-clean-install-'));
const env=Object.fromEntries(Object.entries(process.env).filter(([key])=>!key.startsWith('APERTURE_')&&!['PYTHONPATH','PYTHONHOME','PUBLIC_ORIGIN','HOST','PORT'].includes(key)));
let phase='archive',server,browser,serverLog='',childExit,childSignal;
const report={startedAt:new Date().toISOString(),archiveName:archive.split(sep).at(-1),archiveSha256:archiveHash,status:'running',hardwareTested:false,workBuddyApplicationTested:false,apkBuilt:false};
function run(command,argv,cwd,environment=env){return new Promise((done,fail)=>{
 const child=spawn(command,argv,{cwd,env:environment,stdio:['ignore','pipe','pipe']});let stdout='';
 child.stdout.on('data',b=>stdout+=b);child.stderr.resume();
 child.once('error',error=>fail(new Error(phase+': process unavailable ('+error.code+')')));
 child.once('exit',(code,signal)=>code===0?done(stdout):fail(new Error(phase+': command failed (exit '+code+', signal '+signal+')')));
});}
async function filesAt(path,base=path){const found=[];for(const entry of await readdir(path,{withFileTypes:true})){const file=join(path,entry.name);assert.ok(!entry.isSymbolicLink(),'unexpected source symlink');if(entry.isDirectory())found.push(...await filesAt(file,base));else found.push(file.slice(base.length+1).split(sep).join('/'));}return found;}
try{
 // Reuse the publication validator; it rejects links, traversal and private/runtime paths.
 const extraction='import importlib.util,sys,tarfile; spec=importlib.util.spec_from_file_location("public_source",sys.argv[1]); module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module); archive=tarfile.open(sys.argv[2],"r:gz"); archive.extractall(sys.argv[3],members=module.safe_members(archive)); archive.close()';
 await run('python3',['-c',extraction,join(root,'scripts/prepare-public-source.py'),archive,directory],root);
 const cwd=join(directory,'aperture-tv');
 const manifest=JSON.parse(await readFile(join(cwd,'SOURCE-MANIFEST.json'),'utf8'));
 const seen=new Set();
 for(const item of manifest.files){
  const file=resolve(cwd,item.path);
  assert.ok(file.startsWith(cwd+sep)&&!seen.has(item.path)&&/^[a-f0-9]{64}$/.test(item.sha256),'invalid source manifest path');seen.add(item.path);
  const bytes=await readFile(file);assert.equal(bytes.length,item.bytes);assert.equal(createHash('sha256').update(bytes).digest('hex'),item.sha256);
 }
 assert.deepEqual((await filesAt(cwd)).filter(name=>name!=='SOURCE-MANIFEST.json').sort(),[...seen].sort());
 report.sourceFilesVerified=seen.size;report.sourceCommit=manifest.source?.commit||null;
 report.noGitDirectory=!(await filesAt(cwd)).some(name=>name.startsWith('.git/'));
 report.version=JSON.parse(await readFile(join(cwd,'package.json'),'utf8')).version;
 phase='npm ci';
 await run('npm',['ci','--no-audit','--no-fund','--fetch-timeout=20000','--fetch-retries=0'],cwd);
 report.npmCi=true;report.npmLifecycleScriptsEnabled=true;
 phase='python venv';await run('python3',['-m','venv','.venv'],cwd);report.freshPythonVenv=true;
 const venv=join(cwd,'.venv','bin'),python=join(venv,'python3');
 phase='Pillow install';await run(python,['-m','pip','--isolated','--disable-pip-version-check','install','--index-url','https://pypi.org/simple','--timeout','20','--retries','0','-r','requirements.txt'],cwd);
 report.pillowInstalledFromRequirements=true;
 const appEnv={...env,PATH:venv+delimiter+(env.PATH||''),APERTURE_DATA:join(directory,'family'),APERTURE_LOCAL_AUTOMATION:'0'};
 phase='doctor';const doctor=JSON.parse(await run(process.execPath,['scripts/doctor.mjs','--json'],cwd,appEnv));assert.equal(doctor.ready,true);report.doctor=doctor;
 const reservation=createServer();await new Promise(r=>reservation.listen(0,'127.0.0.1',r));const port=reservation.address().port;await new Promise(r=>reservation.close(r));
 const origin='http://127.0.0.1:'+port;
 phase='checked startup';
 server=spawn(process.execPath,['scripts/start-local.mjs'],{cwd,env:{...appEnv,PORT:String(port),HOST:'127.0.0.1',PUBLIC_ORIGIN:origin},stdio:['ignore','pipe','pipe']});
 server.stdout.on('data',b=>serverLog+=b);server.stderr.on('data',b=>serverLog+=b);
 server.once('exit',(code,signal)=>{childExit=code;childSignal=signal;});
 let health;
 for(let i=0;i<160;i++){
  if(childExit!==undefined||childSignal!==undefined)throw new Error('Source service exited before readiness');
  if(serverLog.includes('Aperture TV: '+origin+'/tv?demo=1')){
   try{const response=await fetch(origin+'/api/health');if(response.ok){health=await response.json();break;}}catch{}
  }
  await new Promise(r=>setTimeout(r,100));
 }
 assert.equal(health?.version,report.version,'own service must announce and return its source version');
 report.checkedStart=true;report.healthVersion=health.version;
 report.ownerKeyMode=(await stat(join(directory,'family','owner-key'))).mode&0o777;
 report.familyDirectoryMode=(await stat(join(directory,'family'))).mode&0o777;
 assert.equal(report.ownerKeyMode,0o600);assert.equal(report.familyDirectoryMode,0o700);
 phase='browser routes';browser=await launchBrowser();
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];
 page.on('pageerror',error=>errors.push(error.message));
 phase='browser guide';await page.goto(origin+'/guide');assert.match(await page.locator('.guide-lead').innerText(),/仅适配小米电视/);
 for(const edition of ['standard','parents']){
  phase='browser home '+edition;await page.goto(origin+'/home?demo=1'+(edition==='parents'?'&edition=parents':''));
  await page.waitForFunction(()=>typeof window.apertureRemoteKey==='function');
  assert.equal(await page.locator('#lobby').isVisible(),true);
  await page.locator('#settings-open').click();await page.locator('[data-action=more-settings]').click();assert.equal(await page.locator('[data-action=install-apps]').isVisible(),true);
  await page.evaluate(()=>window.apertureRemoteKey('Escape'));assert.equal(await page.evaluate(()=>document.activeElement.id),'settings-open');
 }
 phase='browser full family screen';await page.goto(origin+'/tv?demo=1');await page.waitForFunction(()=>document.querySelector('#connection-status').textContent.includes('演示'));
 phase='browser lite family screen';await page.goto(origin+'/tv-lite?demo=1');await page.waitForFunction(()=>document.querySelector('#connection-status').textContent.includes('合成示例'));
 phase='browser cinema disconnected';await page.goto(origin+'/cinema');await page.waitForFunction(()=>typeof window.apertureBack==='function'&&document.querySelector('#cinema-status').textContent==='未连接');assert.equal(await page.evaluate(()=>getComputedStyle(document.body).backgroundColor),'rgb(12, 14, 17)');
 assert.deepEqual(errors,[]);report.browserErrors=errors;
 report.routes=['/guide','/home?demo=1','/home?edition=parents&demo=1','/tv?demo=1','/tv-lite?demo=1','/cinema'];
 phase='default access boundaries';
 report.privateStateStatus=(await fetch(origin+'/api/tv-lite/state')).status;assert.equal(report.privateStateStatus,401);
 report.machineInterfaceDefaultStatus=(await fetch(origin+'/api/local-agent/tools')).status;assert.equal(report.machineInterfaceDefaultStatus,503);
 report.status='passed';
}catch(error){report.status='failed';report.failedPhase=phase;report.error=error.message;process.exitCode=1;}
finally{
 await browser?.close();
 if(server){
  if(server.exitCode===null&&server.signalCode===null)server.kill('SIGTERM');
  if(server.exitCode===null&&server.signalCode===null)await new Promise(r=>server.once('exit',r));
  report.serviceStopped=true;
 }
 await rm(directory,{recursive:true,force:true});report.temporaryDirectoryRemoved=true;report.finishedAt=new Date().toISOString();
 await receipt.writeFile(JSON.stringify(report,null,2)+'\n');await receipt.close();
 console.log(JSON.stringify(report,null,2));
}
