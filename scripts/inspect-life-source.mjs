import {loadSources} from '../server/life/config.mjs';
import {readMcp} from '../server/life/mcp.mjs';
const [filename,sourceId,queryId]=process.argv.slice(2);
if(!filename||!sourceId||!queryId)throw new Error('Usage: node scripts/inspect-life-source.mjs private-config.json source-id query-id');
const sources=await loadSources(filename),source=sources.find(s=>s.id===sourceId),query=source?.queries.find(q=>q.id===queryId);if(!query)throw new Error('Configured query not found');
const data=await readMcp(source,query,{signal:AbortSignal.timeout(12000)}),paths=[];
function visit(value,path,depth){if(depth>6||paths.length>=100)return;if(value&&typeof value==='object'){for(const key of Object.keys(value).slice(0,20))visit(value[key],path?path+'.'+key:key,depth+1);}else paths.push({path,type:value===null?'null':typeof value});}
visit(data,'',0);console.log(JSON.stringify({notice:'Only field paths/types; no values or credentials.',paths},null,2));

