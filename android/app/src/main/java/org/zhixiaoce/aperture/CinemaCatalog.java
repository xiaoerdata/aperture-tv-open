package org.zhixiaoce.aperture;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Data-only TVBox-compatible inputs. No plugin loading, HTML scraping or script execution. */
final class CinemaCatalog {
    static final int MAX_BYTES = 524288, MAX_CHANNELS = 500;
    static final class Site {
        final String name, api;
        Site(String name,String api){this.name=name;this.api=api;}
    }
    static final class Channel {
        final String name,group; final List<String> urls;
        Channel(String name,String group,List<String> urls){this.name=name;this.group=group;this.urls=urls;}
    }
    static final class Live {
        final String name,url; final List<Channel> channels;
        Live(String name,String url,List<Channel> channels){this.name=name;this.url=url;this.channels=channels;}
    }
    static final class Config {
        final List<Site> sites=new ArrayList<>(); final List<Live> lives=new ArrayList<>();
        final List<String> skipped=new ArrayList<>(); final boolean allowHttp;
        Config(boolean allowHttp){this.allowHttp=allowHttp;}
        String json() throws Exception {
            JSONArray ss=new JSONArray(),ls=new JSONArray();
            for(Site s:sites)ss.put(new JSONObject().put("name",s.name).put("type",1).put("api",s.api));
            for(Live l:lives){JSONObject v=new JSONObject().put("name",l.name);if(!l.url.isEmpty())v.put("url",l.url);else{JSONArray cs=new JSONArray();for(Channel c:l.channels)cs.put(new JSONObject().put("name",c.name).put("group",c.group).put("urls",new JSONArray(c.urls)));v.put("channels",cs);}ls.put(v);}
            return new JSONObject().put("version",1).put("sites",ss).put("lives",ls).toString();
        }
    }
    static final class Category {
        final String id,name;Category(String id,String name){this.id=id;this.name=name;}
    }
    static final class Item {
        final String id,name,remark,playFrom,playUrl;
        Item(JSONObject o) throws IOException {id=field(o.optString("vod_id"),100);name=field(o.optString("vod_name"),160);if(id.isEmpty()||name.isEmpty())throw new IOException("影片缺少标识或名称");remark=field(o.optString("vod_remarks"),200);playFrom=o.optString("vod_play_from");playUrl=o.optString("vod_play_url");if(playUrl.length()>MAX_BYTES)throw new IOException("播放列表过大");}
    }
    static final class Page {
        final List<Category> categories=new ArrayList<>();final List<Item> items=new ArrayList<>();int page,pages;
    }
    static final class Episode {
        final String name,url;Episode(String name,String url){this.name=name;this.url=url;}
    }
    static final class Line {
        final String name;final List<Episode> episodes=new ArrayList<>();Line(String name){this.name=name;}
    }
    static String field(String value,int max) throws IOException {
        String v=value==null?"":value.trim();if(v.length()>max||v.indexOf('\0')>=0)throw new IOException("来源字段过长或无效");return v;
    }
    static String url(String raw,String base,boolean allowHttp) throws IOException {
        try{
            String value=field(raw,4096);if(value.isEmpty())throw new Exception();
            URI uri=new URI(value);if(!uri.isAbsolute()&&base!=null&&!base.isEmpty())uri=new URI(base).resolve(uri);
            String scheme=uri.getScheme();if(!("https".equalsIgnoreCase(scheme)||(allowHttp&&"http".equalsIgnoreCase(scheme)))||uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getRawFragment()!=null||uri.getPort()==0||uri.getPort()>65535)throw new Exception();
            return uri.toASCIIString();
        }catch(Exception invalid){throw new IOException("仅支持已允许的 HTTP(S) 地址，不支持脚本、代理协议或网址中的账号");}
    }
    static Config config(String raw,String base,boolean allowHttp) throws Exception {
        if(raw==null||raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES)throw new IOException("来源配置超过512KB");
        Config result=new Config(allowHttp);
        String text=raw.replaceFirst("^\\uFEFF","").trim();
        if(text.startsWith("#EXTM3U")){result.lives.add(new Live("直播","",m3u(text,base,allowHttp)));return result;}
        JSONObject root=new JSONObject(text);JSONObject video=root.optJSONObject("video");
        JSONArray sites=(video==null?root:video).optJSONArray("sites");
        if(sites!=null){if(sites.length()>100)throw new IOException("站点超过100项");for(int i=0;i<sites.length();i++){
            JSONObject s=sites.optJSONObject(i);if(s==null)continue;String name=field(s.optString("name","站点"),120);
            if(s.optInt("type",-1)!=1){result.skipped.add(name+"：当前未接入该站点类型");continue;}
            if(result.sites.size()>=12){result.skipped.add(name+"：超过12个站点");continue;}
            try{result.sites.add(new Site(name,url(s.optString("api"),base,allowHttp)));}catch(IOException unsupported){result.skipped.add(name+"：地址类型不支持");}
        }}
        JSONArray lives=root.optJSONArray("lives");int total=0;
        if(lives!=null){if(lives.length()>100)throw new IOException("直播来源超过100项");for(int i=0;i<lives.length();i++){
            JSONObject l=lives.optJSONObject(i);if(l==null)continue;String name=field(l.optString("name",l.optString("group","直播")),120);
            if(result.lives.size()>=12){result.skipped.add(name+"：超过12个直播来源");continue;}
            String feed=l.optString("url");if(!feed.isEmpty()){try{result.lives.add(new Live(name,url(feed,base,allowHttp),new ArrayList<>()));}catch(IOException invalid){result.skipped.add(name+"：地址类型不支持");}continue;}
            JSONArray entries=l.optJSONArray("channels");List<Channel> channels=new ArrayList<>();
            if(entries!=null)for(int j=0;j<entries.length();j++){
                if(++total>MAX_CHANNELS)throw new IOException("内置频道超过500项");JSONObject c=entries.optJSONObject(j);if(c==null)continue;
                List<String> urls=new ArrayList<>();JSONArray array=c.optJSONArray("urls");if(array==null&&c.has("url"))array=new JSONArray().put(c.optString("url"));
                if(array!=null)for(int k=0;k<Math.min(8,array.length());k++)try{urls.add(url(array.optString(k),base,allowHttp));}catch(IOException invalid){}
                if(!urls.isEmpty())channels.add(new Channel(field(c.optString("name","频道"),120),field(c.optString("group",name),80),urls));else result.skipped.add("一个频道没有可用的直链地址");
            }
            if(!channels.isEmpty())result.lives.add(new Live(name,"",channels));
        }}
        if(root.has("spider"))result.skipped.add("spider 扩展未加载");
        if(root.has("parses"))result.skipped.add("解析器未加载");
        if(result.sites.isEmpty()&&result.lives.isEmpty())throw new IOException("没有可导入的直播或 type=1 JSON 站点；脚本和解析器尚未接入");
        return result;
    }
    static List<Channel> m3u(String raw,String base,boolean allowHttp) throws IOException {
        if(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES||!raw.replaceFirst("^\\uFEFF","").trim().startsWith("#EXTM3U"))throw new IOException("请提供标准 M3U 清单");
        if(raw.contains("#EXT-X-"))throw new IOException("这是 HLS 媒体清单，请将地址放入频道的 urls 字段");
        List<Channel> result=new ArrayList<>();String name="",group="直播";
        for(String line:raw.split("\\r?\\n")){
            String v=line.trim();if(v.length()>16384)throw new IOException("清单行过长");
            if(v.startsWith("#EXTINF:")){int comma=v.lastIndexOf(',');name=field(comma<0?"频道":v.substring(comma+1),120);Matcher m=Pattern.compile("group-title=\"([^\"]*)\"").matcher(v);group=m.find()?field(m.group(1),80):"直播";}
            else if(!v.isEmpty()&&!v.startsWith("#")){
                if(result.size()>=MAX_CHANNELS)throw new IOException("频道超过500项");
                List<String> urls=new ArrayList<>();urls.add(url(v,base,allowHttp));result.add(new Channel(name.isEmpty()?"频道 "+(result.size()+1):name,group,urls));name="";
            }
        }
        if(result.isEmpty())throw new IOException("清单中没有频道");return result;
    }
    static Page page(String raw,int requested) throws Exception {
        JSONObject root=new JSONObject(raw);Page result=new Page();result.page=Math.min(500,Math.max(1,root.optInt("page",requested)));result.pages=Math.max(result.page,Math.min(500,root.optInt("pagecount",result.page)));
        JSONArray classes=root.optJSONArray("class");if(classes!=null)for(int i=0;i<Math.min(100,classes.length());i++){JSONObject c=classes.getJSONObject(i);result.categories.add(new Category(field(c.optString("type_id"),100),field(c.optString("type_name"),120)));}
        JSONArray list=root.optJSONArray("list");if(list!=null)for(int i=0;i<Math.min(120,list.length());i++)result.items.add(new Item(list.getJSONObject(i)));
        return result;
    }
    static List<Line> lines(Item item,boolean allowHttp) throws Exception {
        List<Line> result=new ArrayList<>();String[] groups=item.playUrl.split("\\$\\$\\$",-1),names=item.playFrom.split("\\$\\$\\$",-1);int count=0;
        if(groups.length>20)throw new IOException("播放线路超过20项");
        for(int i=0;i<groups.length;i++){
            Line line=new Line(i<names.length&&!names[i].isEmpty()?field(names[i],120):"线路 "+(i+1));
            for(String entry:groups[i].split("#")){
                if(entry.isEmpty())continue;if(++count>500)throw new IOException("选集超过500项");int dollar=entry.indexOf('$');String name=dollar<0?"播放":field(entry.substring(0,dollar),120),value=dollar<0?entry:entry.substring(dollar+1);
                try{String uri=url(value,null,allowHttp);String path=new URI(uri).getPath().toLowerCase(java.util.Locale.ROOT);if(!path.matches(".*\\.(m3u8|mp4|m4v|webm|mkv|mov|ts)$"))continue;line.episodes.add(new Episode(name,uri));}catch(IOException invalid){}
            }
            if(!line.episodes.isEmpty())result.add(line);
        }
        if(result.isEmpty())throw new IOException("该影片需要额外解析，当前只播放 MP4/HLS 等媒体直链");return result;
    }
}
