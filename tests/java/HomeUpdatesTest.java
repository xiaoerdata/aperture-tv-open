package org.zhixiaoce.aperture;
import java.util.*;import org.json.*;
public final class HomeUpdatesTest {
    static int count;static void check(boolean value,String why){if(!value)throw new AssertionError(why);count++;}
    static CinemaCatalog.Item film(String id,String date,String score,String provider)throws Exception{return new CinemaCatalog.Item(new JSONObject().put("vod_id",id).put("vod_name",id).put("vod_time",date).put("vod_score",score).put("rating_source",provider));}
    public static void main(String[] args)throws Exception{
        List<String> selected=new ArrayList<>();for(int i=0;i<128;i++)selected.add("org.example.app"+i+"/.TV");
        check(AppWall.read(AppWall.encode(selected),Collections.emptyList()).equals(selected),"128 app choices persist in order");
        check(AppWall.read(null,selected).size()==128,"first migration seeds existing favorites");check(AppWall.read("[]",selected).isEmpty(),"explicit empty collection never reseeds");
        String key=AppWall.key(selected.get(127));check(key.matches("wall[a-f0-9]{16}")&&key.length()==20,"opaque keys fit native icon allowlist");check(key.equals(AppWall.key(selected.get(127))),"launch key stable across reordering");check(!key.equals(AppWall.key(selected.get(0))),"distinct applications have distinct fixture keys");
        check(AppWall.clean(Arrays.asList(selected.get(0),selected.get(0),"https://bad","")).equals(Collections.singletonList(selected.get(0))),"deduplication and component validation");
        List<String> tooMany=new ArrayList<>(selected);tooMany.add("org.extra/.TV");try{AppWall.encode(tooMany);throw new AssertionError("129 accepted");}catch(java.io.IOException expected){count++;}
        CinemaCatalog.Item old=film("old","2026-10-01 12:00:00","9.2","豆瓣"),fresh=film("fresh","2026-11-08T03:00:00Z","8.0","Douban"),other=film("other","2026-11-09","9.9","TMDB"),missing=film("missing","","9.8","");
        List<CinemaCatalog.Item> input=Arrays.asList(old,fresh,other,missing);List<CinemaCatalog.Item> latest=CinemaOrder.sorted(input,"latest");check(latest.get(0)==other&&latest.get(1)==fresh&&latest.get(2)==old&&latest.get(3)==missing,"latest date descending, unknown dates last");
        List<CinemaCatalog.Item> rated=CinemaOrder.sorted(input,"douban");check(rated.get(0)==old&&rated.get(1)==fresh,"Douban scores descending");check(rated.indexOf(other)>1&&rated.indexOf(missing)>1,"unattributed and TMDB scores are not Douban");check(input.get(0)==old,"sorting never mutates source list");
        CinemaCatalog.Item explicit=film("explicit","","9.9","TMDB");explicit.doubanRating="8.8";check(CinemaOrder.douban(explicit)==8.8,"dedicated Douban field takes precedence");explicit.doubanRating="11";check(CinemaOrder.douban(explicit)<0,"invalid score not ranked");
        CinemaCatalog.Item timestamp=new CinemaCatalog.Item(new JSONObject().put("vod_id","stamp").put("vod_name","stamp").put("vod_time","").put("vod_time_stamp",1794106800L));check(CinemaOrder.time(timestamp)==1794106800000L,"empty display date falls back to source timestamp");
        CinemaCatalog.Item tie=film("tie","2026-10-01 12:00:00","9.2","豆瓣");check(CinemaOrder.sorted(Arrays.asList(old,tie),"douban").get(0)==old,"equal values retain source order");
        String raw="{\"format\":\"kaishi.resource-pack\",\"version\":1,\"id\":\"org.example.sort\",\"revision\":1,\"title\":\"排序测试\",\"resources\":[{\"id\":\"movies\",\"type\":\"collection\",\"title\":\"电影\",\"sections\":[\"movies\"],\"items\":[{\"id\":\"a\",\"title\":\"影片\",\"metadata\":{\"updatedAt\":\"2026-11-08T03:00:00Z\",\"doubanRating\":8.7},\"episodes\":[{\"title\":\"正片\",\"url\":\"https://example.invalid/movie.mp4\"}]}]}]}";
        CinemaCatalog.Config config=CinemaCatalog.config(raw,null,false);CinemaCatalog.Page page=CinemaResourcePack.page(config.sites.get(0),null,1,null,null);check(page.items.size()==1&&CinemaOrder.douban(page.items.get(0))==8.7,"resource file retains explicit Douban score");check(CinemaOrder.time(page.items.get(0))>0,"resource file retains update time");
        System.out.println(count+" app-wall persistence, safe launch key, sorting provenance and resource-format assertions passed");
    }
}
