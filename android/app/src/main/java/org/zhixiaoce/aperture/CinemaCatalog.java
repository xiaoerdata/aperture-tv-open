package org.zhixiaoce.aperture;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded catalogue formats; executable sites are dispatched through the opt-in plugin engine. */
final class CinemaCatalog {
    static final int MAX_BYTES = 524288, MAX_CHANNELS = 500;
    static final class Site {
        final String name, api, key, ext, jar, spider, base;final int type;final java.util.Map<String,String> headers;
        Site(String name,String api){this(name,api,api,1,java.util.Collections.emptyMap());}
        Site(String name,String api,String key,int type,java.util.Map<String,String> headers){this(name,api,key,type,headers,"","","","");}
        Site(String name,String api,String key,int type,java.util.Map<String,String> headers,String ext,String jar,String spider,String base){this.name=name;this.api=api;this.key=key;this.type=type;this.headers=headers;this.ext=ext;this.jar=jar;this.spider=spider;this.base=base;}
        String identity="";List<String> sections=new ArrayList<>();JSONObject categoryIds=new JSONObject();JSONArray items;
        boolean supports(String section){return sections.isEmpty()||"library".equals(section)||sections.contains(section);}
        boolean resolved(){return type==3||type==4;}
    }
    static final class Channel {
        final String name,group;String id="",logo=""; final List<String> urls;final java.util.Map<String,String> headers;final List<java.util.Map<String,String>> requests;
        Channel(String name,String group,List<String> urls){this(name,group,urls,java.util.Collections.emptyMap());}
        Channel(String name,String group,List<String> urls,java.util.Map<String,String> headers){this(name,group,urls,headers,new ArrayList<>());}
        Channel(String name,String group,List<String> urls,java.util.Map<String,String> headers,List<java.util.Map<String,String>> requests){this.name=name;this.group=group;this.urls=urls;this.headers=headers;this.requests=requests;}
        java.util.Map<String,String> request(int line){return line<requests.size()?requests.get(line):headers;}
        JSONArray requestJson(){JSONArray values=new JSONArray();for(int i=0;i<urls.size();i++)values.put(new JSONObject(request(i)));return values;}
    }
    static final class Live {
        final String name,url; final List<Channel> channels;final java.util.Map<String,String> headers;
        Live(String name,String url,List<Channel> channels){this(name,url,channels,java.util.Collections.emptyMap());}
        Live(String name,String url,List<Channel> channels,java.util.Map<String,String> headers){this.name=name;this.url=url;this.channels=channels;this.headers=headers;}
        String identity="";
        String key(){return identity.isEmpty()?name+"\n"+url:identity;}
    }
    static final class Config {
        final List<Site> sites=new ArrayList<>(); final List<Live> lives=new ArrayList<>();
        final List<Site> warehouses=new ArrayList<>();
        final List<String> skipped=new ArrayList<>(); final boolean allowHttp;
        String packId="",packTitle="",packSha256="";int packRevision;
        Config(boolean allowHttp){this.allowHttp=allowHttp;}
        String json() throws Exception {
            JSONArray ss=new JSONArray(),ls=new JSONArray();
            for(Site s:sites)ss.put(new JSONObject().put("name",s.name).put("type",s.type).put("api",s.api).put("key",s.key).put("header",new JSONObject(s.headers)).put("ext",s.ext).put("jar",s.jar).put("spider",s.spider).put("base",s.base).put("identity",s.identity).put("sections",new JSONArray(s.sections)).put("categoryIds",s.categoryIds).put("items",s.items));
            for(Live l:lives){JSONObject v=new JSONObject().put("name",l.name).put("identity",l.identity).put("header",new JSONObject(l.headers));if(!l.url.isEmpty())v.put("url",l.url);else{JSONArray cs=new JSONArray();for(Channel c:l.channels)cs.put(new JSONObject().put("id",c.id).put("name",c.name).put("group",c.group).put("logo",c.logo).put("urls",new JSONArray(c.urls)).put("header",new JSONObject(c.headers)).put("requestHeaders",c.requestJson()));v.put("channels",cs);}ls.put(v);}
            return new JSONObject().put("format","kaishi.catalog-cache").put("version",1).put("packId",packId).put("packTitle",packTitle).put("packRevision",packRevision).put("packSha256",packSha256).put("sites",ss).put("lives",ls).toString();
        }
    }
    static final class Category {
        final String id,name;Category(String id,String name){this.id=id;this.name=name;}
    }
    static final class Item {
        final String id,name,remark,playFrom,playUrl;List<Line> directLines;
        String poster="",year="",genre="",area="",director="",actors="",summary="",rating="";
        Item metadata(JSONObject o)throws IOException{poster=field(o.optString("vod_pic"),4096);year=field(o.optString("vod_year"),40);genre=field(o.optString("type_name",o.optString("vod_class")),160);area=field(o.optString("vod_area"),100);director=field(o.optString("vod_director"),300);actors=field(o.optString("vod_actor"),600);summary=plain(o.optString("vod_content"));rating=field(o.optString("vod_score"),40);return this;}
        static String plain(String text){String value=(text==null?"":text).replaceAll("<[^>]*>"," ").replace("&nbsp;"," ").replace("&amp;","&").replaceAll("\\s+"," ").trim();return value.substring(0,Math.min(4000,value.length()));}

