import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm,readFile,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {createHash} from 'node:crypto';
import {nasTestJson} from '../scripts/nas-test-json.mjs';
import {checkPack} from '../scripts/resource-pack.mjs';
const run=promisify(execFile),MAX=4*1024*1024;
const pack=resources=>({format:'kaishi.resource-pack',version:1,id:'org.example.capacity',revision:1,title:'容量测试',resources});
const catalog=i=>({id:'catalog'+i,type:'catalog',title:'来源'+i,protocol:'maccms-json',url:'https://example.invalid/api/'+i,sections:['movies']});
const live=i=>({id:'live'+i,type:'live',title:'直播'+i,url:'https://example.invalid/live/'+i+'.m3u'});
const episode=i=>({title:'第'+i+'集',url:'https://example.invalid/'+i+'.mp4'});
const collection=(items,episodes)=>({id:'collection',type:'collection',title:'片单',section:'movies',items:Array.from({length:items},(_,i)=>({id:'item'+i,title:'影片'+i,episodes:Array.from({length:episodes},(_,j)=>episode(j))}))});

test('byte-budget import retains sites, lives, warehouses, channels, items and episodes end to end',{timeout:120000},async()=>{
 const dir=await mkdtemp(join(tmpdir(),'kaishi-capacity-'));
 try{
  const fixtures=[];
  async function add(name,value,expected,{standard=false}={}){
   const bytes=Buffer.from(typeof value==='string'?value:JSON.stringify(value));
   const report=standard?checkPack(bytes):null;
   if(report)assert.equal(report.minimumVersionCode,expected.minimumVersionCode||47);
   await writeFile(join(dir,name),bytes);
   fixtures.push({name,bytes:bytes.length,...expected,authoringChecked:standard,report});
  }
  await add('threefold.json',pack([...Array.from({length:36},(_,i)=>catalog(i)),...Array.from({length:36},(_,i)=>live(i))]),{sites:36,lives:36},{standard:true});
  await add('catalogs.json',pack(Array.from({length:240},(_,i)=>catalog(i))),{sites:240},{standard:true});
  const tvbox={sites:Array.from({length:240},(_,i)=>({key:'site'+i,name:'站点'+i,type:1,api:'https://example.invalid/api/'+i})),lives:Array.from({length:144},(_,i)=>({name:'直播'+i,url:'https://example.invalid/live/'+i+'.m3u'}))};
  await add('tvbox.json',tvbox,{sites:240,lives:144});
  await add('adapter.json',pack([{id:'tvbox',type:'tvbox',title:'TVBox',config:tvbox}]),{sites:240,lives:144},{standard:true});
  await add('warehouses.json',{urls:Array.from({length:193},(_,i)=>({name:'仓库'+i,url:'https://example.invalid/config/'+i}))},{warehouses:193});
  const channels=Array.from({length:6001},(_,i)=>({id:'channel'+i,title:'频道'+i,urls:['https://example.invalid/'+i+'.m3u8']}));
  await add('channels.json',pack([{id:'live',type:'live',title:'频道库',channels}]),{lives:1,channels:6001},{standard:true});
  await add('legacy-channels.json',{lives:[{name:'直播',channels:channels.map(c=>({name:c.title,urls:c.urls}))}]},{lives:1,channels:6001});
  await add('channels.m3u','#EXTM3U\n'+channels.map(c=>'#EXTINF:-1,'+c.title+'\n'+c.urls[0]).join('\n'),{lives:1,channels:6001});
  await add('channels.txt',channels.map(c=>c.title+','+c.urls[0]).join('\n'),{lives:1,channels:6001});
  await add('items.json',pack([collection(6001,2)]),{sites:1,items:6001},{standard:true});
  await add('episodes.json',pack([collection(1,30001)]),{sites:1,items:1,episodes:30001},{standard:true});
  const small=pack([catalog(0)]),smallBytes=Buffer.from(JSON.stringify(small));
  assert.equal(checkPack(smallBytes).minimumVersionCode,21);
  const exact=Buffer.concat([smallBytes,Buffer.alloc(MAX-smallBytes.length,32)]);
  await add('exact-limit.json',exact.toString('utf8'),{sites:1,minimumVersionCode:31},{standard:true});
  const tooBig=Buffer.concat([exact,Buffer.from(' ')]);
  assert.throws(()=>checkPack(tooBig),/超过4MiB/);
  await add('too-big.json',tooBig.toString('utf8'),{reject:true,error:'超过4MiB'});
  // One shared plugin location expands into each normalized site. Reject before preview/save.
  await add('expanded-too-big.json',{spider:'https://example.invalid/'+ 'x'.repeat(3970),sites:Array.from({length:1200},(_,i)=>({name:'站点'+i,type:1,api:'https://example.invalid/'+i}))},{reject:true,error:'展开后的配置超过4MiB'});
  const invalid=pack([catalog(0),catalog(0)]);assert.throws(()=>checkPack(Buffer.from(JSON.stringify(invalid))),/重复/);
  const unsafe=pack([catalog(0)]);unsafe.resources[0].url='javascript:alert(1)';assert.throws(()=>checkPack(Buffer.from(JSON.stringify(unsafe))));
  await writeFile(join(dir,'cases.json'),JSON.stringify(fixtures));
  const cp=await nasTestJson(),jdk=process.env.JAVA_HOME||resolve('.android-local/jdk17');
  const sources=['CinemaCatalog','CinemaDates','CinemaFormats','CinemaResourcePack','CinemaCipher'].map(n=>'android/app/src/main/java/org/zhixiaoce/aperture/'+n+'.java');
  const javaTest='tests/java/ResourceCapacityTest.java';
  await run(join(jdk,'bin/javac'),['-encoding','UTF-8','-cp',cp,'-d',dir,...sources,javaTest]);
  const {stdout}=await run(join(jdk,'bin/java'),['-Xmx256m','-cp',dir+':'+cp,'org.zhixiaoce.aperture.ResourceCapacityTest',dir],{timeout:90000});
  assert.match(stdout,/14 byte-budget fixtures/);console.log(stdout.trim());
  const inputs=[...sources,javaTest,'tests/resource-capacity.test.mjs','scripts/resource-pack.mjs','docs/contracts/kaishi-resource-pack-v1.schema.json'];
  const hashes=Object.fromEntries(await Promise.all(inputs.map(async p=>[p,createHash('sha256').update(await readFile(p)).digest('hex')])));
  await writeFile('docs/verification/s121-resource-capacity.json',JSON.stringify({passed:true,maxInputBytes:MAX,maxNormalizedBytes:MAX,sourceCountQuotas:false,fixtures:fixtures.map(({report,...f})=>f),productionJavaExecuted:true,encryptedPersistenceRoundTrip:true,javaHeapBudgetMiB:256,output:stdout.trim(),sources:hashes,apkBuilt:false,published:false,realXiaomi:false,remoteProvidersTested:false},null,2)+'\n');
 }finally{await rm(dir,{recursive:true,force:true});}
});
