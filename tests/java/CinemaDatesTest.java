package org.zhixiaoce.aperture;
import java.util.*;import org.json.*;
public final class CinemaDatesTest {
 static int count;static void check(boolean value,String why){if(!value)throw new AssertionError(why);count++;}
 static CinemaCatalog.Item film(String id,String year,String released,String updated)throws Exception{return new CinemaCatalog.Item(new JSONObject().put("vod_id",id).put("vod_name",id).put("vod_year",year).put("vod_pubdate",released).put("vod_time",updated));}
 public static void main(String[] args)throws Exception{
  long date=CinemaDates.parse("2026-11-09");for(String value:new String[]{"2026/11/09","2026年11月9日","2026.11.09","20261109","2026-11-09T00:00:00+08:00","2026-11-08T16:00:00Z","2026-11-09 00:00:00","2026-11-09T00:00:00+0800"})check(CinemaDates.parse(value)==date,value);
  for(String value:new String[]{"2026-02-30","2026-13-01","未知","null","0","","2026-11-09garbage","1000","9999999999999"})check(CinemaDates.parse(value)==CinemaDates.UNKNOWN,"invalid "+value);
  check(CinemaDates.parse("2026")<CinemaDates.parse("2026-02"),"year and month parsed");check(CinemaDates.parse("1794106800.125")==1794106800125L,"fractional seconds");check(CinemaDates.parse("1794106800125")==1794106800125L,"milliseconds");
  CinemaCatalog.Item old=film("old","2020","","2026-11-09"),fresh=film("new","2026","","2026-01-01"),release=film("release","2025","2027-01-01","2026-11-09"),missing=film("missing","未知","",""),fallback=film("fallback","","","2026-06-01");
  List<CinemaCatalog.Item> rows=CinemaOrder.sorted(Arrays.asList(old,missing,fallback,release,fresh),"latest");check(rows.equals(Arrays.asList(release,fallback,fresh,old,missing)),"release then year then update fallback, unknown last");
  CinemaCatalog.Item aliases=new CinemaCatalog.Item(new JSONObject().put("vod_id","alias").put("vod_name","alias").put("vod_time","未知").put("vod_time_stamp",1794106800L).put("vod_pubdate","null").put("release_date","2026/11/09").put("douban_score","").put("vod_douban_score","8.9"));
  check(CinemaDates.parse(aliases.updatedAt)==1794106800000L,"invalid first update skips to timestamp");check(CinemaDates.parse(aliases.releaseDate)==date,"invalid first release skips alias");check(CinemaOrder.douban(aliases)==8.9,"empty score skips alias");check(CinemaDates.label(aliases).equals("2026-11-09"),"poster shows sort date");
  check(CinemaDates.label(old).equals("2020"),"poster shows year");check(CinemaDates.label(fallback).equals("更新 2026-06-01"),"update fallback identified");
  CinemaCatalog.Item tie=film("tie","2026","","2026-10-01");check(CinemaOrder.sorted(Arrays.asList(fresh,tie),"latest").get(0)==tie,"equal year uses update time");tie.doubanRating="8";fresh.doubanRating="9";check(CinemaOrder.sorted(Arrays.asList(tie,fresh),"douban").get(0)==fresh,"explicit Douban descending");
  CinemaCatalog.Page xml=CinemaCatalog.page("<rss><list page='1' pagecount='1'><video><id>x</id><name>X</name><last>2026-11-08</last><pubdate>2025-10-01</pubdate><year>2025</year><douban_score>8.2</douban_score></video></list></rss>",1);check(xml.items.size()==1&&xml.items.get(0).releaseDate.equals("2025-10-01")&&xml.items.get(0).updatedAt.equals("2026-11-08"),"XML release and update stay distinct");
  JSONObject item=new JSONObject().put("id","a").put("title","A").put("releaseDate","2026-11-09").put("updatedAt","2026-11-08").put("episodes",new JSONArray().put(new JSONObject().put("title","正片").put("url","https://example.invalid/a.mp4")));
  JSONObject pack=new JSONObject().put("format","kaishi.resource-pack").put("version",1).put("revision",1).put("id","org.example.dates").put("title","日期").put("resources",new JSONArray().put(new JSONObject().put("id","movies").put("type","collection").put("title","电影").put("sections",new JSONArray().put("movies")).put("items",new JSONArray().put(item))));
  CinemaCatalog.Config config=CinemaCatalog.config(pack.toString(),null,false);CinemaCatalog.Item imported=CinemaResourcePack.page(config.sites.get(0),null,1,null,null).items.get(0);check(imported.releaseDate.equals("2026-11-09")&&imported.updatedAt.equals("2026-11-08"),"resource pack release and update retained");check(CinemaCatalog.config(config.json(),null,false).sites.size()==1,"cached pack roundtrip");
  item.put("metadata",new JSONObject().put("releaseDate","2025-01-01"));try{CinemaCatalog.config(pack.toString(),null,false);throw new AssertionError("conflict accepted");}catch(java.io.IOException expected){count++;}
  System.out.println(count+" production date, sort and resource import assertions passed");
 }
}
