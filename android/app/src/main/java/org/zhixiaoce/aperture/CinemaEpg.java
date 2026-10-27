package org.zhixiaoce.aperture;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/** Bounded XMLTV data. Channel IDs are explicit; display-name matching is a fallback only. */
final class CinemaEpg {
    static final class Programme {final String channel,title;final long start,stop;Programme(String c,String t,long s,long e){channel=c;title=t;start=s;stop=e;}}
    final Map<String,List<Programme>> channels=new LinkedHashMap<>();final Map<String,String> names=new HashMap<>();
    static long instant(String value)throws Exception{String v=value.trim();if(!v.matches("[0-9]{14}(?: [+-][0-9]{4})?"))throw new IOException("XMLTV时间格式无效");SimpleDateFormat f=new SimpleDateFormat(v.length()>14?"yyyyMMddHHmmss Z":"yyyyMMddHHmmss",Locale.ROOT);f.setLenient(false);f.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));return f.parse(v).getTime();}
    static String name(String value){return value.toUpperCase(Locale.ROOT).replaceAll("\\s+","").replaceFirst("^(CCTV[0-9]+\\+?).*$","$1");}
    static CinemaEpg read(String xml,long now)throws Exception{
        if(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>8*1024*1024||xml.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")||xml.toUpperCase(Locale.ROOT).contains("<!ENTITY"))throw new IOException("节目单过大或含外部实体");
        CinemaEpg guide=new CinemaEpg();SAXParserFactory factory=SAXParserFactory.newInstance();factory.setNamespaceAware(false);javax.xml.parsers.SAXParser parser=factory.newSAXParser();XMLReader reader=parser.getXMLReader();reader.setEntityResolver((p,s)->{throw new SAXException("XMLTV external entity blocked");});
        reader.setContentHandler(new DefaultHandler(){String channel="",id="",tag="";long start,stop;int count,depth;boolean tv;StringBuilder text=new StringBuilder();
            public void startElement(String uri,String local,String q,Attributes a)throws SAXException{if(++depth>16)throw new SAXException("节目单嵌套过深");if(depth==1){if(!"tv".equals(q))throw new SAXException("不是XMLTV节目单");tv=true;}tag=q;text.setLength(0);if(q.equals("channel")){id=limit(a.getValue("id"),128);if(guide.channels.size()>=500&&!guide.channels.containsKey(id))throw new SAXException("节目单超过500频道");guide.channels.putIfAbsent(id,new ArrayList<>());}if(q.equals("programme")){if(++count>30000)throw new SAXException("节目单超过30000项");channel=limit(a.getValue("channel"),128);try{start=instant(a.getValue("start"));stop=instant(a.getValue("stop"));}catch(Exception e){start=stop=0;}}}
            public void characters(char[] c,int at,int n)throws SAXException{if(text.length()+n>8192)throw new SAXException("节目单字段过长");text.append(c,at,n);}
            public void endElement(String uri,String local,String q)throws SAXException{if(q.equals("display-name")&&!id.isEmpty()&&guide.names.size()<2000)guide.names.putIfAbsent(name(text.toString()),id);if(q.equals("title")&&!channel.isEmpty()&&stop>start&&stop-start<=86400000L&&stop>now-6*3600000L&&start<now+60*3600000L){String title=limit(text.toString().trim(),200);List<Programme> list=guide.channels.get(channel);if(list==null){if(guide.channels.size()>=500)throw new SAXException("节目单超过500频道");list=new ArrayList<>();guide.channels.put(channel,list);}if(!title.isEmpty())list.add(new Programme(channel,title,start,stop));}if(q.equals("channel"))id="";if(q.equals("programme"))channel="";depth--;text.setLength(0);}
        });reader.parse(new InputSource(new StringReader(xml)));for(List<Programme> rows:guide.channels.values())rows.sort(Comparator.comparingLong(p->p.start));return guide;
    }
    private static String limit(String value,int max)throws SAXException{if(value==null||value.length()>max)throw new SAXException("节目单字段无效");return value;}
    List<Programme> programmes(CinemaCatalog.Channel channel){String id=channel.epgId.isEmpty()?names.get(name(channel.name)):channel.epgId;List<Programme> rows=channels.get(id);return rows==null?Collections.emptyList():rows;}
    String nowNext(CinemaCatalog.Channel channel,long now){Programme current=null,next=null;for(Programme p:programmes(channel)){if(p.start<=now&&p.stop>now)current=p;else if(p.start>now){next=p;break;}}return (current==null?"":"正在播  "+current.title)+(next==null?"":(current==null?"":"\n")+"接下来  "+next.title);}
    static CinemaEpg merge(List<CinemaEpg> guides){CinemaEpg out=new CinemaEpg();for(CinemaEpg g:guides){for(Map.Entry<String,List<Programme>> e:g.channels.entrySet())out.channels.putIfAbsent(e.getKey(),e.getValue());for(Map.Entry<String,String> e:g.names.entrySet())out.names.putIfAbsent(e.getKey(),e.getValue());}return out;}
}
