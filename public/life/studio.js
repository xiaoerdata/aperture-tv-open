import {$,el,api} from '../ui/client.js';
import {catalog,groups,recipes,sampleSignals} from './catalog.js';
export function mountLife({demo,getBoard,getRole,mutate,onError}){
  const panel=el('section','tab-panel');panel.id='panel-life';panel.hidden=true;
  panel.innerHTML=`<div class="life-heading"><div><p class="section-no">生活连接 / 让信息回到日常</p><h2>今天的家，<br>一眼就知道。</h2><p>温度、日程、今晚的菜单。<br>从你用的工具里，选一些值得一起看的事。</p><button id="life-try" data-mobile-control="action" data-mobile-tone="primary">体验分享流程 ↗</button></div><div class="life-vignette" aria-label="生活数据展示示意，非真实设备"><div class="life-arch"><span class="vignette-sun" aria-hidden="true"></span><span class="vignette-time">18:30</span><strong>欢迎回家。</strong><span>客厅 24.6°C · 今晚番茄牛腩</span><small>示意内容 · 非真实连接</small></div><p>手机选内容 → 家人一起看</p></div></div>
  <div class="life-section-title"><h3>先从一个日常开始</h3><span>6 种用法 · 可以自由组合</span></div><div class="life-recipes" id="life-recipes"></div>
  <section class="life-workbench" id="life-workbench"><div class="life-section-title"><h3>我的生活连接</h3><span>先预览，再分享</span></div><p class="help">只有勾选分享的摘要会出现在电视。当前为手动读取的快照，过期后会提示更新。</p><details><summary>把米家设备放上电视</summary><p>在自己的 Home Assistant 安装小米官方 Xiaomi Home 集成，并在那边登录、选择家庭和设备。再由服务管理员配置要显示的实体，回到这里读取预览、勾选分享。</p><p>电视原生小米账号与设备授权分别管理；当前为只读快照，过期后请重新读取。不会自动控制家电，也不会把账号凭证发送到电视。</p><a href="/guide#xiaomi-home">查看接入步骤 ↗</a></details><div id="life-sources"></div><p id="life-status" role="status"></p><form id="life-preview" hidden><h4>这些内容，现在只在你的手机上</h4><div id="life-preview-items"></div><p class="help">勾选表示允许已连接的家庭屏和家庭成员查看。最多展示6项；读取失败不会覆盖已分享内容。</p><button id="life-publish" data-mobile-control="action" data-mobile-tone="primary">分享所选内容到电视</button></form><h4>已经分享</h4><div id="life-published"></div></section>
  <div class="life-section-title"><h3>发现适合你家的连接</h3><span>来源已核查 · 账号与设备待验证</span></div><p class="help">可配置表示支持通过连接框架接入，仍需安装上游、授权账号和映射字段。开源许可不代表数据服务免费；境内网络尚未实测。</p><div class="life-filters" id="life-filters" aria-label="来源分类"></div><label class="life-search">搜索连接<input id="life-search" type="search" placeholder="米家、日程、天气、菜谱…"></label><p id="life-catalog-count" class="help" aria-live="polite"></p><div class="life-catalog" id="life-catalog"></div>`;
  $('#panel-connect').before(panel);
  const tab=el('button','','生活连接');tab.dataset.tab='life';tab.dataset.mobileControl='nav';document.querySelector('[data-tab=connect]').before(tab);
  let category='all',recipeFilter=null,preview=null,reading=false,generation=0;
  const status=(value,error=false)=>{const node=$('#life-status');node.textContent=value;node.dataset.error=error;};
  const button=(label,fn)=>{const b=el('button','',label);b.type='button';b.dataset.mobileControl='action';b.onclick=fn;return b;};
  function renderCatalog(){
    const term=$('#life-search').value.toLowerCase().trim(),items=catalog.filter(s=>(category==='all'||s.group===category)&&(!recipeFilter||recipeFilter.includes(s.id))&&[s.name,s.summary,s.needs].join(' ').toLowerCase().includes(term));
    $('#life-catalog').replaceChildren();$('#life-catalog-count').textContent=`${items.length} 个来源${recipeFilter?' · 已按所选用法筛选':''}`;
    for(const s of items){const row=el('article','life-source'),meta=el('p','life-source-meta',s.kind+' · '+s.status),details=el('details'),link=el('a','','查看原项目与接入说明 ↗');link.href=s.url;link.target='_blank';link.rel='noopener noreferrer';
      details.append(el('summary','','准备条件与注意事项'),el('p','',s.needs),el('p','',s.region),el('p','',s.caution),el('p','help','许可：'+s.license),link);row.append(meta,el('h4','',s.name),el('p','',s.summary),details);$('#life-catalog').append(row);}
    if(!items.length)$('#life-catalog').append(el('p','empty','没有找到对应来源，试试别的关键词。'));
  }
  for(const [id,label] of groups){const b=button(label,()=>{category=id;recipeFilter=null;for(const n of $('#life-filters').children)n.setAttribute('aria-pressed',String(n===b));renderCatalog();});b.setAttribute('aria-pressed',String(id==='all'));$('#life-filters').append(b);}
  $('#life-search').oninput=renderCatalog;
  for(const recipe of recipes){const item=el('article','life-recipe'),details=el('details');details.append(el('summary','','看看怎么用'),el('p','',recipe.text),el('p','',recipe.fields.join(' · ')),el('p','help',recipe.privacy));if(recipe.sources.length)details.append(button('查看所需连接',()=>{category='all';recipeFilter=recipe.sources;$('#life-search').value='';for(const [i,b] of [...$('#life-filters').children].entries())b.setAttribute('aria-pressed',String(i===0));renderCatalog();$('#life-filters').scrollIntoView({block:'start'});}));item.append(el('span','recipe-time',recipe.time),el('h4','',recipe.title),details);$('#life-recipes').append(item);}
  function renderPreview(){
    $('#life-preview-items').replaceChildren();$('#life-preview').hidden=!preview;
    if(!preview)return;
    for(const s of preview.signals){const label=el('label','life-choice'),input=el('input'),body=el('span');input.type='checkbox';input.value=s.id;input.disabled=Date.parse(s.expiresAt)<=Date.now();body.append(el('strong','',s.title+' · '+s.value+' '+s.unit),el('small','',s.source+' · '+(s.observedAt?'数据时间 '+new Date(s.observedAt).toLocaleString('zh-CN'):'读取时间 '+new Date(s.fetchedAt).toLocaleTimeString('zh-CN')+'；源时间未知')+(input.disabled?' · 已过期':'')));label.append(input,body);$('#life-preview-items').append(label);}
  }
  async function read(sourceId,queryId){
    if(reading)return;reading=true;const current=++generation;status('正在读取，仅预览，不会自动分享…');
    $('#life-sources').querySelectorAll('button').forEach(b=>b.disabled=true);
    try{preview=await api('/api/life/preview',{sourceId,queryId});if(current!==generation)return;renderPreview();status(preview.unavailable.length?'部分字段不可用：'+preview.unavailable.join('、'):'读取完成。请勾选愿意与家人分享的内容。');}
    catch(e){status(e.message,true);onError(e);}finally{reading=false;$('#life-sources').querySelectorAll('button').forEach(b=>b.disabled=false);}
  }
  async function load(){
    const target=$('#life-sources');target.replaceChildren();
    if(demo){target.append(el('p','empty','示例模式没有连接你的账号或家居设备。点击上方「体验分享流程」使用虚构数据。'));return;}
    if(getRole()!=='owner'){target.append(el('p','empty','请家庭管理员配置和分享生活来源。你可以浏览推荐用法。'));return;}
    try{const sources=await api('/api/life/sources');if(!sources.length)target.append(el('p','empty','尚未配置生活来源。自托管管理员可按项目文档添加连接，再回到这里读取。'));
      for(const s of sources){const row=el('div','life-configured');row.append(el('strong','',s.label));for(const q of s.queries)row.append(button(q.label+' · 读取预览',()=>read(s.id,q.id)));target.append(row);}
    }catch(e){status(e.message,true);onError(e);}
  }
  $('#life-try').onclick=()=>{
    if(!demo){status('此处是你的真实家庭。体验虚构数据请打开示例工作台。');const a=el('a','','打开示例工作台 ↗');a.href='/studio?demo=1&tab=life';a.target='_blank';a.rel='noopener';$('#life-status').append(document.createTextNode(' '),a);$('#life-workbench').scrollIntoView();return;}
    preview={token:'demo',signals:sampleSignals()};renderPreview();status('以下均为虚构示例。选两项，试试分享流程。');$('#life-workbench').scrollIntoView();
  };
  $('#life-preview').onsubmit=async e=>{
    e.preventDefault();if(!preview||reading)return;
    const ids=[...$('#life-preview-items').querySelectorAll('input:checked')].map(n=>n.value);if(!ids.length){status('先选择至少一项。',true);return;}
    $('#life-publish').disabled=true;
    try{
      const ok=await mutate('life.publish',demo?{signals:preview.signals.filter(s=>ids.includes(s.id))}:{token:preview.token,ids},{path:'/api/life/publish'});
      if(ok){preview=null;renderPreview();status(demo?'示例已分享到本页；不会发送到真实电视。':'已分享所选摘要，在线家庭屏将在下次同步时显示。');renderPublished();}
    }finally{$('#life-publish').disabled=false;}
  };
  function renderPublished(){const target=$('#life-published');target.replaceChildren();for(const s of getBoard().life||[]){const row=el('div','entry'),body=el('div');body.append(el('strong','',s.title+' · '+s.value+' '+s.unit),el('p','',s.source+' · '+(Date.parse(s.expiresAt)<=Date.now()?'已过期，请重新读取':'已分享的快照')));row.append(body);if(demo||getRole()==='owner')row.append(button('移除',async()=>{if(await mutate('life.remove',{id:s.id}))renderPublished();}));target.append(row);}if(!target.children.length)target.append(el('p','empty','还没有分享生活数据，电视不会读取你的私人来源。'));}
  renderCatalog();renderPublished();return {load,render:renderPublished};
}
