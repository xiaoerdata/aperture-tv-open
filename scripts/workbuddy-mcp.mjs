import {readFile} from 'node:fs/promises';
import {Server} from '@modelcontextprotocol/sdk/server/index.js';
import {StdioServerTransport} from '@modelcontextprotocol/sdk/server/stdio.js';
import {ListToolsRequestSchema,CallToolRequestSchema} from '@modelcontextprotocol/sdk/types.js';
import {loadConnection,localRequest} from '../server/local-agent/client.mjs';
const at=process.argv.indexOf('--config');if(at<0)throw new Error('Pass --config /absolute/private/connection.json');
const connection=await loadConnection(process.argv[at+1]);
const discover=()=>localRequest(connection,'/api/local-agent/tools');
const initial=await discover();
const version=JSON.parse(await readFile(new URL('../package.json',import.meta.url),'utf8')).version;
const server=new Server({name:'aperture-home',version},{capabilities:{tools:{}},instructions:initial.instructions});
server.setRequestHandler(ListToolsRequestSchema,async()=>({tools:(await discover()).tools}));
server.setRequestHandler(CallToolRequestSchema,async request=>{
  try{const result=await localRequest(connection,'/api/local-agent/call',{name:request.params.name,arguments:request.params.arguments||{}});return {content:[{type:'text',text:JSON.stringify(result)}],structuredContent:result};}
  catch(e){return {isError:true,content:[{type:'text',text:JSON.stringify({code:e.code||'connection_error',message:e.code?e.message:'连接结果未知；写入请先查询原幂等键回执，不要换键重试。'})}]};}
});
await server.connect(new StdioServerTransport());
