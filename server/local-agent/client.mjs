import {readFile,stat} from 'node:fs/promises';
import {isIP} from 'node:net';
import {isPrivatePeer} from './network.mjs';
export function originURL(value,{allowLanHttp=false}={}){
 const u=new URL(value);
 if(u.origin!==value||u.username||u.password||!['https:','http:'].includes(u.protocol))throw new Error('Use a server origin without path or credentials');
 if(u.protocol==='http:'&&!['127.0.0.1','localhost','[::1]'].includes(u.hostname)){
  const host=u.hostname.replace(/^\[|\]$/g,'');
  if(!allowLanHttp||!isIP(host)||!isPrivatePeer(host))throw new Error('Use trusted HTTPS; private-IP HTTP development requires --allow-lan-http');
 }
 return u.origin;
}
export async function loadConnection(filename){const info=await stat(filename);if(process.platform!=='win32'&&(info.mode&0o077))throw new Error('Connection file must be private (chmod 600)');const data=JSON.parse(await readFile(filename,'utf8'));originURL(data.origin,{allowLanHttp:data.allowLanHttp===true});if(!/^[a-zA-Z0-9_-]{40,100}$/.test(data.token||''))throw new Error('Connection token missing');return data;}
export async function localRequest(connection,path,body){
  originURL(connection.origin,{allowLanHttp:connection.allowLanHttp===true});
  const r=await fetch(connection.origin+path,{method:body===undefined?'GET':'POST',headers:{...(connection.token?{Authorization:'Bearer '+connection.token}:{}),'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),redirect:'error',signal:AbortSignal.timeout(10000)});
  const result=await r.json();if(!r.ok){const error=new Error(result.message||'Request failed');error.code=result.code;throw error;}return result;
}
