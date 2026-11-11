package org.zhixiaoce.aperture;
import org.json.JSONObject;
public final class HomeWeatherSnapshotTest {
 static int count;static void check(boolean value,String why){if(!value)throw new AssertionError(why);count++;}
 public static void main(String[] args)throws Exception{
  long now=1794276000000L;JSONObject current=new JSONObject().put("time",now/1000).put("temperature_2m",24.4).put("weather_code",2),payload=new JSONObject().put("fetchedAt",now).put("data",new JSONObject().put("current",current));String raw=HomeWeatherSnapshot.capture(payload);
  check(HomeWeatherSnapshot.title(raw,now).equals("24°  多云"),"valid reading");check(HomeWeatherSnapshot.fresh(raw,now),"fresh");check(HomeWeatherSnapshot.detail(raw,now).equals("当前天气"),"current label");
  check(!HomeWeatherSnapshot.fresh(raw,now+31*60*1000L),"cached reading ages");check(HomeWeatherSnapshot.detail(raw,now+31*60*1000L).equals("上次预报"),"stale label");check(HomeWeatherSnapshot.title(raw,now+25*60*60*1000L).equals("天气暂不可用"),"expired data hidden");
  check(HomeWeatherSnapshot.title(raw,now-10*60*1000L).equals("天气暂不可用"),"future data hidden");check(HomeWeatherSnapshot.capture(new JSONObject()).isEmpty(),"malformed payload");check(HomeWeatherSnapshot.title("not-json",now).equals("天气暂不可用"),"malformed cache");
  current.put("temperature_2m",400);String invalid=HomeWeatherSnapshot.capture(payload);check(!HomeWeatherSnapshot.fresh(invalid,now),"invalid reading never blocks refresh");check(HomeWeatherSnapshot.title(invalid,now).equals("天气暂不可用"),"invalid temperature hidden");
  current.put("temperature_2m",-7.2).put("weather_code",73);check(HomeWeatherSnapshot.title(HomeWeatherSnapshot.capture(payload),now).equals("-7°  雪"),"negative reading");
  current.put("time",now/1000-3*3600);check(HomeWeatherSnapshot.detail(HomeWeatherSnapshot.capture(payload),now).equals("上次预报"),"recent fetch never masks old observation");
  System.out.println(count+" native weather snapshot and freshness assertions passed; synthetic readings");
 }
}
