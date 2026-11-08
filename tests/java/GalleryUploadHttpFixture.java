package org.zhixiaoce.aperture;
import java.io.*;import java.net.*;import java.nio.file.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
/** Phone browser uses production HTTP transport with a synthetic store, loopback only. */
public final class GalleryUploadHttpFixture {
    public static void main(String[] args)throws Exception{
        ServerSocket listener=new ServerSocket(0,8,InetAddress.getLoopbackAddress());String host="127.0.0.1:"+listener.getLocalPort();ExecutorService workers=Executors.newFixedThreadPool(2);AtomicBoolean alive=new AtomicBoolean(true);AtomicInteger saved=new AtomicInteger();byte[] page=Files.readAllBytes(Path.of(args[0]));
        Thread accept=new Thread(()->{while(alive.get())try{Socket socket=listener.accept();workers.execute(()->GalleryUploadHttp.serve(socket,host,"abcdefghijklmnopqrstuvwxyzABCDEF",20*1024*1024,alive::get,()->page,(bytes,valid)->{if(!java.util.Arrays.equals(bytes,GalleryUploadHttpTest.PHOTO))throw new IOException("照片字节与测试原图不一致");return "fixture-"+saved.incrementAndGet();},()->{throw new IllegalStateException("synthetic refresh failure");}));}catch(IOException stopped){break;}});accept.setDaemon(true);accept.start();
        System.out.println("http://"+host+"/#abcdefghijklmnopqrstuvwxyzABCDEF");System.out.flush();System.in.read();alive.set(false);listener.close();workers.shutdownNow();
    }
}
