import {readFile} from 'node:fs/promises';
import {isAbsolute} from 'node:path';
import {fail,text} from '../../shared/model.mjs';
export function validateMediaConfig(config={sources:[],allowedOrigins:[]}){
  if(!Array.isArray(config.sources)||config.sources.length>12||!Array.isArray(config.allowedOrigins)||config.allowedOrigins.length>20)fail('影音配置格式无效');
  const ids=new Set();
  for(const s of config.sources){
    if(!/^[a-z0-9_-]{1,40}$/.test(s.id)||ids.has(s.id))fail('影音来源标识无效');ids.add(s.id);text(s.name,40,'媒体库名称');
    if(s.type==='local'){if(!isAbsolute(s.path||''))fail('家庭媒体目录需为绝对路径');}
    else if(s.type==='jellyfin'){
      const u=new URL(s.url);if(!['http:','https:'].includes(u.protocol)||u.username||u.password||u.search||u.hash)fail('Jellyfin 地址无效');
      if(!/^[A-Z][A-Z0-9_]+$/.test(s.tokenEnv||'')||!/^[-a-fA-F0-9]{1,64}$/.test(s.userId||'')||!/^[-a-fA-F0-9]{1,64}$/.test(s.libraryId||''))fail('Jellyfin 需服务端令牌引用、用户及限定媒体库');
    }else fail('暂不支持此媒体库类型');
  }
  for(const origin of config.allowedOrigins){const u=new URL(origin);if(!['http:','https:'].includes(u.protocol)||u.origin!==origin)fail('直播与直链白名单需为精确 origin');}
  return structuredClone(config);
}
export async function loadMediaConfig(filename){if(!filename)return validateMediaConfig();const bytes=await readFile(filename);if(bytes.length>32768)throw new Error('影音配置超过32KB');const data=JSON.parse(bytes);if(data.format!=='aperture-media-config/v1')throw new Error('影音配置版本无效');return validateMediaConfig(data);}
export function parsePlaylist(input,allowedOrigins){
  if(typeof input!=='string'||Buffer.byteLength(input)>65536)fail('清单需为64KB以内的 M3U 文本');
  const lines=input.replace(/^\uFEFF/,'').split(/\r?\n/);if(lines.shift()?.trim()!=='#EXTM3U')fail('请选择标准 M3U 清单；不支持 TVBox spider/JAR 配置');
  const channels=[];let name='',viewKind='live';
  for(const raw of lines){const line=raw.trim();if(!line)continue;if(line.startsWith('#EXTINF:')){name=line.slice(line.indexOf(',')+1).trim();const group=/group-title="([^"]*)"/.exec(line)?.[1]?.toLowerCase();viewKind=['电视剧','剧集','series'].includes(group)?'series':['电影','movie','movies'].includes(group)?'movie':'live';continue;}if(line.startsWith('#'))continue;
    let url;try{url=new URL(line);}catch{fail('清单包含无效链接');}
    if(url.username||url.password||url.hash||!allowedOrigins.includes(url.origin))fail('清单地址不在管理员批准的来源范围内',403,'media_origin_denied');
    if(channels.length>=100)fail('每份清单最多100项');
    channels.push({name:text(name||'未命名频道',80,'节目名称'),url:url.href,viewKind});name='';viewKind='live';
  }
  if(!channels.length)fail('清单没有可播放项目');return channels;
}
