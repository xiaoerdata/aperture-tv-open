package org.zhixiaoce.aperture;
import org.json.*;
public class ResourceCapacityTest {
 public static void main(String[] args)throws Exception{
  JSONArray items=new JSONArray();for(int i=0;i<1200;i++){JSONArray episodes=new JSONArray();for(int j=0;j<2;j++)episodes.put(new JSONObject().put("title","第"+j+"集").put("url","https://example.com/"+i+"/"+j+".mp4"));items.put(new JSONObject().put("id","item"+i).put("title","电影"+i).put("episodes",episodes));}
  JSONObject resource=new JSONObject().put("id","collection").put("title","大资源库").put("type","collection").put("section","movies").put("items",items);JSONObject pack=new JSONObject().put("format","kaishi.resource-pack").put("version",1).put("id","org.example.capacity").put("revision",1).put("title","大容量").put("resources",new JSONArray().put(resource));
  CinemaCatalog.Config c=CinemaCatalog.config(pack.toString(),null,false),restored=CinemaCatalog.config(c.json(),null,false);if(restored.sites.get(0).items.length()!=1200||CinemaResourcePack.page(restored.sites.get(0),20,null,null).items.size()!=60)throw new AssertionError("large collection must persist and paginate");
  JSONArray list=new JSONArray();for(int i=0;i<200;i++)list.put(new JSONObject().put("vod_id",i+"").put("vod_name","影片"+i));CinemaCatalog.Page page=CinemaCatalog.page(new JSONObject().put("page",501).put("pagecount",700).put("list",list).toString(),501);if(page.items.size()!=200||page.pages!=700)throw new AssertionError("remote items or pagination truncated");
  for(int i=200;i<1001;i++)list.put(new JSONObject().put("vod_id",i+"").put("vod_name","影片"));boolean rejected=false;try{CinemaCatalog.page(new JSONObject().put("list",list).toString(),1);}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError("oversized page must reject explicitly");
  int cases=0;
  if(args.length>0){
   java.nio.file.Path dir=java.nio.file.Path.of(args[0]);JSONArray fixtures=new JSONArray(java.nio.file.Files.readString(dir.resolve("cases.json")));
   javax.crypto.KeyGenerator generator=javax.crypto.KeyGenerator.getInstance("AES");generator.init(128);javax.crypto.SecretKey key=generator.generateKey();
   for(int n=0;n<fixtures.length();n++){
    JSONObject fixture=fixtures.getJSONObject(n);String name=fixture.getString("name"),raw=java.nio.file.Files.readString(dir.resolve(name));
    if(fixture.optBoolean("reject")){
     boolean failed=false;try{CinemaCatalog.config(raw,null,false);}catch(Exception expected){failed=expected.getMessage().contains(fixture.getString("error"));}
     if(!failed)throw new AssertionError(name+": must explicitly reject oversized bytes/expansion");cases++;continue;
    }
    CinemaCatalog.Config imported=CinemaCatalog.config(raw,null,false);
    if(imported.sites.size()!=fixture.optInt("sites")||imported.lives.size()!=fixture.optInt("lives")||imported.warehouses.size()!=fixture.optInt("warehouses")||!imported.skipped.isEmpty())throw new AssertionError(name+": source count or skipped entries");
    if(!imported.warehouses.isEmpty()){cases++;continue;}
    String sealed=CinemaCipher.encrypt(imported.json(),key);CinemaCatalog.Config saved=CinemaCatalog.config(CinemaCipher.decrypt(sealed,key),null,false);
    if(saved.sites.size()!=imported.sites.size()||saved.lives.size()!=imported.lives.size()||!saved.skipped.isEmpty())throw new AssertionError(name+": encrypted restoration must not truncate");
    if(!saved.sites.isEmpty()&&!saved.sites.get(saved.sites.size()-1).key.equals(imported.sites.get(imported.sites.size()-1).key))throw new AssertionError(name+": last site lost");
    if(!saved.lives.isEmpty()&&!saved.lives.get(saved.lives.size()-1).key().equals(imported.lives.get(imported.lives.size()-1).key()))throw new AssertionError(name+": last live source lost");
    if(fixture.has("channels")&&saved.lives.get(0).channels.size()!=fixture.getInt("channels"))throw new AssertionError(name+": channels truncated");
    if(fixture.has("items")&&saved.sites.get(0).items.length()!=fixture.getInt("items"))throw new AssertionError(name+": collection truncated");
    if(fixture.has("episodes")){
     CinemaCatalog.Site site=saved.sites.get(0);CinemaCatalog.Item detail=CinemaResourcePack.page(site,1,null,"item0").items.get(0);
     if(CinemaCatalog.lines(detail,false).get(0).episodes.size()!=fixture.getInt("episodes"))throw new AssertionError(name+": player episodes truncated");
    }
    cases++;
   }
  }
  System.out.println("capacity import, encrypted persistence, page 501 and overflow passed; "+cases+" byte-budget fixtures");
 }
}
