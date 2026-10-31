package org.zhixiaoce.aperture;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** Bounded text/media probes; redirects revalidate policy and drop cross-origin credentials. */
final class CinemaHttp {
    private final OkHttpClient client=new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(6,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS).build();
    private volatile Call active;private volatile long deadline;private volatile int epoch;
    static final class Document {final String text,url;final Map<String,String> headers;Document(String text,String url,Map<String,String> headers){this.text=text;this.url=url;this.headers=headers;}}
    private static final class Body {final byte[] bytes;final String url;final Map<String,String> headers;final String type;Body(byte[] bytes,String url,Map<String,String> headers,String type){this.bytes=bytes;this.url=url;this.headers=new LinkedHashMap<>(headers);this.type=type;}}
    void deadline(long value){deadline=value;}
    void cancel(){epoch++;Call old=active;if(old!=null)old.cancel();}
    private Body bytes(String url,boolean allowHttp,int limit,boolean sample,Map<String,String> supplied)throws Exception {
        return bytes(url,allowHttp,limit,sample,supplied,10);
    }
    private Body bytes(String url,boolean allowHttp,int limit,boolean sample,Map<String,String> supplied,int seconds)throws Exception {
        String valid=CinemaCatalog.url(url,null,allowHttp);Map<String,String> headers=new LinkedHashMap<>(supplied);int turn=epoch;long budget=Math.min(deadline==0?Long.MAX_VALUE:deadline,System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds));
        for(int hop=0;hop<=3;hop++){
            if(turn!=epoch||Thread.currentThread().isInterrupted())throw new IOException("读取已取消");Request.Builder request=new Request.Builder().url(valid).header("User-Agent","Kaishi-TV/0.2");
            for(Map.Entry<String,String> h:headers.entrySet())request.header(h.getKey(),h.getValue());if(sample)request.header("Range","bytes=0-4095");Call call=(seconds>10?client.newBuilder().connectTimeout(seconds,TimeUnit.SECONDS).readTimeout(seconds,TimeUnit.SECONDS).callTimeout(seconds,TimeUnit.SECONDS).build():client).newCall(request.build());call.timeout().timeout(seconds,TimeUnit.SECONDS).deadlineNanoTime(budget);active=call;
            try(Response response=call.execute()){
                if(Arrays.asList(301,302,303,307,308).contains(response.code())){
                    if(hop==3||response.header("Location")==null)throw new IOException("来源重定向过多或无目标");String next=CinemaCatalog.url(response.header("Location"),valid,allowHttp);HttpUrl from=HttpUrl.get(valid),to=HttpUrl.get(next);
                    if(!from.scheme().equals(to.scheme())||!from.host().equals(to.host())||from.port()!=to.port()){Map<String,String> safe=new LinkedHashMap<>();if(headers.containsKey("User-Agent"))safe.put("User-Agent",headers.get("User-Agent"));if(headers.containsKey("Accept"))safe.put("Accept",headers.get("Accept"));headers=safe;}valid=next;continue;
                }
                if(!response.isSuccessful())throw new IOException("来源请求失败（"+response.code()+"）");if(response.body()==null)throw new IOException("来源没有返回内容");if(!sample&&response.body().contentLength()>limit)throw new IOException("来源内容超过限制");
                try(InputStream stream=response.body().byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[4096];int n;while((n=stream.read(buffer,0,Math.min(buffer.length,limit+1-out.size())))!=-1){if(turn!=epoch)throw new IOException("读取已取消");out.write(buffer,0,n);if(out.size()>limit){if(sample)return new Body(Arrays.copyOf(out.toByteArray(),limit),valid,headers,response.header("Content-Type",""));throw new IOException("来源内容超过限制");}if(sample&&out.size()==limit)break;}return new Body(out.toByteArray(),valid,headers,response.header("Content-Type",""));
                }
            }finally{if(active==call)active=null;}
        }throw new IOException("来源重定向失败");
    }
    Document document(String url,boolean allowHttp,Map<String,String> headers)throws Exception{Body b=bytes(url,allowHttp,CinemaCatalog.MAX_BYTES,false,headers);return new Document(new String(b.bytes,StandardCharsets.UTF_8),b.url,b.headers);}
    String xmltv(String url,boolean allowHttp)throws Exception{Body body=bytes(url,allowHttp,4*1024*1024,false,Collections.emptyMap());InputStream raw=new ByteArrayInputStream(body.bytes);if(body.bytes.length>=2&&(body.bytes[0]&255)==31&&(body.bytes[1]&255)==139)raw=new java.util.zip.GZIPInputStream(raw);try(InputStream stream=raw;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1){if(out.size()+n>8*1024*1024)throw new IOException("节目单解压后超过8MiB");out.write(buffer,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    String messages(String url)throws Exception{return new String(bytes(url,false,131072,false,Collections.emptyMap(),15).bytes,StandardCharsets.UTF_8);}
    String text(String url,boolean allowHttp)throws Exception{return document(url,allowHttp,Collections.emptyMap()).text;}
    CinemaCatalog.Config importUrl(String url,boolean allowHttp)throws Exception {
        String checked=CinemaCatalog.url(url,null,allowHttp),path=new java.net.URI(checked).getPath().toLowerCase(Locale.ROOT);
        if(path.matches(".*\\.(mp4|mkv|webm|m4v|mov|ts)$")){CinemaCatalog.Config c=new CinemaCatalog.Config(allowHttp);c.lives.add(new CinemaCatalog.Live("直连媒体","",Arrays.asList(new CinemaCatalog.Channel("播放","直连媒体",Arrays.asList(checked)))));return c;}
        Document d=document(checked,allowHttp,Collections.emptyMap());return CinemaCatalog.config(d.text,d.url,allowHttp);
    }
    List<CinemaCatalog.Channel> live(CinemaCatalog.Live live,boolean allowHttp)throws Exception {if(live.url.isEmpty())return live.channels;Document d=document(live.url,allowHttp,live.headers);if(d.text.trim().startsWith("#EXTM3U")&&d.text.contains("#EXT-X-"))return Arrays.asList(new CinemaCatalog.Channel(live.name,"直播",Arrays.asList(d.url),d.headers));return CinemaCatalog.playlist(d.text,d.url,allowHttp,d.headers);}
    CinemaCatalog.Page home(CinemaCatalog.Site site,boolean allowHttp)throws Exception{if(site.type==5)return CinemaResourcePack.page(site,1,null,null);String endpoint=site.api;if(site.type==4&&!site.ext.isEmpty())endpoint=HttpUrl.get(CinemaCatalog.url(site.api,null,allowHttp)).newBuilder().setQueryParameter("extend",site.ext).build().toString();return CinemaCatalog.page(document(endpoint,allowHttp,site.headers).text,1);}
    CinemaCatalog.Page catalogue(CinemaCatalog.Site site,String category,int page,String search,String id,boolean allowHttp)throws Exception {
        if(site.type==5)return CinemaResourcePack.page(site,page,search,id);
        if(page<1||page>500)throw new IOException("分页范围无效");HttpUrl.Builder url=HttpUrl.get(CinemaCatalog.url(site.api,null,allowHttp)).newBuilder();for(String key:new String[]{"ac","t","pg","wd","ids"})url.removeAllQueryParameters(key);
        if(site.type==4&&!site.ext.isEmpty())url.setQueryParameter("extend",site.ext);url.addQueryParameter("ac",site.type==0?"videolist":"detail");if(category!=null)url.addQueryParameter("t",category);if(id!=null)url.addQueryParameter("ids",id);if(search!=null)url.addQueryParameter("wd",search);url.addQueryParameter("pg",String.valueOf(page));return CinemaCatalog.page(document(url.build().toString(),allowHttp,site.headers).text,page);
    }
    CinemaCatalog.Episode resolve(CinemaCatalog.Site site,String line,CinemaCatalog.Episode episode,boolean allowHttp)throws Exception {
        if(site.type!=4)return episode;
        HttpUrl.Builder endpoint=HttpUrl.get(CinemaCatalog.url(site.api,null,allowHttp)).newBuilder();
        endpoint.setQueryParameter("play",episode.url).setQueryParameter("flag",line);if(!site.ext.isEmpty())endpoint.setQueryParameter("extend",site.ext);
        return resolved(episode,document(endpoint.build().toString(),allowHttp,site.headers).text,allowHttp);
    }
    static CinemaCatalog.Episode resolved(CinemaCatalog.Episode episode,String json,boolean allowHttp)throws Exception {
        org.json.JSONObject value=new org.json.JSONObject(json);
        if(value.optInt("parse",0)!=0||value.optInt("jx",0)!=0)throw new IOException("该线路需要网页解析，请选择直连播放线路");
        Map<String,String> headers=new LinkedHashMap<>(CinemaFormats.headers(value));
        String address=CinemaFormats.stream(value.optString("url"),null,allowHttp,headers);
        return new CinemaCatalog.Episode(episode.name,address,headers);
    }
    CinemaCatalog.Episode media(CinemaCatalog.Episode episode,boolean allowHttp)throws Exception {
        String path=HttpUrl.get(CinemaCatalog.url(episode.url,null,allowHttp)).encodedPath().toLowerCase(Locale.ROOT);
        if(path.startsWith("/share/"))throw new IOException("该线路为分享网页");
        Body body=bytes(episode.url,allowHttp,4096,true,episode.headers,15);
        String text=new String(body.bytes,StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT),type=body.type.toLowerCase(Locale.ROOT);
        if(body.bytes.length==0||type.contains("text/html")||type.contains("application/json")||text.startsWith("<")||text.startsWith("{")||text.startsWith("["))throw new IOException("该线路未返回媒体");
        boolean magic=text.startsWith("#extm3u")||body.bytes.length>=8&&new String(body.bytes,4,4,StandardCharsets.US_ASCII).equals("ftyp")||body.bytes.length>=4&&(body.bytes[0]&255)==0x1a&&(body.bytes[1]&255)==0x45||body.bytes.length>=188&&body.bytes[0]==0x47||text.startsWith("flv");
        if(!magic&&!type.startsWith("video/")&&!type.startsWith("audio/")&&!type.contains("mpegurl")&&!type.contains("octet-stream"))throw new IOException("该线路未返回可识别媒体");
        return new CinemaCatalog.Episode(episode.name,body.url,body.headers);
    }
    String probe(String url,boolean allowHttp)throws Exception{return probe(url,allowHttp,Collections.emptyMap());}
    String probe(String url,boolean allowHttp,Map<String,String> headers)throws Exception {
        byte[] bytes=bytes(url,allowHttp,4096,true,headers).bytes;String sample=new String(bytes,StandardCharsets.UTF_8).trim();
        if(sample.startsWith("#EXTM3U"))return "清单可读取，播放待验证";
        if((bytes.length>=8&&new String(bytes,4,4,StandardCharsets.US_ASCII).equals("ftyp"))||(bytes.length>=4&&(bytes[0]&255)==0x1a&&(bytes[1]&255)==0x45&&(bytes[2]&255)==0xdf&&(bytes[3]&255)==0xa3)||(bytes.length>=188&&bytes[0]==0x47))return "媒体样本可读取，解码待验证";
        throw new IOException("来源没有返回可识别的媒体样本");
    }
}
