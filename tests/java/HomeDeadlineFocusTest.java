package org.zhixiaoce.aperture;
import java.util.*;
/** Shared production modules under focus/pause transitions; this does not execute Android Activity callbacks. */
public final class HomeDeadlineFocusTest {
    static final class Time implements PageDeadline.Clock,PageDeadline.Scheduler {
        long now;Map<Runnable,Long> jobs=new LinkedHashMap<>();public long now(){return now;}
        public void post(Runnable r,long delay){jobs.put(r,now+delay);}public void cancel(Runnable r){jobs.remove(r);}
        void advance(long amount){now+=amount;for(Runnable r:new ArrayList<>(jobs.keySet()))if(jobs.containsKey(r)&&jobs.get(r)<=now){jobs.remove(r);r.run();}}
    }
    static int checks;static void check(boolean pass,String what){if(!pass)throw new AssertionError(what);checks++;}
    public static void main(String[] args){
        Time time=new Time();List<PageDeadline.Ticket> expired=new ArrayList<>();PageDeadline deadline=new PageDeadline(12000,time,time,expired::add);DisplayLifecycle display=new DisplayLifecycle();
        display.bind(deadline::active);PageDeadline.Ticket ticket=deadline.start(new Object());display.resumed(true);time.advance(60000);check(expired.isEmpty(),"resumed without window focus consumes no budget");
        display.focused(true);time.advance(3000);display.focused(false);time.advance(60000);check(expired.isEmpty(),"assistant focus loss pauses timer with display");
        display.focused(true);time.advance(8999);check(expired.isEmpty(),"focus restore preserves remainder");time.advance(1);check(expired.size()==1&&expired.get(0)==ticket,"expires at foreground remainder");
        PageDeadline.Ticket ready=deadline.start(new Object());check(deadline.ready(ready),"ready state");display.focused(false);display.focused(true);time.advance(60000);check(expired.size()==1,"ready timer not revived by focus");
        PageDeadline.Ticket paused=deadline.start(new Object());time.advance(2000);display.resumed(false);time.advance(60000);check(expired.size()==1,"pause cancels while focus still true");display.resumed(true);time.advance(10000);check(expired.size()==2&&expired.get(1)==paused,"resume only restores remaining budget");
        deadline.start(new Object());deadline.cancel();display.bind(null);time.advance(60000);check(expired.size()==2,"destroy cancels independent of display target");
        System.out.println("HomeDeadlineFocus: "+checks+" production-module composition checks passed; Android Activity adapter not executed");
    }
}
