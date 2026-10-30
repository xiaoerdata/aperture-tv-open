(function(root){
  'use strict';
  function number(value){return typeof value==='number'&&isFinite(value)?value:null;}
  function place(value){if(!value||!value.name||number(value.latitude)===null||number(value.longitude)===null||Math.abs(value.latitude)>90||Math.abs(value.longitude)>180)return null;return {name:String(value.name).slice(0,80),country:String(value.country||'').slice(0,80),latitude:Math.round(value.latitude*10000)/10000,longitude:Math.round(value.longitude*10000)/10000,scope:value.scope==='server-egress'?'server-egress':value.scope==='device-egress'?'device-egress':'manual'};}
  function condition(code){if(code===0)return '晴';if(code===1)return '少云';if(code===2)return '多云';if(code===3)return '阴';if(code===45||code===48)return '雾';if(code>=51&&code<=57)return '毛毛雨';if(code>=61&&code<=67)return '雨';if(code>=71&&code<=77)return '雪';if(code>=80&&code<=82)return '阵雨';if(code===85||code===86)return '阵雪';if(code>=95&&code<=99)return '雷雨';return '天气未知';}
  function symbol(code,night){if(night&&(code===0||code===1||code===2))return '☾';if(code===0)return '☀';if(code===1||code===2)return '⛅';if(code===3)return '☁';if(code===45||code===48)return '≋';if(code>=71&&code<=77||code===85||code===86)return '❄';if(code>=95&&code<=99)return 'ϟ';if(code>=51&&code<=82)return '☂';return '—';}
  function normalize(payload,location,now){
    var p=place(location),d=payload&&payload.data,c=d&&d.current,h=d&&d.hourly,w=d&&d.daily,at=number(payload&&payload.fetchedAt);if(!p||!c||!h||!w||!Array.isArray(h.time)||!Array.isArray(w.time)||at===null||at>now+300000||now-at>86400000||typeof d.timezone!=='string')throw Error('天气数据不完整');
    var hours=[],days=[],start=Math.floor(now/3600000)*3600,i;
    for(i=0;i<h.time.length&&hours.length<24;i++)if(number(h.time[i])!==null&&h.time[i]>=start)hours.push({time:h.time[i]*1000,temp:number((h.temperature_2m||[])[i]),rain:number((h.precipitation_probability||[])[i]),code:number((h.weather_code||[])[i]),night:(h.is_day||[])[i]===0});
    for(i=0;i<Math.min(7,w.time.length);i++)if(number(w.time[i])!==null)days.push({time:w.time[i]*1000,high:number((w.temperature_2m_max||[])[i]),low:number((w.temperature_2m_min||[])[i]),rain:number((w.precipitation_probability_max||[])[i]),code:number((w.weather_code||[])[i])});
    if(!hours.length||days.length!==7||number(c.time)===null||number(c.temperature_2m)===null)throw Error('天气数据不完整');
    return {place:p,zone:d.timezone,at:at,observedAt:c.time*1000,temp:c.temperature_2m,feels:number(c.apparent_temperature),humidity:number(c.relative_humidity_2m),wind:number(c.wind_speed_10m),code:number(c.weather_code),night:c.is_day===0,hours:hours,days:days,stale:now-at>1800000||now-c.time*1000>7200000};
  }
  root.KaishiWeatherModel={place:place,condition:condition,symbol:symbol,normalize:normalize,number:number};
}(typeof window==='undefined'?globalThis:window));
