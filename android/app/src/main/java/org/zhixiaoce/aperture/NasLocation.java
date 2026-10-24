package org.zhixiaoce.aperture;

import java.net.URI;
import java.util.Locale;

/** Explicit connection root. No credentials in URLs; file paths are relative to this root. */
final class NasLocation {
    final URI uri;
    final boolean smb;
    final String host, share, folder;
    final int port;
    NasLocation(String raw) {
        try {
            uri = URI.create(raw.trim()); smb = "smb".equals(uri.getScheme()); host = uri.getHost();
            if ((!smb && !"https".equals(uri.getScheme())) || host == null || host.isEmpty() || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535) throw new IllegalArgumentException();
            port = uri.getPort() == -1 ? (smb ? 445 : 443) : uri.getPort();
            String path = uri.getPath();
            if (path == null || !path.startsWith("/")) throw new IllegalArgumentException();
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            path = path.isEmpty() ? "" : relative(path.substring(1));
            if (smb && path.isEmpty()) throw new IllegalArgumentException();
            int slash = path.indexOf('/'); share = smb ? (slash < 0 ? path : path.substring(0, slash)) : "";
            folder = smb ? (slash < 0 ? "" : path.substring(slash + 1)) : path;
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("请输入 smb://地址/共享名 或 https://地址/WebDAV目录/，不在地址中填写账号密码"); }
    }
    static String relative(String value) {
        if (value == null || value.length() > 2048) throw new IllegalArgumentException("路径不符合要求");
        if (value.isEmpty()) return "";
        for (String part : value.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.length() > 255 || part.matches(".*[\\\\:%?*\"<>|\\p{Cntrl}].*") || part.endsWith(".") || part.endsWith(" ")) throw new IllegalArgumentException("路径不符合要求");
        }
        return value;
    }
    String child(String parent, String name) { relative(name); if (name.contains("/")) throw new IllegalArgumentException("不是直接子项"); return relative(parent.isEmpty() ? name : parent + "/" + name); }
    String smbPath(String path) { relative(path); return (folder.isEmpty() ? path : path.isEmpty() ? folder : folder + "/" + path).replace('/', '\\'); }
    URI davPath(String path, boolean directory) {
        relative(path);
        try { String full = "/" + (folder.isEmpty() ? path : path.isEmpty() ? folder : folder + "/" + path); if (directory && !full.endsWith("/")) full += "/"; return new URI("https", null, host, uri.getPort(), full, null, null); }
        catch (Exception invalid) { throw new IllegalArgumentException("路径不符合要求"); }
    }
    static String kind(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.matches(".*\\.(jpg|jpeg|png|webp)$")) return "photo";
        if (n.matches(".*\\.(mp4|m4v|mkv|webm|mov|ts)$")) return "video";
        return "file";
    }
}
