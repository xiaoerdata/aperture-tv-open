/* ES5. Local app suggestions and authenticated household summaries; no account tokens. */
(function () {
 'use strict';
 window.KaishiHome = function (options) {
  var catalog=[], apps={}, slots=[], sources={}, slides=[], index=0, timer=null, mode='auto', current='', disposed=false, expanded=false, foldTimer=null, snapshotReceived=false;
  var panel=document.getElementById('personal-panel'), dock=document.getElementById('home-dock');
  function node(tag,cls,value){var n=document.createElement(tag);n.className=cls||'';if(value!==undefined)n.textContent=value;return n;}
  function id(name){return document.getElementById(name);}
  function info(key){for(var i=0;i<catalog.length;i++)if(catalog[i].id===key)return catalog[i];return null;}
  function stop(){clearTimeout(timer);timer=null;}
  function reduced(){return window.matchMedia&&window.matchMedia('(prefers-reduced-motion: reduce)').matches;}
  function schedule(){if(disposed||mode!=='auto'||slides.length<2||!options.visible()||reduced()||panel.contains(document.activeElement)){stop();return;}if(timer!==null)return;timer=setTimeout(function(){timer=null;index=(index+1)%slides.length;draw(true);schedule();},12000);}
  function draw(animate){
   panel.hidden=mode==='hidden'||!slides.length;id('ambient-home').className='home-screen ambient-screen'+(panel.hidden?' ambient-empty':'');if(panel.hidden){if(panel.contains(document.activeElement))options.focus(id('home-live'));['personal-kind','personal-title','personal-value','personal-meta','personal-count'].forEach(function(key){id(key).textContent='';});id('personal-photo').removeAttribute('src');stop();return;}
   index=Math.min(index,slides.length-1);var slide=slides[index];current=slide.key;
   id('personal-kind').textContent=slide.kind;id('personal-title').textContent=slide.title;id('personal-value').textContent=slide.value;id('personal-meta').textContent=slide.meta||'';id('personal-open').textContent=slide.open;
   id('personal-photo').hidden=!slide.photo;if(slide.photo)id('personal-photo').src=slide.photo;else id('personal-photo').removeAttribute('src');
   id('personal-count').textContent=(index+1)+' / '+slides.length;id('personal-next').hidden=slides.length<2;id('personal-pause').hidden=slides.length<2||reduced();id('personal-pause').textContent=mode==='auto'?'暂停':'轮播';id('personal-pause').setAttribute('aria-pressed',String(mode==='auto'));
   if(panel.contains(document.activeElement)&&document.activeElement.hidden)options.focus(id('personal-open'));
   panel.setAttribute('data-kind',slide.key);var copy=id('personal-copy');copy.className='personal-copy';if(animate&&!reduced()){void copy.offsetWidth;copy.className+=' personal-enter';}
  }
  function update(){
   var data=options.data(), values=[], photos=data.photos||[], metrics=data.metrics||[], notes=data.notes||[], events=data.events||[], life=data.life||[];
   if(photos.length)values.push({key:'photos',kind:'相册',title:photos[0].title||'家庭相册',value:photos[0].caption||'',photo:photos[0].url,open:'打开相册',action:'photos'});
   if(metrics.length){var m=metrics[0];values.push({key:'data',kind:'数据',title:m.title,value:String(m.value)+' '+m.unit,meta:m.date+(m.date===data.today?' · 今日':' · 历史记录'),open:'查看数据',action:'data'});}
   for(var k=0;k<life.length;k++)if(Date.parse(life[k].expiresAt)>Date.now()){var l=life[k];values.push({key:'life',kind:'生活',title:l.title,value:String(l.value)+' '+l.unit,meta:l.source,open:'查看数据',action:'data'});break;}
   if(notes.length)values.push({key:'note',kind:'留言',title:notes[0].title,value:notes[0].body,meta:notes[0].author,open:'查看消息',action:'information'});
   if(events.length)values.push({key:'event',kind:'今天',title:events[0].title,value:events[0].time,meta:events[0].person,open:'查看日程',action:'information'});
   if(sources.nasCount>0)values.push({key:'nas',kind:'NAS',title:'家庭片库',value:sources.nasPhotos?'照片 · 影片':'影片与文件',meta:String(sources.nasCount)+' 个已保存的连接',open:sources.nasPhotos?'打开 NAS 相册':'打开 NAS',action:sources.nasPhotos?'nas-photos':'nas'});
   slides=values;index=0;for(k=0;k<slides.length;k++)if(slides[k].key===current)index=k;draw(false);schedule();
  }
  function mark(entry,icon){
   var box=node('span','app-mark'),picture=node('img','app-real-icon'),fallback='./app-icons/application.svg';
   box.setAttribute('aria-hidden','true');if(entry)box.setAttribute('data-brand',entry.id);
   var preset=entry&&/^app-icons\/[a-z]{2,20}\.(png|jpg)$/.test(entry.icon||'')?'./'+entry.icon:'';
   var nativeIcon=/^\/apps\/icons\/[a-z0-9]{2,20}\.png$/.test(icon||'')?icon:'';
   var stage=nativeIcon?'installed':preset?'preset':'generic';
   picture.alt='';picture.draggable=false;
   picture.onerror=function(){
    if(stage==='installed'&&preset){stage='preset';picture.src=preset;}
    else if(stage!=='generic'){stage='generic';picture.src=fallback;}
    else{picture.onerror=null;picture.hidden=true;}
    box.setAttribute('data-icon-source',stage);
   };
   box.setAttribute('data-icon-source',stage);box.appendChild(picture);picture.src=nativeIcon||preset||fallback;return box;
  }
  function setApps(value,focus){
   clearTimeout(foldTimer);expanded=value;var list=id('home-app-list');id('dock-toggle').setAttribute('aria-expanded',String(value));id('ambient-home').setAttribute('data-apps-expanded',String(value));dock.setAttribute('data-expanded',String(value));list.setAttribute('aria-hidden',String(!value));
   var buttons=list.querySelectorAll('button');for(var b=0;b<buttons.length;b++)buttons[b].tabIndex=value?0:-1;
   if(value){list.hidden=false;list.className='home-app-list apps-unfold';if(focus)options.focus(id('home-apps').querySelector('button')||id('home-app-add'));}
   else{if(focus||list.contains(document.activeElement))options.focus(id('dock-toggle'));list.className='home-app-list apps-fold';if(reduced())list.hidden=true;else foldTimer=setTimeout(function(){if(!expanded)list.hidden=true;},180);}
  }
  id('dock-toggle').onclick=function(){setApps(!expanded,true);};
  function renderApps(){
   var old=document.activeElement.id;id('home-apps').textContent='';
   for(var q=1;snapshotReceived&&q<=4;q++){var empty=id('quick'+q);empty.hidden=slots.some(function(s){return !!s.name;})||q>1;empty.className='shortcut glass';empty.querySelector('strong').textContent='添加应用';empty.querySelector('.shortcut-glyph').textContent='+';empty.setAttribute('aria-label','常用应用 '+q+'，未设置');empty.onclick=options.presets;}
   for(var i=0;i<slots.length;i++){(function(slot){if(!slot.name)return;var item=info(slot.id),button=node('button','dock-app');button.id='dock-'+slot.slot;button.appendChild(mark(item,slot.icon));button.appendChild(node('span','dock-label',slot.name));button.onclick=function(){options.action('open-'+slot.slot);};id('home-apps').appendChild(button);
    var quick=id(slot.slot);if(quick){quick.hidden=false;quick.querySelector('strong').textContent=slot.name;quick.setAttribute('aria-label','打开常用应用 '+slot.name);quick.className='shortcut glass has-app-icon';var glyph=quick.querySelector('.shortcut-glyph');if(glyph){glyph.textContent='';glyph.appendChild(mark(item,slot.icon));}quick.onclick=function(){options.action('open-'+slot.slot);};}
   }(slots[i]));}
   id('dock-stack').textContent='';for(var n=0;n<slots.length&&id('dock-stack').children.length<3;n++)if(slots[n].name)id('dock-stack').appendChild(mark(info(slots[n].id),slots[n].icon));
   if(!id('dock-stack').children.length)id('dock-stack').appendChild(node('span','dock-empty-mark','＋'));
   id('dock-total').textContent=id('home-apps').children.length?String(id('home-apps').children.length)+' 个应用':'添加应用';
   var appButtons=id('home-app-list').querySelectorAll('button');for(n=0;n<appButtons.length;n++)appButtons[n].tabIndex=expanded?0:-1;
   dock.setAttribute('data-populated',String(id('home-apps').children.length>0));
   if(/^dock-quick/.test(old)){var target=id(old);options.focus(target||id('home-app-add'));}
  }
  function presetActions(){var out=[];for(var i=0;i<catalog.length;i++){var c=catalog[i],app=apps[c.id];out.push(['preset-'+c.id,c.name,app&&app.installed?'已安装 · 添加到首页':c.tvName+(snapshotReceived?' · 未安装':' · 待检测'),'']);}return out;}
  function decorate(root){root.className='preset-grid';var buttons=root.querySelectorAll('button');for(var i=0;i<buttons.length;i++){var key=buttons[i].getAttribute('data-action').slice(7),entry=info(key),app=apps[key];buttons[i].insertBefore(mark(entry,app&&app.icon),buttons[i].firstChild);}}
  try{mode=localStorage.getItem('kaishi-home-panels')||'auto';}catch(ignored){}if(['auto','static','hidden'].indexOf(mode)<0)mode='auto';
  function setMode(value){mode=value;try{localStorage.setItem('kaishi-home-panels',mode);}catch(ignored){}update();}
  id('personal-open').onclick=function(){var slide=slides[index];if(slide)options.open(slide.action);};
  id('personal-next').onclick=function(){index=(index+1)%slides.length;draw(true);schedule();};id('personal-pause').onclick=function(){setMode(mode==='auto'?'static':'auto');};
  id('home-app-add').onclick=options.presets;
  document.addEventListener('focusin',schedule);document.addEventListener('visibilitychange',schedule);
  window.addEventListener('pagehide',function(){disposed=true;stop();});
  window.apertureHomeSources=function(value){sources=value&&typeof value==='object'?value:{};update();};
  window.apertureHomeApps=function(value){snapshotReceived=true;apps={};slots=[];if(value&&Array.isArray(value.apps))for(var i=0;i<value.apps.length;i++)apps[value.apps[i].id]=value.apps[i];if(value&&Array.isArray(value.slots))for(i=0;i<Math.min(4,value.slots.length);i++)if(/^quick[1-4]$/.test(value.slots[i].slot))slots.push(value.slots[i]);renderApps();};
  var request=new XMLHttpRequest();request.open('GET','./app-presets.json',true);request.onload=function(){try{if(request.status!==200&&request.status!==0)return;var data=JSON.parse(request.responseText);if(!Array.isArray(data)||data.length>24)return;catalog=data;renderApps();}catch(ignored){}};request.send();
  return {appsExpanded:function(){return expanded;},setApps:setApps,update:update,schedule:schedule,presetActions:presetActions,decorate:decorate,setMode:setMode};
 };
}());
