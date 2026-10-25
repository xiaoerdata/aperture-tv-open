package org.zhixiaoce.aperture;

import java.io.IOException;
import java.util.UUID;
import org.json.JSONObject;

/** Stable, credential-free references into the currently saved NAS connection. */
final class NasWatchRoute {
    final JSONObject connection;
    final String path, root, folder;
    private NasWatchRoute(JSONObject connection,String path,String root) {
        this.connection=connection;this.path=path;this.root=root;
        int slash=path.lastIndexOf('/');folder=slash<0?"":path.substring(0,slash);
    }
    static JSONObject ref(String connectionId,String path,String root) throws Exception {
        if(!UUID.fromString(connectionId).toString().equals(connectionId))throw new IOException("NAS 连接标识无效");
        NasLocation.relative(path);NasLocation.relative(root);
        if(path.isEmpty()||!NasLocation.kind(path).equals("video"))throw new IOException("这条记录不是 NAS 影片");
        if(!root.isEmpty()&&!path.startsWith(root+"/"))throw new IOException("影片不在原片库内");
        String title=path.substring(path.lastIndexOf('/')+1);
        if(title.length()>200)title=title.substring(0,199)+"…";
        return CinemaShelf.ref("nas",connectionId,path,title,"NAS").put("root",root);
    }
    static NasWatchRoute resolve(JSONObject saved,NasLibrary library) throws Exception {
        if(!"nas".equals(saved.optString("kind")))throw new IOException("这条记录不是 NAS 影片");
        JSONObject checked=ref(saved.getString("source"),saved.getString("id"),saved.optString("root"));
        JSONObject connection=library.find(checked.getString("source"));
        if(connection==null)throw new IOException("原 NAS 连接已移除，请重新添加并从片库打开影片");
        return new NasWatchRoute(connection,checked.getString("id"),checked.getString("root"));
    }
}
