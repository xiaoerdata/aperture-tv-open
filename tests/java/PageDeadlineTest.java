package org.zhixiaoce.aperture;

import java.util.ArrayList;
import java.util.List;

public final class PageDeadlineTest {
    static final class Time implements PageDeadline.Clock,PageDeadline.Scheduler {
        long now;final List<Task> tasks=new ArrayList<>();
        static final class Task {Runnable run;long at;boolean canceled;Task(Runnable r,long at){run=r;this.at=at;}}
        public long now(){return now;}
        public void post(Runnable task,long delay){tasks.add(new Task(task,now+delay));}
        public void cancel(Runnable task){for(Task t:tasks)if(t.run==task)t.canceled=true;}
        void advance(long delta){now+=delta;for(Task t:new ArrayList<>(tasks))if(!t.canceled&&t.at<=now){t.canceled=true;t.run.run();}}
    }
    static int count;
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);count++;}
    public static void main(String[] args){
        Time t=new Time();List<PageDeadline.Ticket> expired=new ArrayList<>();PageDeadline g=new PageDeadline(12000,t,t,expired::add);Object a=new Object(),b=new Object();
        PageDeadline.Ticket first=g.start(a);check(g.waiting(first),"initial waiting");t.advance(60000);check(expired.isEmpty(),"not started before resume");
        g.active(true);t.advance(500);check(g.ready(first),"normal ready");t.advance(60000);check(expired.isEmpty(),"ready cancels deadline");check(!g.ready(first),"ready exactly once");
        PageDeadline.Ticket second=g.start(a);t.advance(11999);check(expired.isEmpty(),"not prematurely expired");t.advance(1);check(expired.size()==1&&expired.get(0)==second,"no onPageFinished expires at budget");check(!g.ready(second),"late JS cannot revive expired navigation");
        PageDeadline.Ticket third=g.start(a);t.advance(3000);g.active(false);t.advance(3600000);check(expired.size()==1,"background cancels active timer");g.active(true);t.advance(8999);check(expired.size()==1,"resume preserves remaining active budget");t.advance(1);check(expired.size()==2,"remaining budget expires");
        PageDeadline.Ticket old=g.start(a);Time.Task oldTask=t.tasks.get(t.tasks.size()-1);PageDeadline.Ticket next=g.start(b);
        check(!g.current(old)&&g.current(next)&&next.owner==b,"exact owner and generation");check(!g.ready(old)&&g.waiting(next),"old readiness ignored");oldTask.run.run();check(expired.size()==2&&g.waiting(next),"queued stale alarm ignored");check(next.generation>old.generation,"generation increases");
        g.cancel();check(!g.current(next)&&!g.waiting(next),"cancel invalidates callback ticket");t.advance(12000);check(expired.size()==2,"destroy or native switch cancels deadline");
        PageDeadline.Ticket repeated=g.start(a);t.advance(2000);g.active(true);t.advance(10000);check(expired.size()==3&&expired.get(2)==repeated,"repeat resume cannot reset budget");
        PageDeadline.Ticket fast=g.start(a);t.advance(1);check(g.ready(fast),"ready claim");Time.Task readyTask=t.tasks.get(t.tasks.size()-1);readyTask.run.run();check(expired.size()==3,"already ready alarm ignored");
        PageDeadline.Ticket gone=g.start(a);g.cancel();PageDeadline.Ticket newer=g.start(a);check(!g.current(gone)&&g.current(newer),"same WebView new navigation isolated");check(g.ready(newer),"new page can become ready");
        Time backTime=new Time();List<PageDeadline.Ticket> backExpired=new ArrayList<>();PageDeadline back=new PageDeadline(600,backTime,backTime,backExpired::add);back.active(true);PageDeadline.Ticket pending=back.start(a);backTime.advance(599);check(backExpired.isEmpty(),"Back JS grace");backTime.advance(1);check(backExpired.size()==1&&backExpired.get(0)==pending,"missing Back JS gets native escape");
        PageDeadline.Ticket newerBack=back.start(a);check(!back.ready(pending)&&back.waiting(newerBack),"late Back callback cannot exit new attempt");back.cancel();backTime.advance(600);check(backExpired.size()==1,"background cancels Back callback and timeout");
        System.out.println("PageDeadline: "+count+" deterministic checks passed");
    }
}
