import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createServer} from 'node:http';
import {StreamableHTTPServerTransport} from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import {createApp} from '../server/app.mjs';
import {validateSources} from '../server/life/config.mjs';
import {demoSource} from '../examples/life-source.mjs';
import {fixtureServer} from '../examples/life-fixture.mjs';
async function fixture(t,sources=[demoSource()]){
  const directory=await mkdtemp(join(tmpdir(),'aperture-life-test-')),ownerKey='life-test-owner-123456789012345';
  const listener=createServer();await new Promise(r=>listener.listen(0,'127.0.0.1',r));const port=listener.address().port;await new Promise(r=>listener.close(r));
  const origin=`http://127.0.0.1:${port}`,app=await createApp({directory,ownerKey,origin,sources});await new Promise(r=>app.server.listen(port,'127.0.0.1',r));
  const request=async(path,body,cookie='')=>{const res=await fetch(origin+path,{method:body===undefined?'GET':'POST',headers:{cookie,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});return {status:res.status,data:await res.json(),cookie:res.headers.get('set-cookie')?.split(';')[0]};};
  const owner=(await request('/api/login',{key:ownerKey})).cookie;
  t.after(async()=>{await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});});
  const preview=()=>request('/api/life/preview',{sourceId:sources[0].id,queryId:'today'},owner);
  return {app,owner,request,preview,directory,origin};
}
test('real stdio MCP: preview is private; selective sharing, idempotency, conflict, remove and persisted readback',async t=>{
  const f=await fixture(t);assert.equal((await f.request('/api/life/sources')).status,401);
  const list=await f.request('/api/life/sources',undefined,f.owner);assert.equal(list.status,200);assert.ok(!JSON.stringify(list).includes(process.execPath));
  const p=await f.preview();assert.equal(p.status,200);assert.equal(p.data.signals.length,3);assert.ok(!JSON.stringify(p).includes('私人日记'));assert.equal(f.app.service.read().life,undefined);
  const input={token:p.data.token,ids:[p.data.signals[0].id],execution:{key:'life-publish-001',revision:0}};
  assert.equal((await f.request('/api/life/publish',input,f.owner)).status,200);
  assert.equal((await f.request('/api/life/publish',input,f.owner)).data.replayed,true);
  assert.equal(f.app.service.read().life.length,1);assert.equal(f.app.service.read().life[0].value,'24.6');
  assert.equal((await f.request('/api/life/publish',{...input,execution:{key:'life-conflict-001',revision:0}},f.owner)).data.code,'revision_conflict');
  assert.equal((await f.request('/api/write',{operation:'life.publish',input:{signals:p.data.signals},execution:{key:'bypass-001',revision:1}},f.owner)).status,400);
  const restarted=await createApp({directory:f.directory,ownerKey:'life-test-owner-123456789012345',origin:f.origin});assert.equal(restarted.service.read().life.length,1);
  await f.request('/api/write',{operation:'life.remove',input:{id:input.ids[0]},execution:{key:'life-remove-001',revision:1}},f.owner);assert.equal(f.app.service.read().life.length,0);
});
test('viewer/editor cannot read private sources; preview bound to owner session, revocation checked',async t=>{
  const f=await fixture(t),p=(await f.preview()).data;
  for(const role of ['viewer','editor']){const pair=(await f.request('/api/pair/start',{role})).data;await f.request('/api/pair/approve',{code:pair.code},f.owner);const session=await f.request('/api/pair/poll',pair);assert.equal((await f.request('/api/life/sources',undefined,session.cookie)).status,403);assert.equal((await f.request('/api/life/preview',{sourceId:'local-demo',queryId:'today'},session.cookie)).status,403);}
  const owner2=(await f.request('/api/login',{key:'life-test-owner-123456789012345'})).cookie;
  assert.equal((await f.request('/api/life/publish',{token:p.token,ids:[p.signals[0].id],execution:{key:'other-owner',revision:0}},owner2)).status,410);
  await f.request('/api/logout',{},f.owner);assert.equal((await f.request('/api/life/preview',{},f.owner)).status,401);
});
test('fixed configured calls ignore client-supplied tool names and arguments; unsafe tools refused',async t=>{
  const f=await fixture(t);const p=await f.request('/api/life/preview',{sourceId:'local-demo',queryId:'today',tool:'set_power',arguments:{mode:'error'}},f.owner);assert.equal(p.status,200);
  assert.equal((await f.request('/api/life/preview',{sourceId:'unconfigured',queryId:'today'},f.owner)).status,404);
  const source=demoSource();source.readTools=['set_power'];source.queries[0].tool='set_power';const unsafe=await fixture(t,[source]);assert.equal((await unsafe.preview()).data.code,'unsafe_tool');
});
test('partial fields, stale data, invalid timestamps, upstream errors and limits never silently publish',async t=>{
  for(const mode of ['partial','stale','invalid-time','error','oversize','text']){
    const f=await fixture(t,[demoSource(mode)]),p=await f.preview();
    if(mode==='partial'){assert.equal(p.data.signals.length,2);assert.deepEqual(p.data.unavailable,['客厅温度']);}
    if(mode==='stale'){assert.equal(p.status,200);assert.equal((await f.request('/api/life/publish',{token:p.data.token,ids:[p.data.signals[0].id],execution:{key:'stale-share',revision:0}},f.owner)).data.code,'stale_source');}
    if(mode==='invalid-time')assert.equal(p.data.code,'mapping_required');
    if(mode==='error'){assert.equal(p.data.code,'upstream_error');assert.ok(!JSON.stringify(p).includes('synthetic-private-secret'));}
    if(mode==='oversize')assert.equal(p.data.code,'upstream_too_large');
    if(mode==='text')assert.equal(p.data.signals[0].value,'24.6');
    assert.equal(f.app.service.read().life,undefined);
  }
});
test('real Streamable HTTP MCP transport reads structured values',async t=>{
  const upstream=createServer(async(req,res)=>{const server=fixtureServer(),transport=new StreamableHTTPServerTransport({sessionIdGenerator:undefined});res.on('close',()=>{transport.close();server.close();});await server.connect(transport);await transport.handleRequest(req,res);});
  await new Promise(r=>upstream.listen(0,'127.0.0.1',r));t.after(()=>new Promise(r=>upstream.close(r)));
  const source={...demoSource(),transport:'http',url:`http://127.0.0.1:${upstream.address().port}/mcp`};delete source.command;delete source.args;
  const f=await fixture(t,[source]);const p=await f.preview();assert.equal(p.status,200);assert.equal(p.data.signals[2].value,'番茄牛腩');
});
test('configuration fails closed for unsafe paths, missing review and non-allowlisted queries',()=>{
  for(const change of [s=>s.readOnlyReviewed=false,s=>s.readTools=[],s=>s.command='npx',s=>s.queries[0].tiles[0].valuePath='__proto__.x',s=>s.queries[0].tiles[0].ttlSeconds=0]){const s=demoSource();change(s);assert.throws(()=>validateSources([s]));}
});
