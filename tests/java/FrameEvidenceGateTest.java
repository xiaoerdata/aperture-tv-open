package org.zhixiaoce.aperture;
public final class FrameEvidenceGateTest {
    static int n;static void ok(boolean v,String s){if(!v)throw new AssertionError(s);n++;}
    public static void main(String[] args){
        Object a=new Object(),w=new Object(),v=new Object();FrameEvidenceGate g=new FrameEvidenceGate(a,w,v,0,8000);
        ok(g.valid(a,w,v,true,false,0),"initial target visible");ok(!g.committed(10),"commit before current draw cannot prove frame");g.drawn(100);ok(!g.committed(99),"old frame rejected");ok(g.committed(100),"drawn frame commit accepted");ok(g.copied(0)&&g.state()==FrameEvidenceGate.State.COPIED,"copy succeeds only after commit");
        g=new FrameEvidenceGate(a,w,v,0,8000);ok(!g.valid(a,w,v,false,false,1)&&g.failure().equals("target-not-visible"),"invisible target invalid");
        g=new FrameEvidenceGate(a,w,v,0,8000);g.drawn(100);ok(!g.committed(99)&&g.state()==FrameEvidenceGate.State.WAITING,"delayed drawing waits not stale accept");ok(!g.valid(a,w,v,true,false,8000)&&g.failure().equals("timeout"),"bounded timeout");
        g=new FrameEvidenceGate(a,w,v,0,8000);g.drawn(100);ok(!g.valid(new Object(),w,v,true,false,10),"new activity rejected");ok(!g.committed(101),"late frame cannot revive invalid target");
        g=new FrameEvidenceGate(a,w,v,0,8000);ok(!g.valid(a,new Object(),v,true,false,1),"changed window invalid");
        g=new FrameEvidenceGate(a,w,v,0,8000);ok(!g.valid(a,w,new Object(),true,false,1),"old page target invalid");
        g=new FrameEvidenceGate(a,w,v,0,8000);g.drawn(100);g.committed(100);ok(!g.valid(a,w,v,true,true,1),"destroy after commit invalidates capture");ok(!g.copied(0),"late copy not valid after destroy");
        g=new FrameEvidenceGate(a,w,v,0,8000);ok(!g.copied(0),"file or copy alone not proof");g.drawn(100);g.committed(100);ok(!g.copied(3)&&g.failure().equals("pixel-copy-failed"),"copy failure explicit");
        System.out.println("FrameEvidenceGate: "+n+" checks passed; modeled fences, no real frame capture");
    }
}
