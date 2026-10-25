package org.zhixiaoce.aperture;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Exact packaged resources served from one local HTTPS origin; never a filesystem prefix. */
final class LobbyAssets {
    static final String HOST = "appassets.androidplatform.net";
    static final String DOCUMENT = "/assets/index.html";
    private static final Set<String> FILES = new HashSet<>(Arrays.asList(
        "art/dusk-lake-v1.webp", "art/dusk-lake-thumb-v1.webp", "art/memory-archive-v1.webp", "art/glass-swallow-v1.webp", "app-presets.json", "home-personal.js", "index.html", "style.css", "launcher.js", "living-room.svg", "carezza-4k.jpg", "namib-4k.jpg", "fuji-4k.jpg", "lofoten-4k.jpg", "lofoten-thumb.jpg", "namib-thumb.jpg", "fuji-thumb.jpg",
        "app-icons/bilibili.jpg", "app-icons/tencent.png", "app-icons/iqiyi.png", "app-icons/youku.png", "app-icons/mango.png", "app-icons/migu.jpg", "app-icons/netflix.jpg", "app-icons/appletv.png", "app-icons/youtube.png", "app-icons/disney.png", "app-icons/plex.png", "app-icons/kodi.png", "app-icons/application.svg", "brand/kaishi-icon-v2.webp", "brand/kaishi-parents-v2.webp", "brand/identity.css", "brand/identity.js",
        "fonts/clock-reader.woff2", "fonts/aperture-song.woff2"
    ));
    static String file(String scheme, String host, int port, String path) {
        if (!"https".equals(scheme) || !HOST.equals(host) || port != -1 || path == null || !path.startsWith("/assets/")) return null;
        String name = path.substring(8); return FILES.contains(name) ? name : null;
    }
    static String mime(String name) {
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".html")) return "text/html";
        if (name.endsWith(".css")) return "text/css";
        if (name.endsWith(".js")) return "text/javascript";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".jpg")) return "image/jpeg";
        if (name.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }
}
