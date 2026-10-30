package org.zhixiaoce.aperture;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import org.json.*;

/** Read-only fixed-provider weather transport. No cookies, household tokens or arbitrary URLs. */
final class WeatherProxy implements AutoCloseable {
    static final class Reply {final int status;final byte[] body;Reply(int status,JSONObject value){this.status=status;body=value.toString().getBytes(StandardCharsets.UTF_8);}}
    private static final class Cached {final long until;final Reply reply;Cached(long until,Reply reply){this.until=until;this.reply=reply;}}
    private final OkHttpClient client=new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(5,TimeUnit.SECONDS).readTimeout(6,TimeUnit.SECONDS).callTimeout(8,TimeUnit.SECONDS).build();
    private final Map<String,Cached> cache=new LinkedHashMap<>();private final AtomicInteger inflight=new AtomicInteger();private volatile boolean active,closed;
    static String target(String path,String rawQuery)throws Exception {
        Map<String,String> q=new HashMap<>();if(rawQuery!=null&&!rawQuery.isEmpty())for(String part:rawQuery.split("&",-1)){String[] bits=part.split("=",2);String key=URLDecoder.decode(bits[0],"UTF-8"),value=bits.length==2?URLDecoder.decode(bits[1],"UTF-8"):"";if(q.put(key,value)!=null)throw new IllegalArgumentException();}
        if("/weather/location".equals(path)&&q.isEmpty())return "https://ipwho.is/?fields=success,city,region,country,latitude,longitude,timezone";
        if("/weather/search".equals(path)&&q.size()==1&&q.containsKey("q")){String name=q.get("q").trim();if(name.isEmpty()||name.length()>60||name.matches("(?s).*[\\x00-\\x1f].*"))throw new IllegalArgumentException();return new HttpUrl.Builder().scheme("https").host("geocoding-api.open-meteo.com").addPathSegments("v1/search").addQueryParameter("name",name).addQueryParameter("count","8").addQueryParameter("language","zh").addQueryParameter("format","json").build().toString();}
        if("/weather/forecast".equals(path)&&q.size()==2&&q.containsKey("lat")&&q.containsKey("lon")){
            String lat=q.get("lat"),lon=q.get("lon");if(!lat.matches("-?\\d{1,3}(\\.\\d{1,4})?")||!lon.matches("-?\\d{1,3}(\\.\\d{1,4})?")||Math.abs(Double.parseDouble(lat))>90||Math.abs(Double.parseDouble(lon))>180)throw new IllegalArgumentException();
            return new HttpUrl.Builder().scheme("https").host("api.open-meteo.com").addPathSegments("v1/forecast").addQueryParameter("latitude",lat).addQueryParameter("longitude",lon).addQueryParameter("current","temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m").addQueryParameter("hourly","temperature_2m,precipitation_probability,weather_code,is_day").addQueryParameter("daily","weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max").addQueryParameter("forecast_days","7").addQueryParameter("timezone","auto").addQueryParameter("timeformat","unixtime").build().toString();
        }throw new IllegalArgumentException();
    }
    private Reply error(int status){try{return new Reply(status,new JSONObject().put("message","天气暂不可用，请重试或选择城市"));}catch(JSONException impossible){throw new IllegalStateException(impossible);}}
    Reply read(String url,String method,boolean mainFrame){
        URI uri;try{uri=URI.create(url);}catch(Exception invalid){return null;}
        if(!"https".equals(uri.getScheme())||!LobbyAssets.HOST.equals(uri.getHost())||uri.getPort()!=-1||uri.getUserInfo()!=null||uri.getFragment()!=null||!Arrays.asList("/weather/location","/weather/search","/weather/forecast").contains(uri.getRawPath()))return null;
        if(mainFrame||!"GET".equals(method))return error(403);
        String target;try{target=target(uri.getRawPath(),uri.getRawQuery());}catch(Exception invalid){return error(400);}
        if(!active||closed)return error(503);long now=System.currentTimeMillis();synchronized(cache){Cached hit=cache.get(target);if(hit!=null&&hit.until>now)return hit.reply;}
        if(inflight.incrementAndGet()>4){inflight.decrementAndGet();return error(429);}
        try(Response response=client.newCall(new Request.Builder().url(target).header("Accept","application/json").build()).execute()){
            if(!response.isSuccessful()||response.body()==null)throw new java.io.IOException();java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();try(java.io.InputStream input=response.body().byteStream()){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(out.size()+count>512000)throw new java.io.IOException();out.write(buffer,0,count);}}
            JSONObject data=new JSONObject(out.toString("UTF-8"));if(data.optBoolean("error")||(data.has("success")&&!data.optBoolean("success")))throw new java.io.IOException();
            if("/weather/location".equals(uri.getPath()))data=new JSONObject().put("city",data.optString("city",data.optString("region"))).put("country",data.optString("country")).put("latitude",data.opt("latitude")).put("longitude",data.opt("longitude")).put("scope","device-egress");
            if(!active||closed)return error(503);Reply reply=new Reply(200,new JSONObject().put("data",data).put("fetchedAt",System.currentTimeMillis()));synchronized(cache){if(cache.size()>=32)cache.remove(cache.keySet().iterator().next());cache.put(target,new Cached(now+("/weather/location".equals(uri.getPath())?21600000:600000),reply));}return reply;
        }catch(Exception unavailable){return error(503);}finally{inflight.decrementAndGet();}
    }
    void active(boolean value){active=value;if(!value)client.dispatcher().cancelAll();}
    public void close(){closed=true;active(false);synchronized(cache){cache.clear();}client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();}
}
