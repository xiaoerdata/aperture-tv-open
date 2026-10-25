// Optional local UDP magic packet. This does not enable ADB, bypass standby policy or guarantee wake support.
import {createSocket} from 'node:dgram';
import {isIP} from 'node:net';
import {isPrivatePeer} from '../server/local-agent/network.mjs';
import {magicPacket} from '../shared/wake-on-lan.mjs';
const args=process.argv.slice(2),arg=k=>args[args.indexOf(k)+1];
if(!args.includes('--mac')||!args.includes('--broadcast'))throw new Error('Usage: node scripts/wake-tv.mjs --mac AA:BB:CC:DD:EE:FF --broadcast 192.168.1.255 [--dry-run]');
const packet=magicPacket(arg('--mac')),host=arg('--broadcast');
if(isIP(host)!==4||!isPrivatePeer(host))throw new Error('Use the actual IPv4 subnet broadcast on your private home network');
if(args.includes('--dry-run'))console.log('Validated 102-byte wake packet; nothing sent.');
else{const socket=createSocket('udp4');try{await new Promise((resolve,reject)=>{socket.once('error',reject);socket.bind(0,()=>{socket.setBroadcast(true);socket.send(packet,9,host,error=>error?reject(error):resolve());});});console.log('Wake packet sent. Check whether the television wakes; ADB availability is a separate check.');}finally{socket.close();}}
