package org.zhixiaoce.aperture;
import org.json.*;import java.util.*;
public final class CinemaMetadataTest {
 static int checks;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("metadata assertion "+checks);}
 public static void main(String[] args)throws Exception{
  CinemaMetadata.Name name=new CinemaMetadata.Name("流浪地球.2019.2160p.WEB-DL.mkv","");check(name.title.equals("流浪地球"));check(name.year.equals("2019"));
  name=new CinemaMetadata.Name("The.Last.of.Us.S01E02.1080p.mkv","");check(name.title.equals("The Last of Us"));
  List<JSONObject> items=Arrays.asList(new JSONObject("{\"key\":\"old\",\"title\":\"三体\",\"year\":\"2015\"}"),new JSONObject("{\"key\":\"new\",\"title\":\"三体\",\"year\":\"2023\"}"));
  check(CinemaMetadata.automatic(new CinemaMetadata.Name("三体",""),items).optString("key").equals("new"));check(CinemaMetadata.automatic(new CinemaMetadata.Name("三体","2015"),items).optString("key").equals("old"));check(CinemaMetadata.automatic(new CinemaMetadata.Name("Friends",""),items)==null);
  items=Arrays.asList(new JSONObject("{\"key\":\"old\",\"title\":\"山海之间的故事\",\"year\":\"2020\"}"),new JSONObject("{\"key\":\"new\",\"title\":\"山海之间的旅程\",\"year\":\"2025\"}"));check(CinemaMetadata.automatic(new CinemaMetadata.Name("山海之间",""),items).optString("key").equals("new"));
  CinemaCatalog.Item film=new CinemaCatalog.Item(new JSONObject("{\"vod_id\":\"one\",\"vod_name\":\"影片\",\"vod_pic\":\"https://media.example/poster.jpg\",\"vod_year\":\"2025\",\"vod_content\":\"<p>简介</p>\"}"));check(film.poster.endsWith("poster.jpg"));check(film.summary.equals("简介"));check(film.year.equals("2025"));
  CinemaCatalog.Page xml=CinemaCatalog.page("<rss><list page=\"1\" pagecount=\"1\"><video><id>one</id><name>影片</name><pic>https://media.example/x.jpg</pic><year>2025</year><des><![CDATA[<p>简介</p>]]></des></video></list></rss>",1);check(xml.items.get(0).poster.endsWith("x.jpg"));check(xml.items.get(0).summary.equals("简介"));
  List<CinemaCatalog.Channel> channels=CinemaCatalog.m3u("#EXTM3U\n#EXTINF:-1 tvg-logo=\"./logo.png\" group-title=\"综合\",频道\nhttps://media.example/live.m3u8\n#EXTINF:-1,无台标\nhttps://media.example/two.m3u8","https://media.example/list.m3u",false);check(channels.get(0).logo.equals("./logo.png"));check(channels.get(1).logo.isEmpty());
  List<JSONObject> found=CinemaMetadata.search(new CinemaMetadata.Name("三体",""),"movie","synthetic-only",(url,headers)->{check(url.startsWith("https://api.themoviedb.org/3/search/movie"));check(headers.get("Authorization").equals("Bearer synthetic-only"));check(!url.contains("synthetic-only"));return "{\"results\":[{\"id\":1,\"title\":\"三体\",\"release_date\":\"2023-01-01\",\"poster_path\":\"/p.jpg\",\"overview\":\"<p>测试</p>\"}]}";});check(found.get(0).optString("summary").equals("测试"));check(found.get(0).optString("poster").equals("https://image.tmdb.org/t/p/w500/p.jpg"));
  JSONObject pack=new JSONObject(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("examples/resource-packs/family.kaishi.json")),java.nio.charset.StandardCharsets.UTF_8));
  JSONObject meta=new JSONObject().put("poster","https://media.example/cover.jpg").put("year","2025").put("summary","家庭简介");pack.getJSONArray("resources").getJSONObject(1).getJSONArray("items").getJSONObject(0).put("metadata",meta);pack.getJSONArray("resources").getJSONObject(0).getJSONArray("channels").getJSONObject(0).put("logo","https://media.example/logo.png");
  CinemaCatalog.Config parsed=CinemaCatalog.config(pack.toString(),null,false);CinemaCatalog.Item packed=CinemaResourcePack.page(parsed.sites.get(0),1,null,null).items.get(0);check(packed.poster.endsWith("cover.jpg"));check(packed.summary.equals("家庭简介"));check(packed.year.equals("2025"));check(parsed.lives.get(0).channels.get(0).logo.endsWith("logo.png"));
  CinemaCatalog.Config restored=CinemaCatalog.config(parsed.json(),null,false);check(CinemaResourcePack.page(restored.sites.get(0),1,null,null).items.get(0).poster.equals(packed.poster));check(restored.lives.get(0).channels.get(0).logo.equals(parsed.lives.get(0).channels.get(0).logo));
  System.out.println(checks+" native metadata assertions passed; JVM fixtures only");
 }
}
