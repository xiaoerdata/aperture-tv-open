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
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import android.widget.LinearLayout;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;

/** Private local album. A native return path remains usable when JavaScript or its renderer stops. */
public class GalleryActivity extends Activity {
    static final long LOAD_TIMEOUT_MS=10000,BACK_TIMEOUT_MS=600;
    private static final String DOCUMENT="https://"+LobbyAssets.HOST+"/assets/gallery.html";
    private volatile WebView web;private GalleryStore store;private GalleryUploadSession upload;private AlertDialog notice,uploadConfirmation;
    private final UploadConsent consent=new UploadConsent();private UploadConsent.Ticket consentTicket;
    private volatile boolean closed,resumed;private boolean ready,documentFinished;
    private PageDeadline loadDeadline,backDeadline;private PageDeadline.Ticket navigation,backTicket;
    private WebLoadingView loading;private WebDiagnostics diagnostics;private AndroidGalleryTrace stages;private NativeGallery nativeGallery;private volatile boolean focused;
    private PageDeadline.Scheduler pageTasks;private Runnable probeRetry;
    // Package-visible factories permit isolated probes without exported fault-injection intents.
    protected WebView newWebView(){return new WebView(this);}
    protected PageDeadline.Clock deadlineClock(){return AndroidPageDeadline.clock();}
    protected PageDeadline.Scheduler deadlineScheduler(){return AndroidPageDeadline.scheduler();}
    protected GalleryUploadSession createUploadSession(Runnable changed){return new GalleryUpload(this,changed);}
    protected boolean useWebGallery(){try{android.content.pm.PackageInfo p=WebView.getCurrentWebViewPackage();return p!=null&&Integer.parseInt(p.versionName.split("\\.",2)[0])>=89;}catch(RuntimeException unavailable){return false;}}
    private void stage(GalleryTrace.Stage value){stage(value,GalleryTrace.Asset.NONE,GalleryTrace.Result.NONE,false,0);}
    private void stage(GalleryTrace.Stage value,GalleryTrace.Asset asset,GalleryTrace.Result result,boolean matched,int line){if(stages!=null)stages.record(value,asset,result,navigation==null?0:navigation.generation,resumed,focused,android.os.Looper.myLooper()==android.os.Looper.getMainLooper()&&loadDeadline!=null?loadDeadline.snapshot():null,matched,line);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().getDecorView().setSystemUiVisibility(5894);
        diagnostics=new WebDiagnostics(this);store=new GalleryStore(this);stages=new AndroidGalleryTrace(this);stage(GalleryTrace.Stage.CREATE);
        pageTasks=deadlineScheduler();
        loadDeadline=new PageDeadline(LOAD_TIMEOUT_MS,deadlineClock(),pageTasks,ticket->{if(ticket.owner==web){stage(GalleryTrace.Stage.DEADLINE_EXPIRE);fail(WebDiagnostics.Reason.LOAD_TIMEOUT,false);}});
        backDeadline=new PageDeadline(BACK_TIMEOUT_MS,deadlineClock(),pageTasks,ticket->{if(ticket.owner==web)finishAlbum();});
        if(!useWebGallery()){stage(GalleryTrace.Stage.ROUTE_NATIVE);showNative(false);return;}
        stage(GalleryTrace.Stage.ROUTE_WEB);try{createPage();}catch(RuntimeException unavailable){fail(WebDiagnostics.Reason.WEBVIEW_CREATE_ERROR,false);}
    }
    private boolean current(WebView value){return !closed&&value==web&&loadDeadline.current(navigation);}
    private void createPage(){
        web=newWebView();web.setTag("gallery-web-root");final WebView created=web;navigation=loadDeadline.start(created);stage(GalleryTrace.Stage.DEADLINE_START);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setTextZoom(100);settings.setUseWideViewPort(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){if(current(view))stage(GalleryTrace.Stage.DOCUMENT_START,GalleryTrace.Asset.DOCUMENT,GalleryTrace.Result.NONE,DOCUMENT.equals(url),0);else stage(GalleryTrace.Stage.STALE_CALLBACK);}
            @Override public void onPageCommitVisible(WebView view,String url){if(current(view))stage(GalleryTrace.Stage.DOCUMENT_COMMIT,GalleryTrace.Asset.DOCUMENT,GalleryTrace.Result.NONE,DOCUMENT.equals(url),0);else stage(GalleryTrace.Stage.STALE_CALLBACK);}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                if(!current(view)){stage(GalleryTrace.Stage.STALE_CALLBACK);return blocked();}
                GalleryTrace.Asset kind=GalleryTrace.asset(request.getUrl().getPath());
                WebResourceResponse gallery=store.read(request);if(gallery!=null){stage(kind==GalleryTrace.Asset.API?(gallery.getStatusCode()==200?GalleryTrace.Stage.API_OK:GalleryTrace.Stage.API_ERROR):(gallery.getStatusCode()==200?GalleryTrace.Stage.RESOURCE_HIT:GalleryTrace.Stage.RESOURCE_ERROR),kind,GalleryTrace.Result.NONE,false,0);return gallery;}
                Uri uri=request.getUrl();String file=LobbyAssets.file(uri.getScheme(),uri.getHost(),uri.getPort(),uri.getPath());
                if("GET".equals(request.getMethod())&&file!=null&&uri.getQuery()==null&&uri.getFragment()==null&&uri.getUserInfo()==null)
                    try{java.io.InputStream input=getAssets().open(file);stage(GalleryTrace.Stage.RESOURCE_HIT,kind,GalleryTrace.Result.NONE,false,0);return new WebResourceResponse(LobbyAssets.mime(file),"UTF-8",200,"OK",Collections.singletonMap("Cache-Control","no-store"),input);}catch(IOException ignored){stage(GalleryTrace.Stage.RESOURCE_ERROR,kind,GalleryTrace.Result.NONE,false,0);}
                stage(GalleryTrace.Stage.RESOURCE_DENIED,kind,GalleryTrace.Result.NONE,false,0);
                return blocked();
            }
            @Override public void onPageFinished(WebView view,String url){
                if(!current(view)){stage(GalleryTrace.Stage.STALE_CALLBACK);return;}stage(GalleryTrace.Stage.DOCUMENT_FINISH,GalleryTrace.Asset.DOCUMENT,GalleryTrace.Result.NONE,DOCUMENT.equals(url),0);
                if(!DOCUMENT.equals(url)||!loadDeadline.waiting(navigation))return;
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
                if("/upload".equals(path)){requestUpload();return true;}
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
        web.setWebChromeClient(new WebChromeClient(){@Override public boolean onConsoleMessage(ConsoleMessage message){if(message.messageLevel()==ConsoleMessage.MessageLevel.ERROR&&current(created)){String source=message.sourceId();GalleryTrace.Asset script=source!=null&&source.startsWith("https://"+LobbyAssets.HOST+"/assets/")&&!source.contains("?")&&!source.contains("#")?GalleryTrace.asset(Uri.parse(source).getPath()):GalleryTrace.Asset.OTHER;stage(GalleryTrace.Stage.CONSOLE,script,GalleryTrace.console(message.message()),false,message.lineNumber());}return true;}});
        loading=new WebLoadingView(this,web,"正在打开相册","前台加载预算 10 秒，返回键可退出。","返回首页",this::finishAlbum);
        setContentView(loading.root);diagnostics.event("gallery","loading",navigation.generation,null);web.loadUrl(DOCUMENT);
    }
    private WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
    private void probe(WebView view,PageDeadline.Ticket ticket,int attempt){
        if(!resumed||!current(view)||!loadDeadline.waiting(ticket))return;
        stage(GalleryTrace.Stage.READY_REQUEST);
        try{view.evaluateJavascript("!!(window.KaishiGallerySettings&&typeof window.KaishiGallerySettings.key==='function'&&window.KaishiGallery&&typeof window.KaishiGallery.close==='function')",result->{
            if(!resumed||!current(view)||!loadDeadline.waiting(ticket)){stage(GalleryTrace.Stage.STALE_CALLBACK);return;}
            stage(GalleryTrace.Stage.READY_CALLBACK,GalleryTrace.Asset.NONE,"true".equals(result)?GalleryTrace.Result.TRUE:"false".equals(result)?GalleryTrace.Result.FALSE:GalleryTrace.Result.UNKNOWN,false,0);
            if("true".equals(result)){
                loadDeadline.ready(ticket);ready=true;if(loading!=null)loading.ready();stage(GalleryTrace.Stage.READY_VISIBLE);diagnostics.event("gallery","ready",ticket.generation,null);
                view.evaluateJavascript("window.KaishiGallery.active(true)",null);
            }else if(attempt<2){probeRetry=()->{probeRetry=null;probe(view,ticket,attempt+1);};pageTasks.post(probeRetry,250);}
            else fail(WebDiagnostics.Reason.JS_MISSING,false);
        });}catch(RuntimeException unavailable){stage(GalleryTrace.Stage.READY_ERROR);fail(WebDiagnostics.Reason.JS_MISSING,false);}
    }
    private void fail(WebDiagnostics.Reason reason,boolean rendererGone){
        if(closed||web==null&&reason!=WebDiagnostics.Reason.WEBVIEW_CREATE_ERROR)return;
        long generation=navigation==null?0:navigation.generation;
        stage(GalleryTrace.Stage.FALLBACK);
        diagnostics.event("gallery",rendererGone?"renderer-gone":reason==WebDiagnostics.Reason.LOAD_TIMEOUT?"timeout":"fallback",generation,reason);
        stopUpload(false);dispose(rendererGone);
        if(notice!=null){notice.dismiss();notice=null;}
        showNative(true);
    }
    private void showNative(boolean recovery){
        nativeGallery=new NativeGallery(this,store,new NativeGallery.Actions(){public void upload(){requestUpload();}public void stop(){stopUpload(true);}public void back(){finishAlbum();}public void stages(){AndroidGalleryTrace.show(GalleryActivity.this,getCurrentFocus());}public void snapshot(boolean ok){stage(ok?GalleryTrace.Stage.NATIVE_SNAPSHOT_OK:GalleryTrace.Stage.NATIVE_SNAPSHOT_ERROR);}},recovery);
        ready=true;setContentView(nativeGallery);nativeGallery.active(resumed);stage(GalleryTrace.Stage.NATIVE_READY);
    }
    private void dispose(boolean rendererGone){
        stage(GalleryTrace.Stage.DEADLINE_CANCEL);if(loadDeadline!=null)loadDeadline.cancel();if(backDeadline!=null)backDeadline.cancel();
        if(probeRetry!=null){pageTasks.cancel(probeRetry);probeRetry=null;}
        ready=false;documentFinished=false;loading=null;WebView previous=web;web=null;WebViewDisposal.release(previous,rendererGone);
    }
    private void finishAlbum(){if(closed)return;closed=true;stopUpload(false);if(nativeGallery!=null)nativeGallery.close();dispose(false);finish();}
    private void refresh(){if(!closed&&nativeGallery!=null){nativeGallery.refresh();return;}if(!closed&&ready&&web!=null)web.evaluateJavascript("window.KaishiGallerySettings&&window.KaishiGallerySettings.refresh()",null);}
    private void status(String text){if(!closed&&nativeGallery!=null){nativeGallery.status(text);return;}if(!closed&&ready&&web!=null)web.evaluateJavascript("window.KaishiGallerySettings&&window.KaishiGallerySettings.status("+JSONObject.quote(text)+")",null);}
    private Object uploadOwner(){return nativeGallery!=null?nativeGallery:web;}
    private void cancelUploadConfirmation(){
        consent.cancel();consentTicket=null;
        if(uploadConfirmation!=null){AlertDialog previous=uploadConfirmation;uploadConfirmation=null;previous.dismiss();}
    }
    private void stopUpload(boolean notify){cancelUploadConfirmation();closeUploadSession(notify);}
    private void closeUploadSession(boolean notify){
        if(upload!=null){upload.close();upload=null;}
        if(notify&&nativeGallery!=null)nativeGallery.uploadClosed();
        if(notify&&!closed&&ready&&web!=null)web.evaluateJavascript("window.KaishiGallerySettings&&window.KaishiGallerySettings.uploadClosed()",null);
    }
    private boolean confirmationKey(int code){return code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_NUMPAD_ENTER||code==KeyEvent.KEYCODE_BUTTON_A;}
    private void requestUpload(){
        if(closed||!ready||!resumed||uploadOwner()==null||!getWindow().getDecorView().hasWindowFocus())return;
        if(consent.pending()||upload!=null&&!upload.isClosed()||notice!=null&&notice.isShowing())return;
        if(upload!=null)closeUploadSession(true);
        UploadConsent.Ticket ticket=consentTicket=consent.request(uploadOwner(),android.os.SystemClock.uptimeMillis());
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("开启相册上传？")
            .setMessage("将在这台电视开启临时局域网上传服务，有效10分钟。\n手机与电视需在同一家庭网络。可点“关闭上传”停止；离开相册或切到后台也会停止。")
            .setNegativeButton("取消",null).setPositiveButton("开启10分钟",null).create();
        uploadConfirmation=dialog;final long[] cancelDown={-1};
        dialog.setOnDismissListener(ignored->{if(uploadConfirmation==dialog)uploadConfirmation=null;if(consent.pending(ticket))consent.cancel();});
        dialog.setOnKeyListener((d,code,event)->{
            if(!confirmationKey(code)){if(event.getAction()==KeyEvent.ACTION_DOWN)consent.disarm(ticket);return false;}
            boolean positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE).hasFocus();
            if(event.getAction()==KeyEvent.ACTION_DOWN){
                if(positive)consent.down(ticket,event.getDownTime(),event.getRepeatCount());
                else if(event.getRepeatCount()==0&&event.getDownTime()>ticket.openedAt)cancelDown[0]=event.getDownTime();
            }else if(event.getAction()==KeyEvent.ACTION_UP){
                if(!positive&&cancelDown[0]==event.getDownTime()){consent.cancel();dialog.dismiss();}
                else if(positive&&consent.up(ticket,event.getDownTime())){dialog.dismiss();beginConfirmedUpload(ticket);}else if(!positive)consent.disarm(ticket);
            }
            return true;
        });
        dialog.show();
        android.widget.Button yes=dialog.getButton(AlertDialog.BUTTON_POSITIVE),no=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        TvUi.styleButton(this,yes);TvUi.styleButton(this,no);no.requestFocus();
        // Native accessibility clicks are explicit actions; remote key-up cannot fall through here.
        yes.setOnClickListener(v->{if((yes.hasFocus()||yes.isAccessibilityFocused())&&consent.explicitClick(ticket,android.os.SystemClock.uptimeMillis())){dialog.dismiss();beginConfirmedUpload(ticket);}});
        yes.setOnTouchListener((v,event)->{
            if(event.getAction()==android.view.MotionEvent.ACTION_DOWN){consent.down(ticket,event.getDownTime(),0);return true;}
            if(event.getAction()==android.view.MotionEvent.ACTION_CANCEL){consent.disarm(ticket);return true;}
            if(event.getAction()==android.view.MotionEvent.ACTION_UP){if(event.getX()>=0&&event.getY()>=0&&event.getX()<v.getWidth()&&event.getY()<v.getHeight()&&consent.up(ticket,event.getDownTime())){dialog.dismiss();beginConfirmedUpload(ticket);}else consent.disarm(ticket);return true;}
            return true;
        });
    }
    private void beginConfirmedUpload(UploadConsent.Ticket ticket){
        if(closed||!ready||!resumed||uploadOwner()==null||ticket.owner!=uploadOwner()||!consent.accepted(ticket))return;
        closeUploadSession(true);final GalleryUploadSession next;
        try{next=createUploadSession(()->runOnUiThread(this::refresh));}catch(RuntimeException error){consent.cancel();status("无法开启上传，请重试");return;}
        upload=next;
        new Thread(()->{try{
            String url=next.start();runOnUiThread(()->{if(closed||!resumed||upload!=next||!ready||uploadOwner()==null||!consent.accepted(ticket)||ticket.owner!=uploadOwner()){next.close();return;}
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);if(nativeGallery!=null)nativeGallery.upload(url);else web.evaluateJavascript("window.KaishiGallerySettings.upload("+JSONObject.quote(url)+")",null);});
        }catch(Exception error){next.close();runOnUiThread(()->{if(upload==next){upload=null;consent.cancel();status("无法开启上传，请检查电视与手机的家庭网络");}});}},"Kaishi-album-session").start();
    }
    @Override protected void onResume(){
        super.onResume();resumed=true;loadDeadline.active(true);backDeadline.active(true);stage(GalleryTrace.Stage.RESUME);stage(GalleryTrace.Stage.DEADLINE_ARM);if(nativeGallery!=null)nativeGallery.active(true);
        if(web!=null){web.onResume();web.resumeTimers();if(ready)web.evaluateJavascript("window.KaishiGallery&&window.KaishiGallery.active(true)",null);else if(documentFinished)probe(web,navigation,0);}
    }
    @Override protected void onPause(){
        resumed=false;loadDeadline.active(false);backDeadline.cancel();backDeadline.active(false);stage(GalleryTrace.Stage.PAUSE);stage(GalleryTrace.Stage.DEADLINE_PAUSE);if(nativeGallery!=null)nativeGallery.active(false);
        if(probeRetry!=null){pageTasks.cancel(probeRetry);probeRetry=null;}
        stopUpload(true);
        if(web!=null){if(ready)web.evaluateJavascript("window.KaishiGallery&&window.KaishiGallery.active(false)",null);web.onPause();web.pauseTimers();}
        super.onPause();
    }
    @Override public void onBackPressed(){
        if(closed)return;if(nativeGallery!=null){if(!nativeGallery.back())finishAlbum();return;}if(web==null||!ready){finishAlbum();return;}
        if(backDeadline.waiting(backTicket)){finishAlbum();return;}
        WebView current=web;PageDeadline.Ticket ticket=backTicket=backDeadline.start(current);
        try{current.evaluateJavascript("!!(window.KaishiGallery&&window.KaishiGallery.close())",value->{
            if(closed||current!=web||!backDeadline.ready(ticket))return;
            backDeadline.cancel();if(!"true".equals(value))finishAlbum();
        });}catch(RuntimeException unavailable){finishAlbum();}
    }
    private String remoteKey(int code){return code==19?"ArrowUp":code==20?"ArrowDown":code==21?"ArrowLeft":code==22?"ArrowRight":code==23||code==66||code==160||code==96?"Enter":null;}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(nativeGallery!=null&&getWindow().getDecorView().hasWindowFocus()&&nativeGallery.key(event))return true;
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
    @Override public void onWindowFocusChanged(boolean value){super.onWindowFocusChanged(value);focused=value;stage(GalleryTrace.Stage.FOCUS);}
    @Override protected void onDestroy(){stage(GalleryTrace.Stage.DESTROY);closed=true;stopUpload(false);if(nativeGallery!=null)nativeGallery.close();dispose(false);if(notice!=null)notice.dismiss();super.onDestroy();}
}
