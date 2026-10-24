// Local protocol fixture. No vendor account, smart-home hardware or network data.
import {Server} from '@modelcontextprotocol/sdk/server/index.js';
import {StdioServerTransport} from '@modelcontextprotocol/sdk/server/stdio.js';
import {ListToolsRequestSchema,CallToolRequestSchema} from '@modelcontextprotocol/sdk/types.js';
export function fixtureServer(){
  const server=new Server({name:'aperture-synthetic-life-fixture',version:'1.0.0'},{capabilities:{tools:{}}});
  server.setRequestHandler(ListToolsRequestSchema,async()=>({tools:[
    {name:'read_daily',description:'Synthetic household values only',inputSchema:{type:'object',properties:{mode:{type:'string'}}},annotations:{readOnlyHint:true,destructiveHint:false}},
    {name:'set_power',description:'Writes are deliberately not usable',inputSchema:{type:'object'},annotations:{readOnlyHint:false,destructiveHint:true}}
  ]}));
  server.setRequestHandler(CallToolRequestSchema,async request=>{
    if(request.params.name!=='read_daily')throw new Error('Unexpected write tool call');
    const mode=request.params.arguments?.mode;
    if(mode==='delay')await new Promise(r=>setTimeout(r,20000));
    if(mode==='error')return {isError:true,content:[{type:'text',text:'synthetic-private-secret-must-not-leak'}]};
    const data={temperature:24.6,weather:'傍晚有雨',dinner:'番茄牛腩',privateNote:'这段私人日记没有映射，不应出现在浏览器',observedAt:new Date(Date.now()-(mode==='stale'?3600000:0)).toISOString()};
    if(mode==='partial')data.temperature=null;
    if(mode==='invalid-time')data.observedAt='yesterday';
    if(mode==='html')data.dinner='<img src=x onerror=alert(1)>';
    if(mode==='oversize')data.privateNote='x'.repeat(70000);
    return mode==='text'?{content:[{type:'text',text:JSON.stringify(data)}]}:{content:[{type:'text',text:JSON.stringify(data)}],structuredContent:data};
  });
  return server;
}
if(process.argv[1]===new URL(import.meta.url).pathname)await fixtureServer().connect(new StdioServerTransport());
