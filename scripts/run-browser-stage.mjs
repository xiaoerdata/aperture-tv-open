// Run unchanged browser assertions with stage-specific evidence paths. Temporary sibling
// copies preserve relative imports; only output paths are rewritten, never application code.
import {readFile,writeFile,mkdir,rm} from 'node:fs/promises';
import {createWriteStream} from 'node:fs';
import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
const [stage,...selected]=process.argv.slice(2);
if(!/^s[0-9]+$/.test(stage||''))throw new Error('Usage: node scripts/run-browser-stage.mjs s50 [browser-name.mjs ...]');
const baseline=JSON.parse(await readFile('docs/verification/s47-browser-summary.json','utf8'));
const names=selected.length?selected:baseline.scripts.map(x=>x.script);
if(names.some(n=>!/^browser-[a-z-]+\.mjs$/.test(n)))throw new Error('Invalid browser script name');
await mkdir('artifacts',{recursive:true});await mkdir('docs/verification',{recursive:true});
let previous={scripts:[],attempts:[]};try{previous=JSON.parse(await readFile('docs/verification/'+stage+'-browser-regression.json','utf8'));}catch(e){if(e.code!=='ENOENT')throw e;}
const records=[],queue=[];
for(const name of names){const source=await readFile('scripts/'+name,'utf8');queue.push({name,source,ports:[...source.matchAll(/listen\((\d+)/g)].map(m=>m[1]).filter(p=>p!=='0')});}
const occupied=new Set(),tasks=new Set();
async function run(task){
 const {name,source,ports}=task;for(const p of ports)occupied.add(p);
 const execution='scripts/.'+stage+'-run-'+name,log='artifacts/'+stage+'-'+name.replace(/\.mjs$/,'')+'-attempt-'+Date.now()+'.log';
 const rewrite=source.replace(/(['"`])((?:docs\/images|docs\/verification|artifacts)\/)([^'"`]+?)\1/g,(all,quote,folder,file)=>quote+folder+(file.startsWith(stage+'-')?file:stage+'-'+file.replace(/^s[0-9]+-/,''))+quote);
 await writeFile(execution,rewrite);const start=Date.now();console.log('Running '+name);
 const record={script:name,sourceSha256:createHash('sha256').update(source).digest('hex'),log,outputPathRewriteOnly:true};
 try{
  const stream=createWriteStream(log),child=spawn(process.execPath,[execution],{env:{...process.env,APERTURE_QA_STAGE:stage,APERTURE_QA_IMAGE_PREFIX:stage},stdio:['ignore','pipe','pipe']});child.stdout.pipe(stream,{end:false});child.stderr.pipe(stream,{end:false});
  record.exitCode=await new Promise((resolve,reject)=>{child.once('error',reject);child.once('close',resolve);});await new Promise(r=>stream.end(r));
 }catch(e){record.error=String(e);record.exitCode=-1;}finally{record.seconds=Math.round((Date.now()-start)/100)/10;await rm(execution,{force:true});for(const p of ports)occupied.delete(p);}
 records.push(record);const attempts=[...(previous.attempts||previous.scripts||[]),...records],latest=[...new Map(attempts.map(r=>[r.script,r])).values()];await writeFile('docs/verification/'+stage+'-browser-regression.json',JSON.stringify({environment:'Chromium, isolated temporary local services, synthetic fixtures',scripts:latest,attempts,pending:queue.map(t=>t.name),passed:queue.length===0&&records.length===names.length&&latest.every(r=>r.exitCode===0),realXiaomi:false,nativeMediaPlayerExecuted:false,stagePreservesHistoricalEvidence:true},null,2)+'\n');console.log(name+' exit='+record.exitCode+' '+record.seconds+'s '+log);
}
while(queue.length||tasks.size){
 while(tasks.size<2){const at=queue.findIndex(t=>!t.ports.some(p=>occupied.has(p)));if(at<0)break;const [task]=queue.splice(at,1);const promise=run(task).finally(()=>tasks.delete(promise));tasks.add(promise);}
 if(tasks.size)await Promise.race(tasks);
}
if(records.some(r=>r.exitCode!==0))process.exitCode=1;
