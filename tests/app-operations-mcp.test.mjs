import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID,createHash} from 'node:crypto';
import {mkdtemp,rm,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {createServer} from 'node:http';
import {createApp} from '../server/app.mjs';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';
test('real MCP bridge -> scoped service -> TV HTTP stages -> original-key receipt',async t=>{
 const directory=await mkdtemp(join(tmpdir(),'aperture-app-mcp-'));const listener=createServer();await new Promise(r=>listener.listen(0,'127.0.0.1',r));const port=listener.address().port;await new Promise(r=>listener.close(r));const origin='http://127.0.0.1:'+port;
 const ownerKey='test-app-operations-owner-1234567890';const app=await createApp({directory,origin,ownerKey,localAutomation:true});await new Promise(r=>app.server.listen(port,'127.0.0.1',r));
 const client=new Client({name:'app-management-test',version:'1.0.0'});
 t.after(async()=>{await client.close();await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});});
 async function request(path,body,cookie){const r=await fetch(origin+path,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json',...(cookie?{Cookie:cookie}:{})},body:body===undefined?undefined:JSON.stringify(body)});return {status:r.status,data:await r.json(),cookie:r.headers.get('set-cookie')?.split(';')[0]};}
 const owner=(await request('/api/login',{key:ownerKey})).cookie;
 const tvPair=(await request('/api/pair/start',{role:'viewer'})).data;assert.equal((await request('/api/pair/approve',{code:tvPair.code},owner)).status,200);const tv=(await request('/api/pair/poll',{code:tvPair.code,secret:tvPair.secret})).cookie;
 const pair=(await request('/api/local-pair/start',{})).data;await request('/api/local-pair/approve',{code:pair.code,challenge:pair.challenge,scopes:['home.read','apps.install','apps.uninstall'],hours:1},owner);const grant=(await request('/api/local-pair/poll',pair)).data;
 const config=join(directory,'connection.json');await writeFile(config,JSON.stringify({origin,token:grant.token}),{mode:0o600});await client.connect(new StdioClientTransport({command:process.execPath,args:[resolve('scripts/workbuddy-mcp.mjs'),'--config',config],stderr:'ignore'}));
 const tools=(await client.listTools()).tools;assert.equal(tools.filter(x=>/^aperture_(install|uninstall)_/.test(x.name)).length,6);assert.equal(tools.find(x=>x.name==='aperture_uninstall_send').annotations.destructiveHint,true);
 for(const kind of ['install','uninstall']){
  const id=randomUUID(),secret='synthetic-operation-secret-'+randomUUID(),target=kind==='install'?randomUUID():'org.example.player';
  const started=await request('/api/app-operations/start',{id,secretHash:createHash('sha256').update(secret).digest('hex'),kind,label:'开窗 · 标准版',targets:[{id:target,name:'合成目标'}]},tv);assert.equal(started.status,200);
  const list=await client.callTool({name:'aperture_'+kind+'_requests',arguments:{}});assert.equal(list.structuredContent.requests[0].id,id);
  const command=kind==='install'?{connectionId:target,path:'Apps/player.apk',sha256:'a'.repeat(64),packageName:'org.example.player'}:{packageName:target};const key=kind+'-test-original';
  const sent=await client.callTool({name:'aperture_'+kind+'_send',arguments:{id,command,execution:{key,revision:0}}});assert.equal(sent.structuredContent.state,'delivered');
  const polled=await request('/api/app-operations/poll',{id,secret},tv);assert.equal(polled.data.command.packageName,'org.example.player');
  assert.equal((await request('/api/app-operations/finish',{id,secret,state:'succeeded',revision:1},tv)).status,409);
  assert.equal((await request('/api/app-operations/stage',{id,secret,state:'staging',revision:1},tv)).data.revision,2);
  assert.equal((await request('/api/app-operations/stage',{id,secret,state:'awaiting_system',revision:2},tv)).data.outcome,'awaiting_system_confirmation');
  const waiting=await client.callTool({name:'aperture_'+kind+'_status',arguments:{id,key}});assert.equal(waiting.structuredContent.receipt.state,'awaiting_system');
  assert.equal((await request('/api/app-operations/finish',{id,secret,state:'succeeded',revision:3},tv)).status,200);
  const receipt=await client.callTool({name:'aperture_'+kind+'_status',arguments:{id,key}});assert.equal(receipt.structuredContent.receipt.outcome,'reported_by_tv');
 }
 const actor=Object.entries(app.store.read().sessions).find(([,s])=>s.role==='controller')[0];await request('/api/devices/revoke',{id:actor},owner);
 const revoked=await client.callTool({name:'aperture_install_requests',arguments:{}});assert.equal(revoked.isError,true);
 console.log('Real stdio MCP/HTTP lifecycle passed; TV receipts are synthetic, not Android or WorkBuddy application execution');
});
