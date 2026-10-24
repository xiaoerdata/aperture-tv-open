import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';
import {StreamableHTTPClientTransport} from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import {fail} from '../../shared/model.mjs';
const limit=256*1024;
// Cap HTTP frames before the SDK parses them; disallow all redirect destinations.
async function boundedFetch(input,init){
  const response=await fetch(input,{...init,redirect:'error'});
  if(!response.body)return response;
  const reader=response.body.getReader();let size=0;
  const body=new ReadableStream({async pull(controller){try{const {done,value}=await reader.read();if(done){controller.close();return;}size+=value.length;if(size>limit){await reader.cancel();controller.error(new Error('MCP response too large'));return;}controller.enqueue(value);}catch(e){controller.error(e);}},cancel:r=>reader.cancel(r)});
  return new Response(body,{status:response.status,statusText:response.statusText,headers:response.headers});
}
export async function readMcp(source,query,{signal}={}) {
  const client=new Client({name:'aperture-life-reader',version:'0.2.0-alpha.1'},{capabilities:{}});
  const env={};for(const [target,ref] of Object.entries(source.envFrom||{})){if(!process.env[ref])fail('来源缺少服务端凭证',503,'missing_credentials');env[target]=process.env[ref];}
  let transport;
  if(source.transport==='stdio')transport=new StdioClientTransport({command:source.command,args:source.args,cwd:source.cwd,env,stderr:'ignore',maxBufferSize:limit});
  else{
    const headers={};if(source.bearerEnv){const token=process.env[source.bearerEnv];if(!token)fail('来源缺少服务端凭证',503,'missing_credentials');headers.Authorization='Bearer '+token;}
    transport=new StreamableHTTPClientTransport(new URL(source.url),{requestInit:{headers},fetch:boundedFetch});
  }
  const options={timeout:10000,signal};
  try {
    await client.connect(transport,options);
    let cursor,found;
    // tools/list is checked every connection; descriptions and server instructions are never executed.
    for(let page=0;page<5&&!found;page++){
      const result=await client.listTools(cursor?{cursor}:{},options);found=result.tools.find(t=>t.name===query.tool);
      cursor=result.nextCursor;if(!cursor)break;
    }
    if(!found)fail('配置的读取工具不存在，请更新连接配置',422,'tool_unavailable');
    if(found.annotations?.readOnlyHint===false||found.annotations?.destructiveHint===true)fail('上游将此工具声明为写入或破坏性工具',403,'unsafe_tool');
    if(!source.readTools.includes(query.tool))fail('工具未获授权',403,'unsafe_tool');
    const result=await client.callTool({name:query.tool,arguments:query.arguments},undefined,options);
    if(result.isError)fail('来源读取失败，请在上游检查账号与查询配置',502,'upstream_error');
    if(JSON.stringify(result).length>65536)fail('来源返回内容超过64KB，请缩小查询范围',413,'upstream_too_large');
    if(result.structuredContent)return result.structuredContent;
    const blocks=result.content?.filter(c=>c.type==='text');
    if(blocks?.length!==1)fail('需结构化 JSON 结果；图片与任意文本不能直接映射',422,'mapping_required');
    try{return JSON.parse(blocks[0].text);}catch{fail('来源结果不是 JSON，请配置结构化适配器',422,'mapping_required');}
  } finally {await client.close().catch(()=>{});}
}
