package org.zhixiaoce.aperture;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import android.widget.LinearLayout;
import org.json.JSONArray;
import org.json.JSONObject;

/** Small local-only journal. Input types exclude arbitrary user strings. No telemetry or file cleanup. */
final class AndroidGalleryTrace {
    private static final Object OWNER_LOCK=new Object();private final GalleryTrace trace=new GalleryTrace();private final SharedPreferences prefs;private final long session=SystemClock.elapsedRealtimeNanos();private final int providerMajor;
    AndroidGalleryTrace(Context c){prefs=c.getSharedPreferences("kaishi_gallery_stages",0);synchronized(OWNER_LOCK){prefs.edit().putLong("session",session).apply();}int major=0;try{android.content.pm.PackageInfo p=android.webkit.WebView.getCurrentWebViewPackage();if(p!=null)major=Integer.parseInt(p.versionName.split("\\.",2)[0]);}catch(RuntimeException ignored){}providerMajor=Math.max(0,Math.min(10000,major));}
    void record(GalleryTrace.Stage stage,GalleryTrace.Asset asset,GalleryTrace.Result result,long generation,boolean resumed,boolean focused,PageDeadline.Snapshot budget,boolean matched,int line){synchronized(OWNER_LOCK){
        if(prefs.getLong("session",-1)!=session)return;
        GalleryTrace.Entry e=new GalleryTrace.Entry(stage,asset,result,generation,SystemClock.uptimeMillis(),budget,resumed,focused,matched,line);
        if(!trace.add(e))return;
        try{JSONArray list=new JSONArray();for(GalleryTrace.Entry entry:trace.snapshot())list.put(json(entry));String encoded=list.toString();prefs.edit().putInt("versionCode",BuildConfig.VERSION_CODE).putString("events",encoded).apply();Log.i("KaishiGallery",json(e).toString());}catch(Exception ignored){}
    }}
    private JSONObject json(GalleryTrace.Entry e)throws Exception{return new JSONObject().put("versionCode",BuildConfig.VERSION_CODE).put("screen","gallery").put("session",session).put("providerMajor",providerMajor).put("generation",e.generation).put("uptime",e.uptime).put("stage",e.stage.name()).put("asset",e.asset.name()).put("result",e.result.name()).put("resumed",e.resumed).put("focused",e.focused).put("active",e.active).put("budgetObserved",e.budgetObserved).put("armed",e.armed).put("matched",e.matched).put("remainingMs",e.remaining).put("dueAt",e.dueAt).put("callbackDueAt",e.callbackDueAt).put("callbackAt",e.callbackAt).put("dispatchMeasured",e.callbackAt>=0).put("dispatchDelayMs",e.delay).put("line",e.line);}
    static void show(Activity a,android.view.View opener){
        SharedPreferences p=a.getSharedPreferences("kaishi_gallery_stages",0);LinearLayout rows=new LinearLayout(a);rows.setOrientation(1);
        rows.addView(TvUi.text(a,"仅记录本机加载阶段，不含照片、地址和上传凭证。最近一次相册会话，最多160条。缺少阶段时根因仍可能未知。",20,true));
        try{JSONArray events=new JSONArray(p.getString("events","[]"));for(int i=0;i<events.length();i++){JSONObject e=events.getJSONObject(i);android.widget.TextView row=TvUi.text(a,e.optLong("uptime")+" · "+safe(e.optString("stage"))+" · "+safe(e.optString("asset"))+"\n代次 "+e.optLong("generation")+" · 余量 "+e.optLong("remainingMs")+"ms · 调度延迟 "+e.optLong("dispatchDelayMs")+"ms\n"+safe(e.optString("result"))+" · 匹配 "+e.optBoolean("matched")+" · 前台 "+e.optBoolean("resumed")+" · 焦点 "+e.optBoolean("focused"),18,false);row.setFocusableInTouchMode(true);rows.addView(row);}}catch(Exception ignored){rows.addView(TvUi.text(a,"暂无阶段记录",22,true));}
        TvDialogs.panel(a,"相册加载记录 · v"+p.getInt("versionCode",0),rows,opener);
    }
    private static String safe(String value){return value.matches("[A-Z_]{1,40}")?value:"UNKNOWN";}
}
