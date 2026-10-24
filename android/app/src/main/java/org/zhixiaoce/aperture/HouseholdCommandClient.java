package org.zhixiaoce.aperture;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;
import org.json.JSONObject;

/** Fixed setup endpoints, native cookies, trusted HTTPS. No redirects, custom certificates or JS bridge. */
final class HouseholdCommandClient {
    private final URI origin;
    private final String cookie;
    private volatile HttpURLConnection pending;
    HouseholdCommandClient(String address,String cookies,boolean debug)throws Exception{
        URI value=new URI(address);
        boolean loopback="http".equals(value.getScheme())&&debug&&("127.0.0.1".equals(value.getHost())||"localhost".equals(value.getHost())||"[::1]".equals(value.getHost()));
        if(!("https".equals(value.getScheme())||loopback)||value.getHost()==null||value.getRawUserInfo()!=null||value.getRawQuery()!=null||value.getRawFragment()!=null||!(value.getRawPath().isEmpty()||"/".equals(value.getRawPath()))||value.getPort()==0||value.getPort()>65535||LobbyAssets.HOST.equalsIgnoreCase(value.getHost()))throw new IllegalArgumentException("请先连接可信HTTPS家庭服务；局域网HTTP不用于交接NAS凭证。");
        origin=value;
        Matcher match=Pattern.compile("(?:^|;\\s*)aperture=([A-Za-z0-9_-]{16,200})(?:;|$)").matcher(cookies==null?"":cookies);cookie=match.find()?"aperture="+match.group(1):"";
        if(cookie.isEmpty())throw new IllegalArgumentException("请先在家庭窗景完成这台电视的只读配对。");
    }
    void interrupt(){HttpURLConnection current=pending;if(current!=null)current.disconnect();}
    JSONObject call(String area,String operation,JSONObject input)throws Exception{
        if(!("nas-setup".equals(area)||"app-operations".equals(area))||!("start".equals(operation)||"poll".equals(operation)||"finish".equals(operation)||"stage".equals(operation)))throw new IllegalArgumentException("请求路径无效");
        HttpURLConnection connection=(HttpURLConnection)origin.resolve("/api/"+area+"/"+operation).toURL().openConnection();pending=connection;
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        try{
            connection.setConnectTimeout(3000);connection.setReadTimeout(5000);connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);connection.setRequestMethod("POST");connection.setDoOutput(true);
            connection.setRequestProperty("Cookie",cookie);connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("Accept-Encoding","identity");
            byte[] body=input.toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(body.length);
            try(OutputStream stream=connection.getOutputStream()){stream.write(body);}
            if(connection.getResponseCode()!=200)throw new IOException("家庭连接、授权或交接已失效，请重新发起。");
            String type=connection.getContentType();if(type==null||!type.split(";",2)[0].trim().equalsIgnoreCase("application/json")||connection.getContentLengthLong()>131072)throw new IOException("交接响应无效");
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();try(InputStream stream=connection.getInputStream()){byte[] chunk=new byte[4096];int count;while(true){long remaining=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime());if(remaining<=0)throw new IOException("交接读取超时");connection.setReadTimeout((int)Math.min(1500,remaining));count=stream.read(chunk);if(count<0)break;if(buffer.size()+count>131072)throw new IOException("交接响应过大");buffer.write(chunk,0,count);}}
            return new JSONObject(buffer.toString(StandardCharsets.UTF_8.name()));
        }finally{if(pending==connection)pending=null;connection.disconnect();}
    }
}
