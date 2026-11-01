package org.zhixiaoce.aperture;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;

/** Private local album. A native return path remains usable when JavaScript or its renderer stops. */
public class GalleryActivity extends Activity {
    static final long LOAD_TIMEOUT_MS=10000,BACK_TIMEOUT_MS=600;
    private static final String DOCUMENT="https://"+LobbyAssets.HOST+"/assets/gallery.html";
    private volatile WebView web;private GalleryStore store;private GalleryUpload upload;private AlertDialog notice;
    private volatile boolean closed;private boolean resumed,ready,documentFinished;
    private PageDeadline loadDeadline,backDeadline;private PageDeadline.Ticket navigation,backTicket;
    private WebLoadingView loading;private WebDiagnostics diagnostics;
    private PageDeadline.Scheduler pageTasks;private Runnable probeRetry;
    // Package-visible factories permit isolated probes without exported fault-injection intents.
    protected WebView newWebView(){return new WebView(this);}
    protected PageDeadline.Clock deadlineClock(){return AndroidPageDeadline.clock();}
    protected PageDeadline.Scheduler deadlineScheduler(){return AndroidPageDeadline.scheduler();}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().getDecorView().setSystemUiVisibility(5894);
        diagnostics=new WebDiagnostics(this);store=new GalleryStore(this);
        pageTasks=deadlineScheduler();
        loadDeadline=new PageDeadline(LOAD_TIMEOUT_MS,deadlineClock(),pageTasks,ticket->{if(ticket.owner==web)fail(WebDiagnostics.Reason.LOAD_TIMEOUT,false);});
        backDeadline=new PageDeadline(BACK_TIMEOUT_MS,deadlineClock(),pageTasks,ticket->{if(ticket.owner==web)finishAlbum();});
        try{createPage();}catch(RuntimeException unavailable){fail(WebDiagnostics.Reason.WEBVIEW_CREATE_ERROR,false);}
    }
    private boolean current(WebView value){return !closed&&value==web&&loadDeadline.current(navigation);}
    private void createPage(){
        web=newWebView();final WebView created=web;navigation=loadDeadline.start(created);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setTextZoom(100);settings.setUseWideViewPort(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                if(!current(view))return blocked();
                WebResourceResponse gallery=store.read(request);if(gallery!=null)return gallery;
                Uri uri=request.getUrl();String file=LobbyAssets.file(uri.getScheme(),uri.getHost(),uri.getPort(),uri.getPath());
                if("GET".equals(request.getMethod())&&file!=null&&uri.getQuery()==null&&uri.getFragment()==null&&uri.getUserInfo()==null)
                    try{return new WebResourceResponse(LobbyAssets.mime(file),"UTF-8",200,"OK",Collections.singletonMap("Cache-Control","no-store"),getAssets().open(file));}catch(IOException ignored){}
                return blocked();
            }
            @Override public void onPageFinished(WebView view,String url){
                if(!current(view)||!DOCUMENT.equals(url)||!loadDeadline.waiting(navigation))return;
                documentFinished=true;probe(view,navigation,0);
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){
                if(request.isForMainFrame()&&current(view))fail(WebDiagnostics.Reason.MAIN_DOCUMENT_ERROR,false);
            }
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){
                if(request.isForMainFrame()&&current(view))fail(WebDiagnostics.Reason.HTTP_ERROR,false);
            }
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){
                if(current(view))fail(WebDiagnostics.Reason.RENDERER_GONE,true);else WebViewDisposal.release(view,true);
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();if(!current(view)||!ready||!request.isForMainFrame()||!DOCUMENT.equals(view.getUrl())||!"aperture".equals(uri.getScheme())||!"gallery".equals(uri.getHost())||uri.getQuery()!=null||uri.getFragment()!=null||uri.getUserInfo()!=null)return true;
                String path=uri.getPath();
                if("/back".equals(path)){finishAlbum();return true;}
                if("/upload".equals(path)){beginUpload();return true;}
                if("/close-upload".equals(path)){stopUpload(true);refresh();return true;}
                if(path!=null&&path.matches("/delete/[a-f0-9]{32}")){
                    if(notice!=null&&notice.isShowing())return true;String id=path.substring(8);
                    notice=new AlertDialog.Builder(GalleryActivity.this).setTitle("删除这张照片？").setMessage("从电视相册中删除，手机原图不受影响。")
                        .setNegativeButton("保留",null).setPositiveButton("删除",(d,v)->{try{store.delete(id);refresh();}catch(Exception error){status("删除失败，请重试");}}).create();
                    notice.show();notice.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();return true;
                }
                if(path!=null&&path.matches("/set/(template/(fade|drift|diptych)|seconds/(8|12|20))")){
                    String[] bits=path.split("/");try{store.option(bits[2],bits[3]);refresh();}catch(Exception error){status("设置未能保存");}
                }
                return true;
            }
        });
        loading=new WebLoadingView(this,web,"正在打开相册","最迟等待 10 秒，返回键随时可退出。","返回首页",this::finishAlbum);
        setContentView(loading.root);diagnostics.event("gallery","loading",navigation.generation,null);web.loadUrl(DOCUMENT);
    }
    private WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
    private void probe(WebView view,PageDeadline.Ticket ticket,int attempt){
        if(!resumed||!current(view)||!loadDeadline.waiting(ticket))return;
        try{view.evaluateJavascript("!!(window.KaishiGallerySettings&&typeof window.KaishiGallerySettings.key==='function'&&window.KaishiGallery&&typeof window.KaishiGallery.close==='function')",result->{
            if(!resumed||!current(view)||!loadDeadline.waiting(ticket))return;
            if("true".equals(result)){
                loadDeadline.ready(ticket);ready=true;if(loading!=null)loading.ready();diagnostics.event("gallery","ready",ticket.generation,null);
                view.evaluateJavascript("window.KaishiGallery.active(true)",null);
            }else if(attempt<2){probeRetry=()->{probeRetry=null;probe(view,ticket,attempt+1);};pageTasks.post(probeRetry,250);}
            else fail(WebDiagnostics.Reason.JS_MISSING,false);
        });}catch(RuntimeException unavailable){fail(WebDiagnostics.Reason.JS_MISSING,false);}
    }
    private void fail(WebDiagnostics.Reason reason,boolean rendererGone){
        if(closed||web==null&&reason!=WebDiagnostics.Reason.WEBVIEW_CREATE_ERROR)return;
        long generation=navigation==null?0:navigation.generation;
        diagnostics.event("gallery",rendererGone?"renderer-gone":reason==WebDiagnostics.Reason.LOAD_TIMEOUT?"timeout":"fallback",generation,reason);
        stopUpload(false);dispose(rendererGone);
        if(notice!=null){notice.dismiss();notice=null;}
        LinearLayout body=new LinearLayout(this);body.setTag("kaishi-gallery-error");body.setOrientation(LinearLayout.VERTICAL);
        int pad=TvUi.dp(this,38);body.setPadding(pad,pad,pad,pad);body.setBackgroundColor(android.graphics.Color.rgb(8,15,21));
        body.addView(TvUi.text(this,"相册暂不可用",30,false));
        body.addView(TvUi.text(this,reason==WebDiagnostics.Reason.LOAD_TIMEOUT?"相册加载超时。已保存的照片和设置仍保留。":reason==WebDiagnostics.Reason.RENDERER_GONE?"网页显示进程退出。已保存的照片和设置仍保留。":"页面未能正常加载。已保存的照片和设置仍保留。",22,true));
        android.widget.Button back=TvUi.button(this,"返回首页",this::finishAlbum);body.addView(back);setContentView(body);back.requestFocus();
    }
    private void dispose(boolean rendererGone){
        if(loadDeadline!=null)loadDeadline.cancel();if(backDeadline!=null)backDeadline.cancel();
        if(probeRetry!=null){pageTasks.cancel(probeRetry);probeRetry=null;}
        ready=false;documentFinished=false;loading=null;WebView previous=web;web=null;WebViewDisposal.release(previous,rendererGone);
    }
    private void finishAlbum(){if(closed)return;closed=true;stopUpload(false);dispose(false);finish();}
    private void refresh(){if(!closed&&ready&&web!=null)web.evaluateJavascript("window.KaishiGallerySettings&&window.KaishiGallerySettings.refresh()",null);}
    private void status(String text){if(!closed&&ready&&web!=null)web.evaluateJavascript("window.KaishiGallerySettings&&window.KaishiGallerySettings.status("+JSONObject.quote(text)+")",null);}
    private void stopUpload(boolean notify){
        if(upload!=null){upload.close();upload=null;}
        if(notify&&!closed&&ready&&web!=null)web.evaluateJavascript("window.KaishiGallerySettings&&window.KaishiGallerySettings.uploadClosed()",null);
    }
    private void beginUpload(){
        if(closed||!ready||!resumed)return;
        stopUpload(true);GalleryUpload next=new GalleryUpload(this,()->runOnUiThread(this::refresh));upload=next;
        new Thread(()->{try{
            String url=next.start();runOnUiThread(()->{if(closed||!resumed||upload!=next||!ready||web==null){next.close();return;}
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);web.evaluateJavascript("window.KaishiGallerySettings.upload("+JSONObject.quote(url)+")",null);});
        }catch(Exception error){next.close();runOnUiThread(()->{if(upload==next){upload=null;status("无法开启上传，请检查电视与手机的家庭网络");}});}},"Kaishi-album-session").start();
    }
    @Override protected void onResume(){
        super.onResume();resumed=true;loadDeadline.active(true);backDeadline.active(true);
        if(web!=null){web.onResume();web.resumeTimers();if(ready)web.evaluateJavascript("window.KaishiGallery&&window.KaishiGallery.active(true)",null);else if(documentFinished)probe(web,navigation,0);}
    }
    @Override protected void onPause(){
        resumed=false;loadDeadline.active(false);backDeadline.cancel();backDeadline.active(false);
        if(probeRetry!=null){pageTasks.cancel(probeRetry);probeRetry=null;}
        stopUpload(true);
        if(web!=null){if(ready)web.evaluateJavascript("window.KaishiGallery&&window.KaishiGallery.active(false)",null);web.onPause();web.pauseTimers();}
        super.onPause();
    }
    @Override public void onBackPressed(){
        if(closed)return;if(web==null||!ready){finishAlbum();return;}
        if(backDeadline.waiting(backTicket)){finishAlbum();return;}
        WebView current=web;PageDeadline.Ticket ticket=backTicket=backDeadline.start(current);
        try{current.evaluateJavascript("!!(window.KaishiGallery&&window.KaishiGallery.close())",value->{
            if(closed||current!=web||!backDeadline.ready(ticket))return;
            backDeadline.cancel();if(!"true".equals(value))finishAlbum();
        });}catch(RuntimeException unavailable){finishAlbum();}
    }
    private String remoteKey(int code){return code==19?"ArrowUp":code==20?"ArrowDown":code==21?"ArrowLeft":code==22?"ArrowRight":code==23||code==66||code==160||code==96?"Enter":null;}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int code=event.getKeyCode();if(remoteKey(code)!=null&&ready&&web!=null&&getWindow().getDecorView().hasWindowFocus()&&getCurrentFocus()==web){
            if(event.getAction()==KeyEvent.ACTION_DOWN)return onKeyDown(code,event);if(event.getAction()==KeyEvent.ACTION_UP)return onKeyUp(code,event);
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean onKeyDown(int code,KeyEvent event){
        if(VoiceAssistant.systemVoiceKey(code))return super.onKeyDown(code,event);
        if(code==KeyEvent.KEYCODE_BACK){if(event.getRepeatCount()==0)onBackPressed();return true;}
        String key=remoteKey(code);if(key!=null&&web!=null&&ready){if(!key.equals("Enter")||event.getRepeatCount()==0)web.evaluateJavascript("window.KaishiGallerySettings&&window.KaishiGallerySettings.key("+JSONObject.quote(key)+")",null);return true;}
        return super.onKeyDown(code,event);
    }
    @Override public boolean onKeyUp(int code,KeyEvent event){if(code==KeyEvent.KEYCODE_BACK||remoteKey(code)!=null&&ready&&web!=null&&getCurrentFocus()==web)return true;return super.onKeyUp(code,event);}
    @Override protected void onDestroy(){closed=true;stopUpload(false);dispose(false);if(notice!=null)notice.dismiss();super.onDestroy();}
}
