import {randomUUID} from 'node:crypto';
import {fail,DomainError} from '../../shared/model.mjs';
import {validateSources} from './config.mjs';
import {readHomeAssistant,homeAssistantQuery} from './home-assistant.mjs';
import {readMcp} from './mcp.mjs';
const pick=(value,path)=>path.split('.').reduce((v,p)=>v&&Object.hasOwn(v,p)?v[p]:undefined,value);
export function createLifeService({store,service,sources=[]}){
  const configured=validateSources(sources),previews=new Map();let active=0;
  function owner(actor){const session=store.read().sessions[actor.id];if(!session||session.expiresAt<=Date.now())fail('会话已失效',401,'unauthorized');if(actor.role!=='owner'||session.role!=='owner')fail('生活来源仅家庭管理员可读取和分享',403,'forbidden');}
  function prune(){for(const [key,p] of previews)if(p.until<=Date.now())previews.delete(key);}
  return {
    list(actor){owner(actor);return configured.map(s=>({id:s.id,catalogId:s.catalogId,label:s.label,queries:s.queries.map(q=>({id:q.id,label:q.label}))}));},
    async preview(actor,input){
      owner(actor);const s=configured.find(s=>s.id===input.sourceId),raw=s?.queries.find(q=>q.id===input.queryId),q=raw&&s.transport==='home-assistant'?homeAssistantQuery(raw):raw;if(!q)fail('此生活连接尚未配置',404,'not_configured');
      if(active>=2)fail('有来源正在读取，请稍后再试',429,'busy');prune();active++;
      const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),12000);
      try{
        const data=await (s.transport==='home-assistant'?readHomeAssistant:readMcp)(s,q,{signal:controller.signal});owner(actor);
        const fetchedAt=new Date().toISOString(),signals=[],unavailable=[];
        for(const t of q.tiles){
          const value=pick(data,t.valuePath);
          if(!['string','number','boolean'].includes(typeof value)||(typeof value==='number'&&!Number.isFinite(value))||String(value).length>100||!String(value).trim()){unavailable.push(t.title);continue;}
          const timestamp=t.observedAtPath?pick(data,t.observedAtPath):null;
          // Never replace a missing/invalid source observation time with fetch time.
          if(t.observedAtPath&&(typeof timestamp!=='string'||!/^\d{4}-\d{2}-\d{2}T.*(?:Z|[+-]\d{2}:\d{2})$/.test(timestamp)||!Number.isFinite(Date.parse(timestamp))||Date.parse(timestamp)>Date.now()+60000)){unavailable.push(t.title);continue;}
          const observedAt=timestamp?new Date(timestamp).toISOString():null;
          const expiresAt=new Date((observedAt?Date.parse(observedAt):Date.parse(fetchedAt))+t.ttlSeconds*1000).toISOString();
          signals.push({id:`${s.id}:${q.id}:${t.id}`,sourceId:s.id,title:t.title,value:String(value).trim(),unit:t.unit||'',category:t.category,source:s.label,observedAt,fetchedAt,expiresAt});
        }
        if(!signals.length)fail('没有可展示字段，请核对映射或上游结果',422,'mapping_required');
        if(previews.size>=16)previews.delete(previews.keys().next().value);
        const token=randomUUID(),until=Date.now()+300000;previews.set(token,{actor:actor.id,signals,until});
        return {token,signals,unavailable,expiresAt:new Date(until).toISOString()};
      }catch(e){if(e instanceof DomainError)throw e;fail(controller.signal.aborted?'读取超时，未更新家庭屏':'无法读取来源，请检查服务端连接配置',502,controller.signal.aborted?'upstream_timeout':'upstream_unavailable');}
      finally{clearTimeout(timer);active--;}
    },
    async publish(actor,input,execution){
      owner(actor);prune();const p=previews.get(input.token);
      if(!p||p.actor!==actor.id)fail('预览已过期或不属于此会话，请重新读取',410,'preview_expired');
      if(!Array.isArray(input.ids)||!input.ids.length||new Set(input.ids).size!==input.ids.length||input.ids.some(id=>!p.signals.some(s=>s.id===id)))fail('请选择预览里的内容');
      const signals=p.signals.filter(s=>input.ids.includes(s.id));
      if(signals.some(s=>Date.parse(s.expiresAt)<=Date.now()))fail('所选数据已过期，请重新读取来源',409,'stale_source');
      return service.write(actor,'life.publish',{signals},execution);
    }
  };
}
