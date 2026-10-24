import {sealNas} from './seal.mjs';
import {api as requestApi,uid} from '../ui/client.js';
const $=selector=>document.querySelector(selector);
const id=new URLSearchParams(location.search).get('id'),fingerprint=new URLSearchParams(location.hash.slice(1)).get('fingerprint');
let request,command,timer,busy=false;
const api=(path,body)=>requestApi('/api/'+path,body,{redirect:'error'});
function status(message,error=false){$('#status').textContent=message;$('#status').dataset.error=String(error);}
function later(){clearTimeout(timer);if(!document.hidden)timer=setTimeout(()=>check().catch(failure),3000);}
function failure(error){status(error.code||!command?error.message:'连接结果暂不确定，请查询交接结果。',true);if(command){$('#receipt').hidden=false;$('#receipt-text').textContent='没有自动重发。先查询原交接记录，再决定是否重试。';}}
function render(meta){
  request=meta;
  if(meta.fingerprint!==fingerprint)throw new Error('校验码不一致，请回到电视重新扫码。');
  $('#target').hidden=false;$('#tv-label').textContent=meta.label;$('#fingerprint').textContent=fingerprint.slice(0,12).match(/.{4}/g).join(' ');
  $('#nas-form').hidden=meta.state!=='waiting'||!!command;
  if(meta.state==='waiting'&&!command){status('核对校验码后，填写这台 NAS 的只读连接。');return;}
  $('#receipt').hidden=false;$('#retry').hidden=true;
  const titles={delivered:'资料已交接，等待电视测试目录并保存。',saved:'电视已报告：目录测试通过，连接已保存在电视。',connection_failed:'电视未能连接或保存。资料没有新增；请检查 NAS 服务、账号和目录后，从电视重新发起。',cancelled:'电视已取消这次连接。请从电视重新发起。'};
  $('#receipt-text').textContent=titles[meta.state]||'暂未找到已交接记录。可以重试原加密资料；不会重新生成另一份。';
  $('#receipt h2').textContent=meta.state==='saved'?'电视收到了':meta.state==='delivered'?'等电视检查一下':'交接状态';
  status(meta.state==='saved'?'这次连接已完成。':meta.state==='waiting'?'尚未找到交接记录。':'已更新交接状态。');
  if(meta.state==='waiting'&&command)$('#retry').hidden=false;
  if(meta.state==='delivered')later();
}
async function check(){if(busy)return;busy=true;$('#check').disabled=true;try{render(await api('nas-setup/inspect',{id,...(command?{key:command.execution.key}:{})}));}finally{busy=false;$('#check').disabled=false;}}
async function load(){
  if(!/^[a-f0-9-]{36}$/.test(id||'')||!/^[a-f0-9]{64}$/.test(fingerprint||''))throw new Error('请扫描电视上的完整二维码。');
  if(!isSecureContext||!crypto.subtle)throw new Error('请通过可信 HTTPS 家庭服务配置；HTTP 局域网页面不可提交 NAS 资料。');
  const session=await api('session');if(session.role!=='owner'){const error=new Error('请由家庭管理员确认身份。');error.status=401;throw error;}
  $('#login').hidden=true;await check();
}
function errorMessage(error){status(error.message,true);if(error.status===401||error.status===403)$('#login').hidden=false;}
$('#login-form').onsubmit=async e=>{e.preventDefault();const button=e.target.querySelector('button');button.disabled=true;try{await api('login',{key:e.target.elements.key.value});e.target.reset();await load();}catch(error){errorMessage(error);}finally{button.disabled=false;}};
$('#nas-form').elements.brand.onchange=()=>{$('#brand-help').textContent=$('#nas-form').elements.brand.value.startsWith('Xiaomi')?'仅最新独立 Xiaomi 智能存储；当前只接受 SMB，不预设 WebDAV。新品硬件尚待测试。':'请使用设备已开启的 SMB2/3 或 HTTPS WebDAV；海康/TrueNAS 不预设 WebDAV。';};
async function deliver(){await api('nas-setup/send',command);await check();}
$('#nas-form').onsubmit=async e=>{
  e.preventDefault();if(command||busy||request?.state!=='waiting')return;const form=e.target;$('#send').disabled=true;
  try{
    let draft=Object.fromEntries(new FormData(form));
    if(draft.brand.startsWith('Xiaomi')&&!draft.root.startsWith('smb://'))throw new Error('小米新品当前只按 SMB 路线核验。');
    const root=new URL(draft.root);if(!['smb:','https:'].includes(root.protocol)||root.username||root.password||root.search||root.hash)throw new Error('请填写不含账号密码的 SMB 或 HTTPS WebDAV 目录。');
    const envelope=await sealNas(request,draft);draft.password='';draft.user='';draft=null;
    command={id,envelope,execution:{key:uid(),revision:request.revision}};
    form.elements.password.value='';form.elements.user.value='';form.hidden=true;$('#receipt').hidden=false;status('资料已在手机加密，正在交接…');
    await deliver();
  }catch(error){failure(error);}finally{$('#send').disabled=false;}
};
$('#check').onclick=()=>check().catch(failure);$('#retry').onclick=async()=>{if(busy)return;$('#retry').disabled=true;try{await deliver();}catch(error){failure(error);}finally{$('#retry').disabled=false;}};
document.addEventListener('visibilitychange',()=>{clearTimeout(timer);if(!document.hidden&&request?.state==='delivered')check().catch(failure);});
addEventListener('pagehide',()=>clearTimeout(timer));
load().catch(errorMessage);
