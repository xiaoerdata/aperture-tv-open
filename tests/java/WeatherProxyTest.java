package org.zhixiaoce.aperture;
import okhttp3.HttpUrl;
public final class WeatherProxyTest {
 static int n;static void check(boolean value){if(!value)throw new AssertionError("weather transport assertion "+n);n++;}
 public static void main(String[] args)throws Exception{
  HttpUrl url=HttpUrl.parse(WeatherProxy.target("/weather/forecast","lat=31.23&lon=121.47"));check(url.host().equals("api.open-meteo.com"));check(url.queryParameter("forecast_days").equals("7"));check(url.queryParameter("hourly").contains("is_day"));check(url.queryParameter("timeformat").equals("unixtime"));
  for(String query:new String[]{"lat=NaN&lon=1","lat=91&lon=1","lat=1&lon=2&lat=3","lat=1&lon=2&url=https://127.0.0.1","lat=1&lon=181"}){boolean bad=false;try{WeatherProxy.target("/weather/forecast",query);}catch(Exception invalid){bad=true;}check(bad);}
  check(HttpUrl.parse(WeatherProxy.target("/weather/search","q=%E4%B8%8A%E6%B5%B7")).queryParameter("name").equals("上海"));
  WeatherProxy proxy=new WeatherProxy();check(proxy.read("https://foreign.example/weather/location","GET",false)==null);check(proxy.read("https://user@appassets.androidplatform.net/weather/location","GET",false)==null);check(proxy.read("https://appassets.androidplatform.net/weather/location","POST",false).status==403);check(proxy.read("https://appassets.androidplatform.net/weather/forecast?lat=999&lon=1","GET",false).status==400);check(proxy.read("https://appassets.androidplatform.net/weather/location","GET",false).status==503);proxy.close();
  System.out.println(n+" production weather transport assertions passed");
 }
}
