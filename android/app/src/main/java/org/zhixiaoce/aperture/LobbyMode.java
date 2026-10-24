package org.zhixiaoce.aperture;

/** Home preference is independent of the family screen's lightweight mode. */
final class LobbyMode {
    enum Choice { GLASS, NATIVE_OLD_ENGINE, NATIVE_SELECTED, NATIVE_RECOVERY }
    private boolean rendererUnavailable;

    Choice choose(int webViewMajor, boolean simpleHome) {
        if (rendererUnavailable) return Choice.NATIVE_RECOVERY;
        if (webViewMajor < 89) return Choice.NATIVE_OLD_ENGINE;
        return simpleHome ? Choice.NATIVE_SELECTED : Choice.GLASS;
    }
    boolean webAvailable(int webViewMajor) { return !rendererUnavailable && webViewMajor >= 37; }
    boolean glassAvailable(int webViewMajor) { return !rendererUnavailable && webViewMajor >= 89; }
    void rendererGone() { rendererUnavailable = true; }
    boolean recoveryRequired() { return rendererUnavailable; }
}
