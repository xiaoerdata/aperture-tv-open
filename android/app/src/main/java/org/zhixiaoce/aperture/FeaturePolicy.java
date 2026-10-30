package org.zhixiaoce.aperture;

/** Album implementation remains available to developers; shipping builds omit its entry points. */
final class FeaturePolicy {
    static boolean photos() { return BuildConfig.DEBUG; }
    private FeaturePolicy() {}
}
