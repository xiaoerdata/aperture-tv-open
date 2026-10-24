import {createHash, createPublicKey, timingSafeEqual} from 'node:crypto';
import {fail} from '../../shared/model.mjs';

const hash=value=>createHash('sha256').update(value).digest('hex');
const idPattern=/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/;
const keyPattern=/^[a-zA-Z0-9_-]{8,100}$/;
const same=(a,b)=>typeof a==='string'&&typeof b==='string'&&a.length===b.length&&timingSafeEqual(Buffer.from(a),Buffer.from(b));
function bytes(value,min,max){
  if(typeof value!=='string'||!/^[A-Za-z0-9_-]+$/.test(value))fail('加密资料格式无效');
  const data=Buffer.from(value,'base64url');if(data.length<min||data.length>max||data.toString('base64url')!==value)fail('加密资料长度无效');return data;
}
function envelope(value){
  if(!value||typeof value!=='object'||Array.isArray(value)||Object.keys(value).sort().join(',')!=='ciphertext,iv,key,version'||value.version!==1)fail('仅接受加密NAS资料');
  bytes(value.key,256,256);bytes(value.iv,12,12);bytes(value.ciphertext,18,8192);
  return structuredClone(value);
}
/** Bounded, ephemeral relay. Contains no NAS plaintext; restart/expiry requires a new TV request. */
export function createNasSetup({store,now=Date.now}){
  const requests=new Map();
  function session(actor){const active=store.read().sessions[actor?.id];if(!active||active.expiresAt<=now()||active.role!==actor.role)fail('连接已失效，请重新连接',401,'unauthorized');return active;}
  function configure(actor){const active=session(actor);if(active.role!=='owner'&&!(active.role==='controller'&&active.scopes?.includes('nas.configure')))fail('尚未授权配置电视NAS',403,'scope_denied');}
  function prune(){for(const [id,r]of requests)if(r.expiresAt<=now())requests.delete(id);}
  function read(id){prune();if(!idPattern.test(id||''))fail('电视请求无效');const r=requests.get(id);if(!r)fail('电视请求已过期，请从电视重新发起',410,'setup_expired');return r;}
  function targetAlive(r){const target=store.read().sessions[r.actor];if(!target||target.expiresAt<=now()||!['viewer','owner'].includes(target.role)){if(['waiting','delivered'].includes(r.state)){r.state='cancelled';delete r.envelope;}fail('电视连接已撤销',410,'target_revoked');}}
  function writerAlive(r){if(!r.writer||r.state!=='delivered')return;const s=store.read().sessions[r.writer];if(!s||s.expiresAt<=now()||(s.role!=='owner'&&!(s.role==='controller'&&s.scopes?.includes('nas.configure')))){r.state='cancelled';delete r.envelope;fail('配置授权已撤销',403,'delivery_revoked');}}
  function device(actor,r,secret){session(actor);if(r.actor!==actor.id||typeof secret!=='string'||!same(r.secretHash,hash(secret)))fail('不是当前电视的请求',403,'device_mismatch');targetAlive(r);}
  const metadata=r=>({id:r.id,label:r.label,publicKey:r.publicKey,fingerprint:r.fingerprint,state:r.state,revision:r.revision,expiresAt:r.expiresAt});
  const result=r=>({id:r.id,state:r.state,revision:r.revision,outcome:r.state==='saved'?'saved_on_tv':r.state==='delivered'?'awaiting_tv':r.state==='waiting'?'waiting_for_configuration':r.state});
  return {
    start(actor,input){
      const active=session(actor);if(!['viewer','owner'].includes(active.role))fail('请在电视上发起配置',403,'forbidden');
      if(!input||Object.keys(input).sort().join(',')!=='id,label,publicKey,secretHash'||!idPattern.test(input.id||'')||!/^[a-f0-9]{64}$/.test(input.secretHash||'')||!['开窗 · 标准版','开窗 · 爸妈版'].includes(input.label))fail('电视请求格式无效');
      const encoded=bytes(input.publicKey,270,600);let key;
      try{key=createPublicKey({key:encoded,format:'der',type:'spki'});}catch{fail('电视公钥无效');}
      if(key.asymmetricKeyType!=='rsa'||key.asymmetricKeyDetails?.modulusLength!==2048||key.asymmetricKeyDetails?.publicExponent!==65537n)fail('电视公钥规格无效');
      if(!key.export({format:'der',type:'spki'}).equals(encoded))fail('电视公钥编码无效');
      prune();const old=requests.get(input.id);
      if(old){if(old.actor!==actor.id||old.publicKey!==input.publicKey||old.secretHash!==input.secretHash)fail('请求标识冲突',409,'setup_conflict');return metadata(old);}
      if(requests.size>=64)fail('电视配置请求较多，请稍后重试',429,'quota');
      for(const r of requests.values())if(r.actor===actor.id&&['waiting','delivered'].includes(r.state)){r.state='cancelled';delete r.envelope;}
      const record={id:input.id,actor:actor.id,label:input.label,publicKey:input.publicKey,fingerprint:hash(encoded),secretHash:input.secretHash,state:'waiting',revision:0,expiresAt:now()+600000};
      requests.set(record.id,record);return metadata(record);
    },
    list(actor){configure(actor);prune();return [...requests.values()].filter(r=>{try{targetAlive(r);return ['waiting','delivered'].includes(r.state);}catch{return false;}}).map(metadata);},
    inspect(actor,id,key){configure(actor);if(key!==undefined&&!keyPattern.test(key))fail('幂等键无效');const r=read(id);targetAlive(r);writerAlive(r);const receipt=r.delivery&&r.delivery.actor===actor.id&&r.delivery.key===key?result(r):null;return {...metadata(r),...(key?{receipt:receipt||{outcome:'unknown'}}:{})};},
    send(actor,input){
      configure(actor);const r=read(input?.id);targetAlive(r);
      if(Object.keys(input).sort().join(',')!=='envelope,execution,id'||!input.execution||Object.keys(input.execution).sort().join(',')!=='key,revision'||!keyPattern.test(input.execution.key||'')||!Number.isSafeInteger(input.execution.revision))fail('仅接受请求标识、加密资料和幂等版本');
      const sealed=envelope(input.envelope),digest=hash(JSON.stringify(sealed));
      if(r.delivery?.actor===actor.id&&r.delivery.key===input.execution.key){if(r.delivery.hash!==digest)fail('幂等键已用于不同资料',409,'idempotency_conflict');writerAlive(r);return {...result(r),replayed:true};}
      if(r.state!=='waiting'||input.execution.revision!==r.revision)fail('电视请求已变化，请查询状态',409,'revision_conflict');
      r.writer=actor.id;r.delivery={actor:actor.id,key:input.execution.key,hash:digest};r.envelope=sealed;r.state='delivered';r.revision++;
      return result(r);
    },
    poll(actor,input){const r=read(input?.id);device(actor,r,input?.secret);writerAlive(r);return {...result(r),...(r.envelope?{envelope:structuredClone(r.envelope)}:{})};},
    finish(actor,input){
      const r=read(input?.id);device(actor,r,input?.secret);
      if(!['saved','connection_failed','cancelled'].includes(input?.state))fail('电视回执无效');
      if(r.state===input.state)return result(r);
      if(input.state!=='cancelled'){writerAlive(r);if(r.state!=='delivered'||input.revision!==r.revision)fail('电视请求已变化',409,'revision_conflict');}
      else if(!['waiting','delivered'].includes(r.state))return result(r);
      r.state=input.state;r.revision++;delete r.envelope;return result(r);
    },
    close(){requests.clear();}
  };
}
