import {readFile} from 'node:fs/promises';
import {isAbsolute} from 'node:path';
import {validateHomeAssistant} from './home-assistant.mjs';
import {fail,text} from '../../shared/model.mjs';
const id = v => typeof v==='string' && /^[a-z0-9][a-z0-9_-]{0,39}$/.test(v);
const object = v => v && typeof v==='object' && !Array.isArray(v);
const pathOK = v => typeof v==='string' && v.length<=160 && v.split('.').every(p=>/^[a-zA-Z0-9_-]+$/.test(p) && !['__proto__','constructor','prototype'].includes(p));
export function validateSources(input=[]) {
  if(!Array.isArray(input)||input.length>24)fail('连接配置需为最多24个来源');
  const seen=new Set();
  for(const s of input){
    if(!object(s)||!id(s.id)||seen.has(s.id))fail('连接标识无效或重复');seen.add(s.id);
    text(s.label,40,'来源名称');if(!id(s.catalogId))fail('需指定目录标识');
    if(s.transport==='home-assistant'){validateHomeAssistant(s);continue;}
    if(!['stdio','http'].includes(s.transport)||s.readOnlyReviewed!==true)fail('连接需经管理员逐项审阅只读工具');
    if(s.transport==='stdio'){
      if(!isAbsolute(s.command||'')||!Array.isArray(s.args)||s.args.length>30||s.args.some(a=>typeof a!=='string'||a.length>500))fail('stdio 需绝对可执行文件路径与固定参数');
      if(s.cwd&&!isAbsolute(s.cwd))fail('工作目录需绝对路径');
    }else{
      let url;try{url=new URL(s.url);}catch{fail('MCP 地址无效');}
      if(url.username||url.password||url.hash||!['https:','http:'].includes(url.protocol))fail('MCP 地址配置无效');
      if(url.protocol==='http:'&&!['127.0.0.1','localhost','[::1]'].includes(url.hostname)&&s.allowInsecureLan!==true)fail('局域网明文 HTTP 需显式 allowInsecureLan');
    }
    for(const name of [s.bearerEnv,...Object.values(s.envFrom||{})].filter(Boolean))if(!/^[A-Z][A-Z0-9_]{0,79}$/.test(name))fail('凭证只能引用环境变量名');
    if(!Array.isArray(s.readTools)||!s.readTools.length||s.readTools.length>20||s.readTools.some(t=>typeof t!=='string'||t.length>160))fail('需精确的读取工具白名单');
    if(!Array.isArray(s.queries)||!s.queries.length||s.queries.length>10)fail('每个来源需1至10个固定查询');
    const queryIds=new Set();
    for(const q of s.queries){
      if(!id(q.id)||queryIds.has(q.id)||!s.readTools.includes(q.tool)||!object(q.arguments)||JSON.stringify(q.arguments).length>4096)fail('查询需唯一标识、固定参数及白名单工具');queryIds.add(q.id);text(q.label,48,'查询名称');
      if(!Array.isArray(q.tiles)||!q.tiles.length||q.tiles.length>6)fail('每次最多映射6项');
      const tileIds=new Set();
      for(const t of q.tiles){
        if(!id(t.id)||tileIds.has(t.id)||!pathOK(t.valuePath)||(t.observedAtPath&&!pathOK(t.observedAtPath)))fail('字段路径或标识无效');tileIds.add(t.id);
        text(t.title,24,'展示标题');text(t.unit||'',8,'单位',true);
        if(!['home','today','outside','food','memory','personal'].includes(t.category)||!Number.isInteger(t.ttlSeconds)||t.ttlSeconds<60||t.ttlSeconds>86400)fail('需有效分类及60至86400秒的新鲜度');
      }
    }
  }
  return structuredClone(input);
}
export async function loadSources(filename){
  if(!filename)return [];
  const bytes=await readFile(filename);if(bytes.length>65536)throw new Error('连接配置超过64KB');
  const data=JSON.parse(bytes);if(data.format!=='aperture-connections/v1')throw new Error('连接配置格式无效');
  return validateSources(data.sources);
}
