package org.zhixiaoce.aperture;

import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.*;

/** Actual loopback sockets and production parser; image storage is an explicit fixture. */
public final class GalleryUploadHttpTest {
    static int checks,saves;
    static final byte[] PHOTO=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aA1sAAAAASUVORK5CYII=");
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    static String request(String extra,byte[] body,boolean failRefresh,boolean failStorage,boolean allowed,boolean expect)throws Exception{
        ServerSocket server=new ServerSocket(0,4,InetAddress.getLoopbackAddress());String host="127.0.0.1:"+server.getLocalPort();
        Thread worker=new Thread(()->{try{GalleryUploadHttp.serve(server.accept(),host,"fixture",1024,()->allowed,()->"fixture".getBytes(StandardCharsets.US_ASCII),(bytes,valid)->{if(failStorage)throw new IOException("照片格式不支持");if(!Arrays.equals(bytes,PHOTO))throw new AssertionError("binary body differs");saves++;return "fixture-id";},()->{if(failRefresh)throw new IllegalStateException("synthetic UI refresh error");});}catch(IOException error){throw new RuntimeException(error);}});worker.start();
        String reply;try(Socket socket=new Socket("127.0.0.1",server.getLocalPort())){
            socket.setSoTimeout(3000);OutputStream out=socket.getOutputStream();out.write(("POST /upload HTTP/1.1\r\nHost: "+host+"\r\nAuthorization: Bearer fixture\r\n"+extra+"\r\n").getBytes(StandardCharsets.US_ASCII));out.flush();
            if(expect){BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));check(in.readLine().equals("HTTP/1.1 100 Continue")&&in.readLine().isEmpty(),"Expect handshake arrives before body");out.write(body);out.flush();StringBuilder text=new StringBuilder();String line;while((line=in.readLine())!=null)text.append(line).append('\n');reply=text.toString();}
            else{out.write(body);out.flush();reply=new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);}
        }finally{server.close();worker.join(4000);}check(!worker.isAlive(),"request worker terminated");return reply;
    }
    public static void main(String[] args)throws Exception{
        String length="Content-Length: "+PHOTO.length+"\r\n";
        check(request(length,PHOTO,false,false,true,false).startsWith("HTTP/1.1 200"),"length upload accepted");
        ByteArrayOutputStream chunks=new ByteArrayOutputStream();chunks.write((Integer.toHexString(17)+";client=fixture\r\n").getBytes(StandardCharsets.US_ASCII));chunks.write(PHOTO,0,17);chunks.write("\r\n".getBytes(StandardCharsets.US_ASCII));chunks.write((Integer.toHexString(PHOTO.length-17)+"\r\n").getBytes(StandardCharsets.US_ASCII));chunks.write(PHOTO,17,PHOTO.length-17);chunks.write("\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        check(request("Transfer-Encoding: chunked\r\n",chunks.toByteArray(),false,false,true,false).contains("\"saved\":true"),"chunked binary upload receives success JSON");
        check(request(length,PHOTO,true,false,true,false).contains("\"saved\":true"),"UI refresh failure cannot drop acknowledged save");
        check(request(length+"Expect: 100-continue\r\n",PHOTO,false,false,true,true).startsWith("HTTP/1.1 200"),"Expect upload completes");
        int before=saves;check(request(length,PHOTO,false,true,true,false).startsWith("HTTP/1.1 409"),"storage rejection receives JSON response");
        check(request(length,PHOTO,false,false,false,false).startsWith("HTTP/1.1 403"),"revoked session rejects before save");
        check(request(length+"Transfer-Encoding: chunked\r\n",new byte[0],false,false,true,false).startsWith("HTTP/1.1 400"),"ambiguous framing rejected");
        check(request("Content-Length: 2048\r\n",new byte[0],false,false,true,false).startsWith("HTTP/1.1 413"),"oversized length rejected before allocation");
        check(request("Transfer-Encoding: chunked\r\n","FFFFFFF\r\n".getBytes(StandardCharsets.US_ASCII),false,false,true,false).startsWith("HTTP/1.1 413"),"oversized chunk rejected before allocation");
        check(request("Transfer-Encoding: chunked\r\n","invalid\r\n".getBytes(StandardCharsets.US_ASCII),false,false,true,false).startsWith("HTTP/1.1 400"),"bad chunk returns structured error");
        check(request(length+"Origin: https://example.invalid\r\n",PHOTO,false,false,true,false).startsWith("HTTP/1.1 403"),"foreign origin cannot upload");
        check(before==4&&saves==before,"rejections do not mutate storage");
        System.out.println(checks+" production HTTP transport checks passed; binary PNG preserved; Android image store not executed");
    }
}
