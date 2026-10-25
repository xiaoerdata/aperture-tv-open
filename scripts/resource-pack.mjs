#!/usr/bin/env node
// Public authoring tool for local files. No account credentials, imports or publication side effects.
import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
const MAX=524288,root=new URL('../',import.meta.url);
const schema=JSON.parse(await readFile(new URL('docs/contracts/kaishi-resource-pack-v1.schema.json',root)));
const ajv=new Ajv2020({allErrors:true,strict:true,strictRequired:false});addFormats(ajv);const validate=ajv.compile(schema);
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
function url(value,allowHttp){let u;try{u=new URL(value);}catch{throw Error('地址无效');}if(!['https:',...(allowHttp?['http:']:[])].includes(u.protocol)||u.username||u.password||u.hash||!u.hostname||u.port==='0')throw Error('地址需使用HTTPS；HTTP需显式允许，网址不能包含账号或片段');return u;}
function unique(values,field,path){const seen=new Set();for(const item of values){const key=item[field];if(seen.has(key))throw Error(path+' 中的 '+field+' 重复');seen.add(key);}}
export function checkPack(bytes,{allowHttp=false}={}){
 if(bytes.length>MAX)throw Error('资源包超过512KB');const raw=new TextDecoder('utf-8',{fatal:true}).decode(bytes),pack=JSON.parse(raw);
 if(!validate(pack)){const e=validate.errors[0];throw Error((e.instancePath||'/')+' '+e.message);}
 function walk(value,path=''){
  if(typeof value==='string'&&value.includes('\0'))throw Error(path+' 含无效字符');
  if(Array.isArray(value)){value.forEach((x,i)=>walk(x,path+'/'+i));return;}
  if(value&&typeof value==='object')for(const [k,v]of Object.entries(value)){
   if(['url','baseUrl','poster','logo'].includes(k)&&typeof v==='string')url(v,allowHttp);
   if(k==='urls'&&Array.isArray(v)&&v.every(x=>typeof x==='string'))v.forEach(x=>url(x,allowHttp));
   walk(v,path+'/'+k);
  }
 }
 walk(pack);unique(pack.resources,'id','resources');let sites=0,lives=0,channels=0,items=0,episodes=0;
 for(const resource of pack.resources){
  if(resource.categoryIds)for(const category of Object.keys(resource.categoryIds))if(!resource.sections?.includes(category))throw Error('categoryIds 的分类需先在sections声明');
  if(resource.type==='live'){lives++;channels+=resource.channels?.length||0;if(resource.channels)unique(resource.channels,'id','channels');}
  if(resource.type==='catalog')sites++;
  if(resource.type==='collection'){sites++;items+=resource.items.length;unique(resource.items,'id','items');for(const item of resource.items){episodes+=item.episodes.length;unique(item.episodes,'title','episodes');}}
  if(resource.type==='tvbox'){
   const c=resource.config;if(!Array.isArray(c.sites||[])||!Array.isArray(c.lives||[])||(c.sites||[]).some(s=>!s||![0,1,3,4].includes(s.type))||(c.lives||[]).some(l=>!l))throw Error('TVBox适配器含未支持的站点类型或列表');
   sites+=(c.sites||[]).length;lives+=(c.lives||[]).length;channels+=(c.lives||[]).reduce((n,l)=>n+(l.channels||[]).length,0);
  }
 }
 if(sites>12||lives>12||channels>500||items>200||episodes>500)throw Error('资源包超过12站点/12直播来源/500频道/200节目/500集的限制');
 return {format:pack.format,version:pack.version,id:pack.id,revision:pack.revision,title:pack.title,sha256:sha(bytes),bytes:bytes.length,resources:pack.resources.length,sites,lives,channels,items,episodes,sections:[...new Set(pack.resources.flatMap(r=>r.sections||[r.section].filter(Boolean)))],nativeImportValidationRequired:true};
}
async function fetchPack(address,{allowHttp=false}={}){
 let current=url(address,allowHttp);for(let i=0;i<=3;i++){
  const response=await fetch(current,{redirect:'manual',signal:AbortSignal.timeout(10000)});
  if([301,302,303,307,308].includes(response.status)){await response.body?.cancel();if(i===3||!response.headers.get('location'))throw Error('重定向无效');current=url(new URL(response.headers.get('location'),current).href,allowHttp);continue;}
  if(!response.ok)throw Error('HTTP '+response.status);if(Number(response.headers.get('content-length'))>MAX){await response.body?.cancel();throw Error('资源包超过512KB');}
  const reader=response.body.getReader(),chunks=[];let size=0;while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>MAX){await reader.cancel();throw Error('资源包超过512KB');}chunks.push(value);}return Buffer.concat(chunks);
 }throw Error('无法读取资源包');
}
async function main(){
 const args=process.argv.slice(2),[command,input]=args;const option=name=>{const i=args.indexOf(name);return i<0?undefined:args[i+1];};const allowHttp=args.includes('--allow-http');let bytes;
 if(command==='template'){const output=option('--output');if(!output)throw Error('需要 --output 新文件');bytes=await readFile(new URL('examples/resource-packs/family.kaishi.json',root));await writeFile(output,bytes,{flag:'wx'});console.log(JSON.stringify({...checkPack(bytes),output,exampleAddressesMustBeReplaced:true},null,2));return;}
 if(command==='wrap-tvbox'){
  const output=option('--output'),id=option('--id');if(!input||!output||!id)throw Error('用法：wrap-tvbox 输入.json --id org.example.sources --output 新文件.kaishi.json');
  const content=await readFile(input);if(content.length>MAX)throw Error('输入超过512KB');const pack={format:'kaishi.resource-pack',version:1,id,revision:Number(option('--revision')||1),title:option('--title')||'我的资源',resources:[{id:'tvbox',type:'tvbox',title:'TVBox来源',config:JSON.parse(content.toString('utf8'))}]};bytes=Buffer.from(JSON.stringify(pack,null,2)+'\n');const report=checkPack(bytes,{allowHttp});await writeFile(output,bytes,{flag:'wx'});console.log(JSON.stringify({...report,output},null,2));return;
 }
 if(command==='validate'){if(!input)throw Error('需要资源包文件');bytes=await readFile(input);}
 else if(command==='verify-url'){const expected=option('--sha256');if(!input||!/^[a-f0-9]{64}$/.test(expected||''))throw Error('需要URL及 --sha256 64位小写摘要');bytes=await fetchPack(input,{allowHttp});if(sha(bytes)!==expected)throw Error('发布文件的SHA-256与本地文件不一致');}
 else throw Error('用法：npm run resources -- validate 文件 | template --output 新文件 | wrap-tvbox 输入 --id 编号 --output 新文件 | verify-url 地址 --sha256 摘要');
 console.log(JSON.stringify({status:'valid',...checkPack(bytes,{allowHttp})},null,2));
}
if(process.argv[1]&&fileURLToPath(import.meta.url)===resolve(process.argv[1]))main().catch(error=>{console.error(JSON.stringify({status:'invalid',message:error.message}));process.exitCode=1;});
