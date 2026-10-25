import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,writeFile,mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {checkPack} from '../scripts/resource-pack.mjs';
import {createServer} from 'node:http';
import {nasClasspath} from '../scripts/nas-classpath.mjs';
import {nasTestJson} from '../scripts/nas-test-json.mjs';
const run=promisify(execFile),example='examples/resource-packs/family.kaishi.json';
test('resource schema and authoring checks cover mixed resources, ids, classifications and unsafe inputs',async()=>{
 const bytes=await readFile(example),sample=JSON.parse(bytes);const result=checkPack(bytes);assert.equal(result.resources,5);assert.deepEqual(result.sections,['movies','series','variety']);
 const altered=fn=>{const copy=structuredClone(sample);fn(copy);return Buffer.from(JSON.stringify(copy));};
 for(const change of [p=>p.version=2,p=>p.typo=true,p=>p.resources[1].id='live',p=>p.resources[0].headers={Authorization:'private'},p=>p.resources[3].items[0].episodes[1].title='第1期',p=>p.resources[4].categoryIds.variety=['3','3'],p=>p.resources[4].url='https://user:pass@example.invalid/a',p=>p.resources[4].url='javascript:alert(1)',p=>p.resources[4].sections=['movies']])assert.throws(()=>checkPack(altered(change)));
 const http=altered(p=>p.resources[4].url='http://192.168.1.10/api');assert.throws(()=>checkPack(http));assert.equal(checkPack(http,{allowHttp:true}).sites,4);assert.throws(()=>checkPack(Buffer.alloc(524289)));
 const directory=await mkdtemp(join(tmpdir(),'kaishi-pack-cli-'));try{const output=join(directory,'template.json');await run(process.execPath,['scripts/resource-pack.mjs','template','--output',output]);assert.equal(checkPack(await readFile(output)).id,sample.id);await assert.rejects(run(process.execPath,['scripts/resource-pack.mjs','template','--output',output]));}finally{await rm(directory,{recursive:true,force:true});}
});
test('production Java imports, persists, routes and updates Resource Pack v1',{timeout:180000},async()=>{
 const directory=await mkdtemp(join(tmpdir(),'kaishi-resources-java-')),jdk=process.env.JAVA_HOME||'/tmp/aperture-android-runtime/jdk17';try{
  const cp=(await nasClasspath())+':'+await nasTestJson(),src='android/app/src/main/java/org/zhixiaoce/aperture/';await run(join(jdk,'bin/javac'),['-cp',cp,'-d',directory,...['CinemaCatalog','CinemaFormats','CinemaResourcePack','CinemaHttp','CinemaShelf'].map(n=>src+n+'.java'),'tests/java/ResourcePackTest.java']);
  const {stdout}=await run(join(jdk,'bin/java'),['-cp',directory+':'+cp,'org.zhixiaoce.aperture.ResourcePackTest',example,'examples/resource-packs/tvbox.kaishi.json']);assert.match(stdout,/assertions passed/);console.log(stdout.trim());
  await writeFile('docs/verification/s51-resource-pack.json',JSON.stringify({productionJavaParser:true,schemaAndCliChecked:true,sourceFile:example,example:checkPack(await readFile(example)),output:stdout.trim(),apkBuilt:false,androidActivityExecuted:false,workBuddyClientTested:false,xiaomiTested:false},null,2)+'\n');
 }finally{await rm(directory,{recursive:true,force:true});}
});


test('Agent can verify a published file and reject byte changes or oversized downloads',async()=>{
 const bytes=await readFile(example),expected=checkPack(bytes).sha256;const server=createServer((req,res)=>{if(req.url==='/large'){res.setHeader('Content-Length',524289);res.end(Buffer.alloc(524289));}else{res.setHeader('Content-Type','application/json');res.end(bytes);}});await new Promise(r=>server.listen(0,'127.0.0.1',r));const origin='http://127.0.0.1:'+server.address().port;
 try{const result=await run(process.execPath,['scripts/resource-pack.mjs','verify-url',origin+'/pack','--allow-http','--sha256',expected]);assert.equal(JSON.parse(result.stdout).sha256,expected);await assert.rejects(run(process.execPath,['scripts/resource-pack.mjs','verify-url',origin+'/pack','--allow-http','--sha256','0'.repeat(64)]));await assert.rejects(run(process.execPath,['scripts/resource-pack.mjs','verify-url',origin+'/large','--allow-http','--sha256',expected]));}finally{await new Promise(r=>server.close(r));}
});
