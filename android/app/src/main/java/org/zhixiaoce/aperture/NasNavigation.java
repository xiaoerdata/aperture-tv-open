package org.zhixiaoce.aperture;

import java.util.LinkedHashMap;

/** A selected home folder is the browsing root; returning preserves the prior page and item. */
final class NasNavigation {
    static final class Position {
        final int page; final String focus;
        Position(int page, String focus) { this.page = Math.max(0, page); this.focus = focus; }
    }
    private String root = "";
    private final LinkedHashMap<String, Position> history = new LinkedHashMap<>();
    void reset(String path) { root = NasLocation.relative(path); history.clear(); }
    boolean contains(String path) { NasLocation.relative(path); return root.isEmpty() || path.equals(root) || path.startsWith(root + "/"); }
    void save(String path, int page, String focus) {
        if (!contains(path)) throw new IllegalArgumentException("不在当前片库内");
        history.remove(path); history.put(path, new Position(page, focus));
        if (history.size() > 64) history.remove(history.keySet().iterator().next());
    }
    Position position(String path) { if (!contains(path)) throw new IllegalArgumentException("不在当前片库内"); return history.containsKey(path) ? history.get(path) : new Position(0, ""); }
    String parent(String path) {
        if (!contains(path)) throw new IllegalArgumentException("不在当前片库内"); if (path.equals(root)) return null;
        int slash = path.lastIndexOf('/'); String parent = slash < 0 ? "" : path.substring(0, slash); return contains(parent) ? parent : null;
    }
    String root() { return root; }
}
