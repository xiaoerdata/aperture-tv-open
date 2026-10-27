package org.zhixiaoce.aperture;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

/** Real loopback transport through the production HTTP client; synthetic guide only. */
public final class CinemaGuideHttpTest {
    static int checks;
    interface Run {void run() throws Exception;}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    static void rejects(Run run,String message)throws Exception{boolean failed=false;try{run.run();}catch(Exception expected){failed=true;}check(failed,message);}
    static byte[] gzip(byte[] bytes)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(GZIPOutputStream stream=new GZIPOutputStream(out)){stream.write(bytes);}return out.toByteArray();}
    public static void main(String[] args)throws Exception{
        byte[] xml="<tv><channel id='one'><display-name>测试频道</display-name></channel></tv>".getBytes(StandardCharsets.UTF_8),compressed=gzip(xml),bomb=gzip(new byte[8*1024*1024+1]);
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{String path=exchange.getRequestURI().getPath();if(path.equals("/redirect")){exchange.getResponseHeaders().add("Location","/guide.xml.gz");exchange.sendResponseHeaders(302,-1);}else if(path.equals("/loop")){exchange.getResponseHeaders().add("Location","/loop");exchange.sendResponseHeaders(302,-1);}else{byte[] body=path.equals("/bomb")?bomb:path.equals("/guide.xml.gz")?compressed:path.equals("/broken")?new byte[]{31,(byte)139,1}:path.equals("/large")?new byte[4*1024*1024+1]:xml;exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);}exchange.close();});server.start();
        try{String base="http://127.0.0.1:"+server.getAddress().getPort();CinemaHttp http=new CinemaHttp();
            check(http.xmltv(base+"/guide.xml",true).equals(new String(xml,StandardCharsets.UTF_8)),"plain XML transport");
            check(http.xmltv(base+"/guide.xml.gz",true).equals(new String(xml,StandardCharsets.UTF_8)),"gzip transport");
            check(http.xmltv(base+"/redirect",true).contains("测试频道"),"redirect target transport");
            rejects(()->http.xmltv(base+"/guide.xml",false),"HTTP requires explicit consent");
            rejects(()->http.xmltv(base+"/loop",true),"bounded redirects");
            rejects(()->http.xmltv(base+"/bomb",true),"decoded guide limited to 8MiB");
            rejects(()->http.xmltv(base+"/large",true),"transport limited to 4MiB");
            rejects(()->http.xmltv(base+"/broken",true),"truncated gzip fails");
            System.out.println(checks+" guide HTTP assertions passed");
        }finally{server.stop(0);}
    }
}
