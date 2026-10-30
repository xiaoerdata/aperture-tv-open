package org.zhixiaoce.aperture;
public final class ConfirmPressTest {
    private static int checks;
    private static void check(boolean ok){if(!ok)throw new AssertionError("confirm ownership check "+checks);checks++;}
    public static void main(String[] args){
        ConfirmPress p=new ConfirmPress();
        check(!p.up(23,100,1,false)); // launcher down / new Activity up
        p.down(23,100,1);check(p.up(23,100,1,false));check(!p.up(23,100,1,false));
        p.down(23,200,1);check(!p.up(23,200,2,false)); // async screen replacement
        p.down(23,300,1);check(!p.up(66,300,1,false));
        p.down(23,400,1);check(!p.up(23,401,1,false));
        p.down(23,500,1);check(!p.up(23,500,1,true));
        p.down(23,600,1);p.cancel();check(!p.up(23,600,1,false)); // system voice/focus loss
        p.down(66,700,3);check(p.up(66,700,3,false));
        p.down(160,800,3);check(p.up(160,800,3,false));
        check(FeaturePolicy.photos()==BuildConfig.DEBUG);
        System.out.println(checks+" confirmation/policy assertions passed; photos="+FeaturePolicy.photos());
    }
}
