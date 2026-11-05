package org.zhixiaoce.aperture;
public final class TvScaleTest {
    private static void check(float got,float want){if(Math.abs(got-want)>.001f)throw new AssertionError(got+" != "+want);}
    public static void main(String[] args){
        // Identical rendered windows must be identical on mdpi/xhdpi/OEM overrides.
        for(float density:new float[]{0,1,2,4}){
            check(TvScale.density(density,1080),1.6f);
            check(TvScale.density(density,540),.8f);
            check(TvScale.density(density,2160),3.2f);
        }
        check(TvScale.density(2,0),2);check(TvScale.density(0,0),1);
        for(float density:new float[]{1,2,4}){
            check(TvScale.density(density,1920,1080),1.6f);
            check(TvScale.density(density,3840,2160),3.2f);
            check(TvScale.density(density,960,540),.8f);
            check(TvScale.density(density,960,600),.8f);
            check(TvScale.density(density,1280,640),640/675f);
        }
        if(TvScale.density(4,1080)*44>1080*.07f)throw new AssertionError("control too tall");
        System.out.println("29 density/viewport cases and compact control bound passed");
    }
}
