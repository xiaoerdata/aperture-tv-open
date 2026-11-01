package org.zhixiaoce.aperture;
import android.os.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicReference;import java.util.function.BooleanSupplier;
/** Explicit predicate barrier. A queue-idle notification or fixed sleep is never the predicate. */
final class ProbeWait {
    static void until(BooleanSupplier predicate,long timeout,String label){
        CountDownLatch done=new CountDownLatch(1);boolean[] passed={false};AtomicReference<Throwable> error=new AtomicReference<>();Handler h=new Handler(Looper.getMainLooper());long limit=SystemClock.uptimeMillis()+timeout;
        h.post(new Runnable(){public void run(){try{if(predicate.getAsBoolean()){passed[0]=true;done.countDown();}else if(SystemClock.uptimeMillis()>=limit)done.countDown();else h.postDelayed(this,20);}catch(Throwable e){error.set(e);done.countDown();}}});
        try{if(!done.await(timeout+1000,TimeUnit.MILLISECONDS)||!passed[0])throw new AssertionError("Timed out waiting for "+label,error.get());}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(label,e);}
        if(error.get()!=null)throw new AssertionError(label,error.get());
    }
}
