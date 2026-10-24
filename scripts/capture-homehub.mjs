// Isolated, synthetic recording. No real owner key, QR, token or private data in frames.
import {mkdtemp,mkdir,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {createApp} from '../server/app.mjs';
import {demoBoard} from '../shared/model.mjs';
import {launchBrowser} from './browser.mjs';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';
const directory=await mkdtemp(join(tmpdir(),'aperture-hub-film-')),origin='http://127.0.0.1:4334',ownerKey='synthetic-film-owner-123456789012345';
const app=await createApp({directory,origin,ownerKey,localAutomation:true});
await app.store.transact(s=>{const d=demoBoard();s.board.entries=d.entries.filter(e=>e.type!=='note');s.board.settings.name='示例家庭';});
await new Promise(r=>app.server.listen(4334,'127.0.0.1',r));
const browser=await launchBrowser(),client=new Client({name:'home-film-protocol-client',version:'1'},{capabilities:{}});
await mkdir('media/output',{recursive:true});const output='media/output/home-hub-walkthrough-1920x1080.mp4',fps=12,seconds=24;
let encoder;
try{
 const request=async(path,body,cookie)=>{const r=await fetch(origin+path,{method:'POST',headers:{'Content-Type':'application/json',...(cookie?{cookie}:{})},body:JSON.stringify(body)});if(!r.ok)throw new Error('Setup request failed: '+path);return {data:await r.json(),cookie:r.headers.get('set-cookie')?.split(';')[0]};};
 const owner=(await request('/api/login',{key:ownerKey})).cookie;
 const viewer=(await request('/api/pair/start',{role:'viewer'})).data;await request('/api/pair/approve',{code:viewer.code},owner);const cookie=(await request('/api/pair/poll',viewer)).cookie;
 const pair=(await request('/api/local-pair/start',{})).data;await request('/api/local-pair/approve',{code:pair.code,challenge:pair.challenge,scopes:['home.read','scene.write','content.write'],hours:1},owner);const grant=(await request('/api/local-pair/poll',pair)).data;
 const config=join(directory,'connection.json');await writeFile(config,JSON.stringify({origin,token:grant.token}),{mode:0o600});await client.connect(new StdioClientTransport({command:process.execPath,args:[resolve('scripts/workbuddy-mcp.mjs'),'--config',config],stderr:'ignore'}));
 const context=await browser.newContext({viewport:{width:1920,height:1080}});await context.addCookies([{name:'aperture',value:cookie.slice('aperture='.length),url:origin,httpOnly:true,sameSite:'Strict'}]);const page=await context.newPage();
 const label=()=>page.evaluate(()=>{const p=document.createElement('div');p.className='recording-label';p.textContent='浏览器录屏 · 合成家庭数据 · MCP 协议测试';document.body.append(p);});
 await page.goto(origin+'/guide');await page.evaluate(()=>document.fonts.ready);await label();
 await page.screenshot({path:'media/output/home-hub-guide-1920x1080.png'});
 encoder=spawn(process.env.FFMPEG_PATH||'ffmpeg',['-hide_banner','-loglevel','error','-y','-f','image2pipe','-vcodec','png','-framerate',String(fps),'-i','-','-an','-c:v','libx264','-preset','fast','-crf','21','-pix_fmt','yuv420p','-movflags','+faststart',output],{stdio:['pipe','ignore','pipe']});let stderr='';encoder.stderr.on('data',b=>stderr+=b);const done=new Promise((r,j)=>{encoder.on('error',j);encoder.on('close',code=>code===0?r():j(new Error(stderr)));});done.catch(()=>{});
 const receipts=[];
 async function call(name,args,key){const home=(await client.callTool({name:'aperture_home',arguments:{}})).structuredContent;const result=await client.callTool({name,arguments:{...args,execution:{key,revision:home.revision}}});if(result.isError)throw new Error('Recording MCP call failed');await page.waitForFunction(r=>document.querySelector('#connection-status').textContent.includes('r'+r),result.structuredContent.revision);receipts.push({key,revision:result.structuredContent.revision});}
 for(let frame=0;frame<fps*seconds;frame++){
  if(frame===24)await page.locator('[data-day=home]').click();
  if(frame===48)await page.locator('[data-day=cinema]').click();
  if(frame===72){await page.goto(origin+'/tv');await page.waitForFunction(()=>document.querySelector('#connection-status').textContent.includes('已同步'));await label();}
  if(frame===96)await call('aperture_scene',{scene:'daylight',motion:true,headline:'欢迎回家，今晚慢一点。'},'film-welcome');
  if(frame===144)await call('aperture_add',{entry:{type:'note',title:'晚饭后一起散步',body:'今天不赶时间，去楼下走一走。',author:'家庭助手',pinned:true}},'film-note');
  if(frame===192){await page.screenshot({path:'media/output/home-hub-tv-1920x1080.png'});await call('aperture_scene',{scene:'landscape',motion:true,headline:'把在意的小事，慢慢积累。'},'film-landscape');}
  if(frame===252)await call('aperture_scene',{scene:'daylight',motion:false,headline:'家的变化，交给自己的助手。'},'film-final');
  const bytes=await page.screenshot({type:'png'});if(!encoder.stdin.write(bytes))await once(encoder.stdin,'drain');
  if(frame%72===0)console.log(`Captured ${frame/fps}s / ${seconds}s`);
 }
 encoder.stdin.end();await done;
 await writeFile('media/output/home-hub-manifest.json',JSON.stringify({version:'0.2.0-alpha.1',date:'2026-10-24',file:output,width:1920,height:1080,fps,seconds,audio:false,method:'Browser screenshots sampled into a 12fps walkthrough; not a wall-clock latency measurement',syntheticData:true,actualMcpCalls:true,realWorkBuddyApplication:false,realXiaomiTV:false,qrOrCredentialsInFrames:false,receipts},null,2));console.log(output);
}finally{encoder?.kill();await client.close().catch(()=>{});await browser.close();await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});}
