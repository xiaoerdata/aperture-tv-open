package org.zhixiaoce.aperture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.*;

/** Bounded local notes with revision checks. External HTTPS feeds are never edited. */
final class MessageNotes {
    static JSONObject empty() throws JSONException {return new JSONObject().put("version",1).put("revision",0).put("messages",new JSONArray());}
    static JSONObject edit(JSONObject current,JSONObject command) throws Exception {
        if(!(command.opt("action") instanceof String)||command.has("id")&&!(command.opt("id") instanceof String))throw new IOException("操作字段无效");
        if(!(command.opt("revision") instanceof Number)||command.getDouble("revision")!=current.getLong("revision"))throw new Conflict();
        String action=command.getString("action"),id=command.optString("id","");
        JSONArray rows=current.getJSONArray("messages"),next=new JSONArray();boolean found=false;
        if(!action.equals("save")&&!action.equals("delete"))throw new IOException("操作无效");
        if(action.equals("save")&&!(command.opt("text") instanceof String))throw new IOException("留言需为文字");
        String text=action.equals("save")?command.getString("text").trim():"";
        if(action.equals("save")&&(text.isEmpty()||text.length()>500||text.indexOf('\0')>=0))throw new IOException("请输入1至500字的留言");
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);
            if(row.getString("id").equals(id)){found=true;if(action.equals("save"))next.put(new JSONObject(row.toString()).put("text",text));}
            else next.put(new JSONObject(row.toString()));
        }
        if(!found){
            if(!id.isEmpty()||action.equals("delete"))throw new IOException("留言已不存在，请刷新");
            if(next.length()>=100)throw new IOException("最多保存100条留言");
            next.put(new JSONObject().put("id","local-"+UUID.randomUUID()).put("text",text).put("time","").put("tts",false));
        }
        JSONObject result=new JSONObject().put("version",1).put("revision",current.getLong("revision")+1).put("messages",next);
        if(result.toString().getBytes(StandardCharsets.UTF_8).length>131072)throw new IOException("留言空间已满，请先删减");
        new MessageFeed(result.toString());return result;
    }
    static final class Conflict extends IOException {Conflict(){super("留言已在其他手机更新，请刷新后重试");}}
}
