import {open,mkdir,unlink} from 'node:fs/promises';
import {resolve,dirname} from 'node:path';
import QRCode from 'qrcode';
import {originURL,localRequest} from '../server/local-agent/client.mjs';
const args=process.argv.slice(2),arg=name=>{const i=args.indexOf(name);return i<0?undefined:args[i+1];};
if(!arg('--origin')||!arg('--output'))throw new Error('Usage: node scripts/workbuddy-pair.mjs --origin https://your-home --output /private/aperture-connection.json [--qr /private/pair.png] [--allow-lan-http]');
const allowLanHttp=args.includes('--allow-lan-http'),origin=originURL(arg('--origin'),{allowLanHttp}),output=resolve(arg('--output'));
await mkdir(dirname(output),{recursive:true,mode:0o700});
const file=await open(output,'wx',0o600);let saved=false;
try{
 const pair=await localRequest({origin,allowLanHttp},'/api/local-pair/start',{});
 console.log('请用已登录家庭管理员的手机扫码确认；5分钟内有效。\n'+pair.approvalUrl);
 console.log(await QRCode.toString(pair.approvalUrl,{type:'terminal',small:true}));
 if(arg('--qr')){const qr=await open(resolve(arg('--qr')),'wx',0o600);try{await qr.writeFile(await QRCode.toBuffer(pair.approvalUrl,{width:640,margin:4}));}finally{await qr.close();}}
 if(allowLanHttp)console.log('局域网HTTP开发模式：连接未加密；仅在受信任家庭网络试用，正式使用HTTPS。');
 console.log('二维码只含授权请求，不含管理员口令、领取密钥或连接令牌。跨设备使用同一个家庭地址；HTTPS为默认，私有IP的HTTP须显式启用开发选项。');
 while(Date.now()<pair.expiresAt){
  await new Promise(r=>setTimeout(r,2000));
  const result=await localRequest({origin,allowLanHttp},'/api/local-pair/poll',{code:pair.code,secret:pair.secret});
  if(result.status!=='connected')continue;
  await file.writeFile(JSON.stringify({origin,token:result.token,...(allowLanHttp?{allowLanHttp:true}:{})},null,2));await file.sync();saved=true;
  console.log('连接已自动保存：'+output+'\n将 workbuddy-mcp.mjs 与此私有文件路径添加到 MCP 客户端。之后更新电视无需扫码或填表。');break;
 }
 if(!saved)throw new Error('连接请求已过期，请重新申请');
}finally{await file.close();if(!saved)await unlink(output).catch(()=>{});}
