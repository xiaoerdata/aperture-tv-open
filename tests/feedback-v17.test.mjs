import {test} from 'node:test';
import assert from 'node:assert/strict';
import {networkInterfaces} from 'node:os';
import {mkdtemp,rm,writeFile,readFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawn} from 'node:child_process';
import Ajv from 'ajv';
import {createApp} from '../server/app.mjs';
import {originURL,loadConnection,localRequest} from '../server/local-agent/client.mjs';
import {entrySchema} from '../shared/entry-contract.mjs';
import {normalizeEntry} from '../shared/model.mjs';
import {magicPacket} from '../shared/wake-on-lan.mjs';
const validate=new Ajv({strict:true,allErrors:true}).compile(entrySchema);
test('Agent contract states every required field and domain errors report all missing fields',()=>{
 const entries=[{type:'note',title:'散步',body:'20:00 出门',author:'家人'},{type:'event',title:'散步',date:'2026-10-25',time:'20:00',person:'全家'},{type:'metric',title:'温度',value:22.5,target:30,unit:'°C',source:'传感器',date:'2026-10-25'}];
 for(const entry of entries){assert.equal(validate(entry),true,JSON.stringify(validate.errors));assert.doesNotThrow(()=>normalizeEntry(entry));for(const field of Object.keys(entry).filter(k=>!['type','title'].includes(k))){const invalid={...entry};delete invalid[field];assert.equal(validate(invalid),false,field);assert.throws(()=>normalizeEntry(invalid),new RegExp('缺少字段.*'+field));}}
 assert.throws(()=>normalizeEntry({type:'metric',title:'温度'}),/value、target、unit、source、date/);
 assert.equal(validate({...entries[2],value:'22.5'}),false);assert.throws(()=>normalizeEntry({...entries[2],value:'22.5'}));
});
test('private literal HTTP needs explicit opt-in; opt-in cannot authorize public IP or DNS HTTP',()=>{
 assert.throws(()=>originURL('http://192.168.31.7:4318'));
 for(const host of ['192.168.31.7','10.2.3.4','172.20.1.2','[fd00::123]'])assert.equal(originURL('http://'+host+':4318',{allowLanHttp:true}),'http://'+host+':4318');
 for(const host of ['8.8.8.8','192.168.1.2.example.com','screen.local','169.254.1.1'])assert.throws(()=>originURL('http://'+host+':4318',{allowLanHttp:true}));
 assert.throws(()=>originURL('http://192.168.31.7:4318/path',{allowLanHttp:true}));
});
test('LAN HTTP QR CLI, owner approval, saved policy and MCP read share one exact PUBLIC_ORIGIN',async t=>{
 const ip=Object.values(networkInterfaces()).flat().find(n=>n.family==='IPv4'&&!n.internal&&/^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(n.address))?.address;
 if(!ip)return t.skip('No private LAN interface available');
 const directory=await mkdtemp(join(tmpdir(),'kaishi-feedback-lan-'));let app,child;
 t.after(async()=>{child?.kill();if(app)await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});});
 // Reserve a local port, then use the real private interface throughout; no rewritten Host header.
 const {createServer}=await import('node:net');const reservation=createServer();await new Promise(r=>reservation.listen(0,ip,r));const port=reservation.address().port;await new Promise(r=>reservation.close(r));
 const origin='http://'+ip+':'+port;app=await createApp({directory,origin,ownerKey:'feedback-local-owner-test-only-key',localAutomation:true});await new Promise(r=>app.server.listen(port,ip,r));
 const login=await fetch(origin+'/api/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({key:'feedback-local-owner-test-only-key'})});const cookie=login.headers.get('set-cookie').split(';')[0];
 const config=join(directory,'connection.json');child=spawn(process.execPath,['scripts/workbuddy-pair.mjs','--origin',origin,'--allow-lan-http','--output',config],{stdio:['ignore','pipe','pipe']});let output='',errors='';child.stdout.on('data',b=>output+=b);child.stderr.on('data',b=>errors+=b);const done=new Promise(r=>child.on('exit',r));
 for(let i=0;i<150&&!output.includes('/studio?pair=');i++)await new Promise(r=>setTimeout(r,40));
 const approval=new URL(output.match(/http:\/\/[^\s]+\/studio\?[^\s]+/)?.[0]||'http://invalid');assert.equal(approval.origin,origin,errors);
 const approved=await fetch(origin+'/api/local-pair/approve',{method:'POST',headers:{'Content-Type':'application/json',Cookie:cookie,Origin:origin},body:JSON.stringify({code:approval.searchParams.get('pair'),challenge:approval.searchParams.get('challenge'),scopes:['home.read'],hours:1})});assert.equal(approved.status,200);assert.equal(await done,0,errors);
 const connection=await loadConnection(config);assert.equal(connection.allowLanHttp,true);assert.equal(connection.origin,origin);assert.ok((await localRequest(connection,'/api/local-agent/tools')).tools.some(t=>t.name==='aperture_home'));assert.ok(!output.includes(connection.token));
 const {request}=await import('node:http');const hostStatus=await new Promise((resolve,reject)=>{const requestOnce=request(origin+'/api/health',{headers:{Host:'localhost:'+port}},r=>{r.resume();resolve(r.statusCode);});requestOnce.once('error',reject);requestOnce.end();});assert.equal(hostStatus,421);
 const cross=await fetch(origin+'/api/health',{headers:{Origin:'http://untrusted.invalid'}});assert.equal(cross.status,403);
});
test('WoL packet is validated without claiming standby or ADB acceptance',()=>{
 const p=magicPacket('02:11:22:33:44:55');assert.equal(p.length,102);assert.equal(p.subarray(0,6).toString('hex'),'ffffffffffff');assert.equal(p.subarray(6).toString('hex'),'021122334455'.repeat(16));for(const mac of ['bad','ff:ff:ff:ff:ff:ff','00:00:00:00:00:00'])assert.throws(()=>magicPacket(mac));
});
test('service templates render both platforms without enabling a daemon or copying secrets',async t=>{
 const dir=await mkdtemp(join(tmpdir(),'kaishi service '));t.after(()=>rm(dir,{recursive:true,force:true}));const config=join(dir,'private.env');await writeFile(config,'PUBLIC_ORIGIN=http://127.0.0.1:4318\n',{mode:0o600});
 for(const platform of ['systemd','launchd']){const output=join(dir,platform==='systemd'?'kaishi.service':'org.zhixiaoce.kaishi.plist');const child=spawn('python3',['scripts/service-config.py','--platform',platform,'--env-file',config,'--output',output],{stdio:['ignore','pipe','pipe']});let logs='';child.stdout.on('data',b=>logs+=b);child.stderr.on('data',b=>logs+=b);assert.equal(await new Promise(r=>child.on('exit',r)),0,logs);const text=await readFile(output,'utf8');assert.ok(text.includes(config));assert.ok(!text.includes('PUBLIC_ORIGIN='));assert.match(logs,/Not installed or started/);}
});

