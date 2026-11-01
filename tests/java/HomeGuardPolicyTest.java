package org.zhixiaoce.aperture;
public class HomeGuardPolicyTest {
    private static int passed;
    private static void check(boolean v,String what){if(!v)throw new AssertionError(what);passed++;}
    public static void main(String[] args){
        HomeGuardPolicy p=new HomeGuardPolicy(false);
        check(!p.shouldOpen(false,true,true,false,10000),"opt in required");
        check(!p.shouldOpen(true,false,true,false,10000),"never wake screen");
        check(!p.shouldOpen(true,true,false,false,10000),"other windows untouched");
        check(p.shouldOpen(true,true,true,false,10000),"verified home opens");
        check(!p.shouldOpen(true,true,true,false,10500),"duplicate events throttled");
        check(p.shouldOpen(true,true,false,true,12000),"home key opens");
        p.visit(14000);
        check(!p.shouldOpen(true,true,true,false,14001),"explicit original home visit allowed");
        check(!p.shouldOpen(true,true,false,true,14500),"self generated home suppressed");
        check(!p.shouldOpen(true,true,true,false,50000),"visit not forcibly expired");
        check(p.shouldOpen(true,true,false,true,52000),"next user HOME leaves visit");
        check(!p.visiting(),"visit cleared on HOME");
        p.visit(60000);p.resumeHome();
        check(p.shouldOpen(true,true,true,false,62000),"manual return or wake rearms");
        p=new HomeGuardPolicy(true);
        check(!p.shouldOpen(true,true,true,false,70000),"process restart preserves original visit");
        check(p.shouldOpen(true,true,false,true,71000),"user home ends recovered visit");
        check(!p.shouldOpen(false,true,false,true,74000),"disabled home key untouched");
        System.out.println("HomeGuardPolicy: "+passed+" checks passed; Xiaomi hardware not tested");
    }
}
