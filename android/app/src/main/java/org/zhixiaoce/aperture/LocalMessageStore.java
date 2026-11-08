package org.zhixiaoce.aperture;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

final class LocalMessageStore implements MessagePhoneSession.Notes {
    private static final Object LOCK=new Object();
    private final SharedPreferences prefs;
    LocalMessageStore(Context context){prefs=context.getApplicationContext().getSharedPreferences("kaishi_local_notes",Context.MODE_PRIVATE);}
    public JSONObject read() throws Exception {synchronized(LOCK){String raw=prefs.getString("notes","");if(raw.isEmpty())return MessageNotes.empty();JSONObject state=new JSONObject(raw);new MessageFeed(raw);state.getLong("revision");return state;}}
    public JSONObject edit(JSONObject input,BooleanSupplier allowed) throws Exception {synchronized(LOCK){
        JSONObject next=MessageNotes.edit(read(),input);if(!allowed.getAsBoolean())throw new IOException("扫码编辑已关闭");
        if(!prefs.edit().putString("notes",next.toString()).commit())throw new IOException("保存失败，请重试");return next;
    }}
}
