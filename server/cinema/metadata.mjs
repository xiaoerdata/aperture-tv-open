// Optional metadata providers; no provider or media credentials enter the browser.
export function normalizeName(raw,explicit=''){
 const year=/^(19|20)\d{2}$/.test(explicit)?explicit:String(raw).match(/(?<!\d)((?:19|20)\d{2})(?!\d)/)?.[1]||'';
 const title=String(raw).replace(/\.(mkv|mp4|m4v|webm|avi|mov|ts)$/i,'').replace(/[._]+/g,' ').replace(/[\[【(（](?:[^\]】)）]*?(?:19|20)\d{2}|[^\]】)）]*?(?:1080|2160|720|bluray|webrip|web-dl|h264|h265|中字|国语))[^\]】)）]*[\]】)）]/gi,' ').replace(/\b(?:s\d{1,2}e\d{1,3}|\d{3,4}p|4k|bluray|webrip|web[ -]?dl|h[ .]?26[45]|x26[45])\b.*$/i,'').replace(/第[一二三四五六七八九十百0-9]+[季集].*$/,'').replace(/(?<!\d)(?:19|20)\d{2}(?!\d)/g,'').replace(/[\s._-]+$/,'').trim().slice(0,160);
 return {title,year};
}
const comparable=s=>String(s||'').normalize('NFKC').toLowerCase().replace(/[\p{P}\p{Z}\s]/gu,'');
export function automaticMatch(name,values){
 const title=comparable(name.title);if(!title)return null;
 const overlap=(a,b)=>{if(a===b)return 1;const grams=x=>new Set([...x].slice(1).map((_,i)=>[...x].slice(i,i+2).join(''))),x=grams(a),y=grams(b);return x.size&&y.size?2*[...x].filter(v=>y.has(v)).length/(x.size+y.size):0;};
 let candidates=values.map(v=>({v,score:Math.max(overlap(title,comparable(v.title)),overlap(title,comparable(v.originalTitle)))})).filter(x=>x.score>=0.5);
 if(!candidates.length)return null;const exact=candidates.filter(x=>x.score===1);if(exact.length)candidates=exact;const dated=name.year?candidates.filter(x=>x.v.year===name.year):[];if(dated.length)candidates=dated;
 candidates.sort((a,b)=>(Number(b.v.year)||0)-(Number(a.v.year)||0)||b.score-a.score||String(a.v.key).localeCompare(String(b.v.key)));return candidates[0].v;
}
export const plain=s=>String(s||'').replace(/<[^>]*>/g,' ').replace(/&nbsp;/g,' ').replace(/&amp;/g,'&').replace(/\s+/g,' ').trim().slice(0,4000);
export function providerArtwork(value){try{const u=new URL(value);return u.protocol==='https:'&&!u.username&&!u.password&&!u.port&&!u.hash&&((u.hostname==='image.tmdb.org'&&/^\/t\/p\/(w500|w1280)\/[\w.-]+$/.test(u.pathname))||(u.hostname==='static.tvmaze.com'&&/^\/uploads\//.test(u.pathname)))?u.href:null;}catch{return null;}}
async function bounded(response,max){const reader=response.body.getReader(),chunks=[];let bytes=0;try{while(true){const {done,value}=await reader.read();if(done)break;bytes+=value.length;if(bytes>max)throw new Error('metadata response too large');chunks.push(value);}return Buffer.concat(chunks);}finally{await reader.cancel().catch(()=>{});}}
export function createMetadata({enabled=process.env.APERTURE_METADATA_ONLINE==='1',token=process.env.APERTURE_TMDB_READ_TOKEN||'',fetcher=fetch}={}){
 const cache=new Map(),pending=new Map();let active=0,lastStart=0,closed=false;
 async function json(url,headers){const r=await fetcher(url,{headers,redirect:'error',signal:AbortSignal.timeout(7000)});if(!r.ok)throw new Error('metadata unavailable');return JSON.parse((await bounded(r,524288)).toString());}
 async function search(title,year,kind){
  const name=normalizeName(title,year);if(!enabled||closed)return {candidates:[],match:null,state:'disabled'};if(!token&&kind==='movie')return {candidates:[],match:null,state:'needs_tmdb'};
  const id=kind+':'+name.year+':'+comparable(name.title),hit=cache.get(id);if(hit&&hit.until>Date.now())return hit.value;if(pending.has(id))return pending.get(id);if(active>=2)return {candidates:[],match:null,state:'busy'};active++;
  const work=(async()=>{try{const delay=Math.max(0,lastStart+900-Date.now());lastStart=Date.now()+delay;if(delay)await new Promise(r=>setTimeout(r,delay));let values=[];
   if(token){const type=kind==='movie'?'movie':'tv';const data=await json('https://api.themoviedb.org/3/search/'+type+'?language=zh-CN&include_adult=false&query='+encodeURIComponent(name.title),{Authorization:'Bearer '+token});values=(data.results||[]).slice(0,8).map(o=>({key:`tmdb:${type}:${Number(o.id)}`,title:o.title||o.name,originalTitle:o.original_title||o.original_name,year:(o.release_date||o.first_air_date||'').slice(0,4),summary:plain(o.overview),poster:providerArtwork('https://image.tmdb.org/t/p/w500'+o.poster_path)||'',backdrop:providerArtwork('https://image.tmdb.org/t/p/w1280'+o.backdrop_path)||'',rating:o.vote_average>0?Number(o.vote_average).toFixed(1):'',provider:'TMDB',url:'https://www.themoviedb.org/'+type+'/'+Number(o.id)}));}
   else {const data=await json('https://api.tvmaze.com/search/shows?q='+encodeURIComponent(name.title),{});values=data.slice(0,8).map(({show:o})=>({key:'tvmaze:'+Number(o.id),title:o.name,year:(o.premiered||'').slice(0,4),summary:plain(o.summary),poster:providerArtwork(o.image?.medium)||'',genre:(o.genres||[]).slice(0,8).join(' / '),provider:'TVmaze · CC BY-SA',url:o.url}));}
   const value={candidates:values,match:automaticMatch(name,values),state:values.length?'ready':'empty'};if(cache.size>=128)cache.delete(cache.keys().next().value);cache.set(id,{value,until:Date.now()+86400000});return value;
  }catch{return {candidates:[],match:null,state:'unavailable'};}finally{active--;pending.delete(id);}})();pending.set(id,work);return work;
 }
 return {search,status:()=>({enabled,tmdb:!!token}),close(){closed=true;cache.clear();},async image(url){const target=providerArtwork(url);if(!target)throw new Error('unsupported artwork origin');const r=await fetcher(target,{redirect:'error',signal:AbortSignal.timeout(7000)});if(!r.ok||!/^image\/(jpeg|png|webp)(;|$)/i.test(r.headers.get('content-type')||''))throw new Error('invalid image');return {bytes:await bounded(r,4*1024*1024),type:r.headers.get('content-type')};}};
}
