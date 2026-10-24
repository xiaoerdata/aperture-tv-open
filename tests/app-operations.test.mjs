import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID,createHash} from 'node:crypto';
import {createAppOperations} from '../server/app-operations/service.mjs';
const hash=s=>createHash('sha256').update(s).digest('hex');
function fixture(label='开窗 · 标准版'){
 let now=100000;const sessions={tv:{role:'viewer',expiresAt:999999},tv2:{role:'viewer',expiresAt:999999},agent:{role:'controller',scopes:['home.read','apps.install','apps.uninstall'],expiresAt:999999},read:{role:'controller',scopes:['home.read'],expiresAt:999999},owner:{role:'owner',expiresAt:999999}};
 const actors=Object.fromEntries(Object.entries(sessions).map(([id,s])=>[id,{id,role:s.role}]));const service=createAppOperations({store:{read:()=>({sessions})},now:()=>now});
 const connectionId=randomUUID();
 function window(kind='install',actor=actors.tv){const secret='test-tv-secret-'+randomUUID();const hello={id:randomUUID(),secretHash:hash(secret),label,kind,targets:[{id:kind==='install'?connectionId:'org.example.player',name:'测试目标'}]};return {hello,identity:{id:hello.id,secret},request:service.start(actor,hello)};}
 const command={connectionId,path:'Apps/电视.APK',sha256:'a'.repeat(64),packageName:'org.example.player'};
 return {sessions,actors,service,window,command,advance:ms=>now+=ms};
}
const send=(f,w,command=f.command,key='install-test-key')=>f.service.send(f.actors.agent,w.hello.kind,{id:w.request.id,command,execution:{key,revision:0}});
test('separate scopes, TV identity, immutable targets and expiry are enforced',()=>{
 const f=fixture(),w=f.window();assert.throws(()=>f.service.list(f.actors.read,'install'),e=>e.code==='scope_denied');
 assert.throws(()=>f.service.start(f.actors.agent,w.hello),e=>e.code==='forbidden');
 assert.throws(()=>f.service.poll(f.actors.tv2,w.identity),e=>e.code==='device_mismatch');
 assert.throws(()=>f.service.poll(f.actors.tv,{...w.identity,secret:'wrong'}),e=>e.code==='device_mismatch');
 assert.throws(()=>f.service.start(f.actors.tv,{...w.hello,targets:[{id:randomUUID(),name:'another'}]}),e=>e.code==='window_conflict');
 f.advance(600001);assert.throws(()=>f.service.poll(f.actors.tv,w.identity),e=>e.code==='window_expired');
});
test('one request, canonical idempotency, revisions and truthful staged receipts',()=>{
 const f=fixture(),w=f.window();assert.equal(send(f,w).state,'delivered');
 const reversed=Object.fromEntries(Object.entries(f.command).reverse());assert.equal(send(f,w,reversed).replayed,true);
 assert.throws(()=>send(f,w,{...f.command,sha256:'b'.repeat(64)}),e=>e.code==='idempotency_conflict');
 assert.throws(()=>send(f,w,f.command,'different-key'),e=>e.code==='revision_conflict');
 assert.throws(()=>f.service.finish(f.actors.tv,{...w.identity,state:'succeeded',revision:1}),e=>e.code==='revision_conflict');
 assert.equal(f.service.stage(f.actors.tv,{...w.identity,state:'staging',revision:1}).revision,2);
 assert.equal(f.service.poll(f.actors.tv,w.identity).command,undefined);
 assert.equal(f.service.stage(f.actors.tv,{...w.identity,state:'staging',revision:1}).replayed,true);
 const waiting=f.service.stage(f.actors.tv,{...w.identity,state:'awaiting_system',revision:2});assert.equal(waiting.outcome,'awaiting_system_confirmation');
 const done=f.service.finish(f.actors.tv,{...w.identity,state:'succeeded',revision:3});assert.equal(done.outcome,'reported_by_tv');
 assert.equal(f.service.inspect(f.actors.agent,'install',w.request.id,'install-test-key').receipt.state,'succeeded');
 assert.equal(f.service.inspect(f.actors.agent,'install',w.request.id,'not-original-key').receipt.outcome,'unknown');
 assert.equal(send(f,w).replayed,true);
});
test('NAS traversal, unknown roots, own packages and extra command fields are rejected',()=>{
 for(const mutation of [{path:'../app.apk'},{path:'/app.apk'},{path:'apps/%2e%2e/app.apk'},{path:'app.xapk'},{path:'a\\app.apk'},{connectionId:randomUUID()},{packageName:'org.zhixiaoce.aperture'},{sha256:'invalid'},{url:'https://example.invalid/app.apk'}]){
  const f=fixture(),w=f.window();assert.throws(()=>send(f,w,{...f.command,...mutation}));
 }
});
test('uninstall cannot escape offered targets and install permission does not imply deletion',()=>{
 const f=fixture(),w=f.window('uninstall');f.sessions.agent.scopes=['home.read','apps.install'];assert.throws(()=>send(f,w,{packageName:'org.example.player'}),e=>e.code==='scope_denied');
 f.sessions.agent.scopes.push('apps.uninstall');assert.throws(()=>send(f,w,{packageName:'com.android.settings'}));assert.throws(()=>send(f,w,{packageName:'org.zhixiaoce.aperture'}));
 assert.equal(send(f,w,{packageName:'org.example.player'}).kind,'uninstall');
});
test('revocation blocks delivery and records uncertainty after OS handoff',()=>{
 for(const handed of [false,true]){
  const f=fixture(),w=f.window();send(f,w);
  if(handed){f.service.stage(f.actors.tv,{...w.identity,state:'staging',revision:1});f.service.stage(f.actors.tv,{...w.identity,state:'awaiting_system',revision:2});}
  f.sessions.agent.scopes=['home.read'];assert.throws(()=>f.service.poll(f.actors.tv,w.identity),e=>e.code==='operation_revoked');
  assert.equal(f.service.inspect(f.actors.owner,'install',w.request.id,'install-test-key').state,handed?'unknown':'cancelled');
 }
 const f=fixture(),w=f.window();send(f,w);delete f.sessions.tv;assert.throws(()=>f.service.poll(f.actors.tv,w.identity),e=>e.code==='unauthorized');
});
test('a new TV window cannot report an already handed-off operation cancelled; restart never retries',()=>{
 const f=fixture(),w=f.window();send(f,w);f.service.stage(f.actors.tv,{...w.identity,state:'staging',revision:1});f.service.stage(f.actors.tv,{...w.identity,state:'awaiting_system',revision:2});f.window();
 assert.equal(f.service.inspect(f.actors.agent,'install',w.request.id,'install-test-key').state,'unknown');
 f.service.close();assert.throws(()=>f.service.inspect(f.actors.agent,'install',w.request.id,'install-test-key'),e=>e.code==='window_expired');
});
test('bounded windows reject overflow without changing the latest active grant',()=>{
 const f=fixture();let last;for(let i=0;i<64;i++)last=f.window();assert.throws(()=>f.window(),e=>e.code==='quota');
 assert.equal(f.service.poll(f.actors.tv,last.identity).state,'waiting');f.advance(600001);assert.equal(f.window().request.state,'waiting');
});

test('renamed and legacy television editions keep the same application operation contract',()=>{
 for(const label of ['开窗 · 标准版','开窗 · 爸妈版','开视 · 标准版','开视 · 爸妈版']){
  const f=fixture(label),w=f.window();
  assert.equal(w.request.label,label.replace(/^开窗/,'开视'));
  assert.equal(send(f,w).state,'delivered');
  assert.equal(f.service.poll(f.actors.tv,w.identity).command.packageName,'org.example.player');
 }
});
