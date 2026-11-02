package org.zhixiaoce.aperture;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

final class AndroidPageDeadline {
    private AndroidPageDeadline(){}
    static PageDeadline.Clock clock(){return SystemClock::uptimeMillis;}
    static PageDeadline.Scheduler scheduler(){
        Handler handler=new Handler(Looper.getMainLooper());
        return new PageDeadline.Scheduler(){
            public void post(Runnable task,long delay){handler.postDelayed(task,delay);}
            public void cancel(Runnable task){handler.removeCallbacks(task);}
        };
    }
}
