import {readFile} from 'node:fs/promises';
import {fail,text} from '../../shared/model.mjs';
// First-party, read-only bridge. Vendor OAuth and Xiaomi tokens stay in Home Assistant.
const safeId=v=>typeof v==='string'&&/^[a-z0-9][a-z0-9_-]{0,39}$/.test(v);
const attributes=new Set(['current_temperature','temperature','current_humidity','humidity','battery_level','brightness','percentage','fan_mode','hvac_action','preset_mode']);
export function validateHomeAssistant(s){
 let url;try{url=new URL(s.url);}catch{fail('Home Assistant 地址无效');}
 if(!['http:','https:'].includes(url.protocol)||url.username||url.password||url.search||url.hash||url.pathname!=='/')fail('Home Assistant 需根地址');
 if(url.protocol==='http:'&&!['localhost','127.0.0.1','[::1]'].includes(url.hostname)&&s.allowInsecureLan!==true)fail('局域网 HTTP 需显式 allowInsecureLan');
 if(!/^[A-Z][A-Z0-9_]{0,79}$/.test(s.bearerEnv||''))fail('Home Assistant 凭证需引用环境变量');
 if(!Array.isArray(s.queries)||s.queries.length<1||s.queries.length>4)fail('Home Assistant 需1至4组固定设备查询');
 const ids=new Set();let count=0;
 for(const q of s.queries){if(!safeId(q.id)||ids.has(q.id))fail('设备查询标识重复或无效');ids.add(q.id);text(q.label,48,'查询名称');
  if(!Array.isArray(q.entities)||!q.entities.length||q.entities.length>6)fail('每组需1至6个设备字段');
  const fields=new Set();for(const e of q.entities){count++;if(!safeId(e.id)||fields.has(e.id)||!/^([a-z][a-z0-9_]*)\.([a-z0-9_]{1,100})$/.test(e.entityId||'')||(e.attribute&&!attributes.has(e.attribute)))fail('设备字段配置无效');fields.add(e.id);text(e.title,24,'设备名称');text(e.unit||'',8,'单位',true);if(!Number.isInteger(e.ttlSeconds)||e.ttlSeconds<60||e.ttlSeconds>86400)fail('设备快照需设置60至86400秒有效期');}
 }
 if(count>24)fail('最多24个固定设备字段');return structuredClone(s);
}
export function homeAssistantQuery(q){return {...q,tiles:q.entities.map(e=>({id:e.id,title:e.title,unit:e.unit||'',category:'home',ttlSeconds:e.ttlSeconds,valuePath:'fields.'+e.id}))};}
export async function loadHomeAssistant(filename){
 if(!filename)return [];const bytes=await readFile(filename);if(bytes.length>32768)throw new Error('设备配置超过32KB');const data=JSON.parse(bytes);
 if(data.format!=='aperture-home-assistant/v1')throw new Error('设备配置版本无效');
 return [{...data,id:'home-assistant',label:data.label||'家里的米家与设备',catalogId:'home-assistant',transport:'home-assistant',readOnlyReviewed:true}];
}
async function boundedJson(response){
 if(!response.ok){await response.body?.cancel();throw new Error('HA response unavailable');}
 const reader=response.body?.getReader();if(!reader)throw new Error('HA empty response');let size=0;const chunks=[];
 try{while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>65536)throw new Error('HA response too large');chunks.push(value);}}finally{await reader.cancel().catch(()=>{});}
 return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}
export async function readHomeAssistant(s,q,{signal}){
 const token=process.env[s.bearerEnv];if(!token)throw new Error('HA token unavailable');const fields={},states=new Map();
 for(const e of q.entities){
  if(!states.has(e.entityId)){
   const url=new URL('/api/states/'+encodeURIComponent(e.entityId),s.url);
   const response=await fetch(url,{method:'GET',headers:{Authorization:'Bearer '+token,Accept:'application/json'},redirect:'error',signal});
   const data=await boundedJson(response);if(data.entity_id!==e.entityId)throw new Error('HA entity mismatch');states.set(e.entityId,data);
  }
  const data=states.get(e.entityId);if(['unavailable','unknown'].includes(data.state)){fields[e.id]='离线 / 状态未知';continue;}
  // Deliberately omit last_updated: HA state changes are not sensor observation timestamps.
  fields[e.id]=e.attribute?data.attributes?.[e.attribute]:({on:'开启',off:'关闭',home:'在家',not_home:'离家'}[data.state]||data.state);
 }
 return {fields};
}
