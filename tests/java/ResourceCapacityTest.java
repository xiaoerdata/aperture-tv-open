package org.zhixiaoce.aperture;
import org.json.*;
public class ResourceCapacityTest {
 public static void main(String[] args)throws Exception{
  JSONArray items=new JSONArray();for(int i=0;i<1200;i++){JSONArray episodes=new JSONArray();for(int j=0;j<2;j++)episodes.put(new JSONObject().put("title","第"+j+"集").put("url","https://example.com/"+i+"/"+j+".mp4"));items.put(new JSONObject().put("id","item"+i).put("title","电影"+i).put("episodes",episodes));}
  JSONObject resource=new JSONObject().put("id","collection").put("title","大资源库").put("type","collection").put("section","movies").put("items",items);JSONObject pack=new JSONObject().put("format","kaishi.resource-pack").put("version",1).put("id","org.example.capacity").put("revision",1).put("title","大容量").put("resources",new JSONArray().put(resource));
  CinemaCatalog.Config c=CinemaCatalog.config(pack.toString(),null,false),restored=CinemaCatalog.config(c.json(),null,false);if(restored.sites.get(0).items.length()!=1200||CinemaResourcePack.page(restored.sites.get(0),20,null,null).items.size()!=60)throw new AssertionError("large collection must persist and paginate");
  JSONArray list=new JSONArray();for(int i=0;i<200;i++)list.put(new JSONObject().put("vod_id",i+"").put("vod_name","影片"+i));CinemaCatalog.Page page=CinemaCatalog.page(new JSONObject().put("page",501).put("pagecount",700).put("list",list).toString(),501);if(page.items.size()!=200||page.pages!=700)throw new AssertionError("remote items or pagination truncated");
  for(int i=200;i<1001;i++)list.put(new JSONObject().put("vod_id",i+"").put("vod_name","影片"));boolean rejected=false;try{CinemaCatalog.page(new JSONObject().put("list",list).toString(),1);}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError("oversized page must reject explicitly");
  System.out.println("capacity import, persistence, page 501 and overflow passed");
 }
}
