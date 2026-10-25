package org.zhixiaoce.aperture;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import org.json.*;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

/** Text protocols only. Includes TVBox TXT/M3U conventions, request metadata and MacCMS XML. */
final class CinemaFormats {
    static String json(String text)throws IOException {
        StringBuilder out=new StringBuilder();boolean string=false,escape=false;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(string){out.append(c);if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')string=false;continue;}if(c=='"'){string=true;out.append(c);continue;}
            if(c=='/'&&i+1<text.length()&&text.charAt(i+1)=='/'){while(i<text.length()&&text.charAt(i)!='\n')i++;out.append('\n');}
            else if(c=='/'&&i+1<text.length()&&text.charAt(i+1)=='*'){int end=text.indexOf("*/",i+2);if(end<0)throw new IOException("配置注释未结束");i=end+1;out.append(' ');}else out.append(c);
        }return out.toString();
    }
    static String headerName(String name)throws IOException {
        for(String allowed:new String[]{"User-Agent","Referer","Origin","Cookie","Authorization","Accept"})if(allowed.equalsIgnoreCase(name))return allowed;
        if(name.matches("(?i)x-[a-z0-9-]{1,60}"))return name;throw new IOException("不支持该请求头");
    }
    static void header(Map<String,String> map,String key,String value)throws IOException {
        String name=headerName(key);if(value==null||value.length()>4096||value.indexOf('\r')>=0||value.indexOf('\n')>=0||value.indexOf('\0')>=0)throw new IOException("请求头无效");map.put(name,value);if(map.size()>12)throw new IOException("请求头超过12项");
    }
    static Map<String,String> headers(JSONObject object)throws IOException {
        Map<String,String> result=new LinkedHashMap<>();JSONObject h=object.optJSONObject("header");if(h==null)h=object.optJSONObject("headers");if(h==null){Object raw=object.has("header")?object.opt("header"):object.opt("headers");if(raw instanceof String&&!((String)raw).isEmpty()){if(((String)raw).length()>65536)throw new IOException("请求头过长");try{h=new JSONObject((String)raw);}catch(JSONException invalid){throw new IOException("请求头JSON格式无效");}}}if(h!=null){Iterator<String> keys=h.keys();while(keys.hasNext()){String key=keys.next();header(result,key,h.optString(key));}}
        if(object.has("ua"))header(result,"User-Agent",object.optString("ua"));if(object.has("referer"))header(result,"Referer",object.optString("referer"));return result;
    }
    static String stream(String raw,String base,boolean allowHttp,Map<String,String> headers)throws IOException {
        int pipe=raw.indexOf('|');String address=pipe<0?raw:raw.substring(0,pipe);
        if(pipe>=0)for(String pair:raw.substring(pipe+1).split("&")){int equals=pair.indexOf('=');if(equals<1)throw new IOException("媒体请求头格式无效");try{header(headers,java.net.URLDecoder.decode(pair.substring(0,equals),"UTF-8"),java.net.URLDecoder.decode(pair.substring(equals+1),"UTF-8"));}catch(IllegalArgumentException bad){throw new IOException("媒体请求头编码无效");}}
        return CinemaCatalog.url(address,base,allowHttp);
    }
    private static int comma(String text){char quote=0;for(int i=0;i<text.length();i++){char c=text.charAt(i);if(quote!=0){if(c==quote)quote=0;}else if(c=='\"'||c=='\'')quote=c;else if(c==',')return i;}return -1;}
    static List<CinemaCatalog.Channel> playlist(String raw,String base,boolean allowHttp,Map<String,String> defaults)throws IOException {
        if(raw==null||raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>CinemaCatalog.MAX_BYTES)throw new IOException("直播清单超过512KB");
        String text=raw.replaceFirst("^\\uFEFF","").trim();boolean m3u=text.startsWith("#EXTM3U");if(m3u&&text.contains("#EXT-X-"))throw new IOException("HLS媒体清单应作为单个频道导入");
        List<CinemaCatalog.Channel> result=new ArrayList<>();String name="",group="直播";Map<String,String> headers=new LinkedHashMap<>(defaults);int rows=0;
        for(String line:text.split("\\r?\\n")){String v=line.trim();if(v.length()>16384)throw new IOException("清单行过长");if(v.isEmpty())continue;
            if(m3u&&v.startsWith("#EXTINF:")){int comma=comma(v);name=CinemaCatalog.field(comma<0?"频道":v.substring(comma+1),120);java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("group-title=[\"']([^\"']*)[\"']").matcher(v);group=matcher.find()?CinemaCatalog.field(matcher.group(1),80):"直播";headers=new LinkedHashMap<>(defaults);continue;}
            if(v.startsWith("#EXTVLCOPT:http-user-agent=")){header(headers,"User-Agent",v.substring("#EXTVLCOPT:http-user-agent=".length()));continue;}
            if(v.startsWith("#EXTVLCOPT:http-referrer=")){header(headers,"Referer",v.substring("#EXTVLCOPT:http-referrer=".length()));continue;}
            if(v.startsWith("#"))continue;
            if(!m3u){int comma=v.indexOf(',');if(comma<1)continue;name=CinemaCatalog.field(v.substring(0,comma),120);v=v.substring(comma+1).trim();if(v.equalsIgnoreCase("#genre#")){group=name;continue;}headers=new LinkedHashMap<>(defaults);}
            if(++rows>CinemaCatalog.MAX_CHANNELS)throw new IOException("频道超过500项");List<String> urls=new ArrayList<>();List<Map<String,String>> requests=new ArrayList<>();
            for(String stream:m3u?new String[]{v}:v.split("#")){if(urls.size()>=8)break;try{int alias=stream.lastIndexOf('$');String value=alias<0?stream:stream.substring(0,alias);Map<String,String> specific=new LinkedHashMap<>(headers);urls.add(stream(value,base,allowHttp,specific));requests.add(specific);}catch(IOException invalid){}}
            if(!urls.isEmpty())result.add(new CinemaCatalog.Channel(name.isEmpty()?"频道 "+rows:name,group,urls,new LinkedHashMap<>(headers),requests));name="";
        }
        if(result.isEmpty())throw new IOException("清单中没有可用的 HTTP(S) 频道");return result;
    }
    private static String value(Element node,String tag){NodeList list=node.getElementsByTagName(tag);return list.getLength()>0?list.item(0).getTextContent():"";}
    static CinemaCatalog.Page xml(String raw,int requested)throws Exception {
        if(raw.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")||raw.toUpperCase(Locale.ROOT).contains("<!ENTITY"))throw new IOException("XML不允许外部实体或DTD");
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setExpandEntityReferences(false);factory.setNamespaceAware(false);
        javax.xml.parsers.DocumentBuilder builder=factory.newDocumentBuilder();builder.setEntityResolver((publicId,systemId)->{throw new org.xml.sax.SAXException("External entity blocked");});
        Element root=builder.parse(new InputSource(new StringReader(raw))).getDocumentElement();if(!"rss".equalsIgnoreCase(root.getTagName()))throw new IOException("不是XML采集目录");
        JSONObject data=new JSONObject();JSONArray classes=new JSONArray(),videos=new JSONArray();NodeList types=root.getElementsByTagName("ty");
        for(int i=0;i<Math.min(100,types.getLength());i++){Element t=(Element)types.item(i);classes.put(new JSONObject().put("type_id",t.getAttribute("id")).put("type_name",t.getTextContent()));}
        NodeList list=root.getElementsByTagName("list");if(list.getLength()==0&&types.getLength()==0)throw new IOException("XML缺少目录");if(list.getLength()>0){Element l=(Element)list.item(0);data.put("page",l.getAttribute("page")).put("pagecount",l.getAttribute("pagecount"));}
        NodeList entries=root.getElementsByTagName("video");for(int i=0;i<Math.min(120,entries.getLength());i++){Element v=(Element)entries.item(i);List<String> flags=new ArrayList<>(),urls=new ArrayList<>();NodeList lines=v.getElementsByTagName("dd");for(int j=0;j<lines.getLength();j++){Element line=(Element)lines.item(j);flags.add(line.getAttribute("flag"));urls.add(line.getTextContent());}
            videos.put(new JSONObject().put("vod_id",value(v,"id")).put("vod_name",value(v,"name")).put("vod_remarks",value(v,"note")).put("vod_play_from",String.join("$$$",flags)).put("vod_play_url",String.join("$$$",urls)));}
        return CinemaCatalog.page(data.put("class",classes).put("list",videos).toString(),requested);
    }
}
