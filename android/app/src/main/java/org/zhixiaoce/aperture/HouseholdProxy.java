package org.zhixiaoce.aperture;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exact, read-only household resources for the immutable local lobby. No JS bridge. */
final class HouseholdProxy {
    static final class Reply {
        final int status; final String mime; final byte[] body;
        Reply(int status, String mime, byte[] body) { this.status = status; this.mime = mime; this.body = body; }
    }
    private static final Pattern COOKIE = Pattern.compile("(?:^|;\\s*)aperture=([A-Za-z0-9_-]{16,200})(?:;|$)");
    private final URI origin;
    private final String cookie;
    private final Semaphore boardSlots = new Semaphore(1), imageSlots = new Semaphore(2);
    private final AtomicInteger imageRequests = new AtomicInteger();
    private volatile boolean active, closed;
    private volatile long generation;
    static boolean allowedOrigin(String configuredOrigin, boolean allowLanHttp) {
        try {
            URI value=URI.create(configuredOrigin);
            return ("https".equals(value.getScheme()) || allowLanHttp && "http".equals(value.getScheme()) && privateLiteral(value.getHost())) && value.getHost()!=null && value.getRawUserInfo()==null && value.getRawQuery()==null && value.getRawFragment()==null && (value.getRawPath().isEmpty()||"/".equals(value.getRawPath())) && (value.getPort()==-1||value.getPort()>0&&value.getPort()<=65535) && !LobbyAssets.HOST.equalsIgnoreCase(value.getHost());
        } catch(RuntimeException invalid) { return false; }
    }
    private static boolean privateLiteral(String host) {
        if(host==null)return false;
        String value=host.toLowerCase(java.util.Locale.ROOT);
        if(value.startsWith("[")&&value.endsWith("]"))value=value.substring(1,value.length()-1);
        if("::1".equals(value))return true;
        if(value.indexOf(':')>=0)return value.matches("f[cd][0-9a-f]{2}:[0-9a-f:]+")&&!value.contains("%");
        String[] parts=value.split("\\.",-1);if(parts.length!=4)return false;int[] numbers=new int[4];
        try {for(int i=0;i<4;i++){if(!parts[i].matches("0|[1-9][0-9]{0,2}"))return false;numbers[i]=Integer.parseInt(parts[i]);if(numbers[i]>255)return false;}}catch(NumberFormatException invalid){return false;}
        return numbers[0]==10||numbers[0]==127||numbers[0]==192&&numbers[1]==168||numbers[0]==172&&numbers[1]>=16&&numbers[1]<=31;
    }
    HouseholdProxy(String configuredOrigin, String cookies, boolean allowLanHttp) {
        origin = allowedOrigin(configuredOrigin,allowLanHttp)?URI.create(configuredOrigin):null;
        Matcher match = COOKIE.matcher(cookies == null ? "" : cookies); cookie = match.find() ? "aperture=" + match.group(1) : "";
    }
    static String path(String value) {
        try {
            URI uri = URI.create(value); String path = uri.getRawPath();
            if (!"https".equals(uri.getScheme()) || !LobbyAssets.HOST.equals(uri.getHost()) || uri.getPort() != -1 || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) return null;
            return "/api/board".equals(path) || path != null && path.matches("/media/[a-f0-9]{64}\\.jpg") ? path : null;
        } catch (RuntimeException ignored) { return null; }
    }
    synchronized void active(boolean value) { if (active != value) { generation++; active = value; } }
    synchronized void close() { closed = true; active = false; generation++; }
    private boolean current(long turn) { return !closed && active && generation == turn; }
    private static Reply error(int status) { return new Reply(status, "application/json", ("{\"code\":\"household_unavailable\",\"message\":\"家庭数据暂不可读取，请连接后重试\"}").getBytes(StandardCharsets.UTF_8)); }
    Reply read(String requestUri, String method, boolean mainFrame) {
        String path = path(requestUri);
        if (path == null || !"GET".equals(method) || mainFrame) return null;
        if (origin == null || cookie.isEmpty()) return error(401);
        long turn = generation, deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        if (!current(turn)) return error(503);
        boolean photo = path.startsWith("/media/"), counted = false, acquired = false;
        Semaphore slots = photo ? imageSlots : boardSlots;
        HttpURLConnection connection = null;
        try {
            if (photo) { counted = true; if (imageRequests.incrementAndGet() > 6) return error(429); }
            acquired = slots.tryAcquire(photo ? 4500 : 0, TimeUnit.MILLISECONDS);
            if (!acquired) return error(429);
            if (!current(turn)) return error(503);
            connection = (HttpURLConnection) origin.resolve(path).toURL().openConnection();
            connection.setRequestMethod("GET"); connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            connection.setConnectTimeout(1500); connection.setReadTimeout(1500);
            connection.setRequestProperty("Cookie", cookie); connection.setRequestProperty("Accept", photo ? "image/jpeg" : "application/json"); connection.setRequestProperty("Accept-Encoding", "identity");
            int status = connection.getResponseCode();
            if (!current(turn)) return error(503);
            if (status != 200) return error(status == 401 || status == 403 || status == 404 || status == 429 ? status : 502);
            String type = connection.getContentType(), encoding = connection.getContentEncoding();
            if (type == null || !(photo ? type.split(";", 2)[0].trim().equalsIgnoreCase("image/jpeg") : type.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) || encoding != null && !"identity".equalsIgnoreCase(encoding)) return error(502);
            int maximum = photo ? 8_000_000 : 1_048_576;
            if (connection.getContentLengthLong() > maximum) return error(413);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream stream = connection.getInputStream()) {
                byte[] chunk = new byte[16384]; int size;
                while (true) {
                    if (!current(turn)) return error(503);
                    long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                    if (remaining <= 0) return error(504);
                    connection.setReadTimeout((int) Math.min(1500, remaining));
                    size = stream.read(chunk); if (size < 0) break;
                    if (bytes.size() + size > maximum) return error(413); bytes.write(chunk, 0, size);
                }
            }
            if (!current(turn) || System.nanoTime() >= deadline) return error(503);
            byte[] body = bytes.toByteArray();
            if (photo && (body.length < 4 || (body[0] & 255) != 255 || (body[1] & 255) != 216 || (body[body.length - 2] & 255) != 255 || (body[body.length - 1] & 255) != 217)) return error(502);
            return new Reply(200, photo ? "image/jpeg" : "application/json", body);
        } catch (java.net.SocketTimeoutException timeout) { return error(504); }
        catch (Exception unavailable) { return error(502); }
        finally { if (connection != null) connection.disconnect(); if (acquired) slots.release(); if (counted) imageRequests.decrementAndGet(); }
    }
}
