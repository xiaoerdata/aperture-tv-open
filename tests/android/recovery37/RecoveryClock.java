package org.zhixiaoce.aperture;
import java.util.*;
final class RecoveryClock implements PageDeadline.Clock,PageDeadline.Scheduler {
    long time;static final class Job{Runnable r;long at;Job(Runnable r,long at){this.r=r;this.at=at;}}
    final List<Job> jobs=new ArrayList<>();public long now(){return time;}
    public void post(Runnable r,long delay){jobs.add(new Job(r,time+delay));}public void cancel(Runnable r){jobs.removeIf(j->j.r==r);}
    void advance(long delta){long target=time+delta;for(;;){Job next=null;for(Job j:jobs)if(j.at<=target&&(next==null||j.at<next.at))next=j;if(next==null)break;jobs.remove(next);time=next.at;next.r.run();}time=target;}
}
