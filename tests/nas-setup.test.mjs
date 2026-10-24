import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHash,generateKeyPairSync,randomUUID,webcrypto} from 'node:crypto';
import {mkdtemp,rm,mkdir,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';import {join,resolve} from 'node:path';
import {spawn,execFile} from 'node:child_process';import {promisify} from 'node:util';
import {createInterface} from 'node:readline';
import {nasTestJson} from '../scripts/nas-test-json.mjs';
import {createNasSetup} from '../server/nas-setup/service.mjs';
import {sealNas} from '../public/nas-setup/seal.mjs';
const digest=value=>createHash('sha256').update(value).digest('hex');
function fixture(){
  let clock=1000;const sessions={tv:{role:'viewer',expiresAt:10000000},other:{role:'viewer',expiresAt:10000000},owner:{role:'owner',expiresAt:10000000},editor:{role:'editor',expiresAt:10000000},agent:{role:'controller',scopes:['home.read','nas.configure'],expiresAt:10000000},reader:{role:'controller',scopes:['home.read'],expiresAt:10000000}};
  const store={read:()=>({sessions:structuredClone(sessions)})},service=createNasSetup({store,now:()=>clock});
  const actors=Object.fromEntries(Object.entries(sessions).map(([id,s])=>[id,{id,role:s.role}]));
  const {publicKey}=generateKeyPairSync('rsa',{modulusLength:2048});const encoded=publicKey.export({format:'der',type:'spki'}).toString('base64url');
  const identity={id:randomUUID(),secret:'synthetic-ephemeral-secret-12345678901234567890'};
  const hello={id:identity.id,secretHash:digest(identity.secret),publicKey:encoded,label:'开窗 · 标准版'};
  const request=service.start(actors.tv,hello);
  return {service,sessions,actors,identity,hello,request,setTime:value=>clock=value};
}
const draft={name:'周末电影 · 合成',brand:'Xiaomi 智能存储 · 新款 NAS',root:'smb://192.168.1.20/Family/电影',user:'synthetic-nas-user',password:'synthetic-only-password',domain:'',role:'movies'};
test('relay binds TV, expiry, actor scopes, revocation, delivery receipt and ciphertext identity',async()=>{
  const f=fixture(),s=f.service,a=f.actors;
  assert.throws(()=>s.list(a.reader),e=>e.code==='scope_denied');assert.throws(()=>s.list(a.editor));
  assert.throws(()=>s.start(a.agent,f.hello));assert.throws(()=>s.poll(a.other,f.identity));assert.throws(()=>s.poll(a.tv,{...f.identity,secret:'wrong'}));
  const sealed=await sealNas(f.request,draft,webcrypto),command={id:f.identity.id,envelope:sealed,execution:{key:'synthetic-delivery-001',revision:0}};
  assert.throws(()=>s.send(a.owner,{...command,envelope:draft}));
  assert.throws(()=>s.send(a.owner,{...command,password:'must-not-be-accepted'}));
  assert.equal(s.send(a.agent,command).outcome,'awaiting_tv');assert.equal(s.send(a.agent,command).replayed,true);
  const changed=await sealNas(f.request,draft,webcrypto);assert.throws(()=>s.send(a.agent,{...command,envelope:changed}),e=>e.code==='idempotency_conflict');
  assert.throws(()=>s.send(a.owner,{...command,execution:{...command.execution,key:'different-key'}}),e=>e.code==='revision_conflict');
  assert.equal(s.inspect(a.agent,f.identity.id,command.execution.key).receipt.outcome,'awaiting_tv');
  assert.equal(s.inspect(a.owner,f.identity.id,command.execution.key).receipt.outcome,'unknown');
  assert.ok(!JSON.stringify(s.list(a.owner)).includes(sealed.ciphertext));assert.ok(!JSON.stringify(s.list(a.owner)).includes(draft.password));
  assert.deepEqual(s.poll(a.tv,f.identity).envelope,sealed);
  assert.equal(s.finish(a.tv,{...f.identity,state:'saved',revision:1}).outcome,'saved_on_tv');
  assert.equal(s.finish(a.tv,{...f.identity,state:'saved',revision:1}).state,'saved');assert.ok(!s.poll(a.tv,f.identity).envelope);
  f.sessions.agent.scopes=['home.read'];assert.equal(s.inspect(a.owner,f.identity.id).state,'saved','revocation does not falsely undo a completed local save');
  f.setTime(f.request.expiresAt);assert.throws(()=>s.poll(a.tv,f.identity),e=>e.code==='setup_expired');
});
test('relay quota is bounded without cancelling an existing request on failed creation; expired capacity is reclaimed',()=>{
  const f=fixture();let last=f.request;
  for(let i=1;i<64;i++)last=f.service.start(f.actors.tv,{...f.hello,id:randomUUID()});
  assert.throws(()=>f.service.start(f.actors.tv,{...f.hello,id:randomUUID()}),e=>e.code==='quota');
  assert.equal(f.service.inspect(f.actors.owner,last.id).state,'waiting');
  f.setTime(last.expiresAt);assert.equal(f.service.start(f.actors.tv,{...f.hello,id:randomUUID()}).state,'waiting');
});
test('pending delivery is invalidated by TV revoke, writer revoke or scope reduction; restart loses pending requests',async()=>{
  for(const mutation of [f=>delete f.sessions.tv,f=>delete f.sessions.agent,f=>f.sessions.agent.scopes=['home.read']]){
    const f=fixture();f.service.send(f.actors.agent,{id:f.identity.id,envelope:await sealNas(f.request,draft,webcrypto),execution:{key:'revocation-check',revision:0}});
    mutation(f);assert.throws(()=>f.service.poll(f.actors.tv,f.identity));
  }
  const f=fixture();const second={...f.hello,id:randomUUID()};f.service.start(f.actors.tv,second);
  assert.equal(f.service.poll(f.actors.tv,f.identity).state,'cancelled');assert.equal(f.service.list(f.actors.owner).length,1);
  f.service.close();assert.throws(()=>f.service.poll(f.actors.tv,{...f.identity,id:second.id}),e=>e.code==='setup_expired');
});
test('real WebCrypto sealing interoperates with production Java receiver and rejects tamper, wrong TV, invalid draft and disposed key',async t=>{
  const directory=await mkdtemp(join(tmpdir(),'aperture-setup-'));t.after(()=>rm(directory,{recursive:true,force:true}));
  const json=await nasTestJson(),jdk=process.env.JAVA_HOME||'/tmp/aperture-android-runtime/jdk-unpack/jdk-17.0.21+9',source=resolve('android/app/src/main/java/org/zhixiaoce/aperture');
  const {nasClasspath}=await import('../scripts/nas-classpath.mjs');const cp=json+':'+await nasClasspath();
  await promisify(execFile)(join(jdk,'bin/javac'),['-cp',cp,'-d',directory,...['HouseholdCommandClient','NasSetupCrypto','NasSetupClient','NasSetupImport','NasDraft','NasLocation','NasLibrary','NasCipher','NasReader','NasSocketFactory','SmbNasReader','DavNasReader','LobbyAssets'].map(n=>join(source,n+'.java')),resolve('tests/java/NasSetupHarness.java')]);
  const receiver=spawn(join(jdk,'bin/java'),['-cp',directory+':'+cp,'org.zhixiaoce.aperture.NasSetupHarness'],{stdio:['pipe','pipe','pipe']});t.after(()=>receiver.kill());receiver.stderr.resume();
  const lines=createInterface({input:receiver.stdout})[Symbol.asyncIterator]();const next=async()=>JSON.parse((await lines.next()).value);
  const first=await next(),request={...first.hello,fingerprint:first.fingerprint};
  const call=async command=>{receiver.stdin.write(JSON.stringify(command)+'\n');return next();};
  const sealed=await sealNas(request,draft,webcrypto);
  assert.deepEqual(await call({envelope:sealed}),{valid:true,role:'movies',brand:draft.brand});
  const cipher=Buffer.from(sealed.ciphertext,'base64url');cipher[0]^=1;assert.equal((await call({envelope:{...sealed,ciphertext:cipher.toString('base64url')}})).valid,false);
  const wrong=await sealNas({...request,id:randomUUID()},draft,webcrypto);assert.equal((await call({envelope:wrong})).valid,false);
  for(const invalid of [{...draft,root:'https://nas.example/Family/'},{...draft,id:randomUUID()},{...draft,home:{movies:'private'}},{...draft,role:'shell'},{...draft,root:'smb://user:password@nas/Family'}])assert.equal((await call({envelope:await sealNas(request,invalid,webcrypto)})).valid,false);
  assert.equal((await call({close:true})).closed,true);assert.equal((await call({envelope:sealed})).valid,false);
  await assert.rejects(sealNas({...request,fingerprint:'0'.repeat(64)},draft,webcrypto));
  receiver.stdin.end();
  await mkdir('artifacts',{recursive:true});await writeFile('artifacts/nas-setup-crypto-report.json',JSON.stringify({productionJavaReceiver:true,realWebCrypto:true,rsaOaepSha256Mgf1Sha256:true,aes256GcmRequestBound:true,validDraftAccepted:true,tamperedWrongTvOrDisposedKeyRejected:true,unknownFieldsAndXiaomiDavRejected:true,androidKeyStore:false,androidActivity:false,realDevice:false},null,2)+'\n');
});
