package org.zhixiaoce.aperture;

/** Testing-only frame identity/commit fence; deliberately outside the product source tree. */
final class FrameEvidenceGate {
    enum State { WAITING, COMMITTED, COPIED, INVALID }
    final Object activity,window,target;final long deadline;
    private long drawFrame=-1,commitFrame=-1;private State state=State.WAITING;private String failure="";
    FrameEvidenceGate(Object activity,Object window,Object target,long now,long timeout){this.activity=activity;this.window=window;this.target=target;deadline=now+timeout;}
    State state(){return state;}String failure(){return failure;}long drawFrame(){return drawFrame;}long commitFrame(){return commitFrame;}
    boolean valid(Object a,Object w,Object t,boolean visible,boolean destroyed,long now){
        if(state==State.INVALID)return false;
        if(now>=deadline){invalidate("timeout");return false;}
        if(a!=activity||w!=window||t!=target||destroyed){invalidate("identity-or-lifecycle-changed");return false;}
        if(!visible){invalidate("target-not-visible");return false;}
        return true;
    }
    void drawn(long frameTime){if(state==State.WAITING&&drawFrame<0)drawFrame=frameTime;}
    boolean committed(long frameTime){
        if(state!=State.WAITING||drawFrame<0||frameTime<drawFrame)return false;
        commitFrame=frameTime;state=State.COMMITTED;return true;
    }
    boolean copied(int pixelCopyStatus){
        if(state!=State.COMMITTED)return false;
        if(pixelCopyStatus!=0){invalidate("pixel-copy-failed");return false;}
        state=State.COPIED;return true;
    }
    void invalidate(String reason){if(state!=State.INVALID){state=State.INVALID;failure=reason;}}
}
