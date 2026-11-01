package org.zhixiaoce.aperture;

/** One native confirmation per current page; only a new, released gesture may commit it. */
final class UploadConsent {
    static final class Ticket {
        final Object owner;final long generation,openedAt;
        private Ticket(Object owner,long generation,long openedAt){this.owner=owner;this.generation=generation;this.openedAt=openedAt;}
    }
    private Ticket current;private long sequence,armed=-1,blocked=-1;private boolean accepted;
    Ticket request(Object owner,long now){cancel();current=new Ticket(owner,++sequence,now);return current;}
    boolean pending(){return current!=null&&!accepted;}
    boolean pending(Ticket ticket){return ticket!=null&&current==ticket&&!accepted;}
    boolean accepted(Ticket ticket){return ticket!=null&&current==ticket&&accepted;}
    void down(Ticket ticket,long downTime,int repeat){
        if(!pending(ticket))return;
        if(repeat>0){blocked=downTime;armed=-1;return;}
        if(downTime<=ticket.openedAt||downTime==blocked)return;
        if(armed<0)armed=downTime;
    }
    boolean up(Ticket ticket,long downTime){
        if(!pending(ticket)||armed<0)return false;
        if(downTime!=armed){blocked=armed;armed=-1;return false;}
        armed=-1;accepted=true;return true;
    }
    boolean explicitClick(Ticket ticket,long now){if(!pending(ticket)||now<=ticket.openedAt)return false;accepted=true;armed=-1;return true;}
    void disarm(Ticket ticket){if(pending(ticket)){blocked=armed;armed=-1;}}
    void cancel(){current=null;armed=-1;blocked=-1;accepted=false;}
}
