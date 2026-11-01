package org.zhixiaoce.aperture;
public final class TvScaleTest {
    private static void check(float got,float want){if(Math.abs(got-want)>.001f)throw new AssertionError(got+" != "+want);}
    public static void main(String[] args){
        check(TvScale.density(2,1080),2); check(TvScale.density(4,1080),2);
        check(TvScale.density(4,2160),4); check(TvScale.density(1,720),1);
        check(TvScale.density(2,0),2); check(TvScale.density(0,1080),1);
        System.out.println("viewport density cases passed");
    }
}
