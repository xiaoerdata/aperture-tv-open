package org.zhixiaoce.aperture;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Device-local library model. Remains a v5-compatible array; bindings never use list indexes. */
final class NasLibrary {
    static final String[] ROLES = {"movies", "series", "photos"};
    static final class Route {
        final String id, path;
        final JSONObject connection;
        Route(JSONObject connection, String path) throws Exception {
            this.connection = new JSONObject(connection.toString()); this.id = connection.getString("id"); this.path = path;
        }
    }
    private final JSONArray connections;
    final boolean upgraded;
    NasLibrary(String json) throws Exception {
        connections = new JSONArray(json); if (connections.length() > 8) throw new IllegalArgumentException("连接记录数量异常");
        Set<String> ids = new HashSet<>(); boolean changed = false;
        for (int i = 0; i < connections.length(); i++) {
            JSONObject item = connections.getJSONObject(i);
            if (!item.has("id")) { item.put("id", UUID.randomUUID().toString()); changed = true; }
            String id = item.getString("id");
            if (!UUID.fromString(id).toString().equals(id) || !ids.add(id)) throw new IllegalArgumentException("连接标识异常");
            if (item.has("home") && !(item.get("home") instanceof JSONObject)) throw new IllegalArgumentException("观看入口记录异常");
        }
        upgraded = changed;
        for (String role : ROLES) route(role); // Corrupt/ambiguous bindings fail closed before routing.
    }
    static boolean role(String value) { return "movies".equals(value) || "series".equals(value) || "photos".equals(value); }
    static String title(String role) { if ("movies".equals(role)) return "看电影"; if ("series".equals(role)) return "看电视剧"; if ("photos".equals(role)) return "NAS 相册"; throw new IllegalArgumentException("未知入口"); }
    JSONArray connections() throws Exception { return new JSONArray(connections.toString()); }
    JSONObject find(String id) throws Exception {
        for (int i = 0; i < connections.length(); i++) { JSONObject item = connections.getJSONObject(i); if (item.getString("id").equals(id)) return new JSONObject(item.toString()); }
        return null;
    }
    String json() { return connections.toString(); }
    JSONObject add(JSONObject input) throws Exception {
        if (connections.length() >= 8) throw new IllegalStateException("最多保存8个连接");
        JSONObject item = new JSONObject(input.toString()); item.remove("home"); item.put("id", UUID.randomUUID().toString()); connections.put(item); return new JSONObject(item.toString());
    }
    JSONObject addDraft(NasDraft draft) throws Exception {
        JSONObject saved=add(draft.connection);if(!draft.role.isEmpty())assign(draft.role,saved.getString("id"),"");return saved;
    }
    void remove(String id) throws Exception {
        for (int i = 0; i < connections.length(); i++) if (connections.getJSONObject(i).getString("id").equals(id)) { connections.remove(i); return; }
        throw new IllegalArgumentException("连接已被移除");
    }
    Route route(String role) throws Exception {
        if (!role(role)) throw new IllegalArgumentException("未知入口"); Route result = null;
        for (int i = 0; i < connections.length(); i++) {
            JSONObject item = connections.getJSONObject(i); JSONObject home = item.optJSONObject("home");
            if (home != null && home.has(role)) {
                if (result != null || !(home.get(role) instanceof String)) throw new IllegalArgumentException("观看入口记录异常");
                result = new Route(item, NasLocation.relative(home.getString(role)));
            }
        }
        return result;
    }
    void assign(String role, String id, String path) throws Exception {
        if (!role(role)) throw new IllegalArgumentException("未知入口");
        if (find(id) == null) throw new IllegalArgumentException("连接已被移除"); NasLocation.relative(path);
        clear(role);
        for (int i = 0; i < connections.length(); i++) {
            JSONObject item = connections.getJSONObject(i);
            if (item.getString("id").equals(id)) { JSONObject home = item.optJSONObject("home"); if (home == null) home = new JSONObject(); home.put(role, path); item.put("home", home); }
        }
    }
    void clear(String role) throws Exception {
        if (!role(role)) throw new IllegalArgumentException("未知入口");
        for (int i = 0; i < connections.length(); i++) { JSONObject home = connections.getJSONObject(i).optJSONObject("home"); if (home != null) home.remove(role); }
    }
}
