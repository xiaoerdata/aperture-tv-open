package org.zhixiaoce.aperture;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises the production transport limit. Synthetic storage does not decode images. */
public final class GalleryUploadLimitTest {
    private static final int LIMIT=20*1024*1024;
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static void request(int bytes,boolean chunked,boolean accepted)throws Exception{
        AtomicInteger saved=new AtomicInteger();
        try(ServerSocket server=new ServerSocket(0,4,InetAddress.getLoopbackAddress())){
            String host="127.0.0.1:"+server.getLocalPort();
            Thread worker=new Thread(()->{try{GalleryUploadHttp.serve(server.accept(),host,"fixture",LIMIT,()->true,()->new byte[0],(body,valid)->{check(body.length==bytes,"complete body reaches storage");for(byte b:body)if(b!=37)throw new AssertionError("body corrupted");saved.incrementAndGet();return "fixture";},()->{});}catch(IOException e){throw new RuntimeException(e);}});
            worker.start();
            try(Socket socket=new Socket("127.0.0.1",server.getLocalPort())){
                socket.setSoTimeout(15000);OutputStream out=socket.getOutputStream();
                out.write(("POST /upload HTTP/1.1\r\nHost: "+host+"\r\nAuthorization: Bearer fixture\r\n"+(chunked?"Transfer-Encoding: chunked":"Content-Length: "+bytes)+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                // Oversize framing is rejected before any body allocation or transmission.
                if(!accepted&&chunked)out.write((Integer.toHexString(bytes)+"\r\n").getBytes(StandardCharsets.US_ASCII));
                if(accepted){byte[] block=new byte[65536];java.util.Arrays.fill(block,(byte)37);for(int left=bytes;left>0;){int n=Math.min(block.length,left);if(chunked)out.write((Integer.toHexString(n)+"\r\n").getBytes(StandardCharsets.US_ASCII));out.write(block,0,n);if(chunked)out.write("\r\n".getBytes(StandardCharsets.US_ASCII));left-=n;}if(chunked)out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));}
                out.flush();String reply=new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);worker.join(15000);
                check(!worker.isAlive(),"worker completed");check(reply.startsWith("HTTP/1.1 "+(accepted?"200":"413")),"correct response at boundary");check(saved.get()==(accepted?1:0),"oversize request never saved");
            }
        }
    }
    public static void main(String[] args)throws Exception{
        request(13*1024*1024,true,true);request(LIMIT,false,true);request(LIMIT,true,true);request(LIMIT+1,false,false);request(LIMIT+1,true,false);
        System.out.println(checks+" upload boundary assertions passed; 13/20 MiB accepted, 20 MiB plus one rejected; synthetic image store");
    }
}
