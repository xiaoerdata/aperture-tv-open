// Public data format; no agent credential, account or authority is embedded in a message file.
export function messageSource(value='') {if(!value)return '';const u=new URL(value);if(u.protocol!=='https:'||u.username||u.password||u.hash||u.hostname==='appassets.androidplatform.net'||value.length>2048)throw Error('留言源需为 HTTPS 地址');return u.href;}
export function messageFeed(value) {
 if(!value||value.version!==1||!Array.isArray(value.messages)||value.messages.length>100)throw Error('留言格式无效');
 const timezone=value.timezone||'Asia/Shanghai';new Intl.DateTimeFormat('zh-CN',{timeZone:timezone});const ids=new Set();
 const messages=value.messages.map(m=>{
  if(!m||typeof m.id!=='string'||!/^[A-Za-z0-9._-]{1,96}$/.test(m.id)||ids.has(m.id)||typeof m.text!=='string'||!m.text.trim()||m.text.length>500||m.text.includes('\0'))throw Error('留言内容无效');ids.add(m.id);
  for(const field of ['time','repeat','date','expireAt'])if(m[field]!==undefined&&typeof m[field]!=='string')throw Error('留言字段类型无效');if(m.tts!==undefined&&typeof m.tts!=='boolean')throw Error('播报开关需为布尔值');
  const time=m.time===undefined?'':m.time,repeat=m.repeat===undefined?'once':m.repeat,date=m.date===undefined?'':m.date,expireAt=m.expireAt===undefined?'':m.expireAt;
  if(!['daily','once'].includes(repeat)||typeof time!=='string'||time&&!/^(?:[01]\d|2[0-3]):[0-5]\d(?:-(?:[01]\d|2[0-3]):[0-5]\d)?$/.test(time)||time.length>5&&time.slice(6)<=time.slice(0,5))throw Error('留言时刻无效');
  if(time&&repeat==='once'&&(!/^\d{4}-\d\d-\d\d$/.test(date)||!Number.isFinite(Date.parse(date))||new Date(date).toISOString().slice(0,10)!==date))throw Error('单次留言需明确日期');
  if(expireAt&&(!/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?(?:Z|[+-]\d\d:\d\d)$/.test(expireAt)||!Number.isFinite(Date.parse(expireAt))||new Date(expireAt.slice(0,10)).toISOString().slice(0,10)!==expireAt.slice(0,10)))throw Error('过期时间需含时区');
  return {id:m.id,text:m.text,time,repeat,date,tts:m.tts===true,expireAt};
 });return {version:1,timezone,messages};
}
