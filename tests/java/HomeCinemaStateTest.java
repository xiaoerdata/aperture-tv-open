package org.zhixiaoce.aperture;
import java.util.*;
import java.nio.file.*;
import org.json.*;
public final class HomeCinemaStateTest {
 static int count;interface Work{void run()throws Exception;}
 static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
 static void reject(Work work,String message)throws Exception{boolean failed=false;try{work.run();}catch(Exception good){failed=true;}check(failed,message);}
 public static void main(String[] args)throws Exception{
  List<AppPresets.Entry> apps=AppPresets.read(Files.readString(Path.of(args[0])));check(apps.size()==12,"curated app count");
  Map<String,String> old=new LinkedHashMap<>();old.put("quick1","custom.app/Activity");old.put("quick2","");
  Map<String,String> filled=AppPresets.initialize(old,Arrays.asList("com.a/Tv","com.a/Phone","com.b/Tv","com.c/Tv"));
  check(filled.get("quick1").equals("custom.app/Activity")&&filled.get("quick2").isEmpty(),"custom and explicit hidden slot preserved");
  check(filled.get("quick3").equals("com.a/Tv")&&filled.get("quick4").equals("com.b/Tv"),"TV default order, package dedup and cap");check(AppPresets.initialize(filled,Arrays.asList("com.c/Tv")).equals(filled),"re-entry does not replace slots");
  String url="https://example.invalid/";
  String txt="新闻,#genre#\n频道一,"+url+"a|User-Agent=One$线路一#"+url+"b|User-Agent=Two$线路二\n频道二,"+url+"c\n";
  List<CinemaCatalog.Channel> channels=CinemaCatalog.playlist(txt,null,false,Collections.emptyMap());
  check(channels.size()==2&&channels.get(0).group.equals("新闻"),"TXT groups and channels");check(channels.get(0).request(0).get("User-Agent").equals("One")&&channels.get(0).request(1).get("User-Agent").equals("Two"),"per-line request headers do not bleed");
  CinemaCatalog.Config normalized=CinemaCatalog.config(txt,null,false);CinemaCatalog.Channel round=CinemaCatalog.config(normalized.json(),null,false).lives.get(0).channels.get(0);check(round.request(1).get("User-Agent").equals("Two"),"per-line headers survive save and reload");
  channels=CinemaCatalog.playlist("#EXTM3U\n#EXTINF:-1 group-title=\"新闻,资讯\",我的频道\n#EXTVLCOPT:http-user-agent=TestAgent\n./a.m3u8|Referer=https%3A%2F%2Fexample.invalid%2F\n",url+"list.m3u",false,Collections.emptyMap());check(channels.get(0).name.equals("我的频道")&&channels.get(0).group.equals("新闻,资讯"),"EXTINF quoted comma");check(channels.get(0).request(0).get("User-Agent").equals("TestAgent"),"EXTVLCOPT");
  reject(()->CinemaFormats.stream(url+"a|User-Agent=one%0D%0Atwo",null,false,new HashMap<>()),"header injection rejected");reject(()->CinemaFormats.header(new HashMap<>(),"Host","bad"),"Host override rejected");
  String xml="<rss><class><ty id=\"2\">剧集</ty></class><list page=\"1\" pagecount=\"2\"><video><id>7</id><name>家庭短片</name><note>2集</note><dl><dd flag=\"家庭\"><![CDATA[第一集$"+url+"1.mp4#第二集$"+url+"2.m3u8]]></dd></dl></video></list></rss>";
  CinemaCatalog.Page page=CinemaCatalog.page(xml,1);check(page.items.size()==1&&page.categories.size()==1&&CinemaCatalog.lines(page.items.get(0),false).get(0).episodes.size()==2,"MacCMS XML categories and episodes");check(CinemaCatalog.config(xml,url+"api",false).sites.get(0).type==0,"direct XML endpoint import");
  reject(()->CinemaCatalog.page("<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><rss><list>&x;</list></rss>",1),"XML external entity rejected");reject(()->CinemaCatalog.page("<rss/>",1),"unrecognized XML rejected");reject(()->CinemaCatalog.page("{\"message\":\"not a catalogue\"}",1),"unrecognized JSON rejected");
  check(CinemaCatalog.config("// configuration\n{\"sites\":[{\"type\":4,\"name\":\"API\",\"api\":\""+url+"api\"}]}",url,false).sites.size()==1,"JSON comments and type4 catalogue subset");
  check(CinemaCatalog.config("{\"urls\":[{\"name\":\"仓\",\"url\":\"./config.json\"}]}",url+"all.json",false).warehouses.get(0).api.equals(url+"config.json"),"multi-warehouse relative URL");
  check(CinemaCatalog.config("{\"data\":[{\"sourceName\":\"仓\",\"sourceUrl\":\""+url+"config.json\"}]}",url,false).warehouses.size()==1,"sourceName multi-warehouse shape");
  check(CinemaCatalog.config("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\na.ts",url+"channel.m3u8",false).lives.get(0).channels.size()==1,"HLS imported as one channel");
  CinemaShelf shelf=new CinemaShelf("[]");JSONObject ref=CinemaShelf.ref("vod","source-a","7","家庭短片","");JSONObject other=CinemaShelf.ref("vod","source-b","7","另一部片","");
  shelf.toggle(ref,"favorite");shelf.toggle(ref,"follow");shelf.watch(ref,"家庭","第二集",60000,120000,false,12345);
  CinemaShelf restored=new CinemaShelf(shelf.json());check(restored.resume(ref,"家庭","第二集")==60000,"progress persistence");check(restored.resume(ref,"家庭","第一集")==0&&restored.resume(other,"家庭","第二集")==0,"episode and source isolation");
  restored.latest(ref,2,"第二集","2集",100);restored.latest(ref,4,"第四集","4集",200);restored.latest(ref,5,"第五集","5集",300);check(restored.get(ref).optInt("newEpisodes")==3,"unseen episode count across repeated checks");restored.seen(ref);check(restored.get(ref).optInt("newEpisodes")==0,"seen clears update count");
  restored.watch(ref,"家庭","第二集",120000,120000,true,20000);check(restored.resume(ref,"家庭","第二集")==0,"completed item does not resume at end");restored.clearHistory();check(restored.list(null,"history").isEmpty()&&restored.list("vod","favorite").size()==1&&restored.list("vod","follow").size()==1,"clear history preserves favorites and follows");
  CinemaShelf full=new CinemaShelf("[]");for(int i=0;i<200;i++)full.toggle(CinemaShelf.ref("vod","s",String.valueOf(i),"title", ""),"favorite");reject(()->full.watch(other,"a","b",1,2,false,1),"full protected shelf rejects extra item");check(new CinemaShelf(full.json()).list("vod","favorite").size()==200,"full shelf remains intact");
  reject(()->new CinemaShelf("[{\"kind\":\"vod\",\"source\":\"s\",\"id\":\"i\",\"title\":\"t\",\"position\":-1}]"),"invalid state is rejected");
  System.out.println("Home/Cinema state: "+count+" assertions passed");
 }
}
