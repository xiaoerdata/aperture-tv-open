/** Runs on the configuring phone/local client, never in the TV WebView. No storage or network. */
const b64=bytes=>{
  let text='';for(const byte of new Uint8Array(bytes))text+=String.fromCharCode(byte);
  return btoa(text).replaceAll('+','-').replaceAll('/','_').replace(/=+$/,'');
};
const decode=value=>Uint8Array.from(atob(value.replaceAll('-','+').replaceAll('_','/')),c=>c.charCodeAt(0));
export async function sealNas(request,draft,cryptoProvider=globalThis.crypto){
  if(!cryptoProvider?.subtle)throw new Error('请使用可信HTTPS页面配置NAS');
  const publicBytes=decode(request.publicKey);
  const fingerprint=[...new Uint8Array(await cryptoProvider.subtle.digest('SHA-256',publicBytes))].map(v=>v.toString(16).padStart(2,'0')).join('');
  if(fingerprint!==request.fingerprint)throw new Error('电视公钥校验失败，请重新扫码');
  const clear=new TextEncoder().encode(JSON.stringify(draft));if(clear.length>8176)throw new Error('资料过长');
  const key=await cryptoProvider.subtle.generateKey({name:'AES-GCM',length:256},true,['encrypt']);
  const iv=cryptoProvider.getRandomValues(new Uint8Array(12));
  const publicKey=await cryptoProvider.subtle.importKey('spki',publicBytes,{name:'RSA-OAEP',hash:'SHA-256'},false,['encrypt']);
  const raw=new Uint8Array(await cryptoProvider.subtle.exportKey('raw',key));
  try{
    const ciphertext=await cryptoProvider.subtle.encrypt({name:'AES-GCM',iv,additionalData:new TextEncoder().encode('aperture.nas.setup.v1:'+request.id)},key,clear);
    return {version:1,key:b64(await cryptoProvider.subtle.encrypt({name:'RSA-OAEP'},publicKey,raw)),iv:b64(iv),ciphertext:b64(ciphertext)};
  }finally{clear.fill(0);raw.fill(0);}
}
