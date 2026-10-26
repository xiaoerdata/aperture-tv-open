import {spawn} from 'node:child_process';
import {randomUUID,createHash} from 'node:crypto';
import {localPeer,isPrivatePeer} from '../server/local-agent/network.mjs';
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm,writeFile,readFile,stat} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {createServer} from 'node:http';
import {createApp} from '../server/app.mjs';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';
async function fixture(t,localAutomation=true,trustedProxies=[]){
 const directory=await mkdtemp(join(tmpdir(),'aperture-agent-test-')),listener=createServer();await new Promise(r=>listener.listen(0,'127.0.0.1',r));const port=listener.address().port;await new Promise(r=>listener.close(r));const origin=`http://127.0.0.1:${port}`;
 const app=await createApp({directory,origin,ownerKey:'local-agent-owner-123456789012345',localAutomation,trustedProxies});await new Promise(r=>app.server.listen(port,'127.0.0.1',r));
 const request=async(path,body,headers={})=>{const r=await fetch(origin+path,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json',...headers},body:body===undefined?undefined:JSON.stringify(body)});return {status:r.status,data:await r.json(),cookie:r.headers.get('set-cookie')?.split(';')[0]};};
 const owner=(await request('/api/login',{key:'local-agent-owner-123456789012345'})).cookie;t.after(async()=>{await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});});
 return {directory,origin,app,request,owner,async pair(scopes=['home.read','scene.write','content.write']){const pair=(await request('/api/local-pair/start',{})).data;await request('/api/local-pair/approve',{code:pair.code,challenge:pair.challenge,scopes,hours:1},{cookie:owner});return (await request('/api/local-pair/poll',pair)).data;}};
}
test('local automation is opt-in; human session and old pair flow never imply machine permission',async t=>{
 const off=await fixture(t,false);assert.equal((await off.request('/api/local-pair/start',{})).status,503);
 const f=await fixture(t);assert.equal((await f.request('/api/local-agent/tools',undefined,{cookie:f.owner})).status,401);assert.equal((await f.request('/api/pair/start',{role:'controller'})).status,400);
 const pair=(await f.request('/api/local-pair/start',{})).data;assert.equal((await f.request('/api/pair/approve',{code:pair.code},{cookie:f.owner})).status,403);assert.equal((await f.request('/api/pair/poll',pair)).status,403);
 assert.equal((await f.request('/api/local-pair/approve',{code:pair.code,challenge:pair.challenge,scopes:['home.read'],hours:1},{cookie:f.owner})).status,200);const token=(await f.request('/api/local-pair/poll',pair)).data.token;assert.equal((await f.request('/api/board',undefined,{cookie:'aperture='+token})).status,401);assert.equal((await f.request('/api/agent/tools')).status,503);
});
test('read-only grants filter discovery and block writes; expiration and revocation are per call',async t=>{
 const f=await fixture(t),pair=await f.pair(['home.read']),headers={Authorization:'Bearer '+pair.token};const list=await f.request('/api/local-agent/tools',undefined,headers);assert.deepEqual(list.data.tools.map(t=>t.name),['aperture_home','aperture_receipt']);
 assert.equal((await f.request('/api/local-agent/call',{name:'aperture_scene',arguments:{scene:'gallery',motion:false,headline:'晚安',execution:{key:'denied-write',revision:0}}},headers)).status,403);
 const id=Object.entries(f.app.store.read().sessions).find(([,s])=>s.role==='controller')[0];await f.app.store.transact(s=>s.sessions[id].expiresAt=0);assert.equal((await f.request('/api/local-agent/tools',undefined,headers)).status,401);
 const other=await f.pair();const otherId=Object.entries(f.app.store.read().sessions).find(([,s])=>s.role==='controller')[0];await f.request('/api/devices/revoke',{id:otherId},{cookie:f.owner});assert.equal((await f.request('/api/local-agent/call',{name:'aperture_home',arguments:{}},{Authorization:'Bearer '+other.token})).status,401);
});
test('real stdio MCP bridge shares domain writes, persistent receipts, fresh scopes, and no arbitrary execution',async t=>{
 const f=await fixture(t),pair=await f.pair(),config=join(f.directory,'connection.json');await writeFile(config,JSON.stringify({origin:f.origin,token:pair.token}),{mode:0o600});
 const client=new Client({name:'test-workbuddy-compatible-client',version:'1'},{capabilities:{}});await client.connect(new StdioClientTransport({command:process.execPath,args:[resolve('scripts/workbuddy-mcp.mjs'),'--config',config],stderr:'ignore'}));t.after(()=>client.close());
 assert.equal((await client.listTools()).tools.length,4);const first=(await client.callTool({name:'aperture_home',arguments:{}})).structuredContent;assert.equal(first.revision,0);
 const args={scene:'gallery',motion:false,headline:'欢迎回家，今晚慢一点。',execution:{key:'welcome-home-001',revision:0}};const result=(await client.callTool({name:'aperture_scene',arguments:args})).structuredContent;assert.equal(result.outcome,'applied');assert.equal(f.app.service.read().settings.headline,args.headline);assert.equal((await client.callTool({name:'aperture_scene',arguments:args})).structuredContent.replayed,true);
 const receipt=(await client.callTool({name:'aperture_receipt',arguments:{key:'welcome-home-001'}})).structuredContent;assert.equal(receipt.outcome,'applied');assert.deepEqual(receipt.displayAcknowledgements,[]);
 const stale=await client.callTool({name:'aperture_scene',arguments:{...args,execution:{key:'stale-command',revision:0}}});assert.equal(stale.isError,true);assert.match(stale.content[0].text,/revision_conflict/);
 const invalid=await client.callTool({name:'execute_shell',arguments:{command:'anything'}});assert.equal(invalid.isError,true);
 const id=Object.entries(f.app.store.read().sessions).find(([,s])=>s.role==='controller')[0];await f.app.store.transact(s=>s.sessions[id].scopes=['home.read']);assert.equal((await client.listTools()).tools.length,2);assert.equal((await client.callTool({name:'aperture_scene',arguments:args})).isError,true);
 assert.equal(f.app.store.read().audit[0].actor,'controller');
});

