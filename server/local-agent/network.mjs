import {isIP} from 'node:net';
import {fail} from '../../shared/model.mjs';
export function normalizePeer(value){return value?.startsWith('::ffff:')?value.slice(7):value;}
export function isPrivatePeer(value){
  const ip=normalizePeer(value);if(isIP(ip)===4){const p=ip.split('.').map(Number);return p[0]===127||p[0]===10||(p[0]===172&&p[1]>=16&&p[1]<=31)||(p[0]===192&&p[1]===168);}
  return isIP(ip)===6&&(ip==='::1'||/^f[cd]/i.test(ip));
}
export function localPeer(req,trustedProxies=[]){
  let peer=normalizePeer(req.socket.remoteAddress);
  if(req.headers.forwarded)fail('本机助手不接受未经配置的 Forwarded 头',403,'network_denied');
  if(trustedProxies.includes(peer)){
    const forwarded=req.headers['x-forwarded-for'];
    if(typeof forwarded!=='string'||forwarded.includes(',')||!isIP(normalizePeer(forwarded.trim())))fail('可信代理须重写单一客户端地址',403,'network_denied');
    peer=normalizePeer(forwarded.trim());
  }else if(req.headers['x-forwarded-for'])fail('不接受来自非可信代理的客户端地址声明',403,'network_denied');
  if(!isPrivatePeer(peer))fail('本机助手只允许已验证的私有网络来源',403,'network_denied');return peer;
}
