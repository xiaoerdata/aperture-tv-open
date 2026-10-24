package org.zhixiaoce.aperture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class NativeCompatibilityTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        if (args.length == 1) {
            java.net.URI uri = java.net.URI.create(args[0]);
            String file = LobbyAssets.file(uri.getScheme(), uri.getHost(), uri.getPort(), uri.getPath());
            System.out.print(file == null ? "BLOCKED" : file + "\n" + LobbyAssets.mime(file)); return;
        }
        DisplayLifecycle state = new DisplayLifecycle(); List<Boolean> seen = new ArrayList<>();
        state.bind(seen::add); state.resumed(true);
        check(seen.equals(Arrays.asList(false)), "resumed without window focus must remain paused");
        state.focused(true); state.focused(true); state.resumed(true);
        check(seen.equals(Arrays.asList(false, true)), "resume exactly once after both conditions");
        state.focused(false); state.focused(false);
        check(!state.active() && seen.equals(Arrays.asList(false, true, false)), "assistant overlay pauses even without Activity.onPause");
        state.focused(true); state.resumed(false); state.focused(false); state.focused(true);
        check(!state.active(), "late focus must not resume a paused Activity");
        List<Boolean> replacement = new ArrayList<>(); state.bind(replacement::add); state.resumed(true);
        check(replacement.equals(Arrays.asList(false, true)), "new WebView inherits current lifecycle");
        int before = seen.size(); state.bind(null); state.focused(false); state.resumed(false);
        check(seen.size() == before, "destroyed WebView receives no later callbacks");
        for (String file : new String[] {"index.html", "style.css", "launcher.js", "carezza-4k.jpg", "fonts/clock-reader.woff2", "fonts/aperture-song.woff2"}) check(file.equals(LobbyAssets.file("https", LobbyAssets.HOST, -1, "/assets/" + file)), "allowed asset " + file);
        for (String path : new String[] {"/assets/../owner-key", "/assets/fonts/../../owner-key", "/assets/fonts/Newsreader-OFL.txt", "/assets/index.html/other", "/assets/%2e%2e/owner-key", "/api/board", "/media/private.jpg", "/assets/missing.js"}) check(LobbyAssets.file("https", LobbyAssets.HOST, -1, path) == null, "blocked path " + path);
        check(LobbyAssets.file("https", "attacker.invalid", -1, LobbyAssets.DOCUMENT) == null, "foreign host");
        check(LobbyAssets.file("http", LobbyAssets.HOST, -1, LobbyAssets.DOCUMENT) == null, "cleartext");
        check(LobbyAssets.file("https", LobbyAssets.HOST, 8443, LobbyAssets.DOCUMENT) == null, "foreign port");
        check(LobbyAssets.file("file", null, -1, "/android_asset/index.html") == null, "no filesystem loading");
        check("font/woff2".equals(LobbyAssets.mime("fonts/clock-reader.woff2")), "font MIME");
        check("image/png".equals(LobbyAssets.mime("alpine-dusk.png")), "image MIME");
        check("image/jpeg".equals(LobbyAssets.mime("carezza-4k.jpg")), "4K JPEG MIME");
        System.out.println("Native policy: overlay/resume/destroy transitions and exact local HTTPS resources passed");
    }
}
