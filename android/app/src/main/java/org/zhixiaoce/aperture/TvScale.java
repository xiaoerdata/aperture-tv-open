package org.zhixiaoce.aperture;

/** Bound density-driven native controls to the current rendered TV viewport. */
final class TvScale {
    private TvScale() {}
    static float density(float systemDensity, int viewportHeight) {
        if (viewportHeight <= 0) return systemDensity > 0 ? systemDensity : 1f;
        // The rendered window determines the control size, even when an OEM's
        // base density differs from its override. 675 design dp = one TV height.
        return viewportHeight / 675f;
    }
    static float density(float systemDensity,int viewportWidth,int viewportHeight){
        if(viewportWidth<=0||viewportHeight<=0)return density(systemDensity,viewportHeight);
        return Math.min(viewportWidth/1200f,viewportHeight/675f);
    }
}
