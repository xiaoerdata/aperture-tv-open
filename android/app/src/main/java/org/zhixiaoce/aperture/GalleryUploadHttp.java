package org.zhixiaoce.aperture;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Bounded HTTP upload transport. Android owns the photo store and foreground authorization. */
final class GalleryUploadHttp {
    interface Page {byte[] read() throws Exception;}
    interface Photos {String save(byte[] bytes,BooleanSupplier authorized) throws Exception;}
    private static final class Problem extends IOException {final int status;Problem(int status,String text){super(text);this.status=status;}}
    static void serve(Socket socket,String host,String token,int maxBytes,BooleanSupplier valid,Page page,Photos photos,Runnable changed){
        try(Socket client=socket){
            try{
                client.setSoTimeout(4000);InputStream in=new BufferedInputStream(client.getInputStream(),16384);
                String[] request=line(in).split(" ");if(request.length!=3||!request[2].matches("HTTP/1\\.[01]"))throw new Problem(400,"请求格式无效");
                Map<String,String> headers=new HashMap<>();int headerBytes=0;
                for(int n=0;n<32;n++){
                    String text=line(in);headerBytes+=text.length();if(headerBytes>32768)throw new Problem(431,"请求头过长");if(text.isEmpty())break;
                    int at=text.indexOf(':');if(at<1||headers.put(text.substring(0,at).toLowerCase(Locale.ROOT),text.substring(at+1).trim())!=null||n==31)throw new Problem(400,"请求头无效");
                }
                if(!valid.getAsBoolean())throw new Problem(403,"上传已关闭，请重新扫描电视二维码");
                if(!host.equals(headers.get("host"))||headers.containsKey("forwarded")||headers.containsKey("x-forwarded-for"))throw new Problem(403,"上传地址无效，请重新扫码");
                String origin=headers.get("origin");if(origin!=null&&!origin.equals("http://"+host))throw new Problem(403,"请在扫码打开的原页面上传");
                if(request[0].equals("GET")&&request[1].equals("/")){reply(client,200,"text/html; charset=utf-8",page.read());return;}
                if(!request[0].equals("POST")||!request[1].equals("/upload")||!("Bearer "+token).equals(headers.get("authorization")))throw new Problem(403,"请重新扫描电视上的二维码");
                boolean chunked=headers.containsKey("transfer-encoding");int length=0;
                if(chunked){if(headers.containsKey("content-length")||!"chunked".equalsIgnoreCase(headers.get("transfer-encoding")))throw new Problem(400,"不支持此上传传输格式");}
                else{try{String value=headers.getOrDefault("content-length","");if(!value.matches("[0-9]+"))throw new NumberFormatException();length=Integer.parseInt(value);}catch(NumberFormatException bad){throw new Problem(400,"上传缺少有效文件长度");}if(length<=0||length>maxBytes)throw new Problem(413,"单张照片上限20MB");}
                String expect=headers.get("expect");if(expect!=null){if(!"100-continue".equalsIgnoreCase(expect))throw new Problem(417,"不支持此上传握手");client.getOutputStream().write("HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.US_ASCII));client.getOutputStream().flush();}
                client.setSoTimeout(30000);long deadline=System.nanoTime()+90000000000L;
                byte[] bytes=chunked?chunks(in,maxBytes,deadline,valid):exact(in,length,deadline,valid);
                if(!valid.getAsBoolean())throw new Problem(403,"上传已关闭，请重新扫码");
                final String id;
                try{id=photos.save(bytes,valid);}catch(IOException bad){throw new Problem(409,bad.getMessage()==null?"照片未能保存，请重试":bad.getMessage());}
                // Commit and acknowledge before optional UI refresh. A refresh error must not
                // turn a successfully stored photograph into a browser network failure.
                json(client,200,new JSONObject().put("saved",true).put("id",id));
                try{changed.run();}catch(RuntimeException unavailable){}
            }catch(Problem bad){error(client,bad.status,bad.getMessage());}
            catch(SocketTimeoutException timeout){error(client,408,"上传连接超时，请保持手机联网后重试");}
            catch(EOFException interrupted){error(client,400,"照片传输中断，请重新上传");}
            catch(OutOfMemoryError memory){error(client,503,"电视内存不足，请一次上传一张较小照片");}
            catch(Exception failure){error(client,500,"电视暂时未能处理照片，请重试");}
        }catch(IOException disconnected){/* Peer already closed; never log credentials or photo bytes. */}
    }
    private static String line(InputStream in)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();for(int n=0;n<4096;n++){int c=in.read();if(c<0)throw new EOFException();if(c==10){byte[] b=bytes.toByteArray();int size=b.length;if(size>0&&b[size-1]==13)size--;return new String(b,0,size,StandardCharsets.US_ASCII);}bytes.write(c);}throw new Problem(431,"请求头过长");
    }
    private static byte[] exact(InputStream in,int length,long deadline,BooleanSupplier valid)throws IOException{
        byte[] body=new byte[length];int at=0;
        while(at<length){if(!valid.getAsBoolean())throw new Problem(403,"上传已关闭，请重新扫码");if(System.nanoTime()>deadline)throw new Problem(408,"照片上传用时过长，请重试");int n=in.read(body,at,length-at);if(n<0)throw new EOFException();at+=n;}return body;
    }
    private static byte[] chunks(InputStream in,int limit,long deadline,BooleanSupplier valid)throws IOException{
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        for(;;){
            if(System.nanoTime()>deadline)throw new Problem(408,"照片上传用时过长，请重试");
            String text=line(in).split(";",2)[0].trim();if(!text.matches("[0-9A-Fa-f]{1,8}"))throw new Problem(400,"照片分块格式无效");long size=Long.parseLong(text,16);
            if(size==0){int total=0;for(int i=0;i<16;i++){String trailer=line(in);total+=trailer.length();if(total>4096)throw new Problem(400,"分块附加信息过长");if(trailer.isEmpty())return body.toByteArray();if(i==15)throw new Problem(400,"分块附加信息过长");}}
            if(size>limit-body.size())throw new Problem(413,"单张照片上限20MB");body.write(exact(in,(int)size,deadline,valid));
            if(in.read()!=13||in.read()!=10)throw new Problem(400,"照片分块结束符无效");
        }
    }
    private static void error(Socket socket,int status,String text)throws IOException{try{json(socket,status,new JSONObject().put("error",text));}catch(org.json.JSONException impossible){throw new IOException(impossible);}}
    private static void json(Socket socket,int status,JSONObject value)throws IOException{reply(socket,status,"application/json; charset=utf-8",value.toString().getBytes(StandardCharsets.UTF_8));}
    private static void reply(Socket socket,int status,String type,byte[] body)throws IOException{
        OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 "+status+" Reply\r\nContent-Type: "+type+"\r\nContent-Length: "+body.length+"\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src blob:; frame-ancestors 'none'; form-action 'none'; base-uri 'none'\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();
    }
}
