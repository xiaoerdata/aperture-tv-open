package org.zhixiaoce.aperture;
public final class PlaybackEntryTest {
    static int checks;
    static final class Scene implements PlaybackSession.Focus,PlaybackSession.Output {
        int starts;final PlaybackSession session=new PlaybackSession(this,this);final PlaybackEntryIntent entry=new PlaybackEntryIntent();
        public boolean request(long token,Runnable lost){return true;}public void abandon(long token){}public void start(){starts++;}public void pause(){}public void changed(PlaybackSession.State state){}
        void begin(boolean active){session.active(active);entry.begin(active);session.begin();session.surface(true);}
        void ready(boolean active){session.prepared();if(entry.take(active))session.play();}
        void focus(boolean active,boolean prepared){if(!active)entry.cancel();session.active(active);if(prepared&&entry.take(active))session.play();}
    }
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    public static void main(String[] args){
        Scene early=new Scene();early.begin(false);early.ready(false);early.focus(true,true);check(early.starts==1&&early.session.state()==PlaybackSession.State.PLAYING,"ready-before-first-focus consumes entry once");early.focus(true,true);check(early.starts==1,"repeated focus cannot replay consumed intent");
        Scene late=new Scene();late.begin(false);late.focus(true,false);late.ready(true);check(late.starts==1,"first-focus-before-ready starts exactly once");
        Scene voice=new Scene();voice.begin(false);voice.focus(false,false);voice.ready(false);voice.focus(true,true);check(voice.starts==0,"external focus loss revokes deferred entry intent");
        Scene pause=new Scene();pause.begin(false);pause.entry.cancel();pause.session.pause();pause.ready(false);pause.focus(true,true);check(pause.starts==0,"explicit pause cancels deferred entry");
        Scene active=new Scene();active.begin(true);active.ready(true);active.focus(false,true);active.focus(true,true);check(active.starts==1&&active.session.state()==PlaybackSession.State.INTERRUPTED,"normal active selection still never resumes after interruption");
        System.out.println(checks+" production entry/session integration assertions passed");
    }
}
