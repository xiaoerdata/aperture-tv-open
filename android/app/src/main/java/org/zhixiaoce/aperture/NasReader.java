package org.zhixiaoce.aperture;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/** Common read-only domain contract; UI holds no transport or URL credentials. */
interface NasReader extends Closeable {
    int MAX_ENTRIES = 200, MAX_CHUNK = 262144;
    final class Entry {
        final String name, path; final boolean directory; final long size;
        Entry(String name, String path, boolean directory, long size) { this.name = name; this.path = path; this.directory = directory; this.size = size; }
    }
    interface Resource extends Closeable {
        long size();
        int readAt(long position, byte[] buffer, int offset, int count) throws IOException;
    }
    List<Entry> list(String path) throws IOException;
    Resource open(String path) throws IOException;
    static NasReader connect(NasLocation location, String user, char[] password, String domain) throws IOException {
        return location.smb ? new SmbNasReader(location, user, password, domain) : new DavNasReader(location, user, password);
    }
    static void range(long position, byte[] buffer, int offset, int count) throws IOException {
        if (position < 0 || offset < 0 || count < 0 || offset > buffer.length - count) throw new IOException("读取范围无效");
    }
}
