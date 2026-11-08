package org.zhixiaoce.aperture;

import android.content.Context;
import android.os.SystemClock;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;import java.util.concurrent.*;

/** Explicit ten-minute, upload-only LAN session. The QR secret stays in the URL fragment. */
final class GalleryUpload implements GalleryUploadSession {
    private final Context context;private final GalleryStore store;private final Runnable changed;private volatile ServerSocket server;private final ExecutorService worker=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4));private String token,host;private long expires;private volatile boolean closed;private volatile java.util.function.BooleanSupplier authorized=()->true;private final Set<Socket> clients=Collections.synchronizedSet(new HashSet<>());
    GalleryUpload(Context context,Runnable changed){this.context=context;store=new GalleryStore(context);this.changed=changed;}
    synchronized void authorize(java.util.function.BooleanSupplier allowed){if(server!=null||closed)throw new IllegalStateException("Session already used");authorized=allowed;}
    public synchronized String start()throws Exception{
        if(closed||!authorized.getAsBoolean())throw new IOException("上传已关闭");
        String ip=address();if(ip==null)throw new IOException("电视尚未连接可用的家庭局域网");byte[] secret=new byte[24];new SecureRandom().nextBytes(secret);token=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);if(closed||!authorized.getAsBoolean())throw new IOException("上传已关闭");server=new ServerSocket(0,8,InetAddress.getByName(ip));if(closed||!authorized.getAsBoolean()){close();throw new IOException("上传已关闭");}server.setSoTimeout(1000);host=ip+":"+server.getLocalPort();expires=SystemClock.elapsedRealtime()+600000;
        Thread thread=new Thread(()->{while(valid())try{Socket socket=server.accept();if(!TvLocalControl.privatePeer(socket.getInetAddress())){socket.close();continue;}if(clients.size()>=6){socket.close();continue;}clients.add(socket);socket.setSoTimeout(12000);worker.execute(()->serve(socket));}catch(SocketTimeoutException tick){}catch(Exception stop){break;}close();},"Kaishi-album-upload");thread.setDaemon(true);thread.start();return "http://"+host+"/#"+token;
    }
    public boolean isClosed(){return closed;}
    private boolean valid(){ServerSocket current=server;return !closed&&authorized.getAsBoolean()&&current!=null&&!current.isClosed()&&SystemClock.elapsedRealtime()<expires;}
    private String address()throws Exception{List<String> fallback=new ArrayList<>();for(NetworkInterface net:Collections.list(NetworkInterface.getNetworkInterfaces())){if(!net.isUp()||net.isLoopback())continue;for(InetAddress ip:Collections.list(net.getInetAddresses()))if(ip instanceof Inet4Address&&ip.isSiteLocalAddress()){if(net.getName().startsWith("wlan")||net.getName().startsWith("eth"))return ip.getHostAddress();if(!net.getName().startsWith("tun"))fallback.add(ip.getHostAddress());}}return fallback.isEmpty()?null:fallback.get(0);}
    private final Object saves=new Object();
    private void serve(Socket socket){
        try{GalleryUploadHttp.serve(socket,host,token,GalleryStore.MAX_UPLOAD,this::valid,()->{
            try(InputStream page=context.getAssets().open("gallery-upload.html")){return read(page,128*1024);}
        },(bytes,allowed)->{synchronized(saves){return store.add(bytes,allowed);}},changed);}
        finally{clients.remove(socket);}
    }
    private byte[] read(InputStream in,int max)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException();out.write(b,0,n);}return out.toByteArray();}
    public void close(){closed=true;synchronized(this){ServerSocket old=server;server=null;if(old!=null)try{old.close();}catch(IOException ignored){}}synchronized(clients){for(Socket c:clients)try{c.close();}catch(IOException ignored){}}worker.shutdownNow();}
}
