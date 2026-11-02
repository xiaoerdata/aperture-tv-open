package org.zhixiaoce.aperture;

import android.content.Context;
import android.os.SystemClock;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;import java.util.concurrent.*;

/** Explicit ten-minute, upload-only LAN session. The QR secret stays in the URL fragment. */
final class GalleryUpload implements GalleryUploadSession {
    private final Context context;private final GalleryStore store;private final Runnable changed;private volatile ServerSocket server;private final ExecutorService worker=Executors.newSingleThreadExecutor();private String token,host;private long expires;private volatile boolean closed;private volatile java.util.function.BooleanSupplier authorized=()->true;private final Set<Socket> clients=Collections.synchronizedSet(new HashSet<>());
    GalleryUpload(Context context,Runnable changed){this.context=context;store=new GalleryStore(context);this.changed=changed;}
    synchronized void authorize(java.util.function.BooleanSupplier allowed){if(server!=null||closed)throw new IllegalStateException("Session already used");authorized=allowed;}
    public synchronized String start()throws Exception{
        if(closed||!authorized.getAsBoolean())throw new IOException("上传已关闭");
        String ip=address();if(ip==null)throw new IOException("电视尚未连接可用的家庭局域网");byte[] secret=new byte[24];new SecureRandom().nextBytes(secret);token=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);if(closed||!authorized.getAsBoolean())throw new IOException("上传已关闭");server=new ServerSocket(0,2,InetAddress.getByName(ip));if(closed||!authorized.getAsBoolean()){close();throw new IOException("上传已关闭");}server.setSoTimeout(1000);host=ip+":"+server.getLocalPort();expires=SystemClock.elapsedRealtime()+600000;
        Thread thread=new Thread(()->{while(valid())try{Socket socket=server.accept();if(!TvLocalControl.privatePeer(socket.getInetAddress())){socket.close();continue;}if(clients.size()>=2){socket.close();continue;}clients.add(socket);socket.setSoTimeout(12000);worker.execute(()->serve(socket));}catch(SocketTimeoutException tick){}catch(Exception stop){break;}close();},"Kaishi-album-upload");thread.setDaemon(true);thread.start();return "http://"+host+"/#"+token;
    }
    public boolean isClosed(){return closed;}
    private boolean valid(){return !closed&&authorized.getAsBoolean()&&server!=null&&!server.isClosed()&&SystemClock.elapsedRealtime()<expires;}
    private String address()throws Exception{List<String> fallback=new ArrayList<>();for(NetworkInterface net:Collections.list(NetworkInterface.getNetworkInterfaces())){if(!net.isUp()||net.isLoopback())continue;for(InetAddress ip:Collections.list(net.getInetAddresses()))if(ip instanceof Inet4Address&&ip.isSiteLocalAddress()){if(net.getName().startsWith("wlan")||net.getName().startsWith("eth"))return ip.getHostAddress();if(!net.getName().startsWith("tun"))fallback.add(ip.getHostAddress());}}return fallback.isEmpty()?null:fallback.get(0);}
    private String line(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int i=0;i<4096;i++){int c=in.read();if(c<0)throw new EOFException();if(c==10)return out.toString("US-ASCII").replaceFirst("\\r$","");out.write(c);}throw new IOException();}
    private void serve(Socket socket){try(Socket client=socket){InputStream in=client.getInputStream();String[] request=line(in).split(" ");if(request.length!=3)throw new IOException();Map<String,String> headers=new HashMap<>();for(int n=0;n<32;n++){String l=line(in);if(l.isEmpty())break;int at=l.indexOf(':');if(at<1||headers.put(l.substring(0,at).toLowerCase(Locale.ROOT),l.substring(at+1).trim())!=null)throw new IOException();if(n==31)throw new IOException();}
        if(!valid()||!host.equals(headers.get("host"))||headers.containsKey("transfer-encoding")||headers.containsKey("forwarded")||headers.containsKey("x-forwarded-for"))throw new IOException();String origin=headers.get("origin");if(origin!=null&&!origin.equals("http://"+host))throw new IOException();
        if(request[0].equals("GET")&&request[1].equals("/")){try(InputStream page=context.getAssets().open("gallery-upload.html")){reply(client,200,"text/html; charset=utf-8",read(page,128*1024));}return;}
        if(!request[0].equals("POST")||!request[1].equals("/upload")||!("Bearer "+token).equals(headers.get("authorization"))){reply(client,403,"application/json","{\"error\":\"请重新扫描电视上的二维码\"}".getBytes(StandardCharsets.UTF_8));return;}
        int length=Integer.parseInt(headers.getOrDefault("content-length","0"));if(length<=0||length>GalleryStore.MAX_UPLOAD){reply(client,413,"application/json","{\"error\":\"单张照片上限12MB\"}".getBytes(StandardCharsets.UTF_8));return;}
        byte[] bytes=new byte[length];new DataInputStream(in).readFully(bytes);
        try{String id=store.add(bytes,this::valid);changed.run();reply(client,200,"application/json",("{\"saved\":true,\"id\":\""+id+"\"}").getBytes(StandardCharsets.UTF_8));}catch(IOException bad){reply(client,409,"application/json",new org.json.JSONObject().put("error",bad.getMessage()).toString().getBytes(StandardCharsets.UTF_8));}
    }catch(Exception ignored){}finally{clients.remove(socket);}}
    private byte[] read(InputStream in,int max)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException();out.write(b,0,n);}return out.toByteArray();}
    private void reply(Socket socket,int status,String type,byte[] body)throws IOException{OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 "+status+" Reply\r\nContent-Type: "+type+"\r\nContent-Length: "+body.length+"\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src blob:; frame-ancestors 'none'; form-action 'none'\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();}
    public void close(){closed=true;synchronized(this){ServerSocket old=server;server=null;if(old!=null)try{old.close();}catch(IOException ignored){}}synchronized(clients){for(Socket c:clients)try{c.close();}catch(IOException ignored){}}worker.shutdownNow();}
}
