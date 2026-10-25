import {createHash,timingSafeEqual} from 'node:crypto';
import {fail} from '../../shared/model.mjs';
const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/;
const pkg=/^[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+$/;
const key=/^[A-Za-z0-9_-]{8,100}$/;
const sha=/^[a-f0-9]{64}$/;
const kinds=['install','uninstall','resources'];
const permission=kind=>kind==='resources'?'resources.configure':'apps.'+kind;
const terminal=new Set(['succeeded','cancelled','failed','unknown']);
const hash=x=>createHash('sha256').update(x).digest('hex');
const fields=(value,expected)=>value&&typeof value==='object'&&!Array.isArray(value)&&Object.keys(value).sort().join(',')===expected.split(',').sort().join(',');
const equal=(a,b)=>typeof a==='string'&&typeof b==='string'&&a.length===b.length&&timingSafeEqual(Buffer.from(a),Buffer.from(b));
/** Foreground TV grants shared by application operations and resource imports; one operation per window. */
export function createAppOperations({store,now=Date.now}) {
 const windows=new Map();
 function session(actor){const s=store.read().sessions[actor?.id];if(!s||s.expiresAt<=now()||s.role!==actor.role)fail('会话已过期或撤销',401,'unauthorized');return s;}
 function scope(actor,kind){const s=session(actor);if(!kinds.includes(kind))fail('操作类型无效');if(s.role!=='owner'&&!(s.role==='controller'&&s.scopes?.includes(permission(kind))))fail('尚未授权此设备操作',403,'scope_denied');}
 function prune(){for(const [id,r]of windows)if(r.expiresAt<=now())windows.delete(id);}
 function read(id){prune();if(!uuid.test(id||''))fail('请求标识无效');const r=windows.get(id);if(!r)fail('电视授权窗口已过期',410,'window_expired');return r;}
 function alive(r){
  const tv=store.read().sessions[r.actor];if(!tv||tv.expiresAt<=now()||!['owner','viewer'].includes(tv.role)){if(!terminal.has(r.state)){r.state=r.state==='awaiting_system'?'unknown':'cancelled';delete r.command;}fail('电视连接已撤销',410,'target_revoked');}
  if(r.writer&&!terminal.has(r.state)){const s=store.read().sessions[r.writer];if(!s||s.expiresAt<=now()||(s.role!=='owner'&&!(s.role==='controller'&&s.scopes?.includes(permission(r.kind))))){r.state=r.state==='awaiting_system'?'unknown':'cancelled';delete r.command;fail('Agent操作授权已撤销',403,'operation_revoked');}}
 }
 function device(actor,r,secret){session(actor);if(r.actor!==actor.id||!equal(r.secretHash,hash(typeof secret==='string'?secret:'')))fail('不是当前电视窗口',403,'device_mismatch');alive(r);}
 const meta=r=>({id:r.id,kind:r.kind,label:r.label.replace(/^开窗/,'开视'),fingerprint:r.secretHash,targets:structuredClone(r.targets),state:r.state,revision:r.revision,expiresAt:r.expiresAt});
 const receipt=r=>({id:r.id,kind:r.kind,state:r.state,revision:r.revision,outcome:r.state==='succeeded'?'reported_by_tv':r.state==='awaiting_system'?(r.kind==='resources'?'awaiting_tv_save':'awaiting_system_confirmation'):r.state,expiresAt:r.expiresAt,...(r.result?{result:structuredClone(r.result)}:{})});
 function command(r,value){
  if(r.kind==='resources'){
   if(!fields(value,'url,sha256')||typeof value.url!=='string'||value.url.length>4096||!sha.test(value.sha256||''))fail('资源包需要明确的HTTPS地址和SHA-256');
   let u;try{u=new URL(value.url);}catch{fail('资源包地址无效');}if(u.protocol!=='https:'||u.username||u.password||u.hash||!u.hostname||u.port==='0')fail('资源包地址需为不含账号或片段的HTTPS地址');
   return {url:u.href,sha256:value.sha256};
  }
  if(r.kind==='install'){
   if(!fields(value,'connectionId,path,sha256,packageName')||!r.targets.some(t=>t.id===value.connectionId)||typeof value.path!=='string'||value.path.length>2048||!value.path.toLowerCase().endsWith('.apk')||!sha.test(value.sha256||'')||!pkg.test(value.packageName||'')||value.packageName.length>200||['org.zhixiaoce.aperture','org.zhixiaoce.aperture.parents','org.zhixiaoce.kaishi','org.zhixiaoce.kaishi.parents'].includes(value.packageName)||value.path.split('/').some(p=>!p||p==='.'||p==='..'||p.length>255||/[\\:%?*"<>|\p{Cc}]/u.test(p)||p.endsWith('.')||p.endsWith(' ')))fail('仅接受已授权NAS中的明确APK、包名与SHA-256');
  }else if(!fields(value,'packageName')||!pkg.test(value.packageName||'')||value.packageName.length>200||['org.zhixiaoce.aperture','org.zhixiaoce.aperture.parents','org.zhixiaoce.kaishi','org.zhixiaoce.kaishi.parents'].includes(value.packageName)||!r.targets.some(t=>t.id===value.packageName))fail('应用不在电视此次允许卸载的列表');
  return r.kind==='install'?{connectionId:value.connectionId,path:value.path,sha256:value.sha256,packageName:value.packageName}:{packageName:value.packageName};
 }
 return {
  start(actor,input){
   const s=session(actor);if(!['owner','viewer'].includes(s.role))fail('请在电视发起操作授权',403,'forbidden');
   if(!fields(input,'id,secretHash,label,kind,targets')||!uuid.test(input.id||'')||!sha.test(input.secretHash||'')||!kinds.includes(input.kind)||!['开窗 · 标准版','开窗 · 爸妈版','开视 · 标准版','开视 · 爸妈版'].includes(input.label)||!Array.isArray(input.targets)||input.targets.length>100)fail('电视授权格式无效');
   const seen=new Set();for(const t of input.targets){if(!fields(t,'id,name')||!(input.kind==='resources'?/^kaishi\.resource-pack\.v1$/:input.kind==='install'?uuid:pkg).test(t.id||'')||seen.has(t.id)||typeof t.name!=='string'||t.name.length<1||t.name.length>100||/[\p{Cc}]/u.test(t.name)||['org.zhixiaoce.aperture','org.zhixiaoce.aperture.parents','org.zhixiaoce.kaishi','org.zhixiaoce.kaishi.parents'].includes(t.id))fail('授权目标无效');seen.add(t.id);}
   if(input.kind==='resources'&&input.targets.length!==1)fail('资源包窗口需限定v1格式');
   prune();const old=windows.get(input.id);if(old){if(old.actor!==actor.id||old.secretHash!==input.secretHash||old.kind!==input.kind||JSON.stringify(old.targets)!==JSON.stringify(input.targets))fail('授权窗口标识冲突',409,'window_conflict');return meta(old);}
   if(windows.size>=64)fail('操作窗口已满',429,'quota');
   for(const r of windows.values())if(r.actor===actor.id&&!terminal.has(r.state)){r.state=r.state==='awaiting_system'?'unknown':'cancelled';delete r.command;}
   const r={...structuredClone(input),actor:actor.id,state:'waiting',revision:0,expiresAt:now()+600000};windows.set(r.id,r);return meta(r);
  },
  list(actor,kind){scope(actor,kind);prune();return [...windows.values()].filter(r=>{try{alive(r);return r.kind===kind&&r.state==='waiting';}catch{return false;}}).map(meta);},
  inspect(actor,kind,id,executionKey){scope(actor,kind);const r=read(id);if(r.kind!==kind)fail('操作类型不匹配',403);alive(r);if(!key.test(executionKey||''))fail('幂等键无效');const own=r.delivery?.actor===actor.id&&r.delivery.key===executionKey;return {...meta(r),...(executionKey?{receipt:own?receipt(r):{outcome:'unknown'}}:{})};},
  send(actor,kind,input){
   scope(actor,kind);const r=read(input?.id);if(r.kind!==kind)fail('操作类型不匹配',403);alive(r);
   if(!fields(input,'id,command,execution')||!fields(input.execution,'key,revision')||!key.test(input.execution.key||'')||!Number.isSafeInteger(input.execution.revision))fail('请求与幂等版本无效');
   const cmd=command(r,input.command),digest=hash(JSON.stringify(cmd));
   if(r.delivery?.actor===actor.id&&r.delivery.key===input.execution.key){if(r.delivery.hash!==digest)fail('幂等键已用于不同操作',409,'idempotency_conflict');return {...receipt(r),replayed:true};}
   if(r.state!=='waiting'||r.revision!==input.execution.revision)fail('授权窗口已变化',409,'revision_conflict');
   r.command=cmd;r.writer=actor.id;r.delivery={actor:actor.id,key:input.execution.key,hash:digest};r.state='delivered';r.revision++;return receipt(r);
  },
  poll(actor,input){const r=read(input?.id);device(actor,r,input?.secret);return {...receipt(r),...(r.state==='delivered'?{command:structuredClone(r.command)}:{})};},
  stage(actor,input){
   const r=read(input?.id);device(actor,r,input?.secret);if(!['staging','awaiting_system'].includes(input.state))fail('操作阶段无效');
   if(r.state===input.state&&input.revision===r.revision-1)return {...receipt(r),replayed:true};
   if(input.revision!==r.revision||r.state!==(input.state==='staging'?'delivered':'staging'))fail('电视操作阶段已变化',409,'revision_conflict');
   r.state=input.state;r.revision++;return receipt(r);
  },
  finish(actor,input){
   const r=read(input?.id);device(actor,r,input?.secret);if(!terminal.has(input.state))fail('电视回执无效');
   if(r.state===input.state&&input.revision===r.revision-1)return receipt(r);
   if(terminal.has(r.state)||input.revision!==r.revision||(input.state==='succeeded'&&r.state!=='awaiting_system'))fail('操作尚未得到系统回执',409,'revision_conflict');
   if(r.kind==='resources'&&input.state==='succeeded'){const result=input.result;if(!fields(result,'packId,revision,sha256,changed')||typeof result.packId!=='string'||!/^[a-z0-9][a-z0-9._-]{0,95}$/.test(result.packId)||!Number.isSafeInteger(result.revision)||result.revision<1||result.revision>2147483647||result.sha256!==r.command?.sha256||typeof result.changed!=='boolean')fail('资源包保存回执无效');r.result=structuredClone(result);}
   r.state=input.state;r.revision++;delete r.command;return receipt(r);
  },
  close(){windows.clear();}
 };
}