test('private signing backup survives runtime loss and refuses an accidental new identity',async t=>{
 const {preserveDebugKey}=await import('../scripts/signing-backup.mjs');const {mkdir,stat,unlink}=await import('node:fs/promises');
 const dir=await mkdtemp(join(tmpdir(),'kaishi-signing-fixture-'));t.after(()=>rm(dir,{recursive:true,force:true}));const runtime=join(dir,'runtime'),backup=join(dir,'persistent'),key=join(runtime,'android-user','debug.keystore');
 await assert.rejects(preserveDebugKey({runtimeDirectory:runtime,backupDirectory:backup,existingSignedCandidate:true}),/missing/);
 await mkdir(join(runtime,'android-user'),{recursive:true});await writeFile(key,'synthetic fixture bytes only',{mode:0o600});await preserveDebugKey({runtimeDirectory:runtime,backupDirectory:backup,existingSignedCandidate:true});
 await unlink(key);const restored=await preserveDebugKey({runtimeDirectory:runtime,backupDirectory:backup,existingSignedCandidate:true});assert.equal(restored.restored,true);assert.equal(await readFile(key,'utf8'),'synthetic fixture bytes only');assert.equal((await stat(join(backup,'debug.keystore'))).mode&0o077,0);
 await writeFile(key,'different synthetic fixture bytes');await assert.rejects(preserveDebugKey({runtimeDirectory:runtime,backupDirectory:backup,existingSignedCandidate:true}),/differs/);assert.equal(await readFile(join(backup,'debug.keystore'),'utf8'),'synthetic fixture bytes only');
});
