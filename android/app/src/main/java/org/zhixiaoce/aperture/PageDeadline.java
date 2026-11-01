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
    private volatile Ticket ticket;private long generation,remaining,armedAt,dueAt,callbackDueAt=-1,callbackAt=-1;private boolean active,waiting;private Runnable alarm;
    static final class Snapshot {
        final long now,remaining,dueAt,callbackDueAt,callbackAt,delay;final boolean active,waiting,armed;
        Snapshot(long now,long remaining,long dueAt,long callbackDueAt,long callbackAt,boolean active,boolean waiting,boolean armed){this.now=now;this.remaining=remaining;this.dueAt=dueAt;this.callbackDueAt=callbackDueAt;this.callbackAt=callbackAt;this.active=active;this.waiting=waiting;this.armed=armed;delay=callbackAt>=0?Math.max(0,callbackAt-callbackDueAt):0;}
    }
    /** Observe on the same UI thread as deadline mutations; does not drive or change its clock. */
    Snapshot snapshot(){long now=clock.now();return new Snapshot(now,alarm==null?remaining:Math.max(0,remaining-Math.max(0,now-armedAt)),alarm==null?-1:dueAt,callbackDueAt,callbackAt,active,waiting,alarm!=null);}
    PageDeadline(long budget,Clock clock,Scheduler scheduler,Listener listener){
        if(budget<=0)throw new IllegalArgumentException("Positive deadline required");
        this.budget=budget;this.clock=clock;this.scheduler=scheduler;this.listener=listener;
    }
    Ticket start(Object owner){cancel();ticket=new Ticket(owner,++generation);remaining=budget;dueAt=0;callbackDueAt=callbackAt=-1;waiting=true;arm();return ticket;}
    boolean current(Ticket value){return value!=null&&ticket==value;}
    boolean waiting(Ticket value){return current(value)&&waiting;}
    boolean ready(Ticket value){if(!waiting(value))return false;disarm();waiting=false;return true;}
    void active(boolean value){if(active==value)return;if(!value)disarm();active=value;if(value)arm();}
    void cancel(){disarm();waiting=false;ticket=null;}
    private void disarm(){if(alarm!=null){scheduler.cancel(alarm);remaining=Math.max(0,remaining-Math.max(0,clock.now()-armedAt));alarm=null;}}
    private void arm(){
        if(!active||!waiting||ticket==null||alarm!=null)return;
        Ticket expected=ticket;armedAt=clock.now();dueAt=armedAt+remaining;
        Runnable next=new Runnable(){public void run(){
            if(alarm!=this||!active||!waiting(expected))return;
            callbackDueAt=dueAt;callbackAt=clock.now();alarm=null;remaining=0;waiting=false;listener.expired(expected);
        }};alarm=next;scheduler.post(next,remaining);
    }
}
