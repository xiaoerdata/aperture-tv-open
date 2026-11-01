package org.zhixiaoce.aperture;

/** Bound density-driven native controls to the current rendered TV viewport. */
final class TvScale {
    private TvScale() {}
    static float density(float systemDensity, int viewportHeight) {
        if (viewportHeight <= 0 || systemDensity <= 0) return systemDensity > 0 ? systemDensity : 1f;
        return Math.min(systemDensity, Math.max(.5f, 2f * viewportHeight / 1080f));
    }
}
