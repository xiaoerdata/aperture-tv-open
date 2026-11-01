#!/usr/bin/env node
// Physical-TV authorization only. Not a Hub account/Agent entitlement, shell or silent installer.
import tls from 'node:tls';import https from 'node:https';import {isIP} from 'node:net';
import {readFile,writeFile,mkdir,rename,unlink} from 'node:fs/promises';import {dirname,resolve} from 'node:path';import {homedir} from 'node:os';import {pathToFileURL} from 'node:url';
import {discover} from '../shared/tv-discovery.mjs';import {checkPack} from './resource-pack.mjs';
export function privateHost(host){if(isIP(host)!==4)return false;const p=host.split('.').map(Number);return p[0]===127||p[0]===10||p[0]===192&&p[1]===168||p[0]===172&&p[1]>=16&&p[1]<=31;}
export async function request(config,path,{method='GET',body,token=config.token}={}){
 if(!privateHost(config.host)||!Number.isInteger(config.port)||config.port<1||config.port>65535||!/^([a-f0-9]{64})$/.test(config.fingerprint))throw Error('需要局域网IPv4地址、端口和电视上显示的完整证书SHA-256');
 const agent=new https.Agent({keepAlive:false});agent.createConnection=(options,callback)=>{let done=false;const finish=(error,socket)=>{if(!done){done=true;callback(error,socket);}};const socket=tls.connect({host:config.host,port:config.port,rejectUnauthorized:false,minVersion:'TLSv1.2'},()=>{const actual=socket.getPeerCertificate().fingerprint256?.replaceAll(':','').toLowerCase();if(actual!==config.fingerprint){socket.destroy();finish(Error('电视证书不匹配，未发送令牌或内容'));}else finish(null,socket);});socket.setTimeout(10000,()=>socket.destroy(Error('TLS连接超时')));socket.once('error',e=>finish(e));};
 const bytes=body===undefined?undefined:Buffer.from(JSON.stringify(body));if(bytes?.length>(path==='/resources'?4*1024*1024+65536:524288))throw Error('请求超过允许大小');
 try{return await new Promise((resolveResult,reject)=>{const req=https.request({host:config.host,port:config.port,path,method,agent,headers:{...(token?{Authorization:'Bearer '+token}:{}),...(bytes?{'Content-Type':'application/json','Content-Length':bytes.length}:{})}},res=>{let size=0;const chunks=[];res.on('data',b=>{size+=b.length;if(size>8*1024*1024)req.destroy(Error('响应过大'));else chunks.push(b);});res.on('end',()=>{const data=Buffer.concat(chunks);try{const result=res.headers['content-type']?.startsWith('image/png')?data:JSON.parse(data.toString());if(res.statusCode!==200)reject(Error(result.error||'电视拒绝请求'));else resolveResult(result);}catch{reject(Error('响应无效'));}});});req.setTimeout(path==='/resources'?60000:22000,()=>req.destroy(Error('请求超时；操作结果未知，先查status/ui，不自动重试')));req.on('error',reject);req.end(bytes);});}finally{agent.destroy();}
}
async function main(){
 const args=process.argv.slice(2),command=args.shift(),option=name=>{const i=args.indexOf('--'+name);return i>=0?args[i+1]:undefined;},file=resolve(option('session')||process.env.KAISHI_TV_SESSION||homedir()+'/.config/kaishi/tv-control.json');
 if(command==='discover'){console.log(JSON.stringify(await discover(3000),null,2));return;}
 if(command==='pair'){
  const config={host:option('host'),port:Number(option('port')),fingerprint:option('fingerprint')?.replaceAll(':','').toLowerCase()};const pin=option('pin');if(!/^\d{6}$/.test(pin||''))throw Error('请输入电视显示的六位配对码');
  const pending=await request(config,'/pair/request',{method:'POST',body:{pin},token:null});console.log('请在电视核对 '+pending.verification+'，并用遥控器允许连接。');
  const until=Date.now()+60000;while(Date.now()<until){await new Promise(r=>setTimeout(r,1000));const result=await request(config,'/pair/result',{method:'POST',body:{ticket:pending.ticket},token:null});if(result.state!=='paired')continue;await mkdir(dirname(file),{recursive:true,mode:0o700});const temp=file+'.'+process.pid+'.tmp';try{await writeFile(temp,JSON.stringify({...config,token:result.token,expiresAt:Date.now()+result.expiresInMs})+'\n',{flag:'wx',mode:0o600});await rename(temp,file);}finally{await unlink(temp).catch(()=>{});}console.log(JSON.stringify({paired:true,expiresInMs:result.expiresInMs,sessionFile:file}));return;}throw Error('电视确认已超时，请重新开启配对');
 }
 const config=JSON.parse(await readFile(file,'utf8'));if(config.expiresAt<=Date.now())throw Error('连接已到期，请在电视重新配对');let result;
 if(command==='status'||command==='ui')result=await request(config,'/'+command);
 else if(command==='screenshot'){const output=option('output');if(!output)throw Error('指定 --output 新PNG路径');const png=await request(config,'/screenshot');await writeFile(resolve(output),png,{flag:'wx',mode:0o600});result={saved:resolve(output),scope:'own app UI only; video surfaces excluded'};}
 else if(command==='keys'){const commands=(args[0]||'').split(',').map(key=>({type:'key',key}));result=await request(config,'/commands',{method:'POST',body:{commands}});}
 else if(command==='commands'){const commands=JSON.parse(await readFile(option('file'),'utf8'));result=await request(config,'/commands',{method:'POST',body:{commands}});}
 else if(command==='import'){const data=await readFile(option('file')),allowHttp=args.includes('--allow-lan-http');checkPack(data,{allowHttp});result=await request(config,'/resources',{method:'POST',body:{pack:JSON.parse(data),allowHttp}});}
 else if(command==='probe'){result=await request(config,'/probe',{method:'POST',body:{url:option('url'),allowHttp:args.includes('--allow-lan-http')}});}
 else if(command==='close'){result=await request(config,'/close',{method:'POST',body:{}});await unlink(file);}
 else throw Error('用法：discover | pair --host IP --port N --fingerprint SHA256 --pin 六位码 | status | ui | keys down,ok | commands --file JSON | screenshot --output 新PNG | import --file JSON | probe --url URL | close');
 console.log(JSON.stringify(result,null,2));
}
if(import.meta.url===pathToFileURL(process.argv[1]||'').href)main().catch(e=>{console.error(e.message);process.exitCode=1;});
