package org.zhixiaoce.aperture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.util.Log;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;

/** Local, version-scoped diagnostics. Only fixed enums and package/version metadata are recorded. */
final class WebDiagnostics {
    enum Reason { LOAD_TIMEOUT, JS_MISSING, MAIN_DOCUMENT_ERROR, HTTP_ERROR, RENDERER_GONE, WEBVIEW_CREATE_ERROR, USER_CANCEL_LOAD }
    enum Home { GLASS_LOADING, GLASS_READY, NATIVE_SELECTED, NATIVE_OLD_ENGINE, NATIVE_RECOVERY }
    private final SharedPreferences prefs;private Home home=Home.NATIVE_RECOVERY;
    WebDiagnostics(Context context){
        prefs=context.getSharedPreferences("kaishi_web_diagnostics",Context.MODE_PRIVATE);
        // An old release's failure is not a new release's acceptance result.
        if(prefs.getInt("versionCode",-1)!=BuildConfig.VERSION_CODE)prefs.edit().clear().putInt("versionCode",BuildConfig.VERSION_CODE).apply();
    }
    void mode(Home value){home=value;}
    Home mode(){return home;}
    void event(String screen,String event,long generation,Reason reason){
        if(!"home".equals(screen)&&!"gallery".equals(screen))throw new IllegalArgumentException("Unknown screen");
        if(!"ready".equals(event)&&!"timeout".equals(event)&&!"renderer-gone".equals(event)&&!"fallback".equals(event)&&!"loading".equals(event))throw new IllegalArgumentException("Unknown event");
        if(reason!=null&&reason!=Reason.USER_CANCEL_LOAD)prefs.edit().putString("reason",reason.name()).putString("screen",screen).putLong("at",System.currentTimeMillis()).apply();
        JSONObject entry=new JSONObject();try{entry.put("versionCode",BuildConfig.VERSION_CODE).put("screen",screen).put("event",event).put("generation",generation);if(reason!=null)entry.put("reason",reason.name());}catch(Exception ignored){}
        Log.i("KaishiWeb",entry.toString());
    }
    private String reason(){
        String value=prefs.getString("reason","");
        try{return label(Reason.valueOf(value));}catch(IllegalArgumentException missing){return "无";}
    }
    private static String label(Reason r){switch(r){
        case LOAD_TIMEOUT:return "加载超过前台等待期限";case JS_MISSING:return "页面交互入口未就绪";
        case MAIN_DOCUMENT_ERROR:return "主页面加载失败";case HTTP_ERROR:return "主页面返回错误";
        case RENDERER_GONE:return "网页显示进程退出";case WEBVIEW_CREATE_ERROR:return "网页组件未能创建";
        default:return "主动切换本机首页";
    }}
    private String homeLabel(){switch(home){
        case GLASS_LOADING:return "玻璃首页 · 正在加载";case GLASS_READY:return "玻璃首页 · 已就绪";
        case NATIVE_SELECTED:return "本机风景首页 · 用户选择";case NATIVE_OLD_ENGINE:return "本机风景首页 · 旧网页内核";
        default:return "本机风景首页 · 异常恢复";
    }}
    static String provider(){
        try{PackageInfo info=WebView.getCurrentWebViewPackage();if(info!=null)return safe(info.packageName)+"\n"+safe(info.versionName);}catch(RuntimeException ignored){}
        return "不可确认";
    }
    private static String safe(String value){return value!=null&&value.matches("[A-Za-z0-9._+\\-]{1,120}")?value:"不可确认";}
    AlertDialog show(Activity activity){return show(activity,activity.getCurrentFocus());}
    AlertDialog show(Activity activity,android.view.View returnFocus){
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        row(activity,body,"开视版本",BuildConfig.VERSION_NAME+" · "+BuildConfig.VERSION_CODE);
        row(activity,body,"WebView",provider());row(activity,body,"当前首页",homeLabel());
        String screen=prefs.getString("screen","");row(activity,body,"最近回退",("gallery".equals(screen)?"相册 · ":"home".equals(screen)?"首页 · ":"")+reason());
        long at=prefs.getLong("at",0);row(activity,body,"回退时间",at>0?DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.MEDIUM).format(new Date(at)):"无");
        row(activity,body,"说明","仅保存在本机，不上传。旧网页内核、手动选择本机首页和异常恢复分别显示；升级后重新记录本版本状态。此处不包含片源、照片、地址参数或配对凭证。");
        return TvDialogs.panel(activity,"本机诊断",body,returnFocus);
    }
    private void row(Activity a,LinearLayout parent,String name,String value){
        TextView line=TvUi.text(a,name+"\n"+value,21,false);line.setTag("diagnostics-"+name);line.setFocusable(true);line.setFocusableInTouchMode(true);line.setPadding(TvUi.dp(a,12),TvUi.dp(a,12),TvUi.dp(a,12),TvUi.dp(a,18));
        line.setOnFocusChangeListener((v,f)->{v.setBackground(f?TvUi.glass(a,true):null);if(f)v.requestRectangleOnScreen(new android.graphics.Rect(0,0,v.getWidth(),Math.min(v.getHeight(),TvUi.dp(a,150))),true);});
        parent.addView(line,new LinearLayout.LayoutParams(-1,-2));
    }
}
