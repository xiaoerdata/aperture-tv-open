package org.zhixiaoce.aperture;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Real playlist redirects through production HTTP/parser/cache; no artwork, video or external account fetched. */
public final class CinemaLogoAddressTest {
    static int checks;
    interface Run {void run()throws Exception;}
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    static void rejects(Run run,String why)throws Exception{boolean rejected=false;try{run.run();}catch(Exception expected){rejected=true;}check(rejected,why);}
    public static void main(String[] args)throws Exception {
        List<String> requests=Collections.synchronizedList(new ArrayList<>());
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{String path=exchange.getRequestURI().getPath();requests.add(path);
            if(path.equals("/entry.m3u")){exchange.getResponseHeaders().add("Location","/lists/v2/channels.m3u");exchange.sendResponseHeaders(302,-1);}
            else {byte[] body=("#EXTM3U\n#EXTINF:-1 tvg-logo=\"../logos/channel.png\" tvg-id=\"one\" tvg-chno=\"51\",合成频道\n#EXTVLCOPT:http-user-agent=Synthetic-Player\n./stream.mp4\n").getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);}exchange.close();});server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            CinemaCatalog.Config imported=new CinemaHttp().importUrl(base+"/entry.m3u",true);
            CinemaCatalog.Channel channel=imported.lives.get(0).channels.get(0);
            check(channel.logo.equals(base+"/lists/logos/channel.png"),"relative M3U logo must use final redirected playlist directory");
            check(CinemaCatalog.url(channel.logo,"",true).equals(channel.logo),"sidebar can use imported logo without remembering the playlist base");
            check(channel.urls.get(0).equals(base+"/lists/v2/stream.mp4"),"media URL retains correct redirected base");
            check(channel.number==51&&channel.epgId.equals("one")&&channel.request(0).get("User-Agent").equals("Synthetic-Player"),"logo resolution must not change channel metadata or per-stream headers");
            CinemaCatalog.Channel cached=CinemaCatalog.config(imported.json(),null,true).lives.get(0).channels.get(0);
            check(cached.logo.equals(channel.logo)&&cached.urls.equals(channel.urls),"absolute logo survives save/read with no import URL");
            check(requests.equals(Arrays.asList("/entry.m3u","/lists/v2/channels.m3u")),"parsing does not fetch logo or media, nor send video headers to artwork");
            JSONObject inline=new JSONObject().put("lives",new JSONArray().put(new JSONObject().put("name","测试来源").put("channels",new JSONArray().put(new JSONObject().put("name","合成频道").put("logo","../logos/channel.png").put("urls",new JSONArray().put("./stream.mp4"))))));
            CinemaCatalog.Config legacy=CinemaCatalog.config(inline.toString(),base+"/lists/config.json",true);
            check(legacy.lives.get(0).channels.get(0).logo.equals(base+"/logos/channel.png"),"inline TVBox channel logo uses configuration base");
            check(CinemaCatalog.config(legacy.json(),null,true).lives.get(0).channels.get(0).logo.equals(base+"/logos/channel.png"),"inline configuration cache keeps logo usable");
            rejects(()->CinemaCatalog.url(channel.logo,null,false),"normalization does not bypass explicit HTTP consent");
            List<CinemaCatalog.Channel> https=CinemaCatalog.m3u("#EXTM3U\n#EXTINF:-1 tvg-logo=\"//images.example.invalid/channel.png\",频道\nhttps://media.example.invalid/live.m3u8","https://sources.example.invalid/lists/live.m3u",false);
            check(https.get(0).logo.equals("https://images.example.invalid/channel.png"),"scheme-relative logo keeps HTTPS policy");
            List<CinemaCatalog.Channel> bad=CinemaCatalog.m3u("#EXTM3U\n#EXTINF:-1 tvg-logo=\"javascript:alert(1)\",频道\nhttps://media.example.invalid/live.m3u8","https://sources.example.invalid/list.m3u",false);
            check(bad.size()==1&&bad.get(0).urls.size()==1,"invalid optional logo does not discard a valid playable channel");
            rejects(()->CinemaCatalog.url(bad.get(0).logo,null,false),"invalid logo remains blocked by renderer URL policy");
            List<CinemaCatalog.Channel> withoutBase=CinemaCatalog.m3u("#EXTM3U\n#EXTINF:-1 tvg-logo=\"./channel.png\",频道\nhttps://media.example.invalid/live.m3u8",null,false);
            check(withoutBase.get(0).logo.equals("./channel.png"),"missing historical base is preserved rather than guessed or erased");
            rejects(()->CinemaCatalog.url(withoutBase.get(0).logo,null,false),"unknown base does not become an arbitrary fetch address");
            System.out.println(checks+" production logo address assertions passed");
        } finally {server.stop(0);}
    }
}
