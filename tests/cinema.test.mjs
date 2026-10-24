import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,writeFile,symlink,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createServer} from 'node:http';
import {createApp} from '../server/app.mjs';
import {parseRange,createCinemaService} from '../server/cinema/service.mjs';
import {parsePlaylist} from '../server/cinema/config.mjs';
async function fixture(t,{upstream,config}={}){
 const directory=await mkdtemp(join(tmpdir(),'aperture-cinema-')),root=join(directory,'videos');await mkdir(root);await writeFile(join(root,'家庭旅行.mp4'),Buffer.from('0123456789'));await writeFile(join(directory,'private.mp4'),'SECRET');await symlink(join(directory,'private.mp4'),join(root,'outside.mp4'));
 const reserve=createServer();await new Promise(r=>reserve.listen(0,'127.0.0.1',r));const port=reserve.address().port;await new Promise(r=>reserve.close(r));const origin=`http://127.0.0.1:${port}`,key='cinema-test-owner-123456789012345';
 const app=await createApp({directory,ownerKey:key,origin,mediaConfig:config||{sources:[{id:'family',name:'家庭视频',type:'local',path:root}],allowedOrigins:upstream?[upstream]:[]}});await new Promise(r=>app.server.listen(port,'127.0.0.1',r));
 async function request(path,body,cookie='',headers={}){const res=await fetch(origin+path,{method:body===undefined?'GET':'POST',headers:{cookie,'Content-Type':'application/json',...headers},body:body===undefined?undefined:JSON.stringify(body)});const raw=await res.text();let data;try{data=JSON.parse(raw);}catch{}return {status:res.status,data,raw,headers:res.headers,cookie:res.headers.get('set-cookie')?.split(';')[0]};}
 const owner=(await request('/api/login',{key})).cookie;
 t.after(async()=>{await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});});
 return {app,request,owner,origin,root,enable:(enabled=true,revision=0,sourceIds=['family'])=>request('/api/cinema/settings',{enabled,sourceIds,execution:{key:'settings-'+revision,revision}},owner)};
}
test('cinema default off; owner enables only configured libraries; symlinks and filesystem paths excluded',async t=>{
 const f=await fixture(t);assert.equal((await f.request('/api/cinema/library',undefined,f.owner)).data.code,'cinema_disabled');assert.equal((await f.request('/api/cinema/options')).status,401);assert.equal((await f.request('/api/cinema/health')).status,401);
 const options=await f.request('/api/cinema/options',undefined,f.owner);assert.ok(!JSON.stringify(options).includes(f.root));assert.equal((await f.enable(true,0,['unknown'])).status,400);assert.equal((await f.enable()).status,200);
 const list=await f.request('/api/cinema/library',undefined,f.owner);assert.equal(list.data.items.length,1);assert.ok(!JSON.stringify(list).includes(f.root));assert.equal(list.data.items[0].name,'家庭旅行');
 const path='/api/cinema/play/'+list.data.items[0].id;assert.equal((await f.request(path)).status,401);assert.equal((await f.request(path,undefined,f.owner)).raw,'0123456789');
 const range=await f.request(path,undefined,f.owner,{range:'bytes=2-5'});assert.equal(range.status,206);assert.equal(range.raw,'2345');assert.equal(range.headers.get('content-range'),'bytes 2-5/10');assert.equal((await f.request(path,undefined,f.owner,{range:'bytes=90-100'})).status,416);
 await f.enable(false,1);assert.equal((await f.request(path,undefined,f.owner)).data.code,'cinema_disabled');
});
test('viewer can play enabled media but cannot enable, import, or bypass domain operations',async t=>{
 const f=await fixture(t);await f.enable();const pair=(await f.request('/api/pair/start',{role:'viewer'})).data;await f.request('/api/pair/approve',{code:pair.code},f.owner);const viewer=(await f.request('/api/pair/poll',pair)).cookie;
 assert.equal((await f.request('/api/cinema/health',undefined,viewer)).status,403);assert.equal((await f.request('/api/cinema/health',{},viewer)).status,403);
 const list=await f.request('/api/cinema/library',undefined,viewer);assert.equal(list.status,200);assert.equal((await f.request('/api/cinema/play/'+list.data.items[0].id,undefined,viewer)).status,200);
 assert.equal((await f.request('/api/cinema/settings',{enabled:false,sourceIds:[],execution:{key:'viewer-change',revision:1}},viewer)).status,403);
 assert.equal((await f.request('/api/cinema/playlist',{playlist:'#EXTM3U\nhttps://evil.example/a.mp4'},viewer)).status,403);
 assert.equal((await f.request('/api/write',{operation:'cinema.settings',input:{enabled:true,sourceIds:[]},execution:{key:'bypass-settings',revision:1}},f.owner)).status,400);
 await f.request('/api/logout',{},viewer);assert.equal((await f.request('/api/cinema/play/'+list.data.items[0].id,undefined,viewer)).status,401);
});
test('M3U allowlist, no TVBox scripts; atomic replacement and clearing',async t=>{
 const f=await fixture(t,{upstream:'https://media.example'});await f.enable();
 const body={playlist:'#EXTM3U\n#EXTINF:-1,自有视频\nhttps://media.example/trip.mp4?private=signed-token',execution:{key:'playlist-import',revision:1}};
 assert.equal((await f.request('/api/cinema/playlist',body,f.owner)).status,200);assert.equal((await f.request('/api/cinema/playlist',body,f.owner)).data.replayed,true);
 const library=await f.request('/api/cinema/library',undefined,f.owner);assert.equal(library.data.items.length,2);assert.ok(!JSON.stringify(library).includes('signed-token'));assert.ok(!JSON.stringify(f.app.service.read()).includes('signed-token'));
 assert.throws(()=>parsePlaylist('#EXTM3U\nhttps://media.example.evil/a.mp4',['https://media.example']));assert.throws(()=>parsePlaylist('{"spider":"evil.jar"}',[]));assert.throws(()=>parsePlaylist('#EXTM3U\nfile:///etc/passwd',[]));
 const clear=await f.request('/api/cinema/playlist',{clear:true,execution:{key:'playlist-clear',revision:2}},f.owner);assert.equal(clear.status,200);assert.equal(f.app.store.read().mediaChannels.length,0);
});
test('remote HLS manifest and segments proxied only through opaque approved references; redirects denied',async t=>{
 const remote=createServer((req,res)=>{if(req.url==='/master.m3u8'){res.writeHead(200,{'Content-Type':'application/vnd.apple.mpegurl'});res.end('#EXTM3U\n#EXT-X-TARGETDURATION:5\n#EXTINF:5,\nsegment.ts\n#EXT-X-ENDLIST');}else if(req.url==='/segment.ts'){res.writeHead(200,{'Content-Type':'video/mp2t'});res.end('SEGMENT');}else{res.writeHead(302,{Location:'http://127.0.0.1:1/private'});res.end();}});await new Promise(r=>remote.listen(0,'127.0.0.1',r));t.after(()=>new Promise(r=>remote.close(r)));const url=`http://127.0.0.1:${remote.address().port}`;
 const f=await fixture(t,{upstream:url});await f.enable();await f.request('/api/cinema/playlist',{playlist:`#EXTM3U\n#EXTINF:-1,示例直播\n${url}/master.m3u8\n#EXTINF:-1,跳转地址\n${url}/redirect.mp4`,execution:{key:'hls-import',revision:1}},f.owner);
 const channels=(await f.request('/api/cinema/library',undefined,f.owner)).data.items.filter(i=>i.kind==='channel');const manifest=await f.request('/api/cinema/play/'+channels[0].id,undefined,f.owner);assert.equal(manifest.status,200);assert.ok(!manifest.raw.includes(url));const segment=manifest.raw.split('\n').find(s=>s.startsWith('/api'));assert.equal((await f.request(segment,undefined,f.owner)).raw,'SEGMENT');assert.equal((await f.request('/api/cinema/play/'+channels[0].id+'?part=made-up',undefined,f.owner)).status,410);assert.equal((await f.request('/api/cinema/play/'+channels[1].id,undefined,f.owner)).status,502);
});
test('Jellyfin fixture scopes listing to configured user/library and keeps token server-side',async t=>{
 let checked=false;const remote=createServer((req,res)=>{assert.equal(req.headers['x-emby-token'],'synthetic-jellyfin-test-token');if(req.url.startsWith('/Users/abc/Items')){const u=new URL(req.url,'http://localhost');assert.equal(u.searchParams.get('ParentId'),'def');checked=true;res.setHeader('Content-Type','application/json');res.end(JSON.stringify({Items:[{Id:'123abc',Name:'家庭影片',Container:'mp4'}]}));}else if(req.url.startsWith('/Videos/123abc/stream')){res.setHeader('Content-Type','video/mp4');res.end('JELLYFIN-VIDEO');}else{res.writeHead(404);res.end();}});await new Promise(r=>remote.listen(0,'127.0.0.1',r));t.after(()=>new Promise(r=>remote.close(r)));process.env.APERTURE_TEST_JELLYFIN_TOKEN='synthetic-jellyfin-test-token';t.after(()=>delete process.env.APERTURE_TEST_JELLYFIN_TOKEN);
 const f=await fixture(t,{config:{sources:[{id:'nas',type:'jellyfin',name:'家庭 NAS',url:`http://127.0.0.1:${remote.address().port}`,tokenEnv:'APERTURE_TEST_JELLYFIN_TOKEN',userId:'abc',libraryId:'def'}],allowedOrigins:[]}});await f.enable(true,0,['nas']);const list=await f.request('/api/cinema/library',undefined,f.owner);assert.equal(checked,true);assert.equal(list.data.items.length,1);assert.ok(!JSON.stringify(list).includes('synthetic-jellyfin-test-token'));assert.equal((await f.request('/api/cinema/play/'+list.data.items[0].id,undefined,f.owner)).raw,'JELLYFIN-VIDEO');
});
test('range boundaries reject multiple or malformed ranges, support suffix and open end',()=>{assert.deepEqual(parseRange('bytes=-3',10),{start:7,end:9,status:206});assert.deepEqual(parseRange('bytes=7-',10),{start:7,end:9,status:206});for(const r of ['bytes=1-2,3-4','bytes=-0','bytes=5-2','bytes=NaN-','bytes=-'])assert.equal(parseRange(r,10),null);});
