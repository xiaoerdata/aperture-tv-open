import dgram from 'node:dgram';
const service='_kaishi-tv._tcp.local';
export function query(host=service,type=12){if(typeof host!=='string'||host.length>253||![1,12,16,33].includes(type)||host.split('.').some(x=>!x||x.length>63||!/^[a-z0-9_-]+$/i.test(x)))throw Error('Invalid DNS query');const parts=host.split('.').map(x=>Buffer.concat([Buffer.from([x.length]),Buffer.from(x)]));return Buffer.concat([Buffer.alloc(4),Buffer.from([0,1]),Buffer.alloc(6),...parts,Buffer.from([0,0,type,0,1])]);}
export function records(bytes){
 if(bytes.length<12||bytes.length>9000)throw Error('Invalid mDNS packet');
 function name(offset,visited=new Set()){
  if(visited.has(offset)||visited.size>30)throw Error('DNS pointer cycle');visited.add(offset);let labels=[],end;
  while(true){if(offset>=bytes.length)throw Error('DNS bounds');const count=bytes[offset++];if(!count){end??=offset;break;}if((count&192)===192){if(offset>=bytes.length)throw Error('DNS pointer');const target=((count&63)<<8)|bytes[offset++];labels.push(name(target,new Set(visited)).value);end??=offset;break;}if(count>63||offset+count>bytes.length)throw Error('DNS label');labels.push(bytes.subarray(offset,offset+count).toString());offset+=count;}
  return {value:labels.join('.').toLowerCase(),end};
 }
 let offset=12;const questions=bytes.readUInt16BE(4),total=bytes.readUInt16BE(6)+bytes.readUInt16BE(8)+bytes.readUInt16BE(10);if(questions>32||total>128)throw Error('DNS record limit');
 for(let i=0;i<questions;i++){offset=name(offset).end+4;if(offset>bytes.length)throw Error('DNS question');}
 const out=[];for(let i=0;i<total;i++){const n=name(offset);offset=n.end;if(offset+10>bytes.length)throw Error('DNS record');const type=bytes.readUInt16BE(offset),length=bytes.readUInt16BE(offset+8),begin=offset+10,end=begin+length;offset=end;if(end>bytes.length)throw Error('DNS data');let value;
  if(type===12)value=name(begin).value;
  if(type===33){if(length<7)throw Error('DNS service');value={port:bytes.readUInt16BE(begin+4),host:name(begin+6).value};}
  if(type===16){value={};for(let p=begin;p<end;){const size=bytes[p++];if(p+size>end)throw Error('DNS TXT');const text=bytes.subarray(p,p+size).toString();p+=size;const at=text.indexOf('=');if(at>0)value[text.slice(0,at)]=text.slice(at+1);}}
  if(type===1&&length===4)value=[...bytes.subarray(begin,end)].join('.');if(value!==undefined)out.push({name:n.value,type,value});
 }return out;
}
export async function discover(milliseconds=3000){
 const cache=new Map(),requested=new Set(),socket=dgram.createSocket({type:'udp4',reuseAddr:true});
 return await new Promise((resolve,reject)=>{let timer;const finish=()=>{clearTimeout(timer);try{socket.close();}catch{}const devices=[];for(const r of cache.values())if(r.type===33&&r.name.endsWith('.'+service)){const txt=cache.get(r.name+':16')?.value||{},host=cache.get(r.value.host+':1')?.value;if(host)devices.push({name:r.name,host,port:r.value.port,...txt,authenticated:false});}resolve(devices);};socket.on('error',error=>{clearTimeout(timer);try{socket.close();}catch{}reject(error);});socket.on('message',data=>{try{for(const r of records(data)){if(cache.size<256||cache.has(r.name+':'+r.type))cache.set(r.name+':'+r.type,r);if(r.type===33&&r.name.endsWith('.'+service)&&requested.size<32&&!requested.has(r.value.host)){requested.add(r.value.host);socket.send(query(r.value.host,1),5353,'224.0.0.251');socket.send(query(r.name,16),5353,'224.0.0.251');}}}catch{}});socket.bind(5353,()=>{try{socket.addMembership('224.0.0.251');socket.setMulticastTTL(1);socket.send(query(),5353,'224.0.0.251');timer=setTimeout(finish,milliseconds);}catch(error){socket.close();reject(error);}});});
}
