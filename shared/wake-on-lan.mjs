export function magicPacket(mac){
 if(typeof mac!=='string'||!/^([0-9a-f]{2}[:-]){5}[0-9a-f]{2}$/i.test(mac))throw new Error('Use a six-byte MAC address');
 const address=Buffer.from(mac.replace(/[:-]/g,''),'hex');
 if((address[0]&1)||address.every(n=>n===0))throw new Error('Use the television unicast MAC address');
 return Buffer.concat([Buffer.alloc(6,255),...Array.from({length:16},()=>address)]);
}