test('private peer policy rejects public and spoofed forwarding; QR challenge and network bind grants',async t=>{
 for(const ip of ['127.0.0.1','10.1.2.3','172.16.0.2','192.168.1.3','::1','fd12::4','::ffff:192.168.1.2'])assert.equal(isPrivatePeer(ip),true,ip);
 for(const ip of ['8.8.8.8','172.32.1.2','192.169.1.1','2001:4860::1'])assert.equal(isPrivatePeer(ip),false,ip);
 const req=(peer,headers={})=>({socket:{remoteAddress:peer},headers});
 assert.throws(()=>localPeer(req('8.8.8.8')));assert.throws(()=>localPeer(req('127.0.0.1',{'x-forwarded-for':'10.0.0.1'})));
 assert.throws(()=>localPeer(req('127.0.0.1',{'x-forwarded-for':'10.0.0.1, 10.0.0.2'}),['127.0.0.1']));
 assert.throws(()=>localPeer(req('127.0.0.1',{forwarded:'for=10.0.0.1'}),['127.0.0.1']));
 const f=await fixture(t,true,['127.0.0.1']),net={'x-forwarded-for':'192.168.1.20'};
 assert.equal((await f.request('/api/local-pair/start',{}, {'x-forwarded-for':'8.8.8.8'})).status,403);
 const pair=(await f.request('/api/local-pair/start',{},net)).data;
 assert.ok(pair.approvalUrl.includes(pair.challenge));assert.ok(!pair.approvalUrl.includes(pair.secret));
 assert.equal((await f.request('/api/local-pair/approve',{code:pair.code,scopes:['home.read'],hours:1},{cookie:f.owner})).status,403);
 assert.equal((await f.request('/api/local-pair/approve',{code:pair.code,challenge:pair.challenge,scopes:['home.read'],hours:1},{cookie:f.owner})).status,200);
 assert.equal((await f.request('/api/local-pair/poll',pair,{'x-forwarded-for':'192.168.1.21'})).status,403);
 const grant=(await f.request('/api/local-pair/poll',pair,net)).data;
 assert.equal((await f.request('/api/local-agent/tools',undefined,{...net,Authorization:'Bearer '+grant.token})).status,200);
 assert.equal((await f.request('/api/local-agent/tools',undefined,{'x-forwarded-for':'192.168.1.21',Authorization:'Bearer '+grant.token})).status,403);
});
test('QR pairing CLI claims credential automatically, writes private file, never overwrites it',async t=>{
 const f=await fixture(t),config=join(f.directory,'cli.json'),qr=join(f.directory,'request.png');
 const child=spawn(process.execPath,['scripts/workbuddy-pair.mjs','--origin',f.origin,'--output',config,'--qr',qr],{stdio:['ignore','pipe','pipe']});let output='',stderr='';child.stdout.on('data',b=>output+=b);child.stderr.on('data',b=>stderr+=b);t.after(()=>child.kill());
 const done=new Promise((resolve,reject)=>{child.on('error',reject);child.on('exit',code=>resolve(code));});
 for(let i=0;i<100&&!output.includes('/studio?pair=');i++)await new Promise(r=>setTimeout(r,50));
 const link=new URL(output.match(/http:\/\/[^\s]+\/studio\?[^\s]+/)[0]);
 assert.equal((await f.request('/api/local-pair/approve',{code:link.searchParams.get('pair'),challenge:link.searchParams.get('challenge'),scopes:['home.read'],hours:1},{cookie:f.owner})).status,200);
 assert.equal(await done,0,stderr);const saved=JSON.parse(await readFile(config,'utf8'));assert.equal((await stat(config)).mode&0o077,0);assert.ok((await stat(qr)).size>1000);assert.ok(!output.includes(saved.token));assert.equal((await f.request('/api/local-agent/tools',undefined,{Authorization:'Bearer '+saved.token})).status,200);
 const again=spawn(process.execPath,['scripts/workbuddy-pair.mjs','--origin',f.origin,'--output',config],{stdio:'ignore'});assert.notEqual(await new Promise(r=>again.on('exit',r)),0);assert.equal(JSON.parse(await readFile(config,'utf8')).token,saved.token);
});


