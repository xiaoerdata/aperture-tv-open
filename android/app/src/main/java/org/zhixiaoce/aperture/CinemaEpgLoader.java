package org.zhixiaoce.aperture;

import android.os.Handler;
import android.os.Looper;
import java.util.*;
import java.util.concurrent.*;

/** One bounded, optional XMLTV fetch independent of playback requests. No bundled providers. */
final class CinemaEpgLoader implements AutoCloseable {
    interface Done {void accept(CinemaEpg guide,String state);}
    private final CinemaHttp http=new CinemaHttp();private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService io=Executors.newSingleThreadExecutor();private volatile int epoch;private volatile boolean closed;private String key="",inflight="";private long age;private CinemaEpg cached=new CinemaEpg();private Done waiting;
    void load(List<String> urls,boolean allowHttp,Done done){
        if(closed)return;String next=allowHttp+":"+String.join("\n",urls);
        // A screen may enter playback while the same request is running. Deliver to its current owner.
        if(next.equals(inflight)){waiting=done;return;}
        final int turn=++epoch;http.cancel();inflight="";waiting=null;
        if(next.equals(key)&&System.currentTimeMillis()-age<900000){done.accept(cached,"");return;}
        inflight=next;waiting=done;List<String> sources=new ArrayList<>(urls);
        io.execute(()->{List<CinemaEpg> guides=new ArrayList<>();for(String source:sources){if(closed||turn!=epoch)return;try{guides.add(CinemaEpg.read(http.xmltv(source,allowHttp),System.currentTimeMillis()));}catch(Exception ignored){}}CinemaEpg result=CinemaEpg.merge(guides);main.post(()->{if(closed||turn!=epoch)return;inflight="";key=next;cached=result;age=System.currentTimeMillis();Done callback=waiting;waiting=null;if(callback!=null)callback.accept(result,guides.size()<sources.size()?"节目单暂时不可用":"");});});
    }
    public void close(){closed=true;epoch++;http.cancel();main.removeCallbacksAndMessages(null);io.shutdownNow();}
}
