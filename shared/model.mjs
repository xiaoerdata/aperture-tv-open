export const SCENES = ['daylight', 'gallery', 'landscape'];
export const VERSION = 'aperture-board/v1';
export class DomainError extends Error {
  constructor(message, status = 400, code = 'invalid_input') { super(message); this.status = status; this.code = code; }
}
export const fail = (message, status, code) => { throw new DomainError(message, status, code); };
export function text(value, max, label, optional = false) {
  if (typeof value !== 'string' || value.trim().length > max || (!optional && !value.trim())) fail(`${label}需为${max}字以内的文本`);
  return value.trim();
}
export function validDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value || '') || !Number.isFinite(Date.parse(value)) || new Date(value).toISOString().slice(0,10) !== value) fail('日期无效');
  return value;
}
export function normalizeEntry(input) {
  if (!input || typeof input !== 'object') fail('内容无效');
  const base = {type: input.type, title: text(input.title, 48, '标题')};
  if (input.type === 'note') return {...base, body: text(input.body, 180, '留言'), author: text(input.author, 16, '署名'), pinned: input.pinned === true};
  if (input.type === 'event') {
    validDate(input.date);
    if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(input.time || '')) fail('时间无效');
    return {...base, date: input.date, time: input.time, person: text(input.person, 16, '成员'), done: input.done === true};
  }
  if (input.type === 'metric') {
    const value = Number(input.value), target = Number(input.target);
    if (!Number.isFinite(value) || value < 0 || !Number.isFinite(target) || target <= 0 || target > 1e9 || value > 1e9) fail('数据需为0到10亿以内的数值，目标需大于0');
    return {...base, value, target, unit: text(input.unit, 8, '单位'), source: text(input.source, 40, '来源'), date: validDate(input.date)};
  }
  fail('不支持的内容类型');
}
export function normalizeSettings(input) {
  if (!SCENES.includes(input.scene)) fail('未安装此场景');
  try { new Intl.DateTimeFormat('zh-CN', {timeZone: input.timeZone}).format(); } catch { fail('时区无效'); }
  return {name: text(input.name, 20, '家庭名称'), scene: input.scene, timeZone: text(input.timeZone, 50, '时区'), motion: input.motion !== false,...(input.headline!==undefined?{headline:text(input.headline,40,'场景标题',true)}:{})};
}
export function emptyBoard() { return {version: VERSION, revision: 0, settings: {name: '我们的家', scene: 'daylight', timeZone: 'Asia/Shanghai', motion: true}, entries: [], photos: [], updatedAt: null}; }
export function dateInZone(now, timeZone = 'Asia/Shanghai') {
  const p = new Intl.DateTimeFormat('en-CA', {timeZone, year:'numeric', month:'2-digit', day:'2-digit'}).formatToParts(now);
  const get = key => p.find(x=>x.type === key).value;
  return `${get('year')}-${get('month')}-${get('day')}`;
}
export function demoBoard(now = new Date()) {
  const date = dateInZone(now);
  return {...emptyBoard(), demo: true, settings: {...emptyBoard().settings, name: '晴川小家'}, entries: [
    {id:'demo-note', type:'note', title:'今晚一起吃饭', body:'冰箱里有洗好的葡萄。\n今天早点回家，等你开饭。', author:'妈妈', pinned:true},
    {id:'demo-e1', type:'event', title:'去公园走走', date, time:'07:30', person:'全家', done:true},
    {id:'demo-e2', type:'event', title:'接小满放学', date, time:'16:30', person:'爸爸', done:false},
    {id:'demo-e3', type:'event', title:'一起做晚饭', date, time:'18:30', person:'全家', done:false},
    {id:'demo-m1', type:'metric', title:'本周阅读', value:4, target:7, unit:'天', source:'演示数据', date},
    {id:'demo-m2', type:'metric', title:'家庭散步', value:12, target:20, unit:'公里', source:'演示数据', date},
    {id:'demo-m3', type:'metric', title:'一起吃饭', value:5, target:7, unit:'次', source:'演示数据', date}
  ], photos:[{id:'demo-photo', title:'把周末装进一扇窗', caption:'原创程序插画 · 示例内容', url:'/assets/weekend.svg'}]};
}