test('resource pack stdio MCP uses explicit grant, one TV window and exact save receipt',async t=>{
 const f=await fixture(t),pair=await f.pair(['home.read','resources.configure']),config=join(f.directory,'resources-connection.json');await writeFile(config,JSON.stringify({origin:f.origin,token:pair.token}),{mode:0o600});
 const client=new Client({name:'resource-pack-protocol-fixture',version:'1'},{capabilities:{}});await client.connect(new StdioClientTransport({command:process.execPath,args:[resolve('scripts/workbuddy-mcp.mjs'),'--config',config],stderr:'ignore'}));t.after(()=>client.close());
 const names=(await client.listTools()).tools.map(t=>t.name);assert.ok(names.includes('aperture_resources_send'));assert.ok(!names.includes('aperture_install_send'));
 const id=randomUUID(),secret='synthetic-resource-window',secretHash=createHash('sha256').update(secret).digest('hex');const hello={id,secretHash,label:'开视 · 爸妈版',kind:'resources',targets:[{id:'kaishi.resource-pack.v1',name:'开视资源包 v1'}]};assert.equal((await f.request('/api/app-operations/start',hello,{cookie:f.owner})).status,200);
 const requests=(await client.callTool({name:'aperture_resources_requests',arguments:{}})).structuredContent.requests;assert.equal(requests[0].fingerprint,secretHash);
 const bytes=await readFile('examples/resource-packs/family.kaishi.json'),sha256=createHash('sha256').update(bytes).digest('hex');const args={id,command:{url:'https://example.invalid/family.kaishi.json',sha256},execution:{key:'resource-mcp-001',revision:0}};
 assert.equal((await client.callTool({name:'aperture_resources_send',arguments:args})).structuredContent.state,'delivered');assert.equal((await client.callTool({name:'aperture_resources_send',arguments:args})).structuredContent.replayed,true);
 const identity={id,secret};assert.equal((await f.request('/api/app-operations/poll',identity,{cookie:f.owner})).data.command.sha256,sha256);for(const [state,revision]of [['staging',1],['awaiting_system',2]])assert.equal((await f.request('/api/app-operations/stage',{...identity,state,revision},{cookie:f.owner})).status,200);
 const result={packId:'org.example.family',revision:1,sha256,changed:true};assert.equal((await f.request('/api/app-operations/finish',{...identity,state:'succeeded',revision:3,result},{cookie:f.owner})).status,200);
 const receipt=(await client.callTool({name:'aperture_resources_status',arguments:{id,key:'resource-mcp-001'}})).structuredContent.receipt;assert.deepEqual(receipt.result,result);assert.equal(receipt.outcome,'reported_by_tv');
});

test('simplified family receipt does not claim legacy scene was displayed',async t=>{
 const f=await fixture(t),pair=await f.pair(['home.read']),headers={Authorization:'Bearer '+pair.token};
 const pending=(await f.request('/api/pair/start',{role:'viewer'})).data;await f.request('/api/pair/approve',{code:pending.code},{cookie:f.owner});const viewer=(await f.request('/api/pair/poll',pending)).cookie;
 assert.equal((await f.request('/api/display/ack',{revision:0,scene:'gallery',rendering:'lite',presentation:'family-v2'},{cookie:viewer})).status,200);
 const result=(await f.request('/api/local-agent/call',{name:'aperture_home',arguments:{}},headers)).data;
 const ack=result.displayAcknowledgements[0];assert.equal(ack.presentation,'family-v2');assert.equal(ack.sceneApplied,false);assert.deepEqual(ack.sections,['photos','events','notes']);
 assert.equal((await f.request('/api/display/ack',{revision:0,scene:'gallery',rendering:'lite',presentation:'family-v2'})).status,401);
});
