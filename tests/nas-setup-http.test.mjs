import {test} from 'node:test';import assert from 'node:assert/strict';
import {mkdtemp,rm,mkdir,writeFile,readFile,stat} from 'node:fs/promises';import {existsSync} from 'node:fs';import {tmpdir,userInfo} from 'node:os';import {join,resolve} from 'node:path';
import {createServer,Socket} from 'node:net';import {spawn,execFile} from 'node:child_process';import {promisify} from 'node:util';
import {createApp} from '../server/app.mjs';import {nasSetupHarness} from '../scripts/nas-setup-harness.mjs';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';
async function fixture(t){
  const directory=await mkdtemp(join(tmpdir(),'aperture-setup-http-')),reservation=createServer();await new Promise(r=>reservation.listen(0,'127.0.0.1',r));const port=reservation.address().port;await new Promise(r=>reservation.close(r));
  const origin=`http://127.0.0.1:${port}`,key='synthetic-setup-owner-123456789012345',app=await createApp({directory,origin,ownerKey:key,localAutomation:true});await new Promise(r=>app.server.listen(port,'127.0.0.1',r));
  t.after(async()=>{app.server.closeAllConnections();await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});});
  const request=async(path,body,headers={})=>{const response=await fetch(origin+path,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json',...headers},body:body===undefined?undefined:JSON.stringify(body)});return {status:response.status,cookie:response.headers.get('set-cookie')?.split(';')[0],data:await response.json()};};
  const owner=(await request('/api/login',{key})).cookie;
  async function viewer(){const pair=(await request('/api/pair/start',{role:'viewer'})).data;await request('/api/pair/approve',{code:pair.code},{Cookie:owner});return (await request('/api/pair/poll',pair)).cookie;}
  async function controller(scopes){const pair=(await request('/api/local-pair/start',{})).data;await request('/api/local-pair/approve',{code:pair.code,challenge:pair.challenge,hours:1,scopes},{Cookie:owner});return (await request('/api/local-pair/poll',pair)).data.token;}
  return {directory,origin,app,request,owner,viewer,controller};
}
test('real native Java transport, local sealing CLI and stdio MCP deliver only ciphertext; scopes and receipts remain distinct',async t=>{
  const f=await fixture(t),h=await nasSetupHarness();t.after(()=>h.close());const cookie=await f.viewer();
  const request=await h.call({start:{origin:f.origin,cookie}});assert.equal(request.state,'waiting');assert.ok(Buffer.from(request.qr,'base64').length>1000);assert.ok(!request.approvalUrl.includes(h.hello.identity.secret));
  assert.equal((await f.request('/api/nas-setup/inspect',{id:request.id},{Cookie:cookie})).status,403);
  assert.equal((await f.request('/api/nas-setup/poll',h.hello.identity,{Cookie:await f.viewer()})).status,403);
  assert.equal((await f.request('/api/nas-setup/inspect',{id:request.id},{Cookie:f.owner,Origin:'https://other.invalid'})).status,403);
  const readonly=await f.controller(['home.read']);assert.equal((await f.request('/api/local-agent/call',{name:'aperture_nas_requests',arguments:{}},{Authorization:'Bearer '+readonly})).status,403);
  const token=await f.controller(['home.read','nas.configure']),config=join(f.directory,'agent.json');await writeFile(config,JSON.stringify({origin:f.origin,token}),{mode:0o600});
  const client=new Client({name:'synthetic-nas-client',version:'1'},{capabilities:{}});await client.connect(new StdioClientTransport({command:process.execPath,args:[resolve('scripts/workbuddy-mcp.mjs'),'--config',config],stderr:'ignore'}));t.after(()=>client.close());
  const tools=(await client.listTools()).tools;assert.equal(tools.length,5);assert.equal(tools.find(t=>t.name==='aperture_nas_status').annotations.readOnlyHint,true);
  const requests=(await client.callTool({name:'aperture_nas_requests',arguments:{}})).structuredContent.requests;assert.equal(requests.length,1);
  const requestFile=join(f.directory,'public.json'),input=join(f.directory,'private.json'),output=join(f.directory,'sealed.json');
  await writeFile(requestFile,JSON.stringify(request));const draft={name:'合成电影',brand:'其他 SMB / WebDAV',root:'smb://127.0.0.1:1/Family',user:'synthetic-nas-user',password:'synthetic-no-secret-123',domain:'',role:'movies'};await writeFile(input,JSON.stringify(draft),{mode:0o600});
  const {stdout}=await promisify(execFile)(process.execPath,['scripts/seal-nas-config.mjs','--request',requestFile,'--input',input,'--output',output]);assert.ok(!stdout.includes(draft.password));assert.equal((await stat(output)).mode&0o077,0);
  const command=JSON.parse(await readFile(output,'utf8'));assert.ok(!JSON.stringify(command).includes(draft.password));
  const broken=join(f.directory,'broken.json');await writeFile(broken,'{"password":"synthetic-do-not-log-malformed-secret",',{mode:0o600});
  await assert.rejects(promisify(execFile)(process.execPath,['scripts/seal-nas-config.mjs','--request',requestFile,'--input',broken,'--output',join(f.directory,'must-not-exist.json')]),error=>!error.stdout.includes('synthetic-do-not-log')&&!error.stderr.includes('synthetic-do-not-log'));
  const result=(await client.callTool({name:'aperture_nas_send',arguments:command})).structuredContent;assert.equal(result.outcome,'awaiting_tv');
  assert.equal((await client.callTool({name:'aperture_nas_send',arguments:command})).structuredContent.replayed,true);
  const poll=await h.call({poll:true});assert.equal(poll.state,'delivered');assert.equal((await h.call({envelope:poll.envelope})).valid,true);
  assert.equal((await h.call({verify:true})).valid,false,'unreachable NAS never produces a saved local model');
  await h.call({finish:'connection_failed'});
  const receipt=(await client.callTool({name:'aperture_nas_status',arguments:{id:request.id,key:command.execution.key}})).structuredContent.receipt;assert.equal(receipt.outcome,'connection_failed');
  const stored=await readFile(join(f.directory,'state.json'),'utf8');assert.ok(!stored.includes(draft.password)&&!stored.includes(command.envelope.ciphertext));
  const id=Object.entries(f.app.store.read().sessions).find(([,s])=>s.role==='controller'&&s.scopes.includes('nas.configure'))[0];await f.app.store.transact(s=>s.sessions[id].scopes=['home.read']);assert.equal((await client.listTools()).tools.length,2);
  assert.equal((await client.callTool({name:'aperture_nas_status',arguments:{id:request.id}})).isError,true);
});
test('real encrypted handoff tests signed SMB directory before model save and rechecks writer authorization',async t=>{
  const samba=process.env.APERTURE_NAS_SAMBA_ROOT||'/tmp/aperture-nas-runtime/samba',python=process.env.APERTURE_NAS_TEST_PYTHON||'/tmp/aperture-nas-runtime/venv/bin/python';if(!existsSync(join(samba,'usr/sbin/smbd'))){t.skip('Samba runtime absent; not a passed NAS import');return;}
  const f=await fixture(t),share=join(f.directory,'share');await mkdir(share);await writeFile(join(share,'合成片段.mp4'),'synthetic non-video only');const reservation=createServer();await new Promise(r=>reservation.listen(0,'127.0.0.1',r));const port=reservation.address().port;await new Promise(r=>reservation.close(r));
  const server=spawn(python,[resolve('tests/fixtures/nas-samba.py'),String(port),share,samba,'SMB3_11'],{detached:true,stdio:['ignore','pipe','pipe']});server.stdout.resume();server.stderr.resume();t.after(()=>server.kill());
  await new Promise((done,fail)=>{let ended=false;const timeout=setTimeout(()=>{ended=true;fail(new Error('SMB setup fixture timeout'));},10000);server.once('exit',()=>{if(!ended){ended=true;clearTimeout(timeout);fail(new Error('SMB setup fixture exited'));}});function poll(){if(ended)return;const socket=new Socket();socket.once('connect',()=>{socket.destroy();if(!ended){ended=true;clearTimeout(timeout);done();}});socket.once('error',()=>{socket.destroy();setTimeout(poll,100);});socket.connect(port,'127.0.0.1');}poll();});
  const {sealNas}=await import('../public/nas-setup/seal.mjs');
  for(const mode of ['valid','cancelled','wrong-password','revoked-after-test']){
    const h=await nasSetupHarness();t.after(()=>h.close());const request=await h.call({start:{origin:f.origin,cookie:await f.viewer()}});
    const token=await f.controller(['home.read','nas.configure']);const envelope=await sealNas(request,{name:'合成NAS',brand:'其他 SMB / WebDAV',root:`smb://127.0.0.1:${port}/Family`,user:userInfo().username,password:mode==='wrong-password'?'wrong':'synthetic-nas-only',domain:'',role:'movies'});
    await f.request('/api/local-agent/call',{name:'aperture_nas_send',arguments:{id:request.id,envelope,execution:{key:'smb-setup-'+mode,revision:0}}},{Authorization:'Bearer '+token});await h.call({poll:true});
    if(mode==='revoked-after-test'){
      const original=f.app.nasSetup.poll;
      const actorId=Object.entries(f.app.store.read().sessions).filter(([,s])=>s.role==='controller'&&s.scopes.includes('nas.configure')).at(-1)[0];
      // The normal HTTP handler is async; the mutation is committed before the final authorization check.
      f.app.nasSetup.poll=async(actor,input)=>{await f.app.store.transact(s=>s.sessions[actorId].scopes=['home.read']);return original(actor,input);};
      const result=await h.call({verify:true});assert.equal(result.valid,false);f.app.nasSetup.poll=original;
    }else{
      const result=await h.call({verify:true,cancelled:mode==='cancelled'});assert.equal(result.valid,mode==='valid',mode);
      if(mode==='valid'){assert.equal(result.bound,true);assert.equal(result.encrypted,true);assert.equal(result.count,1);assert.equal((await h.call({finish:'saved'})).outcome,'saved_on_tv');}
    }
  }
  await mkdir('artifacts',{recursive:true});await writeFile('artifacts/nas-setup-integration-report.json',JSON.stringify({realJavaHttpTransport:true,realWebCrypto:true,realSignedSmb311:true,productionImportValidation:true,jvmLibraryAndCipherSave:true,cancelWrongPasswordAndPostTestRevokeRejected:true,androidActivity:false,androidKeyStore:false,mediaDecode:false,brandHardware:false},null,2)+'\n');
});
