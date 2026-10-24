package org.zhixiaoce.aperture;

public final class LobbyModeTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        LobbyMode mode = new LobbyMode();
        for (int version : new int[] {0, 36, 37, 64, 88}) {
            check(mode.choose(version, false) == LobbyMode.Choice.NATIVE_OLD_ENGINE, "legacy engine home " + version);
            check(!mode.glassAvailable(version), "cannot force glass on " + version);
        }
        check(!mode.webAvailable(0) && !mode.webAvailable(36), "missing provider cannot open family web");
        check(mode.webAvailable(37) && mode.webAvailable(88), "legacy family lite remains available");
        System.out.println("PASS legacy/missing providers use native home while capable legacy family lite remains available");

        for (int version : new int[] {89, 120, 140}) {
            check(mode.choose(version, false) == LobbyMode.Choice.GLASS, "modern default " + version);
            check(mode.choose(version, true) == LobbyMode.Choice.NATIVE_SELECTED, "manual choice " + version);
            check(mode.glassAvailable(version) && mode.webAvailable(version), "simple home does not disable family pages");
            check(mode.choose(version, false) == LobbyMode.Choice.GLASS, "explicit reset restores modern home");
        }
        System.out.println("PASS modern default, explicit simple-home selection and reset do not alter family web availability");

        mode.rendererGone(); mode.rendererGone();
        for (int version : new int[] {0, 37, 89, 140}) {
            for (boolean preference : new boolean[] {false, true}) {
                check(mode.choose(version, preference) == LobbyMode.Choice.NATIVE_RECOVERY, "recovery overrides choice");
                check(!mode.glassAvailable(version) && !mode.webAvailable(version), "no renderer restart on Home or retry");
            }
        }
        check(mode.recoveryRequired(), "recovery lasts for this Activity instance");
        System.out.println("PASS renderer exit is idempotent and blocks implicit web recreation across Home/preference changes");

        LobbyMode reopened = new LobbyMode();
        check(reopened.choose(140, false) == LobbyMode.Choice.GLASS, "new Activity permits modern home again");
        check(reopened.choose(140, true) == LobbyMode.Choice.NATIVE_SELECTED, "saved user preference survives new policy");
        check(reopened.choose(64, false) == LobbyMode.Choice.NATIVE_OLD_ENGINE, "restart does not bypass old engine rule");
        System.out.println("PASS a newly opened Activity can retry without clearing the user's independent simple-home preference");
        System.out.println("TRACE WebView64 -> NATIVE_OLD_ENGINE; family lite allowed");
        System.out.println("TRACE WebView140 -> GLASS -> renderer exit -> NATIVE_RECOVERY; no automatic retry");
        System.out.println("TRACE new Activity + simple preference -> NATIVE_SELECTED; family web allowed");
    }
}
