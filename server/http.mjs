import {createServer} from 'node:http';
import {readFile, stat} from 'node:fs/promises';
import {resolve, join, extname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {demoBoard, DomainError, fail} from '../shared/model.mjs';
import {liteDisplay} from './display-lite.mjs';
import {localPeer} from './local-agent/network.mjs';
import {appOperationsHttp} from './app-operations/http.mjs';
import {nasSetupHttp} from './nas-setup/http.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const version=JSON.parse(await readFile(join(root,'package.json'),'utf8')).version;
const types={'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'text/javascript; charset=utf-8','.mjs':'text/javascript; charset=utf-8','.svg':'image/svg+xml','.json':'application/json; charset=utf-8','.webp':'image/webp','.jpg':'image/jpeg','.png':'image/png','.woff2':'font/woff2'};
export function createHttp({service,store,auth,images,origin,life,cinema,nasSetup,appOperations,localAgent,localAutomation,trustedProxies=[]}) {
  const allowed=new URL(origin); const buckets=new Map();let eventClients=0;
  function rate(req,group,limit) {
    const key=`${req.socket.remoteAddress}:${group}`, now=Date.now();
    if(buckets.size>2048) for(const [k,v] of buckets) if(v.reset<=now)buckets.delete(k);
    let b=buckets.get(key);if(!b||b.reset<=now){b={count:0,reset:now+60_000};buckets.set(key,b);}
    if(++b.count>limit) fail('请求较多，请一分钟后重试',429,'rate_limited');
  }
  function cookie(token) { return `aperture=${token}; HttpOnly; SameSite=Strict; Path=/; Max-Age=2592000${allowed.protocol==='https:'?'; Secure':''}`; }
  async function json(req) {
    if(!req.headers['content-type']?.startsWith('application/json')) fail('请使用JSON请求',415);
    let size=0;const chunks=[];
    for await(const chunk of req){size+=chunk.length;if(size>4_100_000)fail('内容过大',413);chunks.push(chunk);}
    let value;try{value=JSON.parse(Buffer.concat(chunks).toString());}catch{fail('JSON格式不正确');}
    if(!value || typeof value!=='object' || Array.isArray(value))fail('JSON请求需为对象');
    return value;
  }
  const server=createServer(async(req,res)=>{
    const send=(data,status=200)=>{res.writeHead(status,{'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify(data));};
    res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('Referrer-Policy','no-referrer');
    res.setHeader('Cache-Control','no-store');
    res.setHeader('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' blob:; media-src 'self' blob:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
    try {
      if(req.headers.host !== allowed.host) fail('主机地址未配置',421,'invalid_host');
      if(req.headers.origin && req.headers.origin !== allowed.origin) fail('来源不允许',403,'invalid_origin');
      const requestUrl=new URL(req.url,allowed),path=requestUrl.pathname;
      if((path==='/'||path==='/home')&&['GET','HEAD'].includes(req.method)){res.writeHead(302,{Location:'/launcher/index.html'+requestUrl.search});return res.end();}
      const chromium=/Chrom(?:e|ium)\/([0-9]+)/.exec(req.headers['user-agent']||'');
      if((path==='/'||path==='/tv')&&['GET','HEAD'].includes(req.method)&&chromium&&Number(chromium[1])<89){res.writeHead(302,{Location:'/tv-lite'+requestUrl.search});return res.end();}
      if(path.startsWith('/api/')) rate(req,'api',600);
      if(path==='/api/tv-lite/demo' && req.method==='GET') return send(liteDisplay(demoBoard()));
      if(path==='/api/demo' && req.method==='GET') return send(demoBoard());
      if(path==='/api/health' && req.method==='GET') return send({status:'ok',version});
      if(path==='/api/login' && req.method==='POST') {rate(req,'login',8);const b=await json(req);res.setHeader('Set-Cookie',cookie(await auth.login(b.key)));return send({role:'owner'});}
      if(path==='/api/pair/start' && req.method==='POST') {rate(req,'pair-start',10);const b=await json(req);if(!['viewer','editor'].includes(b.role))fail('连接角色无效');return send(await auth.start(b.role));}
      if(path==='/api/pair/poll' && req.method==='POST') {const b=await json(req),result=await auth.poll(b.code,b.secret);if(result.token){res.setHeader('Set-Cookie',cookie(result.token));delete result.token;}return send(result);}
      if(path.startsWith('/api/agent') || path==='/agent') return send({code:'agent_not_available',message:'Hosted Agent integration is not enabled in this standalone alpha.'},503);
      if(path.startsWith('/api/local-pair')||path.startsWith('/api/local-agent')){
        if(!localAutomation)fail('本机助手连接未开启',503,'local_automation_disabled');
        if(path==='/api/local-pair/start'&&req.method==='POST'){rate(req,'pair-start',10);await json(req);const pair=await auth.start('controller',localPeer(req,trustedProxies));return send({...pair,approvalUrl:allowed.origin+'/studio?pair='+pair.code+'&challenge='+pair.challenge,networkVerified:true});}
        if(path==='/api/local-pair/poll'&&req.method==='POST'){const b=await json(req);return send(await auth.poll(b.code,b.secret,true,localPeer(req,trustedProxies)));}
        if(path==='/api/local-pair/approve'&&req.method==='POST'){const actor=auth.read(req),b=await json(req);return send(await auth.approve(actor,b.code,{scopes:b.scopes,hours:b.hours,challenge:b.challenge}));}
        const actor=auth.readController(req,localPeer(req,trustedProxies));
        if(path==='/api/local-agent/tools'&&req.method==='GET')return send(localAgent.discovery(actor));
        if(path==='/api/local-agent/call'&&req.method==='POST'){rate(req,'local-agent-call',60);const b=await json(req);return send(await localAgent.call(actor,b.name,b.arguments));}
        fail('接口不存在',404);
      }
      if(path.startsWith('/api/') || path.startsWith('/media/')) {
        const actor=auth.read(req);
        if(path.startsWith('/api/app-operations/')){rate(req,'app-operations',60);return send(await appOperationsHttp({path,req,actor,body:()=>json(req),service:appOperations}));}
        if(path.startsWith('/api/nas-setup/')){rate(req,'nas-setup',60);return send(await nasSetupHttp({path,req,actor,body:()=>json(req),service:nasSetup,origin:allowed.origin}));}
        if(path==='/api/session' && req.method==='GET') return send({role:actor.role,expiresAt:actor.expiresAt});
        if(path==='/api/logout' && req.method==='POST'){await json(req);await auth.revoke(actor,actor.id);res.setHeader('Set-Cookie','aperture=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0');return send({ok:true});}
        if(path==='/api/tv-lite/state' && req.method==='GET') return send(liteDisplay(service.read()));
        if(path==='/api/board' && req.method==='GET') return send(service.read());
        if(path==='/api/events'&&req.method==='GET'){
          if(eventClients>=24)fail('展示连接已满',429);eventClients++;
          res.writeHead(200,{'Content-Type':'text/event-stream','Cache-Control':'no-store','X-Accel-Buffering':'no'});res.write('retry: 5000\n\n');
          const notify=revision=>{try{auth.read(req);res.write(`event: revision\ndata: ${revision}\n\n`);}catch{res.end();}};
          const unsubscribe=store.subscribe(notify),timer=setInterval(()=>{try{auth.read(req);res.write(': heartbeat\n\n');}catch{res.end();}},2000);
          res.on('close',()=>{clearInterval(timer);unsubscribe();eventClients--;});return;
        }
        if(path==='/api/display/ack'&&req.method==='POST'){
          rate(req,'display-ack',30);const b=await json(req);
          if(!['viewer','owner'].includes(actor.role)||!Number.isSafeInteger(b.revision)||b.revision<0||b.revision>service.read().revision||!['daylight','gallery','landscape'].includes(b.scene))fail('展示回执无效');
          await store.transact(state=>{if(!state.sessions[actor.id]||state.sessions[actor.id].expiresAt<=Date.now())fail('会话已失效',401);state.displays||={};for(const [id,d]of Object.entries(state.displays))if(!state.sessions[id]||Date.now()-Date.parse(d.at)>86400000)delete state.displays[id];state.displays[actor.id]={revision:b.revision,scene:b.scene,at:new Date().toISOString(),kind:'browser-render',rendering:b.rendering==='lite'?'lite':'full',...(b.presentation==='family-v2'?{presentation:'family-v2',sceneApplied:false,sections:['photos','events','notes']}:{})};});return send({ok:true});
        }
        if(path==='/api/local-automation-status'&&req.method==='GET'){if(actor.role!=='owner')fail('只有管理员可查看',403);return send({enabled:localAutomation});}
        if(path==='/api/cinema/health' && req.method==='GET')return send(cinema.health(actor));
        if(path==='/api/cinema/health' && req.method==='POST'){rate(req,'media-health',4);await json(req);return send(cinema.check(actor));}
        if(path==='/api/cinema/options' && req.method==='GET')return send(cinema.options(actor));
        if(path==='/api/cinema/library' && req.method==='GET'){rate(req,'media-library',12);return send(await cinema.library(actor));}
        if(path.startsWith('/api/cinema/metadata/')&&req.method==='GET'){rate(req,'media-metadata',40);return send(await cinema.metadata(actor,path.slice('/api/cinema/metadata/'.length),requestUrl.searchParams.get('candidate')));}
        if(path.startsWith('/api/cinema/art/')&&req.method==='GET'){rate(req,'media-art',120);await cinema.art(actor,path.slice('/api/cinema/art/'.length),requestUrl.searchParams.get('candidate'),res);return;}
        if(path==='/api/cinema/settings' && req.method==='POST'){const b=await json(req);return send(await cinema.configure(actor,b,b.execution));}
        if(path==='/api/cinema/playlist' && req.method==='POST'){const b=await json(req);return send(b.clear===true?await cinema.clear(actor,b.execution):await cinema.import(actor,b,b.execution));}
        if(path.startsWith('/api/cinema/play/')&&['GET','HEAD'].includes(req.method)){await cinema.play(actor,path.slice('/api/cinema/play/'.length),new URL(req.url,allowed).searchParams.get('part'),req,res);return;}
        if(path==='/api/life/sources' && req.method==='GET') return send(life.list(actor));
        if(path==='/api/life/preview' && req.method==='POST'){rate(req,'life-read',12);return send(await life.preview(actor,await json(req)));}
        if(path==='/api/life/publish' && req.method==='POST'){rate(req,'write',60);const b=await json(req);return send(await life.publish(actor,b,b.execution));}
        if(path==='/api/devices' && req.method==='GET') {if(actor.role!=='owner')fail('只有管理员可查看设备',403);return send(Object.entries(store.read().sessions).filter(([,s])=>s.expiresAt>Date.now()).map(([id,s])=>({id,...s,current:id===actor.id})));}
        if(path==='/api/devices/revoke' && req.method==='POST'){await auth.revoke(actor,(await json(req)).id);return send({ok:true});}
        if(path==='/api/pair/approve' && req.method==='POST'){return send(await auth.approve(actor,(await json(req)).code));}
        if(path==='/api/write' && req.method==='POST') {
          rate(req,'write',60);const b=await json(req);
          if(['photo.add','life.publish'].includes(b.operation)||b.operation?.startsWith('cinema.')) fail('此操作需通过对应的配置、预览或上传入口');
          return send(await service.write(actor,b.operation,b.input,b.execution));
        }
        if(path==='/api/photos' && req.method==='POST') {
          if(!['owner','editor'].includes(actor.role))fail('无上传权限',403);
          rate(req,'upload',12);const b=await json(req);
          const asset=await images.save(b.data);
          return send(await service.write(actor,'photo.add',{asset,title:b.title,caption:b.caption},b.execution));
        }
        if(path.startsWith('/media/') && req.method==='GET') {
          const asset=path.slice(7);
          if(!/^[a-f0-9]{64}\.jpg$/.test(asset) || !service.read().photos.some(p=>p.asset===asset))fail('照片不存在',404);
          const bytes=await readFile(join(images.directory,asset));res.writeHead(200,{'Content-Type':'image/jpeg'});return res.end(bytes);
        }
        fail('接口不存在',404,'not_found');
      }
      if(req.method!=='GET' && req.method!=='HEAD')fail('方法不支持',405);
      const pages={'/':'tv.html','/tv':'tv.html','/tv-lite':'lite/index.html','/diagnostics':'diagnostics/index.html','/studio':'studio.html','/film':'film.html','/cinema':'cinema/index.html','/guide':'guide/index.html','/nas-setup':'nas-setup/index.html'};
      let file;
      if(pages[path])file=join(root,'public',pages[path]);
      else if(path==='/vendor/hls.mjs')file=join(root,'node_modules/hls.js/dist/hls.mjs');
      else if(['/shared/model.mjs','/shared/entry-contract.mjs'].includes(path))file=join(root,path.slice(1));
      else {
        const decoded=decodeURIComponent(path);
        file=resolve(root,'public','.'+decoded);
        if(!file.startsWith(join(root,'public')+'/') || !types[extname(file)])fail('资源不存在',404);
      }
      const info=await stat(file);if(!info.isFile())fail('资源不存在',404);
      res.writeHead(200,{'Content-Type':types[extname(file)] || 'application/octet-stream'});
      res.end(req.method==='HEAD'?undefined:await readFile(file));
    } catch(error) {
      if(res.headersSent){res.end();return;}
      const status=error instanceof DomainError?error.status:error.code==='ENOENT'?404:500;
      send({code:error.code==='ENOENT'?'not_found':error instanceof DomainError?error.code:'internal_error',message:status===500?'服务暂时不可用，请稍后重试':status===404?'内容不存在':error.message},status);
    }
  });
  server.requestTimeout=20_000;server.headersTimeout=15_000;
  return server;
}
