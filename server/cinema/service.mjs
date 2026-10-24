import {readdir,realpath,stat,open} from 'node:fs/promises';
import {join,extname,basename,sep} from 'node:path';
import {randomUUID,createHash} from 'node:crypto';
import {Readable} from 'node:stream';
import {pipeline} from 'node:stream/promises';
import {fail} from '../../shared/model.mjs';
import {validateMediaConfig,parsePlaylist} from './config.mjs';
import {createHealthMonitor} from './health.mjs';
const mime={'.mp4':'video/mp4','.m4v':'video/mp4','.webm':'video/webm','.mkv':'video/x-matroska','.mov':'video/quicktime'};
const hash=v=>createHash('sha256').update(v).digest('hex').slice(0,32);
export function parseRange(range,size){
  if(!range)return {start:0,end:size-1,status:200};
  const m=/^bytes=(\d*)-(\d*)$/.exec(range);if(!m||(!m[1]&&!m[2])||size===0)return null;
  let start,end;if(!m[1]){const suffix=Number(m[2]);if(!Number.isSafeInteger(suffix)||suffix<1)return null;start=Math.max(0,size-suffix);end=size-1;}else{start=Number(m[1]);end=m[2]?Math.min(Number(m[2]),size-1):size-1;}
  if(!Number.isSafeInteger(start)||!Number.isSafeInteger(end)||start<0||start>=size||end<start)return null;
  return {start,end,status:206};
}
export function createCinemaService({store,service,config}){
  const media=validateMediaConfig(config),items=new Map(),parts=new Map();let streams=0,browsing=0;
  function session(actor,owner=false){const active=store.read().sessions[actor.id];if(!active||active.expiresAt<=Date.now())fail('连接已失效',401,'unauthorized');if(owner&&(actor.role!=='owner'||active.role!=='owner'))fail('影音配置仅管理员可修改',403,'forbidden');}
  function enabled(actor){session(actor);if(!store.read().board.cinema?.enabled)fail('家庭影院尚未开启',403,'cinema_disabled');}
  const activeSources=()=>store.read().board.cinema?.sourceIds||[];
  const health=createHealthMonitor({config:media,snapshot:()=>({enabled:!!store.read().board.cinema?.enabled,sourceIds:activeSources(),channels:store.read().mediaChannels||[]})});
  function remember(item){if(items.size>=4096)items.delete(items.keys().next().value);items.set(item.id,item);return {id:item.id,sourceId:item.sourceId,name:item.name,kind:item.kind||'video',format:item.format||'',sourceName:item.sourceName,viewKind:item.viewKind||'movie',health:item.type==='remote'?health.channel(item.url):health.source(item.sourceId)};}
  async function upstream(url,options={}){try{const r=await fetch(url,{...options,redirect:'error',signal:options.signal||AbortSignal.timeout(8000)});if(!r.ok)fail('媒体来源暂时不可用',502,'media_upstream');return r;}catch(e){if(e.status)throw e;fail('无法连接媒体来源',502,'media_upstream');}}
  function tokenHeaders(s){const token=process.env[s.tokenEnv];if(!token)fail('媒体库缺少服务端凭证',503,'missing_credentials');return {'X-Emby-Token':token};}
  function jellyUrl(s,path,params={}){const u=new URL(s.url.replace(/\/$/,'')+'/'+path);for(const [k,v] of Object.entries(params))u.searchParams.set(k,String(v));return u;}
  async function boundedText(response,max){const reader=response.body.getReader();let size=0;const chunks=[];while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>max){await reader.cancel();fail('媒体来源返回内容过大',413);}chunks.push(value);}return Buffer.concat(chunks).toString('utf8');}
  function partUrl(item,url){const u=new URL(url);if(!media.allowedOrigins.includes(u.origin)||u.username||u.password)fail('直播分片来源尚未授权',403,'media_origin_denied');if(parts.size>=4096)parts.delete(parts.keys().next().value);const id=randomUUID();parts.set(id,{itemId:item.id,url:u.href,until:Date.now()+3600000});return `/api/cinema/play/${item.id}?part=${id}`;}
  return {
    options(actor){session(actor,true);return {sources:media.sources.map(({id,name,type})=>({id,name,type})),allowedOrigins:media.allowedOrigins,playlistCount:(store.read().mediaChannels||[]).length};},
    async configure(actor,input,execution){
      session(actor,true);if(typeof input.enabled!=='boolean'||!Array.isArray(input.sourceIds)||input.sourceIds.some(id=>!media.sources.some(s=>s.id===id)))fail('请选择已配置的媒体库');
      const kinds=input.sourceKinds||{};if(!kinds||typeof kinds!=='object'||Array.isArray(kinds)||Object.entries(kinds).some(([id,kind])=>!input.sourceIds.includes(id)||!['auto','movie','series'].includes(kind)))fail('媒体分类无效');
      const result=await service.write(actor,'cinema.settings',{enabled:input.enabled,sourceIds:[...new Set(input.sourceIds)],sourceKinds:kinds},execution);if(!result.replayed)health.invalidate();return result;
    },
    async import(actor,input,execution){session(actor,true);const channels=parsePlaylist(input.playlist,media.allowedOrigins);const result=await service.write(actor,'cinema.playlist',{channels},execution);if(!result.replayed)health.invalidate();return result;},
    async clear(actor,execution){session(actor,true);const result=await service.write(actor,'cinema.playlist',{channels:[]},execution);if(!result.replayed)health.invalidate();return result;},
    health(actor){session(actor,true);return health.status();},
    check(actor){session(actor,true);return health.start();},
    close(){health.close();},
    async library(actor){
      enabled(actor);if(browsing>=2)fail('媒体库正在读取，请稍后再试',429);browsing++;
      try{
        const result=[],unavailable=[];
        for(const s of media.sources.filter(s=>activeSources().includes(s.id))){
          try{
            if(s.type==='local'){
              const root=await realpath(s.path);let visited=0,count=0;
              async function walk(path,depth){if(depth>5||visited>5000||count>=200)return;for(const ent of await readdir(path,{withFileTypes:true})){if(++visited>5000||count>=200)break;if(ent.isSymbolicLink()||ent.name.startsWith('.'))continue;const filename=join(path,ent.name);if(ent.isDirectory())await walk(filename,depth+1);else if(ent.isFile()&&mime[extname(ent.name).toLowerCase()]){count++;const ext=extname(ent.name).toLowerCase();result.push(remember({id:hash(s.id+':'+filename),sourceId:s.id,sourceName:s.name,name:basename(ent.name,ext),format:ext.slice(1),viewKind:store.read().board.cinema?.sourceKinds?.[s.id]==='series'?'series':'movie',filename,root,type:'local'}));}}}
              await walk(root,0);
            }else{
              const response=await upstream(jellyUrl(s,`Users/${s.userId}/Items`,{ParentId:s.libraryId,Recursive:true,IncludeItemTypes:'Movie,Episode,Video',Limit:200,Fields:'MediaSources',SortBy:'SortName'}),{headers:tokenHeaders(s)});
              const data=JSON.parse(await boundedText(response,2_000_000));
              for(const video of (data.Items||[]).slice(0,200)){if(!/^[-a-fA-F0-9]{1,64}$/.test(video.Id||''))continue;result.push(remember({id:hash(s.id+':'+video.Id),sourceId:s.id,sourceName:s.name,name:String(video.Name||'未命名视频').slice(0,80),format:String(video.Container||video.MediaSources?.[0]?.Container||'').slice(0,15),type:'jellyfin',viewKind:store.read().board.cinema?.sourceKinds?.[s.id]==='series'?'series':store.read().board.cinema?.sourceKinds?.[s.id]==='movie'?'movie':video.Type==='Episode'?'series':'movie',videoId:video.Id}));}
            }
          }catch{unavailable.push(s.name);}
        }
        for(const [i,channel] of (store.read().mediaChannels||[]).entries())result.push(remember({id:hash('playlist:'+channel.url),sourceId:'playlist',sourceName:'我的播放清单',name:channel.name,format:new URL(channel.url).pathname.endsWith('.m3u8')?'hls':'直链',type:'remote',url:channel.url,kind:channel.viewKind==='movie'||channel.viewKind==='series'?'video':'channel',viewKind:channel.viewKind||'live'}));
        enabled(actor);return {items:result,unavailable,limitPerSource:200};
      }finally{browsing--;}
    },
    async play(actor,id,part,req,res){
      enabled(actor);const item=items.get(id);if(!item)fail('视频索引已失效，请刷新媒体库',404);
      if(item.type==='remote'){if(!(store.read().mediaChannels||[]).some(c=>c.url===item.url))fail('播放清单已移除',403);}
      else if(!activeSources().includes(item.sourceId))fail('媒体库已取消分享',403);
      if(streams>=4)fail('同时播放数量已达上限',429);streams++;
      const abort=new AbortController(),revoked=()=>{try{enabled(actor);if(item.type==='remote'&&!(store.read().mediaChannels||[]).some(c=>c.url===item.url))throw new Error();if(item.type!=='remote'&&!activeSources().includes(item.sourceId))throw new Error();}catch{abort.abort();res.destroy();}};
      const timer=setInterval(revoked,2000);const deadline=setTimeout(()=>abort.abort(),4*3600000);req.on('aborted',()=>abort.abort());res.on('close',()=>abort.abort());let file;
      try{
        if(item.type==='local'){
          const path=await realpath(item.filename);if(!path.startsWith(item.root+sep))fail('文件不在共享目录内',403);
          file=await open(path,'r');const info=await file.stat();if(!info.isFile())fail('视频不存在',404);const range=parseRange(req.headers.range,info.size);
          if(!range){res.writeHead(416,{'Content-Range':`bytes */${info.size}`});res.end();return;}
          res.writeHead(range.status,{'Content-Type':mime[extname(path).toLowerCase()],'Accept-Ranges':'bytes','Content-Length':Math.max(0,range.end-range.start+1),...(range.status===206?{'Content-Range':`bytes ${range.start}-${range.end}/${info.size}`}:{})});
          if(req.method==='HEAD'||info.size===0){res.end();return;}await pipeline(file.createReadStream({start:range.start,end:range.end,autoClose:false}),res,{signal:abort.signal});
        }else{
          const s=media.sources.find(s=>s.id===item.sourceId);let url=item.type==='jellyfin'?jellyUrl(s,`Videos/${item.videoId}/stream`,{Static:true}):item.url;
          if(part){const selected=parts.get(part);if(item.type!=='remote'||!selected||selected.itemId!==id||selected.until<Date.now())fail('直播片段已过期，请重新播放',410);url=selected.url;}
          const headers={...(item.type==='jellyfin'?tokenHeaders(s):{}),...(req.headers.range?{Range:req.headers.range}:{})};
          const headerTimer=setTimeout(()=>abort.abort(),15000);let response;
          try{response=await upstream(url,{headers,method:req.method,signal:abort.signal});}finally{clearTimeout(headerTimer);}
          const type=response.headers.get('content-type')||'application/octet-stream';
          if(req.method!=='HEAD'&&(/mpegurl/i.test(type)||new URL(url).pathname.endsWith('.m3u8'))){
            if(item.type!=='remote')fail('此媒体库请使用直播放映格式',422);
            const playlist=await boundedText(response,262144);if(!playlist.trimStart().startsWith('#EXTM3U'))fail('直播清单格式无效',422);
            const rewritten=playlist.split(/\r?\n/).map(line=>line.startsWith('#')?line.replace(/URI="([^"]+)"/g,(_,uri)=>`URI="${partUrl(item,new URL(uri,url).href)}"`):line.trim()?partUrl(item,new URL(line.trim(),url).href):line).join('\n');
            res.writeHead(200,{'Content-Type':'application/vnd.apple.mpegurl'});res.end(rewritten);return;
          }
          if(!/^(video\/|audio\/|application\/(octet-stream|mp4))/i.test(type))fail('来源未返回视频或音频',422);
          const out={'Content-Type':type};for(const h of ['content-length','content-range','accept-ranges'])if(response.headers.has(h))out[h]=response.headers.get(h);
          res.writeHead(response.status,out);if(req.method==='HEAD'){res.end();return;}await pipeline(Readable.fromWeb(response.body),res,{signal:abort.signal});
        }
      }finally{clearInterval(timer);clearTimeout(deadline);streams--;await file?.close().catch(()=>{});}
    }
  };
}
