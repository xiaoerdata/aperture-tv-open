package org.zhixiaoce.aperture;
import org.json.JSONObject;
/** Compact, dated forecast shared by glass home and the native fallback. */
final class HomeWeatherSnapshot {
    static String capture(JSONObject payload){try{JSONObject data=payload.getJSONObject("data"),current=data.getJSONObject("current");return new JSONObject().put("fetchedAt",payload.getLong("fetchedAt")).put("current",current).toString();}catch(Exception invalid){return "";}}
    static boolean fresh(String raw,long now){return age(raw,now)<30*60*1000L&&!"天气暂不可用".equals(title(raw,now));}
    private static long age(String raw,long now){try{JSONObject value=new JSONObject(raw);long at=value.getLong("fetchedAt"),observed=value.getJSONObject("current").getLong("time")*1000L;long fetched=now-at,reading=now-observed;if(fetched< -300000||reading< -300000||fetched>86400000||reading>86400000)return Long.MAX_VALUE;return Math.max(0,Math.max(fetched,reading-90*60*1000L));}catch(Exception invalid){return Long.MAX_VALUE;}}
    static String title(String raw,long now){try{if(age(raw,now)==Long.MAX_VALUE)return "天气暂不可用";double temp=new JSONObject(raw).getJSONObject("current").getDouble("temperature_2m");return Double.isFinite(temp)&&temp>=-100&&temp<=70?Math.round(temp)+"°  "+condition(new JSONObject(raw).getJSONObject("current").optInt("weather_code",-1)):"天气暂不可用";}catch(Exception invalid){return "天气暂不可用";}}
    static String detail(String raw,long now){return "天气暂不可用".equals(title(raw,now))?"进入看天气重试或选城市":fresh(raw,now)?"当前天气":"上次预报";}
    static String condition(int code){if(code==0)return "晴";if(code==1)return "少云";if(code==2)return "多云";if(code==3)return "阴";if(code==45||code==48)return "雾";if(code>=51&&code<=57)return "毛毛雨";if(code>=61&&code<=67)return "雨";if(code>=71&&code<=77)return "雪";if(code>=80&&code<=82)return "阵雨";if(code==85||code==86)return "阵雪";if(code>=95&&code<=99)return "雷雨";return "天气未知";}
}
