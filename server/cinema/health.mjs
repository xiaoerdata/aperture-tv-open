import {open,opendir,realpath} from 'node:fs/promises';
import {join,extname,sep} from 'node:path';
import {createHash} from 'node:crypto';
const digest=value=>createHash('sha256').update(value).digest('hex').slice(0,32);
export const channelHealthId=url=>'channel:'+digest(url);
const outcomes={readable:'媒体数据可读取，编码仍需电视确认',reachable:'媒体库可访问，具体影片播放待确认',empty:'来源可访问，但没有可读取的视频',auth_required:'需要在后台检查来源登录或授权',unavailable:'来源暂时不可用',timeout:'读取超时',invalid_media:'返回的不是可识别的媒体',origin_denied:'清单包含尚未批准的来源',redirect_blocked:'来源要求跳转，请在后台核对地址',format_unverified:'地址返回媒体，但容器格式尚未确认'};
class ProbeError extends Error {constructor(status){super(status);this.status=status;}}
async function sample(response,max,complete=false){
 if(!response.body)throw new ProbeError('invalid_media');
 const reader=response.body.getReader(),chunks=[];let size=0;
 try{while(true){const {done,value}=await reader.read();if(done)break;if(size+value.length>max){if(complete)throw new ProbeError('invalid_media');chunks.push(value.subarray(0,max-size));size=max;break;}chunks.push(value);size+=value.length;if(size===max&&!complete)break;}}
 finally{await reader.cancel().catch(()=>{});}
 return Buffer.concat(chunks);
}
const recognizable=b=>(b.length>=8&&['ftyp','styp','moof','moov'].includes(b.subarray(4,8).toString()))||(b.length>=4&&b.subarray(0,4).equals(Buffer.from([0x1a,0x45,0xdf,0xa3])))||(b.length>188&&b[0]===0x47&&b[188]===0x47)||(b.length>=3&&b.subarray(0,3).toString()==='ID3');
function approved(value,base,origins){let url;try{url=new URL(value,base);}catch{throw new ProbeError('invalid_media');}if(url.username||url.password||url.hash||!origins.includes(url.origin))throw new ProbeError('origin_denied');return url.href;}
async function request(url,signal,headers={}){
 const response=await fetch(url,{signal,headers,redirect:'manual'});
 if(!response.ok){await response.body?.cancel();throw new ProbeError(response.status===401||response.status===403?'auth_required':response.status>=300&&response.status<400?'redirect_blocked':'unavailable');}
 return response;
}
export async function probeRemote(url,{origins,signal,headers={},depth=0}={}){
 url=approved(url,url,origins);const response=await request(url,signal,{...headers,Range:'bytes=0-65535'}),type=response.headers.get('content-type')||'';
 const hls=/mpegurl/i.test(type)||new URL(url).pathname.toLowerCase().endsWith('.m3u8');
 if(hls){
  const text=(await sample(response,65536,true)).toString('utf8').replace(/^\uFEFF/,'');
  if(!text.startsWith('#EXTM3U')||depth>=3||(!text.includes('#EXT-X-STREAM-INF:')&&!(text.includes('#EXTINF:')&&text.includes('#EXT-X-TARGETDURATION:'))))throw new ProbeError('invalid_media');
  const lines=text.split(/\r?\n/).map(s=>s.trim());
  for(const line of lines){if(line&&!line.startsWith('#'))approved(line,url,origins);for(const match of line.matchAll(/URI="([^"]+)"/g))approved(match[1],url,origins);}
  const first=lines.find(s=>s&&!s.startsWith('#'));if(!first)throw new ProbeError('empty');
  return probeRemote(approved(first,url,origins),{origins,signal,headers,depth:depth+1});
 }
 const bytes=await sample(response,8192);
 if(!bytes.length||/^\s*(?:<!doctype|<html|<form|\{\s*"(?:error|message)")/i.test(bytes.subarray(0,256).toString()))throw new ProbeError('invalid_media');
 if(recognizable(bytes))return 'readable';
 if(/^(video\/|audio\/|application\/(octet-stream|mp4))/i.test(type))return 'format_unverified';
 throw new ProbeError('invalid_media');
}
async function probeLocal(source,signal){
 const root=await realpath(source.path);let visited=0;
 async function find(path,depth){if(depth>4||visited>=300||signal.aborted)return null;for await(const e of await opendir(path)){if(signal.aborted||++visited>300)break;if(e.isSymbolicLink()||e.name.startsWith('.'))continue;const file=join(path,e.name);if(e.isDirectory()){const found=await find(file,depth+1);if(found)return found;}else if(e.isFile()&&['.mp4','.m4v','.webm','.mkv','.mov'].includes(extname(file).toLowerCase()))return file;}return null;}
 const candidate=await find(root,0);if(!candidate)return 'empty';const path=await realpath(candidate);if(!path.startsWith(root+sep))throw new ProbeError('unavailable');
 const file=await open(path,'r');try{const data=Buffer.alloc(8192),{bytesRead}=await file.read(data,0,data.length,0);return bytesRead?'reachable':'empty';}finally{await file.close();}
}
export function createHealthMonitor({config,snapshot,intervalMs=900000,cooldownMs=30000,probeTimeoutMs=6000}){
 let results=new Map(),running=false,startedAt=null,completedAt=null,controller=null,closed=false,generation=0,lastStart=0;
 const sourceId=s=>'source:'+s.id;
 function targets(){const state=snapshot();if(!state.enabled)return [];return [...config.sources.filter(s=>state.sourceIds.includes(s.id)).map(s=>({id:sourceId(s),name:s.name,kind:'source',source:s})),...state.channels.map(c=>({id:channelHealthId(c.url),name:c.name,kind:'channel',channel:c}))];}
 function status(){const current=targets();return {running,startedAt,completedAt,items:current.map(t=>({id:t.id,name:t.name,kind:t.kind,...(results.get(t.id)||{status:'untested',detail:'尚未检测',checkedAt:null})}))};}
 async function check(target,signal){
  try{
   let outcome;
   if(target.channel)outcome=await probeRemote(target.channel.url,{origins:config.allowedOrigins,signal});
   else if(target.source.type==='local')outcome=await probeLocal(target.source,signal);
   else{
    const s=target.source,token=process.env[s.tokenEnv];if(!token)throw new ProbeError('auth_required');
    const url=new URL(s.url.replace(/\/$/,'')+`/Users/${s.userId}/Items`);url.searchParams.set('ParentId',s.libraryId);url.searchParams.set('Limit','1');
    const response=await request(url,signal,{'X-Emby-Token':token}),data=JSON.parse((await sample(response,131072,true)).toString());
    if(!Array.isArray(data.Items))throw new ProbeError('invalid_media');outcome=data.Items.length?'reachable':'empty';
   }
   return {status:outcome,detail:outcomes[outcome],checkedAt:new Date().toISOString()};
  }catch(e){const status=signal.aborted?'timeout':e instanceof ProbeError?e.status:'unavailable';return {status,detail:outcomes[status]||outcomes.unavailable,checkedAt:new Date().toISOString()};}
 }
 function start(force=false){
  if(closed||running||(!force&&Date.now()-lastStart<cooldownMs))return status();const jobs=targets();if(!jobs.length)return status();
  const turn=++generation;controller=new AbortController();const root=controller;running=true;lastStart=Date.now();startedAt=new Date().toISOString();completedAt=null;
  for(const t of jobs)results.set(t.id,{status:'checking',detail:'正在读取媒体样本',checkedAt:null});
  let next=0;
  const worker=async()=>{while(!closed&&!root.signal.aborted&&next<jobs.length){const t=jobs[next++],timeout=AbortSignal.timeout(probeTimeoutMs),signal=AbortSignal.any([root.signal,timeout]);const result=await check(t,signal);if(turn===generation&&!root.signal.aborted)results.set(t.id,result);}};
  Promise.all([worker(),worker()]).finally(()=>{if(turn===generation){running=false;controller=null;completedAt=new Date().toISOString();}}).catch(()=>{});
  return status();
 }
 function invalidate(){generation++;controller?.abort();controller=null;running=false;lastStart=0;results=new Map();start();}
 const timer=setInterval(()=>start(),intervalMs);timer.unref();const initial=setTimeout(()=>start(),0);initial.unref();
 return {status,start,invalidate,source:id=>results.get('source:'+id)||null,channel:url=>results.get(channelHealthId(url))||null,close(){closed=true;generation++;controller?.abort();clearInterval(timer);clearTimeout(initial);}};
}
