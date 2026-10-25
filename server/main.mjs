import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import {randomBytes} from 'node:crypto';
import {createApp} from './app.mjs';
import {loadHomeAssistant} from './life/home-assistant.mjs';
import {loadSources} from './life/config.mjs';
import {loadMediaConfig} from './cinema/config.mjs';
const directory=resolve(process.env.APERTURE_DATA || '.data'),port=Number(process.env.PORT || 4318),host=process.env.HOST || '127.0.0.1';
if(host!=='127.0.0.1' && !process.env.PUBLIC_ORIGIN)throw new Error('Set PUBLIC_ORIGIN before exposing the server');
await mkdir(directory,{recursive:true,mode:0o700});
const keyFile=join(directory,'owner-key');let ownerKey=process.env.APERTURE_OWNER_KEY;
if(!ownerKey){try{ownerKey=(await readFile(keyFile,'utf8')).trim();}catch(e){if(e.code!=='ENOENT')throw e;ownerKey=randomBytes(24).toString('base64url');await writeFile(keyFile,ownerKey+'\n',{mode:0o600,flag:'wx'});}}
const origin=process.env.PUBLIC_ORIGIN || `http://127.0.0.1:${port}`;
const sources=[...await loadSources(process.env.APERTURE_CONNECTIONS),...await loadHomeAssistant(process.env.APERTURE_HOME_ASSISTANT_CONFIG)];
const mediaConfig=await loadMediaConfig(process.env.APERTURE_MEDIA_CONFIG);
const {server}=await createApp({directory,ownerKey,origin,sources,mediaConfig,localAutomation:process.env.APERTURE_LOCAL_AUTOMATION==='1',trustedProxies:(process.env.APERTURE_TRUSTED_PROXIES||'').split(',').map(s=>s.trim()).filter(Boolean)});
server.listen(port,host,()=>{
  console.log(`Aperture TV: ${origin}/tv?demo=1\nFamily studio: ${origin}/studio\nOwner key is stored privately in ${keyFile}; never share it in a recording.`);
  process.send?.({type:'aperture-ready',origin});
});

for(const signal of ['SIGINT','SIGTERM'])process.once(signal,()=>{
  server.close(()=>process.exit(0));
  setTimeout(()=>process.exit(0),10000).unref();
});
