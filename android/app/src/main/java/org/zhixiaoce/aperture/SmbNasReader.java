package org.zhixiaoce.aperture;

import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.Directory;
import com.hierynomus.smbj.share.File;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mssmb2.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** SMB2/3 only, signed authenticated sessions, no DFS or mutation operations. */
final class SmbNasReader implements NasReader {
    private final NasLocation location;
    private final SMBClient client;
    private Connection connection;
    private Session session;
    private DiskShare share;
    private volatile boolean closed;
    SmbNasReader(NasLocation location, String user, char[] password, String domain) throws IOException {
        this.location = location;
        if (user == null || user.isEmpty()) throw new IOException("请使用 NAS 专用只读账号；不支持来宾连接");
        client = new SMBClient(SmbConfig.builder().withSocketFactory(new NasSocketFactory()).withDialects(SMB2Dialect.SMB_2_0_2, SMB2Dialect.SMB_2_1, SMB2Dialect.SMB_3_0, SMB2Dialect.SMB_3_0_2, SMB2Dialect.SMB_3_1_1)
            .withSigningRequired(true).withDfsEnabled(false).withMultiProtocolNegotiate(false).withTimeout(8, TimeUnit.SECONDS).withSoTimeout(8000).withReadBufferSize(MAX_CHUNK).build());
        try { connection = client.connect(location.host, location.port); session = connection.authenticate(new AuthenticationContext(user, password, domain)); if(session.isGuest()||session.isAnonymous())throw new IOException("不使用来宾连接"); share = (DiskShare) session.connectShare(location.share); }
        catch (Exception error) { close(); throw new IOException(unavailable().getMessage(), error); }
    }
    private IOException unavailable() { return new IOException("NAS 暂不可读取：请检查网络、SMB 2/3、签名、账号及文件夹权限"); }
    private void check() throws IOException { if (closed) throw new IOException("连接已关闭"); }
    @Override public List<Entry> list(String path) throws IOException {
        check(); List<Entry> result = new ArrayList<>();
        try (Directory directory = share.openDirectory(location.smbPath(path), EnumSet.of(AccessMask.FILE_LIST_DIRECTORY, AccessMask.FILE_READ_ATTRIBUTES), EnumSet.noneOf(FileAttributes.class), SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, EnumSet.of(SMB2CreateOptions.FILE_DIRECTORY_FILE, SMB2CreateOptions.FILE_OPEN_REPARSE_POINT))) {
            Iterator<FileIdBothDirectoryInformation> iterator = directory.iterator(); int visited = 0;
            while (iterator.hasNext()) {
                check(); FileIdBothDirectoryInformation item = iterator.next(); if (++visited > MAX_ENTRIES + 2) throw new IOException("文件夹超过200项，请在 NAS 拆成较小的相册或片库");
                String name = item.getFileName(); if (name.equals(".") || name.equals("..")) continue;
                if ((item.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT.getValue()) != 0) continue;
                try { result.add(new Entry(name, location.child(path, name), (item.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0, item.getEndOfFile())); }
                catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException error) { throw error; } catch (Exception error) { throw new IOException(unavailable().getMessage(), error); }
        result.sort(Comparator.comparing((Entry e) -> !e.directory).thenComparing(e -> e.name)); return result;
    }
    @Override public Resource open(String path) throws IOException {
        check();
        try {
            File file = share.openFile(location.smbPath(path), EnumSet.of(AccessMask.FILE_READ_DATA, AccessMask.FILE_READ_ATTRIBUTES), EnumSet.noneOf(FileAttributes.class), SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE, SMB2CreateOptions.FILE_OPEN_REPARSE_POINT));
            long length;
            try {
                com.hierynomus.msfscc.fileinformation.FileAllInformation info = file.getFileInformation();
                if ((info.getBasicInformation().getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT.getValue()) != 0) throw new IOException("不读取链接文件");
                length = info.getStandardInformation().getEndOfFile();
            }
            catch (Exception error) { file.close(); throw error; }
            return new Resource() {
                private boolean ended;
                public long size() { return length; }
                public synchronized int readAt(long position, byte[] bytes, int offset, int count) throws IOException {
                    check(); NasReader.range(position, bytes, offset, count); if (ended) throw new IOException("文件已关闭"); if (count == 0) return 0; if (position >= length) return -1;
                    try { return file.read(bytes, position, offset, (int) Math.min(Math.min(count, MAX_CHUNK), length - position)); }
                    catch (RuntimeException error) { throw unavailable(); }
                }
                public synchronized void close() throws IOException { if (!ended) { ended = true; file.close(); } }
            };
        } catch (Exception error) { throw new IOException(unavailable().getMessage(), error); }
    }
    @Override public void close() {
        closed = true;
        try { if (share != null) share.close(); } catch (Exception ignored) {}
        try { if (session != null) session.close(); } catch (Exception ignored) {}
        try { if (connection != null) connection.close(true); } catch (Exception ignored) {}
        client.close();
    }
}
