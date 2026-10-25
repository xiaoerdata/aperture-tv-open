package org.zhixiaoce.aperture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Kaishi Resource Pack v1: declarative data; parsing never fetches or executes anything. */
final class CinemaResourcePack {
    static final String FORMAT="kaishi.resource-pack";
    static final List<String> SECTIONS=Arrays.asList("movies","series","variety");
    private static IOException invalid(String path,String reason){return new IOException(path+"："+reason);}
    private static void keys(JSONObject value,String path,String... allowed)throws Exception{
        Set<String> names=new HashSet<>(Arrays.asList(allowed));Iterator<String> it=value.keys();while(it.hasNext()){String key=it.next();if(!names.contains(key))throw invalid(path+"."+key,"未知字段");}
    }
    private static String string(JSONObject o,String key,int max,boolean required)throws Exception{
        if(!o.has(key)){if(required)throw invalid(key,"缺少字段");return "";}
        Object v=o.get(key);if(!(v instanceof String))throw invalid(key,"需要文本");String result=CinemaCatalog.field((String)v,max);if(required&&result.isEmpty())throw invalid(key,"不能为空");return result;
    }
    private static String id(JSONObject o,String key,int max)throws Exception{String value=string(o,key,max,true);if(!value.matches("[a-z0-9][a-z0-9._-]*"))throw invalid(key,"仅使用小写字母、数字、点、短横线和下划线");return value;}
    private static int integer(JSONObject o,String key,int min,int max)throws Exception{Object n=o.get(key);if(!(n instanceof Number)||((Number)n).doubleValue()!=((Number)n).intValue())throw invalid(key,"需要整数");int value=((Number)n).intValue();if(value<min||value>max)throw invalid(key,"超出范围");return value;}
    private static JSONArray array(JSONObject o,String key,int min,int max)throws Exception{Object v=o.get(key);if(!(v instanceof JSONArray))throw invalid(key,"需要数组");JSONArray a=(JSONArray)v;if(a.length()<min||a.length()>max)throw invalid(key,"数量需在"+min+"至"+max+"之间");return a;}
    private static JSONObject object(JSONArray a,int at,String path)throws Exception{Object v=a.get(at);if(!(v instanceof JSONObject))throw invalid(path+"["+at+"]","需要对象");return (JSONObject)v;}
    private static String address(JSONObject o,String key,boolean allowHttp)throws Exception{return CinemaCatalog.url(string(o,key,4096,true),null,allowHttp);}
    private static Map<String,String> headers(JSONObject o)throws Exception{
        if(!o.has("headers"))return Collections.emptyMap();if(!(o.get("headers") instanceof JSONObject))throw invalid("headers","需要对象");
        JSONObject value=o.getJSONObject("headers");if(value.length()>8)throw invalid("headers","最多8项");Iterator<String> it=value.keys();
        while(it.hasNext()){String key=it.next();if(!Arrays.asList("accept","user-agent","referer","origin","content-type").contains(key.toLowerCase(Locale.ROOT)))throw invalid("headers."+key,"资源包只允许公开的播放请求头");if(!(value.get(key) instanceof String)||value.getString(key).length()>1024)throw invalid("headers."+key,"需要1024字以内文本");}
        return CinemaFormats.headers(new JSONObject().put("header",value));
    }
    private static List<String> sections(JSONObject o,boolean required)throws Exception{
        if(!o.has("sections")&&!required)return new ArrayList<>();JSONArray values=array(o,"sections",1,3);List<String> result=new ArrayList<>();
        for(int i=0;i<values.length();i++){Object v=values.get(i);if(!(v instanceof String)||!SECTIONS.contains(v)||result.contains(v))throw invalid("sections","分类无效或重复");result.add((String)v);}return result;
    }
    private static JSONObject categories(JSONObject o,List<String> sections)throws Exception{
        if(!o.has("categoryIds"))return new JSONObject();if(!(o.get("categoryIds") instanceof JSONObject))throw invalid("categoryIds","需要对象");JSONObject values=o.getJSONObject("categoryIds"),result=new JSONObject();Iterator<String> it=values.keys();
        while(it.hasNext()){String key=it.next();if(!sections.contains(key))throw invalid("categoryIds."+key,"需先在sections声明");JSONArray ids=array(values,key,1,100);Set<String> seen=new HashSet<>();JSONArray out=new JSONArray();for(int i=0;i<ids.length();i++){Object v=ids.get(i);if(!(v instanceof String))throw invalid("categoryIds."+key,"分类编号必须是文本");String text=CinemaCatalog.field((String)v,100);if(text.isEmpty()||!seen.add(text))throw invalid("categoryIds."+key,"编号为空或重复");out.put(text);}result.put(key,out);}return result;
    }
    static String sha256(String raw)throws Exception{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    static CinemaCatalog.Config read(JSONObject root,boolean allowHttp,String raw)throws Exception{
        keys(root,"pack","format","version","id","revision","title","description","author","resources");
        if(!FORMAT.equals(string(root,"format",64,true))||integer(root,"version",1,1)!=1)throw invalid("version","不支持的资源包版本");
        CinemaCatalog.Config result=new CinemaCatalog.Config(allowHttp);result.packId=id(root,"id",96);result.packTitle=string(root,"title",120,true);result.packRevision=integer(root,"revision",1,Integer.MAX_VALUE);result.packSha256=sha256(raw);
        string(root,"description",500,false);string(root,"author",120,false);JSONArray resources=array(root,"resources",1,24);Set<String> ids=new HashSet<>();int channelCount=0,itemCount=0,episodeCount=0;
        for(int i=0;i<resources.length();i++){
            JSONObject r=object(resources,i,"resources");String key=id(r,"id",64),name=string(r,"title",120,true),type=string(r,"type",32,true);if(!ids.add(key))throw invalid("resources."+key,"资源编号重复");String identity="kaishi:"+result.packId+":"+key;
            if(type.equals("live")){
                keys(r,key,"id","type","title","url","channels","headers");Map<String,String> header=headers(r);if(r.has("url")==r.has("channels"))throw invalid(key,"直播需选择url或channels之一");CinemaCatalog.Live live;
                if(r.has("url"))live=new CinemaCatalog.Live(name,address(r,"url",allowHttp),new ArrayList<>(),header);
                else{JSONArray values=array(r,"channels",1,500);List<CinemaCatalog.Channel> channels=new ArrayList<>();Set<String> channelIds=new HashSet<>();for(int j=0;j<values.length();j++){
                    JSONObject c=object(values,j,key+".channels");keys(c,key+".channels","id","title","group","urls","headers","logo");String channelId=id(c,"id",64);if(!channelIds.add(channelId))throw invalid(key,"频道编号重复");String title=string(c,"title",120,true),group=string(c,"group",80,false);Map<String,String> h=new LinkedHashMap<>(header);h.putAll(headers(c));JSONArray urls=array(c,"urls",1,8);List<String> links=new ArrayList<>();for(int n=0;n<urls.length();n++){if(!(urls.get(n) instanceof String))throw invalid(key,"频道地址需要文本");links.add(CinemaCatalog.url(urls.getString(n),null,allowHttp));}CinemaCatalog.Channel channel=new CinemaCatalog.Channel(title,group.isEmpty()?name:group,links,h);channel.id=channelId;channel.logo=c.has("logo")?address(c,"logo",allowHttp):"";channels.add(channel);
                }live=new CinemaCatalog.Live(name,"",channels,header);}
                live.identity=identity;result.lives.add(live);channelCount+=live.channels.size();
            }else if(type.equals("catalog")){
                keys(r,key,"id","type","title","url","protocol","sections","categoryIds","headers");String protocol=string(r,"protocol",32,true);int kind=protocol.equals("maccms-json")?1:protocol.equals("maccms-xml")?0:protocol.equals("tvbox-type4")?4:-1;if(kind<0)throw invalid(key,"不支持的目录协议");
                CinemaCatalog.Site site=new CinemaCatalog.Site(name,address(r,"url",allowHttp),identity,kind,headers(r));site.identity=identity;site.sections=sections(r,true);site.categoryIds=categories(r,site.sections);result.sites.add(site);
            }else if(type.equals("collection")){
                keys(r,key,"id","type","title","section","items","headers");String section=string(r,"section",16,true);if(!SECTIONS.contains(section))throw invalid(key,"清单分类无效");
                CinemaCatalog.Site site=new CinemaCatalog.Site(name,"",identity,5,headers(r));site.identity=identity;site.sections.add(section);site.items=collectionItems(array(r,"items",1,200),allowHttp);result.sites.add(site);itemCount+=site.items.length();for(int j=0;j<site.items.length();j++)episodeCount+=site.items.getJSONObject(j).getJSONArray("episodes").length();
            }else if(type.equals("tvbox")){
                keys(r,key,"id","type","title","config","baseUrl","sections","categoryIds");if(!(r.get("config") instanceof JSONObject))throw invalid(key,"config需要TVBox对象");JSONObject config=r.getJSONObject("config");if(config.has("format")||config.has("urls")||config.has("data")||!(config.has("sites")||config.has("lives")))throw invalid(key,"TVBox适配器需单个sites/lives配置，不能嵌套资源包或多仓");
                if(config.optJSONArray("parses")!=null&&config.getJSONArray("parses").length()==0)config.remove("parses");
                String base=r.has("baseUrl")?address(r,"baseUrl",allowHttp):null;CinemaCatalog.Config legacy=CinemaCatalog.config(config.toString(),base,allowHttp);if(!legacy.warehouses.isEmpty())throw invalid(key,"请先选择一个TVBox配置，资源包不嵌套多仓选择");if(!legacy.skipped.isEmpty())throw invalid(key,"含未支持的TVBox项："+legacy.skipped.get(0));
                List<String> sections=sections(r,false);JSONObject categoryIds=categories(r,sections);for(CinemaCatalog.Site site:legacy.sites){site.sections=new ArrayList<>(sections);site.categoryIds=categoryIds;result.sites.add(site);}for(CinemaCatalog.Live live:legacy.lives){result.lives.add(live);channelCount+=live.channels.size();}
            }else throw invalid(key,"不支持的资源类型："+type);
            if(result.sites.size()>12||result.lives.size()>12||channelCount>500||itemCount>200||episodeCount>500)throw invalid("resources","超过12站点/12直播来源/500频道/200节目/500集的上限");
        }
        if(result.json().getBytes(StandardCharsets.UTF_8).length>CinemaCatalog.MAX_BYTES)throw invalid("resources","展开后的配置超过512KB");return result;
    }
    static JSONArray collectionItems(JSONArray input,boolean allowHttp)throws Exception{
        if(input.length()<1||input.length()>200)throw invalid("items","需为1至200项");JSONArray out=new JSONArray();Set<String> ids=new HashSet<>();int total=0;
        for(int i=0;i<input.length();i++){
            JSONObject item=object(input,i,"items");keys(item,"items","id","title","remark","episodes","metadata");String id=id(item,"id",64);if(!ids.add(id))throw invalid("items","节目编号重复");JSONObject normalized=new JSONObject().put("id",id).put("title",string(item,"title",160,true)).put("remark",string(item,"remark",200,false));if(item.has("metadata")){JSONObject m=item.getJSONObject("metadata");keys(m,"metadata","poster","year","genre","area","director","actors","summary");JSONObject meta=new JSONObject();for(String key:new String[]{"poster","year","genre","area","director","actors","summary"})if(m.has(key))meta.put(key,key.equals("poster")?address(m,key,allowHttp):string(m,key,key.equals("summary")?4000:key.equals("actors")?600:key.equals("year")?40:key.equals("director")?300:key.equals("area")?100:160,false));normalized.put("metadata",meta);}JSONArray episodes=array(item,"episodes",1,500),list=new JSONArray();Set<String> titles=new HashSet<>();
            for(int j=0;j<episodes.length();j++){JSONObject episode=object(episodes,j,"episodes");keys(episode,"episodes","title","url","headers");String title=string(episode,"title",120,true);if(!titles.add(title))throw invalid("episodes","同一节目的集名需唯一");if(++total>500)throw invalid("episodes","一个清单最多500集");list.put(new JSONObject().put("title",title).put("url",address(episode,"url",allowHttp)).put("headers",new JSONObject(headers(episode))));}normalized.put("episodes",list);out.put(normalized);
        }return out;
    }
    static void restoreSite(CinemaCatalog.Site site,JSONObject data,boolean allowHttp)throws Exception{
        site.identity=CinemaCatalog.field(data.optString("identity"),200);JSONArray values=data.optJSONArray("sections");if(values!=null&&values.length()>0)site.sections=sections(data,true);site.categoryIds=categories(data,site.sections);
        if(site.type==5){site.items=collectionItems(data.getJSONArray("items"),allowHttp);if(site.sections.size()!=1)throw invalid("sections","内置清单需一个分类");}
    }
    static CinemaCatalog.Page page(CinemaCatalog.Site site,int page,String search,String id)throws Exception{
        if(page<1||page>500)throw invalid("page","分页无效");CinemaCatalog.Page result=new CinemaCatalog.Page();result.page=page;result.pages=1;result.categories.add(new CinemaCatalog.Category(site.sections.get(0),sectionTitle(site.sections.get(0))));List<CinemaCatalog.Item> matches=new ArrayList<>();
        for(int i=0;i<site.items.length();i++){
            JSONObject item=site.items.getJSONObject(i);String title=item.getString("title");if(id!=null&&!id.equals(item.getString("id"))||search!=null&&!title.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))continue;
            List<CinemaCatalog.Line> lines=new ArrayList<>();CinemaCatalog.Line line=new CinemaCatalog.Line("播放");JSONArray episodes=item.getJSONArray("episodes");for(int j=0;j<episodes.length();j++){JSONObject e=episodes.getJSONObject(j);Map<String,String> headers=new LinkedHashMap<>(site.headers);headers.putAll(headers(e));line.episodes.add(new CinemaCatalog.Episode(e.getString("title"),e.getString("url"),headers));}lines.add(line);CinemaCatalog.Item value=new CinemaCatalog.Item(item.getString("id"),title,item.optString("remark"),lines);JSONObject m=item.optJSONObject("metadata");if(m!=null)value.metadata(new JSONObject().put("vod_pic",m.optString("poster")).put("vod_year",m.optString("year")).put("type_name",m.optString("genre")).put("vod_area",m.optString("area")).put("vod_director",m.optString("director")).put("vod_actor",m.optString("actors")).put("vod_content",m.optString("summary")));matches.add(value);
        }
        result.pages=Math.max(1,(matches.size()+59)/60);for(int i=(page-1)*60;i<Math.min(matches.size(),page*60);i++)result.items.add(matches.get(i));return result;
    }
    static boolean shouldApply(CinemaCatalog.Config current,CinemaCatalog.Config next)throws IOException{
        if(!next.packId.isEmpty()&&next.packId.equals(current.packId)){
            if(next.packRevision<current.packRevision)throw new IOException("此资源包修订号较旧，请使用较新版本");
            if(next.packRevision==current.packRevision){if(next.packSha256.equals(current.packSha256))return next.allowHttp!=current.allowHttp;throw new IOException("同一修订号内容不同，请增加revision");}
        }return true;
    }
    static String sectionTitle(String section){return section.equals("variety")?"综艺":section.equals("series")?"电视剧":"电影";}
    static boolean matches(String section,String name){String n=name.toLowerCase(Locale.ROOT);if(section.equals("variety"))return n.contains("综艺")||n.contains("真人秀")||n.contains("脱口秀")||n.contains("晚会")||n.contains("variety");if(section.equals("series"))return n.contains("电视剧")||n.contains("剧集")||n.contains("series");if(section.equals("movies"))return n.contains("电影")||n.contains("movie");return true;}
    static List<CinemaCatalog.Category> categories(CinemaCatalog.Site site,CinemaCatalog.Page page,String view)throws Exception{
        List<CinemaCatalog.Category> matches=new ArrayList<>();JSONArray ids=site.categoryIds.optJSONArray(view);for(CinemaCatalog.Category c:page.categories){boolean selected=ids==null?matches(view,c.name):false;if(ids!=null)for(int i=0;i<ids.length();i++)if(c.id.equals(ids.getString(i)))selected=true;if(selected)matches.add(c);}return matches;
    }
}
