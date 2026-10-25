package org.zhixiaoce.aperture;

/** Read-modify-write under one process-wide lock; callers never persist stale Activity snapshots. */
final class CinemaShelfStore {
    interface Storage { String read() throws Exception; void write(String json) throws Exception; }
    interface Edit { void apply(CinemaShelf shelf) throws Exception; }
    private final Object lock;
    private final Storage storage;
    CinemaShelfStore(Object lock, Storage storage) { this.lock=lock; this.storage=storage; }
    CinemaShelf read() throws Exception { synchronized(lock) { return new CinemaShelf(storage.read()); } }
    CinemaShelf update(Edit edit) throws Exception {
        synchronized(lock) {
            CinemaShelf latest=new CinemaShelf(storage.read());
            edit.apply(latest);
            storage.write(latest.json());
            return latest;
        }
    }
}