        Item(String id,String name,String remark,List<Line> lines){this.id=id;this.name=name;this.remark=remark;this.playFrom="";this.playUrl="";this.directLines=lines;}
        Item(JSONObject o) throws IOException {id=field(o.optString("vod_id"),8192);name=field(o.optString("vod_name"),160);if(id.isEmpty()||name.isEmpty())throw new IOException("影片缺少标识或名称");remark=field(o.optString("vod_remarks"),200);playFrom=o.optString("vod_play_from");playUrl=o.optString("vod_play_url");if(playUrl.length()>MAX_BYTES)throw new IOException("播放列表过大");metadata(o);}
    }
    static final class Page {
        final List<Category> categories=new ArrayList<>();final List<Item> items=new ArrayList<>();int page,pages;
    }
    static final class Episode {
        final String name,url;final java.util.Map<String,String> headers;
        Episode(String name,String url){this(name,url,java.util.Collections.emptyMap());}
        Episode(String name,String url,java.util.Map<String,String> headers){this.name=name;this.url=url;this.headers=headers;}
    }
    static final class Line {
        final String name;final List<Episode> episodes=new ArrayList<>();Line(String name){this.name=name;}
    }
    static String extension(String value,int max)throws IOException{String result=value==null?"":value;if(result.length()>max||result.indexOf('\0')>=0)throw new IOException("扩展参数过长或无效");return result;}
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
        String text=raw.replaceFirst("^\\uFEFF","").trim();if(text.startsWith("//")||text.startsWith("/*"))text=CinemaFormats.json(text).trim();
        if(text.startsWith("#EXTM3U")&&text.contains("#EXT-X-")){if(base==null)throw new IOException("HLS媒体文件需要原始网络地址");result.lives.add(new Live("直播","",java.util.Arrays.asList(new Channel("直播","直播",java.util.Arrays.asList(url(base,null,allowHttp))))));return result;}
        if(text.startsWith("#EXTM3U")||!text.startsWith("{")&&!text.startsWith("<")&&text.contains(",")){result.lives.add(new Live("直播","",playlist(text,base,allowHttp,java.util.Collections.emptyMap())));return result;}
        if(text.startsWith("<")){page(text,1);if(base==null)throw new IOException("XML目录需要网络API地址");result.sites.add(new Site("影视",url(base,null,allowHttp),base,0,java.util.Collections.emptyMap()));return result;}
        JSONObject root=new JSONObject(CinemaFormats.json(text));
        if("kaishi.resource-pack".equals(root.optString("format")))return CinemaResourcePack.read(root,allowHttp,raw);
        boolean cache="kaishi.catalog-cache".equals(root.optString("format"));
        if(root.optString("format").startsWith("kaishi.")&&!cache)throw new IOException("不支持的开视资源文件格式");
        if(cache){result.packId=root.optString("packId");result.packTitle=root.optString("packTitle");result.packRevision=root.optInt("packRevision");result.packSha256=root.optString("packSha256");}
        if(root.has("list")&&!root.has("sites")){page(text,1);if(base==null)throw new IOException("采集目录需要网络API地址");result.sites.add(new Site("影视",url(base,null,allowHttp)));return result;}
        JSONArray feeds=root.optJSONArray("urls");if(feeds==null)feeds=root.optJSONArray("data");
        if(feeds!=null&&!root.has("sites")&&!root.has("lives")){if(feeds.length()>64)throw new IOException("仓库超过64项");for(int i=0;i<feeds.length();i++){JSONObject feed=feeds.optJSONObject(i);if(feed==null)continue;try{result.warehouses.add(new Site(field(feed.optString("name",feed.optString("sourceName","配置")),120),url(feed.optString("url",feed.optString("sourceUrl")),base,allowHttp)));}catch(IOException invalid){result.skipped.add("一个仓库地址不支持");}}if(result.warehouses.isEmpty())throw new IOException("没有可读取的仓库地址");return result;}
        JSONObject video=root.optJSONObject("video");
        JSONArray sites=(video==null?root:video).optJSONArray("sites");
        if(sites!=null){if(sites.length()>100)throw new IOException("站点超过100项");for(int i=0;i<sites.length();i++){
            JSONObject s=sites.optJSONObject(i);if(s==null)continue;String name=field(s.optString("name","站点"),120);
            if(s.optInt("type",-1)!=0&&s.optInt("type",-1)!=1&&s.optInt("type",-1)!=3&&s.optInt("type",-1)!=4&&!(cache&&s.optInt("type",-1)==5)){result.skipped.add(name+"：当前未接入该站点类型");continue;}
            if(result.sites.size()>=12){result.skipped.add(name+"：超过12个站点");continue;}
            try{int type=s.optInt("type",1);String sourceBase=field(s.optString("base",base==null?"":base),4096);String api=type==5?"":type==3?field(s.optString("api"),4096):url(s.optString("api"),sourceBase,allowHttp);if(api.isEmpty()&&type!=5)throw new IOException("站点缺少API");Site site=new Site(name,api,field(s.optString("key",s.optString("api")),4096),type,CinemaFormats.headers(s),extension(s.optString("ext"),65536),field(s.optString("jar"),4096),field(s.optString("spider",root.optString("spider")),4096),sourceBase);if(cache)CinemaResourcePack.restoreSite(site,s,allowHttp);result.sites.add(site);}catch(IOException unsupported){result.skipped.add(name+"：地址类型不支持");}
        }}
        JSONArray lives=root.optJSONArray("lives");int total=0;
        if(lives!=null){if(lives.length()>100)throw new IOException("直播来源超过100项");for(int i=0;i<lives.length();i++){
            JSONObject l=lives.optJSONObject(i);if(l==null)continue;String name=field(l.optString("name",l.optString("group","直播")),120);
            if(result.lives.size()>=12){result.skipped.add(name+"：超过12个直播来源");continue;}
            String feed=l.optString("url");if(!feed.isEmpty()){try{Live live=new Live(name,url(feed,base,allowHttp),new ArrayList<>(),CinemaFormats.headers(l));if(cache)live.identity=field(l.optString("identity"),200);result.lives.add(live);}catch(IOException invalid){result.skipped.add(name+"：地址类型不支持");}continue;}
            JSONArray entries=l.optJSONArray("channels");List<Channel> channels=new ArrayList<>();
            if(entries!=null)for(int j=0;j<entries.length();j++){
                if(++total>MAX_CHANNELS)throw new IOException("内置频道超过500项");JSONObject c=entries.optJSONObject(j);if(c==null)continue;
                List<String> urls=new ArrayList<>();List<java.util.Map<String,String>> requests=new ArrayList<>();java.util.Map<String,String> header=new java.util.LinkedHashMap<>(CinemaFormats.headers(l));header.putAll(CinemaFormats.headers(c));JSONArray array=c.optJSONArray("urls");if(array==null&&c.has("url"))array=new JSONArray().put(c.optString("url"));
                JSONArray savedHeaders=c.optJSONArray("requestHeaders");
                if(array!=null)for(int k=0;k<Math.min(8,array.length());k++)try{java.util.Map<String,String> specific=new java.util.LinkedHashMap<>(header);if(savedHeaders!=null&&savedHeaders.optJSONObject(k)!=null)specific.putAll(CinemaFormats.headers(new JSONObject().put("header",savedHeaders.getJSONObject(k))));urls.add(CinemaFormats.stream(array.optString(k),base,allowHttp,specific));requests.add(specific);}catch(IOException invalid){}
                if(!urls.isEmpty()){Channel channel=new Channel(field(c.optString("name","频道"),120),field(c.optString("group",name),80),urls,header,requests);if(cache)channel.id=field(c.optString("id"),100);channel.logo=field(c.optString("logo",c.optString("tvg-logo")),4096);channels.add(channel);}else result.skipped.add("一个频道没有可用的直链地址");
            }
            if(!channels.isEmpty()){Live live=new Live(name,"",channels);if(cache)live.identity=field(l.optString("identity"),200);result.lives.add(live);}
        }}
        if(root.has("parses"))result.skipped.add("解析器未加载");
        if(result.sites.isEmpty()&&result.lives.isEmpty())throw new IOException("没有可导入的直播或影视站点");
        return result;
    }
    static List<Channel> playlist(String raw,String base,boolean allowHttp,java.util.Map<String,String> headers)throws IOException{return CinemaFormats.playlist(raw,base,allowHttp,headers);}
    static List<Channel> m3u(String raw,String base,boolean allowHttp) throws IOException {
        return playlist(raw,base,allowHttp,java.util.Collections.emptyMap());
    }
    static Page page(String raw,int requested) throws Exception {
        if(raw==null||raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES)throw new IOException("目录超过512KB");
        raw=raw.replaceFirst("^\\uFEFF","");if(raw.trim().startsWith("<"))return CinemaFormats.xml(raw,requested);
        JSONObject root=new JSONObject(CinemaFormats.json(raw.replaceFirst("^\\uFEFF","")));if(root.optJSONArray("list")==null&&root.optJSONArray("class")==null)throw new IOException("不是采集目录");Page result=new Page();result.page=Math.min(500,Math.max(1,root.optInt("page",requested)));result.pages=Math.max(result.page,Math.min(500,root.optInt("pagecount",result.page)));
        JSONArray classes=root.optJSONArray("class");if(classes!=null)for(int i=0;i<Math.min(100,classes.length());i++){JSONObject c=classes.getJSONObject(i);result.categories.add(new Category(field(c.optString("type_id"),100),field(c.optString("type_name"),120)));}
        JSONArray list=root.optJSONArray("list");if(list!=null)for(int i=0;i<Math.min(120,list.length());i++)result.items.add(new Item(list.getJSONObject(i)));
        return result;
    }
    static List<Line> lines(Item item,boolean allowHttp) throws Exception {return lines(item,allowHttp,java.util.Collections.emptyMap(),false);}
    static List<Line> lines(Item item,boolean allowHttp,java.util.Map<String,String> headers) throws Exception {return lines(item,allowHttp,headers,false);}
    static List<Line> lines(Item item,boolean allowHttp,java.util.Map<String,String> headers,boolean resolve) throws Exception {
        if(item.directLines!=null)return item.directLines;
        List<Line> result=new ArrayList<>();String[] groups=item.playUrl.split("\\$\\$\\$",-1),names=item.playFrom.split("\\$\\$\\$",-1);int count=0;
        if(groups.length>20)throw new IOException("播放线路超过20项");
        for(int i=0;i<groups.length;i++){
            Line line=new Line(i<names.length&&!names[i].isEmpty()?field(names[i],120):"线路 "+(i+1));
            for(String entry:groups[i].split("#")){
                if(entry.isEmpty())continue;if(++count>500)throw new IOException("选集超过500项");int dollar=entry.indexOf('$');String name=dollar<0?"播放":field(entry.substring(0,dollar),120),value=dollar<0?entry:entry.substring(dollar+1);
                if(resolve){line.episodes.add(new Episode(name,field(value,8192),new java.util.LinkedHashMap<>(headers)));continue;}
                try{java.util.Map<String,String> merged=new java.util.LinkedHashMap<>(headers);String uri=CinemaFormats.stream(value,null,allowHttp,merged);String path=new URI(uri).getPath().toLowerCase(java.util.Locale.ROOT);if(path.matches(".*\\.(html?|php)$")||path.matches(".*/(watch|detail|vodplay)/.*"))continue;line.episodes.add(new Episode(name,uri,merged));}catch(IOException invalid){}
            }
            if(!line.episodes.isEmpty())result.add(line);
        }
        if(result.isEmpty())throw new IOException("该影片需要额外解析，当前只播放 MP4/HLS 等媒体直链");return result;
    }
}
