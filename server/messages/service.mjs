import {messageSource,messageFeed} from '../../shared/message-feed.mjs';
// Optional preview/household reader. APK has its own local reader and persistent offline cache.
export function createMessages({source='',fetcher=fetch,now=Date.now}={}) {
 const url=messageSource(source);let cached={version:1,messages:[]},pending=null,last=0,controller,closed=false;
 async function load(){controller=new AbortController();const timeout=setTimeout(()=>controller.abort(),15000);try{
  let address=url;for(let hop=0;hop<=3;hop++){
   const r=await fetcher(address,{signal:controller.signal,redirect:'manual',credentials:'omit',headers:{Accept:'application/json'}});
   if([301,302,303,307,308].includes(r.status)){await r.body?.cancel();if(hop===3||!r.headers.get('location'))throw Error();address=messageSource(new URL(r.headers.get('location'),address).href);continue;}
   if(!r.ok)throw Error();const reader=r.body.getReader();let size=0,chunks=[];try{for(;;){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>131072)throw Error();chunks.push(value);}}finally{await reader.cancel();}
   const result=messageFeed(JSON.parse(Buffer.concat(chunks).toString('utf8')));if(!closed)cached=result;return;
  }
 }catch{}finally{clearTimeout(timeout);controller=null;}}
 return {async read(){if(url&&!closed&&(!last||now()-last>=60000)){if(!pending){last=now();pending=load().finally(()=>pending=null);}await pending;}return {...cached,configured:!!url,messages:cached.messages.filter(m=>!m.expireAt||Date.parse(m.expireAt)>now())};},close(){closed=true;controller?.abort();}};
}
