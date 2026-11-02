package org.zhixiaoce.aperture;

/** Implemented by the real session; isolated UI tests supply a launcher which never binds a port. */
interface GalleryUploadSession extends AutoCloseable {
    String start() throws Exception;
    boolean isClosed();
    @Override void close();
}
