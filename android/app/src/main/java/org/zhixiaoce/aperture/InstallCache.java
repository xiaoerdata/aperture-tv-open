package org.zhixiaoce.aperture;

import java.io.*;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Bounded immutable staging. Not an APK signature verifier; the OS verifies installs. */
final class InstallCache {
    static final long MAX_BYTES = 200L * 1024 * 1024;
    interface Source extends Closeable { long size(); int read(byte[] buffer) throws IOException; }
    static File directory(File cache) { return new File(cache, "install-apks"); }
    static boolean apkName(String name) { return name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk"); }
    static File staged(File directory, String path) throws IOException {
        if (path == null || !path.matches("/[a-f0-9-]{36}\\.apk")) throw new FileNotFoundException();
        File file = new File(directory, path.substring(1));
        if (!file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile()) || !file.isFile()) throw new FileNotFoundException();
        return file;
    }
    static File copy(File directory, Source source, BooleanSupplier cancelled) throws Exception {
        File partial = null, complete = null; boolean success = false;
        try (Source input = source) {
            if (source.size() > MAX_BYTES) throw new IOException("APK不能超过200MB");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建安装缓存");
            String id = UUID.randomUUID().toString(); partial = new File(directory, id + ".part"); complete = new File(directory, id + ".apk");
            long total = 0;
            try (OutputStream output = new FileOutputStream(partial)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (cancelled.getAsBoolean()) throw new IOException("已取消");
                    if (count <= 0 || count > buffer.length) throw new IOException("读取中断");
                    total += count; if (total > MAX_BYTES) throw new IOException("APK不能超过200MB"); output.write(buffer, 0, count);
                }
            }
            if (cancelled.getAsBoolean()) throw new IOException("已取消");
            if (total == 0 || (source.size() >= 0 && total != source.size())) throw new IOException("APK未读取完整");
            if (!partial.renameTo(complete)) throw new IOException("无法保存安装缓存");
            success = true; return complete;
        } finally { if (!success) { if (partial != null) partial.delete(); if (complete != null) complete.delete(); } }
    }
    static String sha256(File file) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) { byte[] data = new byte[65536]; int count; while ((count = input.read(data)) != -1) hash.update(data, 0, count); }
        StringBuilder value = new StringBuilder(); for (byte b : hash.digest()) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); return value.toString();
    }
}
