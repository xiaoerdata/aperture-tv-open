package org.zhixiaoce.aperture;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

public final class EmbeddedCinemaTest {
    interface Checked{void run() throws Exception;}
    static int assertions;
    static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    static void reject(Checked action,String message) throws Exception {boolean failed=false;try{action.run();}catch(Exception expected){failed=true;}check(failed,message);}
    public static void main(String[] args) throws Exception {
        String fixture="{\"spider\":\"https://example.invalid/plugin.jar\",\"sites\":[{\"name\":\"家庭库\",\"type\":1,\"api\":\"/api\"},{\"name\":\"脚本站\",\"type\":3,\"api\":\"csp_Plugin\"}],\"lives\":[{\"name\":\"直播\",\"channels\":[{\"name\":\"家庭短片\",\"urls\":[\"https://media.example.invalid/a.m3u8\"]}]}]}";
        CinemaCatalog.Config c=CinemaCatalog.config(fixture,"https://media.example.invalid/config.json",false);
        check(c.sites.size()==1&&c.lives.size()==1&&c.skipped.size()==2,"only compatible sites imported with explicit skipped summary");
        check(c.sites.get(0).api.equals("https://media.example.invalid/api"),"relative config API resolves against file URL");
        check(!c.json().contains("spider")&&!c.json().contains("csp_Plugin"),"normalization drops executable extension data");
        check(CinemaCatalog.config(c.json(),null,false).lives.get(0).channels.get(0).urls.size()==1,"normalized config round-trips");
        javax.crypto.KeyGenerator keys=javax.crypto.KeyGenerator.getInstance("AES");keys.init(256);javax.crypto.SecretKey key=keys.generateKey();String largeRecord="x".repeat(140000),sealed=CinemaCipher.encrypt(largeRecord,key);
        check(CinemaCipher.decrypt(sealed,key).equals(largeRecord),"media ciphertext does not inherit the smaller NAS record limit");
        byte[] damaged=java.util.Base64.getDecoder().decode(sealed.split("\\.")[1]);damaged[0]^=1;
        reject(()->CinemaCipher.decrypt(sealed.split("\\.")[0]+"."+java.util.Base64.getEncoder().encodeToString(damaged),key),"source ciphertext tamper rejected");
        reject(()->CinemaCipher.encrypt("x".repeat(CinemaCatalog.MAX_BYTES+1),key),"normalized encrypted configuration has the same size bound");
        for(String bad:new String[]{"file:///private/key","javascript:alert(1)","proxy://channel","https://user:pass@example.invalid/file","https://example.invalid:0/a","https://example.invalid/a#secret","http://example.invalid/a"})reject(()->CinemaCatalog.url(bad,null,false),"invalid URL "+bad.split(":")[0]);
        check(CinemaCatalog.url("http://192.168.1.10/a",null,true).startsWith("http:"),"explicit HTTP import supports household LAN");
        reject(()->CinemaCatalog.config("x".repeat(CinemaCatalog.MAX_BYTES+1),null,false),"bounded raw input");
        String m3u="#EXTM3U\n#EXTINF:-1 group-title=\"家里\",频道一\nhttps://media.example.invalid/a.m3u8\n#EXTINF:-1 group-title=\"家里\",频道二\n./second.mp4\n";
        List<CinemaCatalog.Channel> channels=CinemaCatalog.m3u(m3u,"https://media.example.invalid/channels/list.m3u",false);
        check(channels.size()==2&&channels.get(1).urls.get(0).equals("https://media.example.invalid/channels/second.mp4"),"M3U groups and relative stream URL");
        reject(()->CinemaCatalog.m3u("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\na.ts", "https://example.invalid/a.m3u8",false),"HLS segments are not imported as television channels");
        StringBuilder huge=new StringBuilder("#EXTM3U\n");for(int i=0;i<501;i++)huge.append("#EXTINF:-1,A\nhttps://example.invalid/a.mp4\n");reject(()->CinemaCatalog.m3u(huge.toString(),null,false),"bounded channel count");
        JSONObject video=new JSONObject().put("vod_id",7).put("vod_name","家庭短片").put("vod_play_from","家庭线路$$$其他").put("vod_play_url","第一集$https://example.invalid/1.mp4#第二集$https://example.invalid/2.m3u8$$$网页$https://example.invalid/watch/7");
        String page=new JSONObject().put("class",new JSONArray().put(new JSONObject().put("type_id",2).put("type_name","电视剧"))).put("list",new JSONArray().put(video)).put("page",1).put("pagecount",3).toString();
        CinemaCatalog.Page parsed=CinemaCatalog.page(page,1);check(parsed.categories.get(0).id.equals("2")&&parsed.items.get(0).id.equals("7"),"numeric IDs supported");
        List<CinemaCatalog.Line> lines=CinemaCatalog.lines(parsed.items.get(0),false);check(lines.size()==1&&lines.get(0).episodes.size()==2,"direct episodes grouped; web resolver links excluded");
        reject(()->CinemaCatalog.lines(new CinemaCatalog.Item(new JSONObject().put("vod_id","8").put("vod_name","网页").put("vod_play_url","https://example.invalid/watch/8")),false),"resolver requirement is explicit");
        reject(()->CinemaCatalog.page("<html>error</html>",1),"HTML is not a catalogue");
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger api=new AtomicInteger(),redirected=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1);String[] query={""};
        java.util.concurrent.ExecutorService threads=java.util.concurrent.Executors.newCachedThreadPool(r->{Thread t=new Thread(r);t.setDaemon(true);return t;});server.setExecutor(threads);
        server.createContext("/api",e->{api.incrementAndGet();query[0]=e.getRequestURI().getRawQuery();byte[] b=page.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
        server.createContext("/redirect",e->{e.getResponseHeaders().set("Location","/target");e.sendResponseHeaders(302,-1);e.close();});
        server.createContext("/target",e->{redirected.incrementAndGet();e.sendResponseHeaders(200,-1);e.close();});
        server.createContext("/html",e->{byte[] b="<html>unavailable</html>".getBytes();e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
        server.createContext("/stream",e->{byte[] b="#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\na.ts\n".getBytes();e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
        server.createContext("/large",e->{e.sendResponseHeaders(200,CinemaCatalog.MAX_BYTES+100L);e.close();});
        server.createContext("/slow",e->{entered.countDown();try{Thread.sleep(1800);e.sendResponseHeaders(200,-1);}catch(Exception ignored){}finally{e.close();}});
        server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();CinemaHttp http=new CinemaHttp();
        try{
            reject(()->http.text(base+"/api",false),"HTTP blocked before any request without approval");check(api.get()==0,"unapproved request did not reach server");
            CinemaCatalog.Site site=new CinemaCatalog.Site("测试",base+"/api?ac=wrong&pg=wrong&token=synthetic");http.catalogue(site,"2",3,"周末",null,true);
            check(query[0].contains("ac=detail")&&!query[0].contains("wrong")&&query[0].contains("t=2")&&query[0].contains("pg=3")&&query[0].contains("token=synthetic"),"canonical TVBox category/search query retains unrelated API parameters");
            http.catalogue(site,null,1,null,"7",true);check(query[0].contains("ids=7")&&!query[0].contains("wd="),"detail request uses selected ID");
            check(http.home(new CinemaCatalog.Site("目录",base+"/api?token=synthetic"),true).categories.size()==1&&!query[0].contains("ac="),"home categories use the base API without forcing detail mode");
            reject(()->http.catalogue(site,null,501,null,null,true),"bounded catalogue pagination");
            reject(()->http.text(base+"/redirect",true),"catalogue redirect rejected");check(redirected.get()==0,"redirect destination never fetched");
            reject(()->http.probe(base+"/html",true),"HTTP200 HTML is not healthy media");check(http.probe(base+"/stream",true).contains("播放待验证"),"HLS probe does not claim decoding");
            reject(()->http.text(base+"/large",true),"content-length bound");
            http.deadline(System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(200));long start=System.nanoTime();reject(()->http.text(base+"/slow",true),"health request deadline");check(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(2),"deadline bounded");http.deadline(0);
        }finally{http.cancel();server.stop(0);threads.shutdownNow();}
        System.out.println("Embedded cinema: "+assertions+" parser/HTTP assertions passed; no device playback claim");
    }
}
