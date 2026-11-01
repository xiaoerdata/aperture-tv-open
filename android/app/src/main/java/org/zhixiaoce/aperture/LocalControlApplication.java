package org.zhixiaoce.aperture;
/** Application-local lifecycle registration only; no boot receiver or background foreground service. */
public final class LocalControlApplication extends android.app.Application {
    @Override public void onCreate(){super.onCreate();TvLocalControl.install(this);}
}
