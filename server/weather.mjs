// Public forecast transport. Fixed HTTPS providers; never fetch a caller-supplied URL/IP.
const hosts = {location:'https://ipwho.is/?fields=success,city,region,country,latitude,longitude,timezone',search:'https://geocoding-api.open-meteo.com/v1/search',forecast:'https://api.open-meteo.com/v1/forecast'};
export function weatherTarget(path, query) {
  if (path === '/weather/location') { if ([...query].length) throw Error('invalid'); return hosts.location; }
  if (path === '/weather/search') {
    if ([...query].some(([key])=>key!=='q') || query.getAll('q').length!==1) throw Error('invalid');
    const q=query.get('q')?.trim();if(!q || q.length>60 || /[\u0000-\u001f]/.test(q))throw Error('invalid');
    return hosts.search+'?'+new URLSearchParams({name:q,count:'8',language:'zh',format:'json'});
  }
  if(path === '/weather/forecast') {
    if ([...query].some(([key])=>!['lat','lon'].includes(key)) || query.getAll('lat').length!==1 || query.getAll('lon').length!==1) throw Error('invalid');
    const lat=query.get('lat'),lon=query.get('lon'),number=/^-?\d{1,3}(\.\d{1,4})?$/;
    if(!number.test(lat)||!number.test(lon)||Math.abs(Number(lat))>90||Math.abs(Number(lon))>180)throw Error('invalid');
    return hosts.forecast+'?'+new URLSearchParams({latitude:lat,longitude:lon,current:'temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m',hourly:'temperature_2m,precipitation_probability,weather_code,is_day',daily:'weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max',forecast_days:'7',timezone:'auto',timeformat:'unixtime'});
  }
  throw Error('invalid');
}
export function createWeather({fetcher=fetch,now=Date.now}={}) {
  const cache=new Map(),pending=new Map(),controllers=new Set();
  return {close(){for(const controller of controllers)controller.abort();controllers.clear();cache.clear();},async read(path,query) {
    let target;try{target=weatherTarget(path,query);}catch{return {status:400,body:{message:'天气查询参数无效'}};}
    const key=target,hit=cache.get(key);if(hit&&hit.until>now())return hit.value;
    if(pending.has(key))return pending.get(key);
    if(pending.size>=4)return {status:429,body:{message:'天气查询较多，请稍后重试'}};
    const task=Promise.resolve().then(async()=>{
      const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),8000);controllers.add(controller);
      try{
        const response=await fetcher(target,{signal:controller.signal,redirect:'error',headers:{Accept:'application/json'}});
        if(!response.ok)throw Error('upstream');let length=0;const chunks=[];
        for await(const chunk of response.body){length+=chunk.length;if(length>512000){controller.abort();throw Error('size');}chunks.push(chunk);}
        let data=JSON.parse(Buffer.concat(chunks).toString());if(data.error||data.success===false)throw Error('provider');
        if(path==='/weather/location')data={success:true,city:String(data.city||data.region||''),country:String(data.country||''),latitude:data.latitude,longitude:data.longitude,scope:'server-egress'};
        const value={status:200,body:{data,fetchedAt:now()}};
        if(cache.size>=32)cache.delete(cache.keys().next().value);cache.set(key,{until:now()+(path==='/weather/location'?21600000:600000),value});return value;
      }catch{return {status:503,body:{message:'天气暂不可用，请重试或选择城市'}};}finally{clearTimeout(timer);controllers.delete(controller);pending.delete(key);}
    });pending.set(key,task);return task;
  }};
}
