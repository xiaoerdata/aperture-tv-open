import {mountLocalAssistant} from './ui/local-assistant.js';
import {demoBoard,emptyBoard,normalizeEntry,normalizeSettings,dateInZone} from '/shared/model.mjs';
import {mountCinema} from './cinema/studio.js';
import {$,el,api,photoUrl,uid} from './ui/client.js';
let demo=new URLSearchParams(location.search).get('demo')==='1',board=demo?demoBoard():emptyBoard(),role='owner',pending=null,busy=false,previewUrl=null,pairTimer=0,pairGeneration=0,settingsDirty=false;
const cinema=mountCinema({demo,getBoard:()=>board,getRole:()=>role,mutate,onError:statusError});
const localAssistant=mountLocalAssistant({demo,getRole:()=>role,onError:statusError,refreshDevices:devices});
const setStatus=(message,error=false)=>{const n=$('#save-status');n.textContent=message;n.dataset.error=String(error);};
const formData=form=>Object.fromEntries(new FormData(form));
function action(label,fn){const b=el('button','',label);b.type='button';b.dataset.mobileControl='action';b.onclick=fn;return b;}
function statusError(e){setStatus(e.message,true);if(e.status===401){$('#workspace').hidden=true;$('#welcome').hidden=false;$('#login-status').textContent='连接已失效，请重新登录。未提交草稿保留在本页。';}}
function populateSettings(){if(settingsDirty)return;const form=$('#settings-form');for(const [key,value] of Object.entries(board.settings)){const input=form.elements[key];if(input)input.type==='checkbox'?input.checked=value:input.value=value;}}
function render(){
  $('#welcome').hidden=true;$('#workspace').hidden=false;$('#demo-notice').hidden=!demo;
  $('#studio-home').textContent=board.settings.name;$('#studio-status').textContent=demo?'示例家庭 · 当前页面内体验':(role==='owner'?'家庭管理员':'家庭成员')+' · 已同步 r'+board.revision;
  for(const id of ['event-list','note-list','photo-list'])$('#'+id).replaceChildren();
  const events=board.entries.filter(e=>e.type==='event').sort((a,b)=>(a.date+a.time).localeCompare(b.date+b.time));$('#events-count').textContent=events.length+' 个安排';
  for(const item of events){const row=el('article','entry'+(item.done?' done':'')),info=el('div'),actions=el('div','entry-actions');info.append(el('strong','',item.title),el('p','',`${item.date} · ${item.time} · ${item.person}`));actions.append(action(item.done?'撤销完成':'完成',()=>mutate('entry.toggle',{id:item.id,done:!item.done})),action('删除',()=>remove('entry.remove',item)));row.append(info,actions);$('#event-list').append(row);}
  for(const item of board.entries.filter(e=>e.type==='note')){const note=el('article','note'),footer=el('footer');footer.append(el('span','',item.author+(item.pinned?' · 优先展示':'')),action('移除',()=>remove('entry.remove',item)));note.append(el('h3','',item.title),el('p','',item.body),footer);$('#note-list').append(note);}
  for(const p of board.photos){const item=el('article','photo-item'),img=el('img');img.src=photoUrl(p);img.alt=p.caption||p.title;item.append(img,el('h3','',p.title),el('p','',p.caption||''),action('移除照片',()=>remove('photo.remove',p)));$('#photo-list').append(item);}
  for(const [id,message] of [['event-list','暂无安排'],['note-list','暂无留言'],['photo-list','暂无照片']])if(!$('#'+id).children.length)$('#'+id).append(el('p','empty',message));
  $('#approve-form').hidden=role!=='owner';populateSettings();localAssistant.openFromLink();
}
async function reload(){if(demo){render();return;}board=await api('/api/board');render();}
function demoMutation(operation,input){
  if(operation==='cinema.settings')board.cinema=input;
  if(operation==='life.publish'){const ids=new Set(input.signals.map(s=>s.id)),keep=(board.life||[]).filter(s=>!ids.has(s.id));if(keep.length+input.signals.length>6)throw new Error('最多展示6项，请先移除旧项目');board.life=[...keep,...input.signals];}
  if(operation==='life.remove')board.life=(board.life||[]).filter(s=>s.id!==input.id);
  if(operation==='entry.add')board.entries.push({...normalizeEntry(input),id:uid()});
  if(operation==='entries.import')board.entries.push(...input.entries.map(e=>({...normalizeEntry(e),id:uid()})));
  if(operation==='entry.remove')board.entries=board.entries.filter(e=>e.id!==input.id);
  if(operation==='entry.toggle'){const e=board.entries.find(e=>e.id===input.id);if(e)e.done=input.done;}
  if(operation==='settings.update')board.settings=normalizeSettings(input);
  if(operation==='photo.add')board.photos.push({id:uid(),url:input.url,title:input.title,caption:input.caption});
  if(operation==='photo.remove')board.photos=board.photos.filter(p=>p.id!==input.id);
  board.revision++;render();return {revision:board.revision};
}
async function mutate(operation,input,{path='/api/write',extra={}}={}){
  if(busy)return false;busy=true;const buttons=[...document.querySelectorAll('#workspace button')];buttons.forEach(b=>b.disabled=true);setStatus('正在保存…');
  const fingerprint=JSON.stringify({operation,input,...extra});
  if(!pending||pending.fingerprint!==fingerprint)pending={fingerprint,execution:{key:uid(),revision:board.revision}};
  try{
    const result=demo?demoMutation(operation,input):await api(path,path==='/api/write'?{operation,input,execution:pending.execution}:{...input,...extra,execution:pending.execution});
    pending=null;setStatus(demo?'示例已更新；未发送到真实电视。':`已保存 r${result.revision}。在线电视将在下次同步时更新。`);
    try{await reload();}catch{setStatus(`已保存 r${result.revision}，但回读失败。请刷新内容，不要重复提交。`,true);}
    return true;
  }catch(e){statusError(e);return false;}finally{busy=false;buttons.forEach(b=>b.disabled=false);}
}
async function remove(operation,item){if(!confirm(`移除「${item.title}」？`))return;await mutate(operation,{id:item.id});}
for(const tab of document.querySelectorAll('[data-tab]'))tab.onclick=()=>{for(const b of document.querySelectorAll('[data-tab]')){if(b===tab)b.setAttribute('aria-current','page');else b.removeAttribute('aria-current');$('#panel-'+b.dataset.tab).hidden=b!==tab;}if(tab.dataset.tab==='connect'){devices();localAssistant.load();}if(tab.dataset.tab==='cinema')cinema.load();};
for(const kind of ['event','note']){
  const form=$('#'+kind+'-form');form.onsubmit=async e=>{e.preventDefault();const data=formData(form);data.type=kind;if(kind==='note')data.pinned=form.elements.pinned.checked;try{normalizeEntry(data);}catch(e){statusError(e);return;}if(await mutate('entry.add',data)){form.reset();setDates();}};
}
function setDates(){for(const input of document.querySelectorAll('input[type=date]'))if(!input.value)input.value=dateInZone(new Date(),board.settings.timeZone);}
$('#refresh').onclick=async()=>{try{await reload();pending=null;setStatus('已读取最新内容，未提交的草稿仍然保留。');await devices();}catch(e){statusError(e);}};
$('#login-form').onsubmit=async e=>{e.preventDefault();const button=e.target.querySelector('button');button.disabled=true;try{await api('/api/login',{key:$('#owner-key').value});$('#owner-key').value='';role='owner';await reload();}catch(e){$('#login-status').textContent=e.message;}finally{button.disabled=false;}};
$('#logout').onclick=async()=>{try{if(!demo)await api('/api/logout',{});location.href='/studio';}catch(e){statusError(e);}};
$('#request-editor').onclick=async()=>{
  const generation=++pairGeneration;clearTimeout(pairTimer);$('#editor-pair').hidden=false;$('#editor-status').textContent='正在生成连接码…';
  try{const pair=await api('/api/pair/start',{role:'editor'});if(generation!==pairGeneration)return;$('#editor-code').textContent=pair.code;$('#editor-status').textContent='请管理员在其工作台批准此代码，5分钟内有效。';
    const poll=async()=>{if(generation!==pairGeneration)return;try{const result=await api('/api/pair/poll',{code:pair.code,secret:pair.secret});if(generation!==pairGeneration)return;if(result.status==='connected'){role=result.role;await reload();return;}pairTimer=setTimeout(poll,2500);}catch(e){$('#editor-status').textContent=e.message;}};pairTimer=setTimeout(poll,2500);
  }catch(e){$('#editor-status').textContent=e.message;}
};
$('#photo-form').elements.file.onchange=e=>{if(previewUrl)URL.revokeObjectURL(previewUrl);const file=e.target.files[0];$('#photo-preview').hidden=!file;if(file){previewUrl=URL.createObjectURL(file);$('#photo-preview').src=previewUrl;}};
$('#photo-form').onsubmit=async e=>{
  e.preventDefault();const form=e.target,file=form.elements.file.files[0];if(!file || file.size>3_000_000){setStatus('请选择3MB以内的JPEG、PNG或WebP图片',true);return;}
  if(!['image/jpeg','image/png','image/webp'].includes(file.type)){setStatus('暂不支持此图片格式',true);return;}
  const input={title:form.elements.title.value,caption:form.elements.caption.value};
  let ok;
  if(demo){input.url=URL.createObjectURL(file);ok=await mutate('photo.add',input);}else{const bytes=new Uint8Array(await file.arrayBuffer());let binary='';for(let i=0;i<bytes.length;i+=8192)binary+=String.fromCharCode(...bytes.subarray(i,i+8192));ok=await mutate('photo.add',input,{path:'/api/photos',extra:{data:btoa(binary)}});}
  if(ok){form.reset();$('#photo-preview').hidden=true;if(previewUrl){URL.revokeObjectURL(previewUrl);previewUrl=null;}}
};
$('#settings-form').addEventListener('input',()=>settingsDirty=true);
$('#settings-form').onsubmit=async e=>{e.preventDefault();const input={...board.settings,...formData(e.target)};try{normalizeSettings(input);}catch(e){statusError(e);return;}if(await mutate('settings.update',input)){settingsDirty=false;populateSettings();}};
$('#approve-form').onsubmit=async e=>{e.preventDefault();if(demo){setStatus('示例模式不能连接真实设备。请管理员登录后操作。',true);return;}const button=e.target.querySelector('button');button.disabled=true;try{const r=await api('/api/pair/approve',{code:e.target.elements.code.value});setStatus('已批准'+(r.role==='viewer'?'电视只读':'家人编辑')+'连接，等待设备领取。');e.target.reset();setTimeout(devices,3500);}catch(e){statusError(e);}finally{button.disabled=false;}};
async function devices(){
  $('#device-list').replaceChildren();if(demo){$('#device-list').append(el('p','empty','演示模式没有连接真实设备。'));return;}if(role!=='owner'){$('#device-list').append(el('p','empty','请管理员连接或移除设备。'));return;}
  try{const list=await api('/api/devices');for(const d of list){const row=el('div','entry'),info=el('div');info.append(el('strong','',d.label+(d.current?' · 当前设备':'')),el('p','',d.role==='viewer'?'只读展示':d.role==='editor'?'可编辑家庭内容':d.role==='controller'?'本机助手 · '+(d.scopes||[]).join(' / ')+' · 到期 '+new Date(d.expiresAt).toLocaleString('zh-CN'):'家庭管理员'));row.append(info);if(!d.current)row.append(action('移除',async()=>{if(!confirm('移除此设备？其下一次请求将失效。'))return;try{await api('/api/devices/revoke',{id:d.id});await devices();}catch(e){statusError(e);}}));$('#device-list').append(row);}}
  catch(e){statusError(e);}
}
setDates();
if(demo)render();else{try{const session=await api('/api/session');role=session.role;if(role==='viewer'){$('#login-status').textContent='当前浏览器是电视只读设备。编辑请用已授权手机或管理员口令登录。';}else await reload();}catch(e){if(e.status!==401)$('#login-status').textContent='暂时无法连接服务，请稍后重试。';}}
window.addEventListener('pagehide',()=>{pairGeneration++;clearTimeout(pairTimer);if(previewUrl)URL.revokeObjectURL(previewUrl);});

