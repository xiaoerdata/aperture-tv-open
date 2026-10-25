import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID,createHash} from 'node:crypto';
import {createAppOperations} from '../server/app-operations/service.mjs';
import {createLocalAgent} from '../server/local-agent/service.mjs';
function fixture(){
 const sessions={tv:{role:'viewer',expiresAt:Date.now()+3600000},agent:{role:'controller',scopes:['home.read','resources.configure'],expiresAt:Date.now()+3600000},old:{role:'controller',scopes:['home.read','apps.install','nas.configure'],expiresAt:Date.now()+3600000}};
 const actors=Object.fromEntries(Object.entries(sessions).map(([id,s])=>[id,{id,role:s.role}])),store={read:()=>({sessions})},service=createAppOperations({store}),agent=createLocalAgent({store,appOperations:service,enabled:true});const id=randomUUID(),secret='resource-grant-secret',hello={id,secretHash:createHash('sha256').update(secret).digest('hex'),label:'开视 · 标准版',kind:'resources',targets:[{id:'kaishi.resource-pack.v1',name:'开视资源包 v1'}]};service.start(actors.tv,hello);
 return {sessions,actors,service,agent,id,secret,command:{url:'https://example.invalid/family.kaishi.json',sha256:'a'.repeat(64)}};
}
const send=(f,command=f.command)=>f.agent.call(f.actors.agent,'aperture_resources_send',{id:f.id,command,execution:{key:'pack-update-001',revision:0}});
test('resource authoring automation has an independent explicit scope',async()=>{
 const f=fixture();assert.equal(f.agent.discovery(f.actors.old).tools.some(t=>t.name.startsWith('aperture_resources_')),false);await assert.rejects(f.agent.call(f.actors.old,'aperture_resources_requests',{}),e=>e.code==='scope_denied');assert.equal(f.agent.discovery(f.actors.agent).tools.filter(t=>t.name.startsWith('aperture_resources_')).length,3);assert.equal((await f.agent.call(f.actors.agent,'aperture_resources_requests',{})).requests.length,1);f.service.close();
});
test('resource delivery is idempotent and only a bound native save receipt means success',async()=>{
 const f=fixture();assert.equal((await send(f)).state,'delivered');assert.equal((await send(f)).replayed,true);await assert.rejects(send(f,{...f.command,sha256:'b'.repeat(64)}),e=>e.code==='idempotency_conflict');
 const device={id:f.id,secret:f.secret};assert.throws(()=>f.service.finish(f.actors.tv,{...device,state:'succeeded',revision:1}),e=>e.code==='revision_conflict');
 f.service.stage(f.actors.tv,{...device,state:'staging',revision:1});assert.equal(f.service.stage(f.actors.tv,{...device,state:'awaiting_system',revision:2}).outcome,'awaiting_tv_save');
 const result={packId:'org.example.family',revision:2,sha256:f.command.sha256,changed:true};assert.throws(()=>f.service.finish(f.actors.tv,{...device,state:'succeeded',revision:3,result:{...result,sha256:'b'.repeat(64)}}));
 f.service.finish(f.actors.tv,{...device,state:'succeeded',revision:3,result});const receipt=await f.agent.call(f.actors.agent,'aperture_resources_status',{id:f.id,key:'pack-update-001'});assert.equal(receipt.receipt.outcome,'reported_by_tv');assert.deepEqual(receipt.receipt.result,result);f.service.close();
});
test('revocation and URL policy block later resource operations',async()=>{
 for(const bad of [{url:'http://192.168.1.1/a'},{url:'https://u:p@example.invalid/a'},{url:'https://example.invalid/a#x'},{url:'file:///a'},{sha256:'bad'},{allowHttp:true}]){const f=fixture();await assert.rejects(send(f,{...f.command,...bad}));f.service.close();}
 const f=fixture();await send(f);f.sessions.agent.scopes=['home.read'];assert.throws(()=>f.service.poll(f.actors.tv,{id:f.id,secret:f.secret}),e=>e.code==='operation_revoked');await assert.rejects(f.agent.call(f.actors.agent,'aperture_resources_status',{id:f.id,key:'pack-update-001'}),e=>e.code==='scope_denied');f.service.close();
});
