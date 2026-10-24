(function () {
  'use strict';
  var local = location.protocol === 'file:' || (location.protocol === 'https:' && location.host === 'appassets.androidplatform.net' && location.pathname === '/assets/index.html'), pane = null, prior = null, disposed = false, parents = false;
  var labels = {}, query = {}, timer, contentTimer, request = null, detailState = null;
  var section = 'overview', contentPage = 0, slide = 0, photoIndex = 0, playing = false, carouselTimer = null, board = null;
  var virtualHome = local && location.protocol === 'https:', nativeActive = !virtualHome;
  var reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  function $(id) { return document.getElementById(id); }
  function text(id, value) { $(id).textContent = value; }
  location.search.slice(1).split('&').forEach(function (part) {
    var bits = part.split('=');
    try { query[decodeURIComponent(bits[0])] = decodeURIComponent((bits[1] || '').replace(/\+/g, ' ')); } catch (ignored) {}
  });
  labels.live = query.live || ''; labels.cinema = query.cinema || ''; labels.system = query.system || '';
  parents = query.edition === 'parents';
  if (parents) {
    document.body.className = 'parents'; document.title = '开窗 · 爸妈版';
    ['entry-family', 'entry-photos', 'entry-data', 'entry-information', 'entry-tools', 'entry-system'].forEach(function (id) { $(id).hidden = true; });
    $('entry-series').hidden = false; $('parents-shortcuts').hidden = false;
    document.querySelector('.wordmark small').textContent = '爸妈版';
    document.querySelector('.mood-switch').hidden = true;
    document.querySelector('.entry-heading h2').textContent = '观看';
    document.querySelector('#entry-live strong').textContent = '看电视';
    document.querySelector('#entry-cinema strong').textContent = '看电影';
    document.querySelector('#entry-system strong').textContent = '原来的桌面';
    text('environment', '');
    text('live-label', ''); text('cinema-label', ''); text('system-label', '');
    $('settings-open').textContent = '设置';
    $('settings-open').setAttribute('aria-label', '设置');
  }
  window.apertureNasLabels = function (movies, series) { if (!parents) return; text('cinema-label', String(movies)); text('series-label', String(series)); };
  if (local) {
    text('environment', '');
    text('local-note', '');
  }
  if (!parents && labels.live) text('live-label', labels.live);
  if (!parents && labels.cinema) text('cinema-label', labels.cinema);
  if (labels.system) text('system-label', labels.system);
  var specs = {
    live: {title:'直播', actions:[
      ['open-live','直播应用',labels.live || '未设置','primary'],
      ['family-live','家庭频道','',''], ['choose-live','更换直播应用','','']
    ]},
    cinema: {title:'影视', actions:[
      ['open-cinema','影视应用',labels.cinema || '未设置','primary'],
      ['nas','NAS','',''], ['family-library','家庭片库','',''], ['choose-cinema','更换影视应用','','']
    ]},
    system: {title:'原桌面', actions:[
      ['open-system','返回原桌面',labels.system || '','primary'],
      ['home-settings','默认桌面','',''], ['choose-system','选择桌面','','']
    ]},
    shortcuts: {title:'常用应用', actions:[['choose-shortcuts','管理快捷入口','','primary'],['apps','所有应用','',''],['install-apps','安装应用','','']]},
    devices: {title:'米家', description:'通过 Home Assistant 授权分享设备状态。', actions:[
      ['account-settings','小米账号','','primary'], ['show-data','设备状态','','']
    ]},
    voice: {title:'小爱同学', description:'语音键由原系统处理。远场唤醒在系统设置中开启。', actions:[
      ['open-assistant','打开小爱同学',query.assistant || '','primary'], ['voice-help','语音帮助','',''], ['voice-settings','语音设置','','']
    ]},
    settings: {title:'设置', actions:[
      ['configure','家庭服务','','primary'], ['voice-help','小爱同学','',''], ['apps','所有应用','',''],
      ['home-settings','默认桌面','',''], ['home-mode','首页样式','',''], ['material','透明效果','',''], ['install-apps','安装应用','','']
    ]}
  };
  if (parents) {
    specs.settings.actions = [
      ['configure','家庭服务','','primary'], ['choose-shortcuts','常用应用','',''], ['nas','NAS','',''],
      ['nas-photos','NAS 相册','',''], ['home-mode','首页样式','',''], ['open-system','返回原桌面','',''],
      ['choose-system','选择桌面','',''], ['home-settings','默认桌面','',''], ['voice-help','小爱同学','',''],
      ['family','家庭屏','',''], ['install-apps','安装应用','','']
    ];
  }
  function nativeAction(action) {
    if (!local) { text('action-status', '请在电视上操作'); return; }
    location.href = 'aperture://action/' + action;
  }
  function familyPage(page) {
    if (local) { nativeAction(page); return; }
    var demo = query.demo === '1';
    location.href = page === 'family' ? (parents ? '/tv-lite?edition=parents' + (demo ? '&demo=1' : '') : '/tv' + (demo ? '?demo=1' : '')) : '/cinema?from=home' + (parents ? '&edition=parents' : '') + '&view=' + (page === 'family-live' ? 'live' : page === 'family-series' ? 'series' : page === 'family-movies' ? 'movies' : 'library');
  }
  function material(value) {
    if (parents) value = true;
    $('lobby').className = value ? 'lobby solid' : 'lobby';
    try { if (!parents) localStorage.setItem('aperture-launcher-solid', value ? '1' : '0'); } catch (ignored) {}
    var control = document.querySelector('[data-action=material]');
    if (control) { control.setAttribute('aria-pressed', String(value)); control.querySelector('strong').textContent = value ? '开启透明效果' : '关闭透明效果'; }
  }
  function act(action) {
    if (action === 'close-detail') { closePane(); return; }
    if (action === 'show-data') { closePane(); setSection('data'); return; }
    if (action === 'material') { material($('lobby').className.indexOf('solid') === -1); return; }
    if (action === 'family' || action === 'family-live' || action === 'family-library' || action === 'family-series' || action === 'family-movies') { familyPage(action); return; }
    nativeAction(action);
  }
  function openPane(name) {
    var spec = specs[name]; if (!spec) return;
    if (!pane) prior = document.activeElement;
    pane = name;
    text('pane-title', spec.title); text('pane-eyebrow', ''); text('pane-description', spec.description || ''); text('pane-footnote', spec.footnote || ''); text('action-status', '');
    $('pane-description').hidden = !spec.description; $('pane-footnote').hidden = !spec.footnote;
    $('pane-art').setAttribute('data-pane', name); $('pane-actions').textContent = '';
    for (var i = 0; i < spec.actions.length; i++) {
      (function (entry) {
        var button = document.createElement('button'), title = document.createElement('strong'), detail = document.createElement('small');
        button.className = 'pane-action ' + entry[3]; button.setAttribute('data-action', entry[0]);
        title.textContent = entry[1]; detail.textContent = entry[2]; button.appendChild(title); button.appendChild(detail);
        button.onclick = function () { act(entry[0]); }; $('pane-actions').appendChild(button);
      }(spec.actions[i]));
    }
    $('pane-backdrop').hidden = false; $('lobby').setAttribute('aria-hidden', 'true');
    material($('lobby').className.indexOf('solid') !== -1);
    $('pane-actions').querySelector('button').focus();
  }
  function closePane() {
    if (!pane) return false;
    if (pane === 'detail') { if (detailState) { var key = (detailState.metric ? 'metric:' : 'life:') + detailState.id, candidates = $('collection-body').querySelectorAll('[data-content-id]'); for (var j = 0; j < candidates.length; j++) if (candidates[j].getAttribute('data-content-id') === key) prior = candidates[j]; } detailState = null; delete specs.detail; text('pane-title', ''); text('pane-description', ''); text('pane-footnote', ''); }
    pane = null; $('pane-backdrop').hidden = true; $('lobby').removeAttribute('aria-hidden');
    if (prior && prior.focus && prior.offsetWidth) prior.focus(); else $(parents && /^quick[1-4]$/.test(query.focus || '') ? query.focus : parents ? 'entry-live' : 'entry-live').focus();
    return true;
  }
  var panes = document.querySelectorAll('[data-pane]');
  for (var i = 0; i < panes.length; i++) panes[i].onclick = function () { var name = this.getAttribute('data-pane'); if (parents && name === 'live') familyPage('family-live'); else if (parents && name === 'cinema') familyPage('family-movies'); else openPane(name); };
  $('entry-series').onclick = function () { familyPage('family-series'); };
  ['quick1', 'quick2', 'quick3', 'quick4'].forEach(function (slot) {
    var name = query[slot] || '';
    $(slot).querySelector('strong').textContent = name || '未设置';
    $(slot).setAttribute('aria-label', name ? '打开常用应用 ' + name : '常用应用 ' + slot.slice(-1) + '，未设置');
    $(slot).onclick = function () { if (local) nativeAction('open-' + slot); else openPane('shortcuts'); };
  });
  $('shortcut-all').onclick = function () { if (local) nativeAction('apps'); else openPane('shortcuts'); };

  $('entry-family').onclick = function () { familyPage('family'); };
  $('settings-open').onclick = function () { openPane('settings'); };
  $('brand-home').onclick = function (event) { event.preventDefault(); closePane(); setSection('overview'); $(parents ? 'entry-live' : 'entry-live').focus(); };
  $('pane-close').onclick = closePane;
  $('pane-backdrop').onclick = function (event) { if (event.target === this) closePane(); };
  // Household content is read through the existing authenticated service. No new token or native bridge.
  function element(tag, className, value) {
    var node = document.createElement(tag); if (className) node.className = className;
    if (value !== undefined) node.textContent = value; return node;
  }
  function today() {
    var d = new Date(), zone = (board && board.settings && board.settings.timeZone) || 'Asia/Shanghai';
    try {
      var formatter = new Intl.DateTimeFormat('en-CA', {timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit'});
      if (formatter.formatToParts) { var bits = {}; formatter.formatToParts(d).forEach(function (p) { bits[p.type] = p.value; }); return bits.year + '-' + bits.month + '-' + bits.day; }
      var formatted = formatter.format(d); if (/^\d{4}-\d{2}-\d{2}$/.test(formatted)) return formatted;
    } catch (ignored) {}
    if (zone === 'Asia/Shanghai') { d = new Date(d.getTime() + 8 * 3600000); return d.getUTCFullYear() + '-' + ('0' + (d.getUTCMonth() + 1)).slice(-2) + '-' + ('0' + d.getUTCDate()).slice(-2); }
    return d.getFullYear() + '-' + ('0' + (d.getMonth() + 1)).slice(-2) + '-' + ('0' + d.getDate()).slice(-2);
  }
  function entries(type) {
    return ((board && board.entries) || []).filter(function (e) { return e.type === type; }).sort(function (a, b) {
      if (type === 'note') return Number(!!b.pinned) - Number(!!a.pinned);
      if (type === 'metric') return (a.date === today() ? -1 : 0) - (b.date === today() ? -1 : 0);
      if (type === 'event') return String(a.time).localeCompare(String(b.time)); return 0;
    });
  }
  function photos() { return ((board && board.photos) || []).map(function (p) { return {title:p.title, caption:p.caption, url:p.url || (p.asset ? '/media/' + p.asset : '')}; }).filter(function (p) { return /^\/(media\/[a-f0-9]{64}\.jpg|assets\/[a-zA-Z0-9_.-]+)$/.test(p.url); }); }
  function tile(title, detail, className) {
    var node = element('article', 'collection-tile glass ' + (className || ''));
    node.appendChild(element('h2', '', title)); if (detail) node.appendChild(element('p', '', detail));
    $('collection-body').appendChild(node); return node;
  }
  function actionTile(title, detail, action) {
    var button = element('button', 'collection-tile collection-tool glass');
    button.appendChild(element('strong', '', title));
    button.onclick = action; $('collection-body').appendChild(button); return button;
  }
  function pageItems(values) {
    var count = Math.max(1, Math.ceil(values.length / 2)); contentPage = Math.max(0, Math.min(contentPage, count - 1));
    $('collection-paging').hidden = count < 2; text('collection-page', (contentPage + 1) + ' / ' + count);
    $('collection-prev').disabled = contentPage === 0; $('collection-next').disabled = contentPage >= count - 1;
    return values.slice(contentPage * 2, contentPage * 2 + 2);
  }
  $('collection-nas').onclick = function () { nativeAction('nas-photos'); };
  $('collection-prev').onclick = function () { contentPage--; contentView(); if (this.disabled) $('collection-next').focus(); };
  $('collection-next').onclick = function () { contentPage++; contentView(); if (this.disabled) $('collection-prev').focus(); };
  function dataPresentation(m, metric) {
    var stale = !metric && !(Date.parse(m.expiresAt) > Date.now());
    return {stale:stale, value:stale ? '待更新' : String(m.value), source:metric ? m.source + ' · ' + m.date + (m.date === today() ? ' · 今日记录' : ' · 历史记录') : m.source + ' · ' + (stale ? '快照过期，需重新读取' : m.observedAt ? '数据时间 ' + new Date(m.observedAt).toLocaleString('zh-CN') : '读取时间 ' + new Date(m.fetchedAt).toLocaleString('zh-CN')), full:(stale ? '待更新' : m.value + ' ' + m.unit) + (metric ? '\n目标 ' + m.target + ' ' + m.unit : '')};
  }
  function detail(m, metric) {
    var data = dataPresentation(m, metric); detailState = {id:m.id, metric:metric};
    specs.detail = {title:m.title, eyebrow:'家人分享的内容', description:data.full, footnote:data.source, actions:[['close-detail', '返回分区', '保留当前页', 'primary']]}; openPane('detail');
  }
  function contentView() {
    var specs = {
      photos: ['相册', '', ''],
      data: ['数据', '', ''],
      information: ['消息', '', ''],
      tools: ['工具', '', '']
    }, spec = specs[section];
    if (!spec) return;
    $('collection-nas').hidden = section !== 'photos';
    text('collection-title', spec[0]); text('collection-description', spec[1]); text('collection-kicker', spec[2]);
    $('collection-body').textContent = ''; $('collection-body').className = section === 'tools' ? 'collection-body tools-grid' : 'collection-body'; $('collection-paging').hidden = true;
    if (section === 'photos') {
      var list = photos();
      if (!list.length) {
        var art = element('article', 'collection-tile collection-photo glass'), image = element('img');
        image.src = './carezza-4k.jpg'; image.alt = 'Carezza · Cem Sagisman'; art.appendChild(image);
        var caption = element('span', '', '山湖之间'); caption.appendChild(element('small', '', 'Cem Sagisman · CC0')); art.appendChild(caption); $('collection-body').appendChild(art);
        var empty = tile('暂无照片', '', 'collection-empty');
        var link = element('button', 'collection-link glass', local ? 'NAS 相册' : '家庭屏'); link.onclick = function () { if (local) nativeAction('nas-photos'); else familyPage('family'); }; empty.appendChild(link);
      }
      pageItems(list).forEach(function (p) {
        var art = element('article', 'collection-tile collection-photo glass'), image = element('img'); image.src = p.url; image.alt = p.caption || p.title;
        art.appendChild(image); var caption = element('span', '', p.title); caption.appendChild(element('small', '', p.caption || '家庭照片')); art.appendChild(caption); $('collection-body').appendChild(art);
      });
    } else if (section === 'data') {
      var metrics = entries('metric'), life = (board && board.life) || [];
      var dataItems = metrics.map(function (m) { return {metric:m}; }).concat(life.map(function (m) { return {signal:m}; }));
      pageItems(dataItems).forEach(function (item) {
        var m = item.metric || item.signal, data = dataPresentation(m, !!item.metric);
        var node = tile(m.title, '', 'collection-data'), value = element('div', 'collection-value', data.value);
        if (!data.stale) value.appendChild(element('em', '', m.unit)); node.appendChild(value);
        if (item.metric) node.appendChild(element('p', '', '目标 ' + m.target + ' ' + m.unit)); node.appendChild(element('small', '', data.source));
        node.setAttribute('tabindex', '0'); node.setAttribute('role', 'button'); node.setAttribute('aria-label', '查看 ' + m.title + ' 的详情');
        node.setAttribute('data-content-id', (item.metric ? 'metric:' : 'life:') + m.id);
        node.onclick = function () { detail(m, !!item.metric); };
      });
      if (!metrics.length && !life.length) tile('暂无数据', '', 'collection-empty');
    } else if (section === 'information') {
      var events = entries('event').filter(function (e) { return e.date === today(); }), notes = entries('note');
      var information = events.map(function (e) { return {event:e}; }).concat(notes.map(function (n) { return {note:n}; }));
      pageItems(information).forEach(function (item) {
        if (item.event) { var e = item.event, row = tile(e.title, e.time + ' · ' + e.person + (e.done ? ' · 已完成' : ' · 待办')); }
        else { var n = item.note, row = tile(n.title, n.body); row.appendChild(element('small', '', n.author + (n.pinned ? ' · 置顶留言' : ' · 家人留言'))); }
      });
      if (!events.length && !notes.length) tile('暂无消息', '', 'collection-empty');
    } else if (section === 'tools') {
      actionTile('应用', '查看所有已安装的可启动应用', function () { if (local) nativeAction('apps'); else openPane('settings'); });
      actionTile('NAS', '直接浏览照片与影片 · 无需电脑中转', function () { nativeAction('nas'); });
      actionTile('家庭服务', '连接电脑 / NAS 上的开窗服务', function () { if (local) nativeAction('configure'); else familyPage('family'); });
      actionTile('米家', '原系统账号、米家授权与家庭状态', function () { openPane('devices'); });
      actionTile('小爱同学', '原生语音键、远场唤醒与系统设置', function () { openPane('voice'); });
      actionTile('原桌面', '原来的桌面与 Home 键设置', function () { openPane('system'); });
      actionTile('助手', '配对和自动化在家庭服务中管理', function () { familyPage('family'); });
      actionTile('设置', '减少透明效果、设置本机入口', function () { openPane('settings'); });
    }
    if (query.demo === '1') $('collection-body').appendChild(element('span', 'collection-demo', '演示内容'));
  }
  function setSection(name) {
    if (['overview', 'photos', 'data', 'information', 'tools'].indexOf(name) < 0) return;
    pauseCarousel(); if (section !== name) contentPage = 0; section = name; $('lobby').setAttribute('data-section', name);
    document.querySelector('.welcome-space').hidden = name !== 'overview';
    document.querySelector('.dashboard').hidden = name !== 'overview'; $('collection-view').hidden = name === 'overview';
    var tabs = document.querySelectorAll('.section-nav [data-section]');
    for (var n = 0; n < tabs.length; n++) tabs[n].setAttribute('aria-pressed', String(tabs[n].getAttribute('data-section') === name));
    contentView();
    // The activated summary/hero may now be hidden; move focus to a visible, stable control.
    if (!document.activeElement.offsetWidth) (name === 'overview' ? $('entry-live') : document.querySelector('.back-overview')).focus();
  }
  var sectionButtons = document.querySelectorAll('button[data-section]');
  for (i = 0; i < sectionButtons.length; i++) sectionButtons[i].onclick = function () { setSection(this.getAttribute('data-section')); };
  function pauseCarousel() {
    playing = false; clearInterval(carouselTimer); carouselTimer = null;
    text('slide-play', reduced ? '轮播关闭' : '自动轮播'); $('slide-play').setAttribute('aria-pressed', 'false');
  }
  function showSlide(next) {
    slide = (next + 4) % 4;
    if (parents) return;
    var available = photos(), m = entries('metric')[0], n = entries('note')[0], p = available.length ? available[photoIndex % available.length] : null;
    var slides = [
      ['', p ? p.title : '相册', p ? (p.caption || '') : '暂无照片', '查看相册', 'photos', ''],
      ['', m ? m.title : '数据', m ? String(m.value) + ' ' + m.unit : '暂无数据', '查看数据', 'data', ''],
      ['', n ? n.title : '消息', n ? n.body : '暂无消息', '查看消息', 'information', ''],
      ['', '工具', '应用 · NAS · 系统设置', '打开工具', 'tools', '']
    ], data = slides[slide];
    text('hero-eyebrow', data[0]); text('hero-title', data[1]); text('hero-description', data[2]);
    $('hero-open').textContent = data[3]; $('hero-open').onclick = function () { setSection(data[4]); }; text('hero-source', data[5]);
    text('scene-number', (slide + 1) + ' / 4');
    text('scene-caption', '');
    $('hero-memory').hidden = slide !== 0 || !p;
    if (p && slide === 0) { $('hero-memory').src = p.url; $('hero-memory').alt = p.caption || p.title; }
    else $('hero-memory').removeAttribute('src');
    var dots = document.querySelectorAll('[data-slide]'); for (var j = 0; j < dots.length; j++) dots[j].setAttribute('aria-pressed', String(j === slide));
  }
  $('slide-prev').onclick = function () { pauseCarousel(); showSlide(slide - 1); };
  $('slide-next').onclick = function () { pauseCarousel(); if (slide === 3) photoIndex++; showSlide(slide + 1); };
  var dots = document.querySelectorAll('[data-slide]');
  for (i = 0; i < dots.length; i++) dots[i].onclick = function () { pauseCarousel(); showSlide(Number(this.getAttribute('data-slide'))); };
  $('slide-play').onclick = function () {
    if (playing || reduced || parents) { pauseCarousel(); return; }
    playing = true; text('slide-play', '暂停轮播'); this.setAttribute('aria-pressed', 'true');
    carouselTimer = setInterval(function () { if (!document.hidden && !pane && section === 'overview') { if (slide === 3) photoIndex++; showSlide(slide + 1); } }, 12000);
  };
  if (reduced) { $('slide-play').disabled = true; text('slide-play', '轮播关闭'); }
  document.addEventListener('mousedown', function (event) { if (event.target.id !== 'slide-play') pauseCarousel(); });
  document.addEventListener('touchstart', function (event) { if (event.target.id !== 'slide-play') pauseCarousel(); });
  document.addEventListener('focusin', function () { if (playing) pauseCarousel(); });
  function renderContent() {
    var m = entries('metric')[0], n = entries('note')[0], p = photos()[0];
    text('metric-value', m ? String(m.value) : '—'); text('metric-title', m ? m.title + ' · ' + m.unit : '暂无数据');
    text('metric-detail', m ? '目标 ' + m.target + ' ' + m.unit + ' · ' + m.source : '');
    $('metric-progress').style.width = m ? Math.max(0, Math.min(100, m.value / m.target * 100)) + '%' : '0%';
    text('data-detail', m ? '记录于 ' + m.date + (m.date === today() ? ' · 今日' : ' · 历史数据') : '');
    text('note-title', n ? n.title : '暂无消息'); text('note-body', n ? n.body : ''); text('note-author', n ? n.author + (n.pinned ? ' · 置顶留言' : ' · 家庭留言') : '');
    if (pane === 'detail' && detailState) {
      var candidates = detailState.metric ? ((board && board.entries) || []).filter(function (e) { return e.type === 'metric'; }) : (board && board.life) || [];
      var currentDetail = candidates.filter(function (e) { return e.id === detailState.id; })[0];
      if (!currentDetail) closePane();
      else { var data = dataPresentation(currentDetail, detailState.metric); specs.detail.description = data.full; specs.detail.footnote = data.source; text('pane-description', data.full); text('pane-footnote', data.source); }
    }
    if (!parents) showSlide(slide);
    if (section !== 'overview' && section !== 'tools') {
      var focused = $('collection-body').contains(document.activeElement), focusKey = focused ? document.activeElement.getAttribute('data-content-id') : null;
      contentView();
      if (focused) { var nodes = $('collection-body').querySelectorAll('[data-content-id]'), nextFocus = null; for (var j = 0; j < nodes.length; j++) if (nodes[j].getAttribute('data-content-id') === focusKey) nextFocus = nodes[j]; (nextFocus || document.querySelector('.back-overview')).focus(); }
    }
  }
  function loadContent() {
    if (location.protocol === 'file:' || (parents && local) || !nativeActive || query.demo === '1' || disposed || document.hidden || request) return;
    request = new XMLHttpRequest(); request.open('GET', '/api/board', true); request.timeout = 8000;
    var current = request;
    function done() {
      if (request !== current || disposed) return; request = null;
      try { if (current.status !== 200) throw new Error('not connected'); var next = JSON.parse(current.responseText); if (!Array.isArray(next.entries) || !Array.isArray(next.photos)) throw new Error('invalid board'); board = next; text('local-note', '已连接'); }
      catch (ignored) { board = null; text('local-note', '未连接'); }
      renderContent();
    }
    current.onload = done; current.onerror = done; current.ontimeout = done; current.send();
  }
  if (query.demo === '1') {
    board = {entries: [
      {type: 'metric', title: '今日步数', value: 6842, target: 8000, unit: '步', source: '演示数据', date: today()},
      {type: 'metric', title: '本周阅读', value: 4, target: 7, unit: '天', source: '演示数据', date: today()},
      {type: 'metric', title: '家庭散步', value: 12, target: 20, unit: '公里', source: '演示数据', date: today()},
      {type: 'note', title: '晚饭', body: '18:30，全家用餐。', author: '妈妈', pinned: true},
      {type: 'event', title: '一起做晚饭', date: today(), time: '18:30', person: '全家'},
      {type: 'event', title: '去楼下散散步', date: today(), time: '20:00', person: '全家'}
    ], photos: []}; text('local-note', '演示内容');
  }
  renderContent(); loadContent(); contentTimer = setInterval(loadContent, virtualHome ? 5000 : 30000);
  function back() {
    if (closePane()) return true;
    if (section !== 'overview') { var old = section; setSection('overview'); $(parents ? 'entry-live' : 'entry-' + old).focus(); return true; }
    return false;
  }
  function items() {
    var root = pane ? $('pane-backdrop') : $('lobby'), nodes = root.querySelectorAll('button,a[href],[tabindex="0"]'), result = [];
    for (var j = 0; j < nodes.length; j++) if (!nodes[j].disabled && nodes[j].offsetWidth && nodes[j].offsetHeight) result.push(nodes[j]);
    return result;
  }
  function focusNode(node) {
    if (node && !node.disabled && node.offsetWidth && node.offsetHeight) {
      node.focus(); node.scrollIntoView({block:'nearest'}); return true;
    }
    return false;
  }
  // Menus are linear lists; home entries are rows. Do not jump to a diagonal control.
  function rowKey(name) {
    if (pane) {
      var menu = Array.prototype.slice.call($('pane-actions').querySelectorAll('button:not(:disabled)'));
      var index = menu.indexOf(document.activeElement);
      if (name === 'ArrowLeft' || name === 'ArrowRight') return true;
      if (name === 'ArrowDown') return focusNode(index < 0 ? menu[0] : menu[index+1]) || true;
      if (name === 'ArrowUp') return focusNode(index > 0 ? menu[index-1] : $('pane-close')) || true;
    }
    var active = document.activeElement;
    if (active === $('hero-open')) {
      if (name === 'ArrowUp') return focusNode(document.querySelector('.section-nav [aria-pressed="true"]')) || true;
      if (name === 'ArrowDown') return focusNode($('slide-prev')) || true;
      if (name === 'ArrowRight') return focusNode(document.querySelector('.data-card')) || true;
    }
    var group = active.closest ? active.closest('.destinations,.shortcuts-row,.section-nav,.carousel-controls,.tools-grid') : null;
    if (!group) return false;
    var visible = Array.prototype.slice.call(group.querySelectorAll('button')).filter(function(n){return !n.disabled && n.offsetWidth && n.offsetHeight;});
    var position = visible.indexOf(active), columns = visible.length;
    if (group.className.indexOf('destinations') !== -1 || group.className.indexOf('tools-grid') !== -1) {
      var tools = group.className.indexOf('tools-grid') !== -1;
      columns = !tools && parents ? (innerWidth / innerHeight > 4/3 ? 3 : 1) : (innerWidth / innerHeight > 4/3 ? 4 : 2);
      var column = position % columns;
      if (name === 'ArrowLeft') return focusNode(column > 0 ? visible[position-1] : null) || true;
      if (name === 'ArrowRight') return focusNode(column+1 < columns ? visible[position+1] : null) || true;
      if (name === 'ArrowDown') {
        if (focusNode(visible[position+columns])) return true;
        if (parents && !tools) { var shortcuts = $('parents-shortcuts').querySelectorAll('button'); return focusNode(shortcuts[Math.min(shortcuts.length-1,Math.round(column*(shortcuts.length-1)/Math.max(1,columns-1)))]) || true; }
        return true;
      }
      if (name === 'ArrowUp') return focusNode(position >= columns ? visible[position-columns] : tools ? document.querySelector('.back-overview') : parents ? $('settings-open') : $('hero-open')) || true;
    }
    if (group.className.indexOf('shortcuts-row') !== -1 && name === 'ArrowUp') {
      var core = document.querySelectorAll('.destinations>.destination:not([hidden])');
      return focusNode(core[Math.min(core.length-1,Math.round(position*(core.length-1)/Math.max(1,visible.length-1)))]) || true;
    }
    if (group.className.indexOf('section-nav') !== -1 && name === 'ArrowDown') return focusNode(section === 'overview' ? $('hero-open') : document.querySelector('.back-overview')) || true;
    if (group.className.indexOf('section-nav') !== -1 && name === 'ArrowRight' && position === visible.length-1) return focusNode($('settings-open')) || true;
    if (group.className.indexOf('section-nav') !== -1 && name === 'ArrowLeft' && position === 0) return focusNode($('brand-home')) || true;
    if (group.className.indexOf('carousel-controls') !== -1 && name === 'ArrowDown') return focusNode($('entry-live')) || true;
    if (group.className.indexOf('carousel-controls') !== -1 && name === 'ArrowUp') return focusNode($('hero-open')) || true;
    if (name === 'ArrowLeft' || name === 'ArrowRight') {
      return focusNode(visible[position+(name === 'ArrowLeft' ? -1 : 1)]) || true;
    }
    return false;
  }
  function key(event) {
    var map = {37: 'ArrowLeft', 38: 'ArrowUp', 39: 'ArrowRight', 40: 'ArrowDown', 13: 'Enter', 27: 'Escape', 4: 'BrowserBack', 9: 'Tab'};
    var name = event.key || map[event.keyCode || event.which], nodes = items(), current = nodes.indexOf(document.activeElement);
    if (name !== 'Enter' || document.activeElement.id !== 'slide-play') pauseCarousel();
    if (name === 'Escape' || name === 'BrowserBack') { if (back()) event.preventDefault(); return; }
    if (name === 'Tab' && pane) { event.preventDefault(); nodes[(current + (event.shiftKey ? -1 : 1) + nodes.length) % nodes.length].focus(); return; }
    if ((name === 'Enter' || name === ' ') && current >= 0) { event.preventDefault(); nodes[current].click(); return; }
    if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].indexOf(name) < 0) return;
    event.preventDefault(); if (!nodes.length) return;
    if (current < 0) { nodes[0].focus(); return; }
    if (rowKey(name)) return;
    var a = nodes[current].getBoundingClientRect(), ax = a.left + a.width / 2, ay = a.top + a.height / 2, best = null, score = Infinity;
    for (var k = 0; k < nodes.length; k++) {
      if (k === current) continue;
      var b = nodes[k].getBoundingClientRect(), dx = b.left + b.width / 2 - ax, dy = b.top + b.height / 2 - ay;
      var horizontal = name === 'ArrowLeft' || name === 'ArrowRight', main = horizontal ? dx : dy;
      if (name === 'ArrowLeft' || name === 'ArrowUp') main = -main;
      var nextScore = main + Math.abs(horizontal ? dy : dx) * 2.4;
      if (main > 4 && nextScore < score) { score = nextScore; best = nodes[k]; }
    }
    if (best) { best.focus(); best.scrollIntoView({block: 'nearest'}); }
  }
  document.addEventListener('keydown', key);
  window.apertureRemoteKey = function (name) { var handled = false; key({key: name, preventDefault: function () { handled = true; }}); return handled; };
  window.apertureBack = back;
  window.apertureNativeFocus = function (active) { nativeActive = !!active; if (!active) { pauseCarousel(); if (request) { var pending = request; request = null; pending.abort(); } } else loadContent(); };
  function clock() {
    if (disposed || document.hidden) return;
    var date = new Date(); text('clock', ('0' + date.getHours()).slice(-2) + ':' + ('0' + date.getMinutes()).slice(-2));
    text('date-day', (date.getMonth() + 1) + '月' + date.getDate() + '日');
    text('date-week', ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][date.getDay()]);
  }
  try { material(localStorage.getItem('aperture-launcher-solid') === '1'); } catch (ignored) {}
  clock(); timer = setInterval(clock, 15000);
  document.addEventListener('visibilitychange', function () { clock(); if (document.hidden) pauseCarousel(); else loadContent(); });
  if (window.matchMedia) { var preference = window.matchMedia('(prefers-reduced-motion: reduce)'); var changed = function () { reduced = preference.matches; pauseCarousel(); $('slide-play').disabled = reduced; if (reduced) text('slide-play', '轮播关闭'); }; if (preference.addListener) preference.addListener(changed); }
  window.addEventListener('pagehide', function () { disposed = true; clearInterval(timer); clearInterval(contentTimer); pauseCarousel(); if (request) { var pending = request; request = null; pending.abort(); } });
  window.addEventListener('pageshow', function (event) { if (event.persisted) location.reload(); });
  if (specs[query.pane] && !parents) openPane(query.pane); else $(parents && /^quick[1-4]$/.test(query.focus || '') ? query.focus : parents ? 'entry-live' : 'entry-live').focus();
}());