let importedEntries=null;
$('#import-content').onchange=async e=>{const file=e.target.files[0];importedEntries=null;$('#confirm-import').hidden=true;$('#content-preview').replaceChildren();if(!file)return;try{if(file.size>65536)throw new Error('内容包不得超过64KB');const data=JSON.parse(await file.text());if(data.format!=='aperture-content/v1'||!Array.isArray(data.entries)||data.entries.length<1||data.entries.length>20)throw new Error('请选择包含1至20条内容的有效内容包');const entries=data.entries.map(normalizeEntry);if(entries.some(entry=>!['event','note'].includes(entry.type)))throw new Error('此处只导入安排与留言');importedEntries=entries;for(const entry of importedEntries)$('#content-preview').append(el('p','help',entry.type+' · '+entry.title));$('#confirm-import').hidden=false;}catch(error){$('#content-preview').textContent=error.message;}};
$('#confirm-import').onclick=async()=>{if(importedEntries&&await mutate('entries.import',{entries:importedEntries})){importedEntries=null;$('#confirm-import').hidden=true;$('#import-content').value='';$('#content-preview').textContent='已导入，可在对应栏目查看。';}};

window.addEventListener('pageshow',event=>{if(event.persisted)location.reload();});

// Legacy life deep links stay on the simplified household workspace.

if(new URLSearchParams(location.search).get('tab')==='cinema')document.querySelector('[data-tab=cinema]').click();
