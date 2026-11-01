package org.zhixiaoce.aperture;
/** Real WebView and local assets; explicit test-only route override, not modern-engine acceptance. */
public final class R3WebGalleryProbe extends R3NativeGalleryProbe {
    @Override protected boolean useWebGallery(){return true;}
}
