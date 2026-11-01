package org.zhixiaoce.aperture;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Bounded, credential-free image pipeline, independent of playback/catalogue requests. */
final class CinemaArtwork implements AutoCloseable {
    private static final class Target {
        final java.lang.ref.WeakReference<ImageView> view;final Runnable missing;
        Target(ImageView view,Runnable missing){this.view=new java.lang.ref.WeakReference<>(view);this.missing=missing;}
    }
    private static final class Batch {final int epoch;final List<Target> targets=new ArrayList<>();Batch(int epoch){this.epoch=epoch;}}
    // Accessed on the UI thread; a repeated cover consumes one worker job, not one per tile.
    private final Map<String,Batch> pending=new HashMap<>();
    private final Activity activity;private final Handler main=new Handler(android.os.Looper.getMainLooper());
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(256));
    private final Set<HttpURLConnection> active=Collections.synchronizedSet(new HashSet<>());
    private final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(12*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
    private final File directory;private volatile int epoch;private volatile boolean closed;
    CinemaArtwork(Activity a){activity=a;directory=new File(a.getCacheDir(),"cinema-art-v1");directory.mkdirs();}
    static String address(String value,String base,boolean allowHttp){try{return CinemaCatalog.url(value,base,allowHttp);}catch(Exception e){return "";}}
    void bind(ImageView target,String value,String base,boolean allowHttp){bind(target,value,base,allowHttp,null);}
    void bind(ImageView target,String value,String base,boolean allowHttp,Runnable missing){
        String url=address(value,base,allowHttp);target.setTag(url);if(url.isEmpty()){if(missing!=null)missing.run();return;}Bitmap saved=memory.get(url);if(saved!=null){target.setImageBitmap(saved);return;}int turn=epoch;
        Batch existing=pending.get(url);if(existing!=null){existing.targets.add(new Target(target,missing));return;}
        Batch batch=new Batch(turn);batch.targets.add(new Target(target,missing));pending.put(url,batch);
        try{workers.execute(()->{
            if(turn!=epoch||closed)return;Bitmap decoded=null;
            try{decoded=load(url,allowHttp);if(decoded!=null&&turn==epoch&&!closed)memory.put(url,decoded);}catch(Exception|OutOfMemoryError ignored){}
            final Bitmap result=decoded;main.post(()->{
                if(pending.get(url)!=batch)return;pending.remove(url);
                if(closed||turn!=epoch)return;
                for(Target binding:batch.targets){ImageView view=binding.view.get();if(view!=null&&url.equals(view.getTag())){if(result!=null)view.setImageBitmap(result);else if(binding.missing!=null)binding.missing.run();}}
            });
        });}catch(RejectedExecutionException full){pending.remove(url);if(missing!=null)missing.run();}
    }

    private Bitmap load(String url,boolean allowHttp)throws Exception{
        StringBuilder key=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(url.getBytes("UTF-8")))key.append(String.format(java.util.Locale.ROOT,"%02x",b&255));File file=new File(directory,key.toString());byte[] data=null;
        if(file.isFile()&&file.length()<=4*1024*1024&&System.currentTimeMillis()-file.lastModified()<7L*86400000){try(InputStream in=new FileInputStream(file)){data=read(in,4*1024*1024);}file.setLastModified(System.currentTimeMillis());}
        if(data==null){String current=url;for(int hop=0;hop<=3;hop++){HttpURLConnection c=(HttpURLConnection)new URL(current).openConnection();active.add(c);try{c.setInstanceFollowRedirects(false);c.setConnectTimeout(5000);c.setReadTimeout(6000);c.setRequestProperty("User-Agent","Kaishi-TV/0.2");c.setRequestProperty("Accept","image/*");int status=c.getResponseCode();if(status==301||status==302||status==303||status==307||status==308){if(hop==3||c.getHeaderField("Location")==null)throw new IOException();current=CinemaCatalog.url(c.getHeaderField("Location"),current,allowHttp);continue;}if(status!=200||c.getContentLengthLong()>4*1024*1024)throw new IOException();String type=c.getContentType();if(type==null||!type.toLowerCase(java.util.Locale.ROOT).startsWith("image/"))throw new IOException();try(InputStream in=c.getInputStream()){data=read(in,4*1024*1024);}break;}finally{active.remove(c);c.disconnect();}}if(data==null)throw new IOException();}
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,options);if(options.outWidth<=0||options.outHeight<=0||(long)options.outWidth*options.outHeight>64000000L)throw new IOException();options.inJustDecodeBounds=false;options.inSampleSize=1;while(options.outWidth/options.inSampleSize>900||options.outHeight/options.inSampleSize>1200)options.inSampleSize*=2;options.inPreferredConfig=Bitmap.Config.RGB_565;Bitmap result=BitmapFactory.decodeByteArray(data,0,data.length,options);if(result==null)throw new IOException();
        if(result!=null&&!file.isFile()){synchronized(directory){trim(data.length);try(FileOutputStream out=new FileOutputStream(file)){out.write(data);}catch(IOException ignored){file.delete();}}}return result;
    }
    private void trim(int bytes){File[] files=directory.listFiles();if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=bytes;for(File f:files)total+=f.length();for(File f:files){if(total<=32L*1024*1024)break;long n=f.length();if(f.delete())total-=n;}}
    private static byte[] read(InputStream in,int max)throws IOException{long deadline=android.os.SystemClock.elapsedRealtime()+12000;ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted()||android.os.SystemClock.elapsedRealtime()>deadline||out.size()+n>max)throw new IOException();out.write(buffer,0,n);}return out.toByteArray();}
    void cancel(){epoch++;pending.clear();workers.getQueue().clear();synchronized(active){for(HttpURLConnection c:active)c.disconnect();}main.removeCallbacksAndMessages(null);}
    public void close(){closed=true;cancel();workers.shutdownNow();memory.evictAll();}
}
