package org.zhixiaoce.aperture;

import okhttp3.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/** HTTPS WebDAV: Depth 1 listing, HEAD and validated byte-range GET. Never follows redirects. */
final class DavNasReader implements NasReader {
    private final NasLocation location;
    private final OkHttpClient client;
    private final String authorization;
    private volatile boolean closed;
    DavNasReader(NasLocation location, String user, char[] password) {
        this(location, user, password, new OkHttpClient.Builder());
    }
    // Package-only injection for a certificate-pinned synthetic HTTPS protocol test.
    DavNasReader(NasLocation location, String user, char[] password, OkHttpClient.Builder builder) {
        this.location = location;
        if (user == null || user.isEmpty() || user.contains(":")) throw new IllegalArgumentException("请填写 WebDAV 专用只读账号");
        authorization = Credentials.basic(user, new String(password), StandardCharsets.UTF_8);
        client = builder.followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build();
    }
    private Request.Builder request(String path, boolean directory) throws IOException {
        if (closed) throw new IOException("连接已关闭");
        return new Request.Builder().url(location.davPath(path, directory).toString()).header("Authorization", authorization).header("Accept-Encoding", "identity").header("Cache-Control", "no-store");
    }
    private Response execute(Request request) throws IOException {
        if (closed) throw new IOException("连接已关闭");
        Response response;
        try { response = client.newCall(request).execute(); } catch (IOException error) { throw new IOException("NAS 连接失败，请检查 HTTPS 证书、网络与服务端口"); }
        if (closed || response.code() == 401 || response.code() == 403 || response.isRedirect()) {
            int status = response.code(); response.close();
            throw new IOException(status == 401 || status == 403 ? "NAS 账号或目录权限未通过" : "连接已关闭或地址发生跳转，请填写最终 WebDAV 地址");
        }
        return response;
    }
    private static byte[] bounded(Response response, int maximum) throws IOException {
        if (response.body() == null || response.body().contentLength() > maximum) throw new IOException("NAS 返回内容过大");
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (InputStream in = response.body().byteStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) { if (result.size() + n > maximum) throw new IOException("NAS 返回内容过大"); result.write(buffer, 0, n); }
        }
        return result.toByteArray();
    }
    @Override public List<Entry> list(String path) throws IOException {
        String query = "<?xml version=\"1.0\" encoding=\"utf-8\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/><d:getcontentlength/></d:prop></d:propfind>";
        Request request = request(path, true).header("Depth", "1").method("PROPFIND", RequestBody.create(query, MediaType.get("application/xml; charset=utf-8"))).build();
        try (Response response = execute(request)) {
            if (response.code() != 207) throw new IOException("地址不是可读取的 WebDAV 目录（需要207响应）");
            String type = response.header("Content-Type", "").toLowerCase(Locale.ROOT);
            if (!type.startsWith("application/xml") && !type.startsWith("text/xml")) throw new IOException("WebDAV 返回了非目录内容");
            return parse(location, path, bounded(response, 1048576));
        }
    }
    static List<Entry> parse(NasLocation location, String parent, byte[] xml) throws IOException {
        try {
            // Accept UTF-8 only. Reject DTD/entities before parsing, including UTF-16 obfuscation.
            String source = new String(xml, StandardCharsets.UTF_8);
            String upper = source.toUpperCase(Locale.ROOT);
            if (source.indexOf('\0') >= 0 || upper.contains("<!DOCTYPE") || upper.contains("<!ENTITY") || source.indexOf('\uFFFD') >= 0) throw new IOException("不支持的目录文档");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true); factory.setExpandEntityReferences(false);
            try { factory.setFeature("http://xml.org/sax/features/external-general-entities", false); factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception unsupported) { /* DTD rejected above; resolver below always denies. */ }
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> { throw new org.xml.sax.SAXException("External entity denied"); });
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            Document document = builder.parse(new InputSource(new StringReader(source)));
            if (!"DAV:".equals(document.getDocumentElement().getNamespaceURI()) || !"multistatus".equals(document.getDocumentElement().getLocalName())) throw new IOException("不是 WebDAV 目录");
            NodeList responses = document.getElementsByTagNameNS("DAV:", "response");
            if (responses.getLength() > MAX_ENTRIES + 1) throw new IOException("文件夹超过200项，请拆成较小的相册或片库");
            URI current = location.davPath(parent, true); String prefix = current.getPath();
            List<Entry> result = new ArrayList<>(); Set<String> seen = new HashSet<>();
            for (int i = 0; i < responses.getLength(); i++) {
                Element item = (Element) responses.item(i); String href = text(item, "href"); if (href == null) continue;
                URI target = current.resolve(href);
                if (!Objects.equals(current.getScheme(), target.getScheme()) || !Objects.equals(current.getHost(), target.getHost()) || effectivePort(current) != effectivePort(target) || target.getRawUserInfo() != null || target.getRawQuery() != null || target.getRawFragment() != null) continue;
                String path = target.getPath(); if (path == null || !path.startsWith(prefix)) continue;
                String name = path.substring(prefix.length()); if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
                if (name.isEmpty() || name.contains("/")) continue;
                String child; try { child = location.child(parent, name); } catch (IllegalArgumentException invalid) { continue; }
                if (!seen.add(child)) continue;
                NodeList propstats = item.getElementsByTagNameNS("DAV:", "propstat");
                for (int j = 0; j < propstats.getLength(); j++) {
                    Element propstat = (Element) propstats.item(j); String status = text(propstat, "status");
                    if (status == null || !status.matches("HTTP/1\\.[01] 200(?: .*)?")) continue;
                    boolean directory = propstat.getElementsByTagNameNS("DAV:", "collection").getLength() > 0;
                    long size = -1; try { size = Long.parseLong(text(propstat, "getcontentlength")); } catch (Exception ignored) {}
                    result.add(new Entry(name, child, directory, size)); break;
                }
            }
            result.sort(Comparator.comparing((Entry e) -> !e.directory).thenComparing(e -> e.name)); return result;
        } catch (IOException error) { throw error; } catch (Exception invalid) { throw new IOException("WebDAV 目录格式无法读取"); }
    }
    private static int effectivePort(URI uri) { return uri.getPort() == -1 ? 443 : uri.getPort(); }
    private static String text(Element item, String name) { NodeList list = item.getElementsByTagNameNS("DAV:", name); return list.getLength() == 0 ? null : list.item(0).getTextContent().trim(); }
    @Override public Resource open(String path) throws IOException {
        long length; String etag;
        try (Response response = execute(request(path, false).head().build())) {
            if (response.code() != 200) throw new IOException("NAS 文件不可读取");
            try { length = Long.parseLong(response.header("Content-Length", "-1")); } catch (Exception invalid) { throw new IOException("NAS 未返回文件长度"); }
            if (length < 0) throw new IOException("NAS 未返回文件长度");
            etag = response.header("ETag"); if (etag != null && etag.startsWith("W/")) etag = null;
        }
        final long total = length; final String version = etag;
        return new Resource() {
            private volatile boolean ended;
            public long size() { return total; }
            public int readAt(long position, byte[] buffer, int offset, int count) throws IOException {
                NasReader.range(position, buffer, offset, count); if (ended || closed) throw new IOException("文件已关闭"); if (count == 0) return 0; if (position >= total) return -1;
                int wanted = (int) Math.min(Math.min(count, MAX_CHUNK), total - position); long end = position + wanted - 1;
                Request.Builder range = request(path, false).header("Range", "bytes=" + position + "-" + end); if (version != null) range.header("If-Match", version);
                try (Response response = execute(range.build())) {
                    if (response.code() != 206 || !Objects.equals(response.header("Content-Range"), "bytes " + position + "-" + end + "/" + total) || !"identity".equalsIgnoreCase(response.header("Content-Encoding", "identity")) || version != null && !version.equals(response.header("ETag"))) throw new IOException("NAS 不支持稳定的分段读取，文件可能已更新，请重新打开");
                    byte[] bytes = bounded(response, wanted); if (bytes.length != wanted || ended || closed) throw new IOException("NAS 文件读取中断"); System.arraycopy(bytes, 0, buffer, offset, wanted); return wanted;
                }
            }
            public void close() { ended = true; }
        };
    }
    @Override public void close() { closed = true; client.dispatcher().cancelAll(); client.connectionPool().evictAll(); client.dispatcher().executorService().shutdown(); }
}
