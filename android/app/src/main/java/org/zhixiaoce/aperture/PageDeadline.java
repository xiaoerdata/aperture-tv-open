package org.zhixiaoce.aperture;

/** Foreground-only deadline. Every asynchronous callback must retain its exact navigation ticket. */
final class PageDeadline {
    interface Clock { long now(); }
    interface Scheduler { void post(Runnable task,long delay); void cancel(Runnable task); }
    interface Listener { void expired(Ticket ticket); }
    static final class Ticket {
        final Object owner; final long generation;
        private Ticket(Object owner,long generation){this.owner=owner;this.generation=generation;}
    }
    private final Clock clock;private final Scheduler scheduler;private final Listener listener;private final long budget;
    private volatile Ticket ticket;private long generation,remaining,armedAt;private boolean active,waiting;private Runnable alarm;
    PageDeadline(long budget,Clock clock,Scheduler scheduler,Listener listener){
        if(budget<=0)throw new IllegalArgumentException("Positive deadline required");
        this.budget=budget;this.clock=clock;this.scheduler=scheduler;this.listener=listener;
    }
    Ticket start(Object owner){cancel();ticket=new Ticket(owner,++generation);remaining=budget;waiting=true;arm();return ticket;}
    boolean current(Ticket value){return value!=null&&ticket==value;}
    boolean waiting(Ticket value){return current(value)&&waiting;}
    boolean ready(Ticket value){if(!waiting(value))return false;disarm();waiting=false;return true;}
    void active(boolean value){if(active==value)return;if(!value)disarm();active=value;if(value)arm();}
    void cancel(){disarm();waiting=false;ticket=null;}
    private void disarm(){if(alarm!=null){scheduler.cancel(alarm);remaining=Math.max(0,remaining-Math.max(0,clock.now()-armedAt));alarm=null;}}
    private void arm(){
        if(!active||!waiting||ticket==null||alarm!=null)return;
        Ticket expected=ticket;armedAt=clock.now();
        Runnable next=new Runnable(){public void run(){
            if(alarm!=this||!active||!waiting(expected))return;
            alarm=null;remaining=0;waiting=false;listener.expired(expected);
        }};alarm=next;scheduler.post(next,remaining);
    }
}
