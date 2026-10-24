import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {probeRemote,createHealthMonitor} from '../server/cinema/health.mjs';
import {parsePlaylist} from '../server/cinema/config.mjs';
const waitFor=async fn=>{for(let i=0;i<100;i++){if(fn())return;await new Promise(r=>setTimeout(r,10));}assert.fail('condition timed out');};
async function remote(t){let requests=[];const server=createServer((req,res)=>{requests.push(req.url);const path=req.url.split('?')[0];
 if(path==='/auth'){res.writeHead(401);res.end('private');}else if(path==='/redirect'){res.writeHead(302,{Location:'http://127.0.0.1:1/private'});res.end();}
 else if(path.endsWith('.m3u8')){res.setHeader('Content-Type','application/vnd.apple.mpegurl');res.end(path==='/good.m3u8'?'#EXTM3U\n#EXT-X-TARGETDURATION:5\n#EXTINF:5,\n/clip.mp4':path==='/off-origin.m3u8'?'#EXTM3U\n#EXT-X-TARGETDURATION:5\n#EXTINF:5,\n/clip.mp4\n#EXTINF:5,\nhttp://127.0.0.1:1/secret':'#EXTM3U\n#EXTINF:5,\n/clip.mp4');}
 else if(path==='/html'){res.setHeader('Content-Type','video/mp4');res.end('<!doctype html><html>login</html>');}
 else if(path==='/unknown'){res.setHeader('Content-Type','video/mp4');res.end('unrecognized sample');}
 else {res.setHeader('Content-Type','video/mp4');res.end(Buffer.concat([Buffer.from([0,0,0,16]),Buffer.from('ftypisom')]));}
 });await new Promise(r=>server.listen(0,'127.0.0.1',r));t.after(()=>new Promise(r=>server.close(r)));return {origin:`http://127.0.0.1:${server.address().port}`,requests};}
test('probe reads actual samples; rejects login, authentication, redirects and every off-origin HLS entry',async t=>{
 const f=await remote(t),probe=path=>probeRemote(f.origin+path,{origins:[f.origin],signal:AbortSignal.timeout(1000)});
 assert.equal(await probe('/clip.mp4'),'readable');assert.equal(await probe('/good.m3u8'),'readable');assert.equal(await probe('/unknown'),'format_unverified');
 for(const [path,status] of [['/html','invalid_media'],['/auth','auth_required'],['/redirect','redirect_blocked'],['/bad.m3u8','invalid_media'],['/off-origin.m3u8','origin_denied']])await assert.rejects(probe(path),e=>e.status===status);
 assert.ok(!f.requests.includes('/secret'));
});
test('monitor stays idle while disabled; invalidation scans automatically, concurrent starts join, results omit private URL',async t=>{
 const f=await remote(t);let enabled=false;const monitor=createHealthMonitor({config:{sources:[],allowedOrigins:[f.origin]},snapshot:()=>({enabled,sourceIds:[],channels:[{name:'家庭频道',url:f.origin+'/clip.mp4?secret=private-token'}]})});t.after(()=>monitor.close());
 monitor.start();assert.equal(f.requests.length,0);enabled=true;monitor.invalidate();assert.equal(monitor.start().running,true);await waitFor(()=>!monitor.status().running);
 assert.equal(monitor.status().items[0].status,'readable');assert.equal(f.requests.length,1);monitor.start();assert.equal(f.requests.length,1);assert.ok(!JSON.stringify(monitor.status()).includes('private-token'));assert.ok(!JSON.stringify(monitor.status()).includes(f.origin));enabled=false;monitor.invalidate();assert.deepEqual(monitor.status().items,[]);
});
test('playlist classifies three parent destinations without executing TVBox scripts',()=>{
 const list=parsePlaylist('#EXTM3U\n#EXTINF:-1,直播\nhttps://media.example/a\n#EXTINF:-1 group-title="电视剧",剧集\nhttps://media.example/b\n#EXTINF:-1 group-title="电影",电影\nhttps://media.example/c',['https://media.example']);assert.deepEqual(list.map(i=>i.viewKind),['live','series','movie']);assert.throws(()=>parsePlaylist('{"spider":"x.jar"}',[]));
});

test('periodic scans bound concurrency, time out stalled streams, and stop on close',async t=>{
 let active=0,peak=0,total=0;const server=createServer((req,res)=>{total++;peak=Math.max(peak,++active);res.on('close',()=>active--);res.writeHead(200,{'Content-Type':'video/mp4'});res.flushHeaders();});await new Promise(r=>server.listen(0,'127.0.0.1',r));t.after(()=>{server.closeAllConnections();return new Promise(r=>server.close(r));});const origin=`http://127.0.0.1:${server.address().port}`;
 const monitor=createHealthMonitor({config:{sources:[],allowedOrigins:[origin]},snapshot:()=>({enabled:true,sourceIds:[],channels:[1,2,3].map(n=>({name:'频道'+n,url:origin+'/'+n}))}),intervalMs:250,cooldownMs:0,probeTimeoutMs:60});t.after(()=>monitor.close());
 await waitFor(()=>monitor.status().completedAt!==null);assert.ok(monitor.status().items.every(i=>i.status==='timeout'));assert.ok(peak<=2);await waitFor(()=>total>=4);monitor.close();await new Promise(r=>setTimeout(r,30));const stopped=total;await new Promise(r=>setTimeout(r,280));assert.equal(total,stopped);
});
