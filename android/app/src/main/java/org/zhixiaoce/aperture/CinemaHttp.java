package org.zhixiaoce.aperture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Bounded foreground catalogue requests; redirects and plugin protocols are never followed. */
final class CinemaHttp {
    private final OkHttpClient client=new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(6,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS).build();
    private volatile Call active;
    private volatile long deadline;
    void deadline(long value){deadline=value;}
    void cancel(){Call old=active;if(old!=null)old.cancel();}
    private byte[] bytes(String url,boolean allowHttp,int limit,boolean sample) throws Exception {
        String valid=CinemaCatalog.url(url,null,allowHttp);Request.Builder request=new Request.Builder().url(valid).header("User-Agent","Kaishi-TV/0.2");
        if(sample)request.header("Range","bytes=0-4095");Call call=client.newCall(request.build());if(deadline!=0)call.timeout().deadlineNanoTime(deadline);active=call;
        try(Response response=call.execute()){
            if(!response.isSuccessful())throw new IOException("来源请求失败（"+response.code()+"）");
            if(response.body()==null)throw new IOException("来源没有返回内容");
            if(!sample&&response.body().contentLength()>limit)throw new IOException("来源内容超过限制");
            try(InputStream stream=response.body().byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;
                while((n=stream.read(buffer,0,Math.min(buffer.length,limit+1-out.size())))!=-1){out.write(buffer,0,n);if(out.size()>limit){if(sample)return java.util.Arrays.copyOf(out.toByteArray(),limit);throw new IOException("来源内容超过限制");}if(sample&&out.size()==limit)break;}
                return out.toByteArray();
            }
        }finally{if(active==call)active=null;}
    }
    String text(String url,boolean allowHttp) throws Exception {return new String(bytes(url,allowHttp,CinemaCatalog.MAX_BYTES,false),StandardCharsets.UTF_8);}
    CinemaCatalog.Page home(CinemaCatalog.Site site,boolean allowHttp) throws Exception {return CinemaCatalog.page(text(site.api,allowHttp),1);}
    CinemaCatalog.Page catalogue(CinemaCatalog.Site site,String category,int page,String search,String id,boolean allowHttp) throws Exception {
        if(page<1||page>500)throw new IOException("分页范围无效");
        HttpUrl.Builder url=HttpUrl.get(CinemaCatalog.url(site.api,null,allowHttp)).newBuilder();
        for(String key:new String[]{"ac","t","pg","wd","ids"})url.removeAllQueryParameters(key);
        url.addQueryParameter("ac","detail");if(category!=null)url.addQueryParameter("t",category);if(id!=null)url.addQueryParameter("ids",id);if(search!=null)url.addQueryParameter("wd",search);url.addQueryParameter("pg",String.valueOf(page));
        return CinemaCatalog.page(text(url.build().toString(),allowHttp),page);
    }
    String probe(String url,boolean allowHttp) throws Exception {
        byte[] bytes=bytes(url,allowHttp,4096,true);String sample=new String(bytes,StandardCharsets.UTF_8).trim();
        if(sample.startsWith("#EXTM3U"))return "清单可读取，播放待验证";
        if((bytes.length>=8&&new String(bytes,4,4,StandardCharsets.US_ASCII).equals("ftyp"))||(bytes.length>=4&&(bytes[0]&255)==0x1a&&(bytes[1]&255)==0x45&&(bytes[2]&255)==0xdf&&(bytes[3]&255)==0xa3)||(bytes.length>=188&&bytes[0]==0x47))return "媒体样本可读取，解码待验证";
        throw new IOException("来源没有返回可识别的媒体样本");
    }
}
