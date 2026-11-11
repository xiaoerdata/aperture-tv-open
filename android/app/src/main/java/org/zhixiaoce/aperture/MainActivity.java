package org.zhixiaoce.aperture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import org.json.JSONObject;

/** Offline lobby and same-origin family content. No JavaScript bridge or remote intent dispatch. */
public class MainActivity extends Activity {
    private static final String LOBBY = "https://" + LobbyAssets.HOST + LobbyAssets.DOCUMENT;
    private volatile WebView web;
    private HouseholdProxy household;
    private WeatherProxy weather;private HomeMessages messages;private boolean ambientAwake;
    private SharedPreferences preferences;
    private DestinationApps destinations;
    private VoiceAssistant assistant;
    private ApkInstaller installer;
    private final DisplayLifecycle display = new DisplayLifecycle();
    private final LobbyMode lobby = new LobbyMode();
    private Uri trusted;
    private ScenicNativeHome scenicHome;
    private AlertDialog returnHomeDialog;
    private AlertDialog settingsMenuDialog;
    private boolean ready = false, lobbyMode = false;
    private boolean resumed,closed,homeDocumentFinished;
    private PageDeadline homeDeadline;private PageDeadline.Ticket homeTicket;
    private WebLoadingView homeLoading;private WebDiagnostics diagnostics;private AlertDialog diagnosticsDialog;
    private PageDeadline.Scheduler homeTasks;
    private Runnable homeProbeRetry;
    static final long HOME_LOAD_TIMEOUT_MS=12000;
    protected WebView newWebView(){return new WebView(this);}
    protected PageDeadline.Clock deadlineClock(){return AndroidPageDeadline.clock();}
    protected PageDeadline.Scheduler deadlineScheduler(){return AndroidPageDeadline.scheduler();}

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); preferences = getSharedPreferences("aperture", MODE_PRIVATE);
        diagnostics=new WebDiagnostics(this);
        homeTasks=deadlineScheduler();
        homeDeadline=new PageDeadline(HOME_LOAD_TIMEOUT_MS,deadlineClock(),homeTasks,ticket->{
            if(ticket.owner==web)failHome(web,ticket,WebDiagnostics.Reason.LOAD_TIMEOUT,false);
        });
        destinations = new DestinationApps(this, preferences, slot -> showLobby(slot));
        assistant = new VoiceAssistant(this);
        installer = new ApkInstaller(this, () -> destinations.shortcuts());
        messages=new HomeMessages(this,()->{if(web!=null&&ready&&lobbyMode)web.evaluateJavascript("window.apertureMessagesRefresh&&window.apertureMessagesRefresh()",null);if(scenicHome!=null&&messages!=null)scenicHome.messages(messages.display());});
        immersive(); showLobby("");
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        // The system sends HOME to the chosen singleTask entry. Always return to its first page.
        if(returnHomeDialog!=null)returnHomeDialog.dismiss();
        if(settingsMenuDialog!=null)settingsMenuDialog.dismiss();
        if(diagnosticsDialog!=null)diagnosticsDialog.dismiss();
        destinations.dismissHomeSettings();showLobby("");
    }
    protected int webViewVersion() {
        try { PackageInfo provider = WebView.getCurrentWebViewPackage(); return provider != null ? Integer.parseInt(provider.versionName.split("\\.")[0]) : 0; }
        catch (RuntimeException ignored) { return 0; }
    }
    private void immersive() { getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY); }
    private boolean sameOrigin(Uri uri) { return trusted != null && trusted.getScheme().equals(uri.getScheme()) && trusted.getHost().equals(uri.getHost()) && trusted.getPort() == uri.getPort(); }
    private boolean lobbyDocument(Uri uri) { return LobbyAssets.DOCUMENT.equals(uri.getPath()) && LobbyAssets.file(uri.getScheme(), uri.getHost(), uri.getPort(), uri.getPath()) != null; }
    private java.util.Map<String, String> householdHeaders() {
        java.util.Map<String, String> headers = new java.util.HashMap<>(); headers.put("Cache-Control", "no-store"); headers.put("X-Content-Type-Options", "nosniff"); return headers;
    }
    private WebResourceResponse blockedAsset() { return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", Collections.singletonMap("Cache-Control", "no-store"), new ByteArrayInputStream(new byte[0])); }
    private WebResourceResponse packagedAsset(Uri uri) {
        if("https".equals(uri.getScheme())&&LobbyAssets.HOST.equals(uri.getHost())&&uri.getPort()==-1&&uri.getUserInfo()==null&&uri.getQuery()==null&&uri.getFragment()==null&&uri.getPath()!=null&&uri.getPath().matches("/apps/icons/[a-z0-9]{2,20}\\.png")){
            String key=uri.getPath().substring(12,uri.getPath().length()-4);byte[] icon=destinations.icon(key);return icon==null?blockedAsset():new WebResourceResponse("image/png",null,200,"OK",householdHeaders(),new ByteArrayInputStream(icon));
        }
        String file = LobbyAssets.file(uri.getScheme(), uri.getHost(), uri.getPort(), uri.getPath());
        if (file == null) return blockedAsset();
        try { return new WebResourceResponse(LobbyAssets.mime(file), file.endsWith(".woff2") || file.endsWith(".png") || file.endsWith(".jpg") || file.endsWith(".webp") ? null : "UTF-8", 200, "OK", Collections.singletonMap("Cache-Control", "no-store"), getAssets().open(file)); }
        catch (IOException error) { return blockedAsset(); }
    }
    private void destroyWeb() { destroyWeb(false); }
    private void destroyWeb(boolean rendererGone) {
        if(homeDeadline!=null)homeDeadline.cancel();homeTicket=null;homeDocumentFinished=false;
        if(homeProbeRetry!=null){homeTasks.cancel(homeProbeRetry);homeProbeRetry=null;}
        homeLoading=null;
        if (weather != null) { weather.close(); weather = null; }
        if (household != null) { household.close(); household = null; }
        ambientAwake=false;getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(messages!=null)messages.active(false);
        ready = false; display.bind(null); WebView previous = web; web = null;
        WebViewDisposal.release(previous,rendererGone);
    }
    private void webActive(WebView current, boolean active) {
        if (current != web) return;
        if(lobbyMode&&homeDeadline!=null){
            homeDeadline.active(active);
            if(!active&&homeProbeRetry!=null){homeTasks.cancel(homeProbeRetry);homeProbeRetry=null;}
        }
        if(messages!=null)messages.active(active&&lobbyMode);syncAmbientAwake();
        if (household != null) household.active(active);
        if (weather != null) weather.active(active);
        if (active) { current.onResume(); current.resumeTimers(); }
        // Best effort: stop carousel/media before yielding the window; never auto-resume playback.
        if(ready)current.evaluateJavascript("(function(){if(window.apertureNativeFocus)window.apertureNativeFocus(" + active + ");" + (active ? "" : "var m=document.querySelectorAll('video,audio');for(var i=0;i<m.length;i++)m[i].pause();") + "})()", null);
        if (!active) { current.onPause(); current.pauseTimers(); }
        else if(lobbyMode&&homeDocumentFinished&&!ready)completeLobbyLoad(current,homeTicket,0);
    }
    private void completeLobbyLoad(WebView current,PageDeadline.Ticket ticket,int attempt) {
        if(closed||!resumed||!display.active()||current!=web||!lobbyMode||!homeDeadline.waiting(ticket))return;
        try{current.evaluateJavascript("typeof window.apertureRemoteKey==='function'&&typeof window.apertureSetViewport==='function'",result->{
            if(closed||!resumed||!display.active()||current!=web||!homeDeadline.waiting(ticket))return;
            if("true".equals(result)){
                if(!homeDeadline.ready(ticket))return;
                ready=true;diagnostics.mode(WebDiagnostics.Home.GLASS_READY);diagnostics.event("home","ready",ticket.generation,null);
                if(homeLoading!=null)homeLoading.ready();
                syncLobbyViewport(current);webActive(current,display.active());nasLabels();
            }else if(attempt<2){
                homeProbeRetry=()->{homeProbeRetry=null;completeLobbyLoad(current,ticket,attempt+1);};
                homeTasks.post(homeProbeRetry,250);
            }else failHome(current,ticket,WebDiagnostics.Reason.JS_MISSING,false);
        });}catch(RuntimeException unavailable){failHome(current,ticket,WebDiagnostics.Reason.JS_MISSING,false);}
    }
    private void failHome(WebView current,PageDeadline.Ticket ticket,WebDiagnostics.Reason reason,boolean rendererGone){
        if(closed||current!=web||!homeDeadline.current(ticket))return;
        diagnostics.event("home",reason==WebDiagnostics.Reason.LOAD_TIMEOUT?"timeout":rendererGone?"renderer-gone":"fallback",ticket.generation,reason);
        if(reason!=WebDiagnostics.Reason.USER_CANCEL_LOAD)lobby.webFailed();
        destroyWeb(rendererGone);
        nativeLobby(reason==WebDiagnostics.Reason.USER_CANCEL_LOAD?WebDiagnostics.Home.NATIVE_SELECTED:WebDiagnostics.Home.NATIVE_RECOVERY);
        android.widget.Toast.makeText(this,reason==WebDiagnostics.Reason.LOAD_TIMEOUT?"首页加载超时，已打开本机风景首页":reason==WebDiagnostics.Reason.USER_CANCEL_LOAD?"已打开本机风景首页":"首页暂不可用，已打开本机风景首页",android.widget.Toast.LENGTH_LONG).show();
    }
    private String lastViewportReport = "";
    @SuppressWarnings("deprecation") // Called on UI thread; includes WebView's effective page zoom.
    private void syncLobbyViewport(WebView current) {
        if(current!=web||!lobbyMode||!ready||current.getWidth()<=0||current.getHeight()<=0)return;
        float pageScale=current.getScale();
        if(!Float.isFinite(pageScale)||pageScale<=0)return;
        android.graphics.Rect visible=new android.graphics.Rect();
        int width=current.getWidth(),height=current.getHeight();
        if(current.getGlobalVisibleRect(visible)){
            width=Math.min(width,visible.width());height=Math.min(height,visible.height());
        }
        if(width<=0||height<=0)return;
        final String nativeSize="pixels="+width+"x"+height+" pageScale="+pageScale+" density="+getResources().getDisplayMetrics().density;
        current.evaluateJavascript("window.apertureSetViewport?window.apertureSetViewport("+(width/(double)pageScale)+","+(height/(double)pageScale)+"):{error:'viewport-api-unavailable'}",value->{
            if(current!=web)return;
            String report=nativeSize+" dom="+value;
            if(!report.equals(lastViewportReport)){lastViewportReport=report;android.util.Log.i("KaishiViewport",report);}
        });
    }
    private void createWeb(boolean lobby) {
        destroyWeb(); scenicHome=null; lobbyMode = lobby; lastViewportReport=""; web = newWebView();
        final WebView created=web;
        final PageDeadline.Ticket navigation=lobby?homeDeadline.start(created):null;homeTicket=navigation;
        if(lobby){diagnostics.mode(WebDiagnostics.Home.GLASS_LOADING);diagnostics.event("home","loading",navigation.generation,null);}
        final WeatherProxy forecast=lobby?new WeatherProxy(value->{String snapshot=HomeWeatherSnapshot.capture(value);if(!snapshot.isEmpty())preferences.edit().putString("home_weather_snapshot",snapshot).apply();}):null;weather=forecast;
        String householdOrigin = preferences.getString("origin", "");
        final HouseholdProxy feed = lobby && !BuildConfig.PARENTS_EDITION ? new HouseholdProxy(householdOrigin, householdOrigin.isEmpty() ? "" : CookieManager.getInstance().getCookie(householdOrigin), preferences.getBoolean("allowLanHttp", false)) : null;
        household = feed;
        WebSettings settings = web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setAllowFileAccessFromFileURLs(false); settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false); settings.setSupportMultipleWindows(false); settings.setTextZoom(100);
        settings.setSupportZoom(false); settings.setBuiltInZoomControls(false); settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(!lobby); settings.setLoadWithOverviewMode(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false); WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage message) {
                if(lobby && message.message().startsWith("[KaishiHome] ")){
                    android.util.Log.i("KaishiHome",message.message());return true;
                }
                if(lobby && message.messageLevel()==android.webkit.ConsoleMessage.MessageLevel.ERROR){
                    String source=android.net.Uri.parse(message.sourceId()==null?"":message.sourceId()).getLastPathSegment();
                    if(source!=null&&source.matches("[a-z-]+\\.js"))
                        android.util.Log.w("KaishiHome","script-error file="+source+" line="+message.lineNumber());
                }
                return false;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public void onScaleChanged(WebView view,float oldScale,float newScale) {
                if(lobby&&view==web)view.post(()->syncLobbyViewport(view));
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) { if (view == web&&(!lobby||homeDeadline.waiting(navigation))) ready = false; }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl(); if (view != web || !request.isForMainFrame()) return true;
                if (lobby) {
                    // Only the immutable packaged page can request these fixed actions.
                    Uri current = Uri.parse(view.getUrl() == null ? "" : view.getUrl());
                    if (lobbyDocument(current) && "aperture".equals(uri.getScheme()) && "action".equals(uri.getHost()) && uri.getQuery() == null && uri.getFragment() == null) action(uri.getPath());
                    return true;
                }
                if (!sameOrigin(uri)) return true;
                if ("/home".equals(uri.getPath()) || "/launcher/index.html".equals(uri.getPath())) { showLobby(""); return true; }
                if ("/native-menu".equals(uri.getPath())) { quickMenu(); return true; }
                if ("/studio".equals(uri.getPath()) || "/guide".equals(uri.getPath())) { new AlertDialog.Builder(MainActivity.this).setMessage("请在家人的手机打开：\n" + trusted + uri.getPath()).setPositiveButton("知道了", null).show(); return true; }
                return false;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (view != web) return blockedAsset();
                if (lobby) {
                    if (!"GET".equals(request.getMethod())) return blockedAsset();
                    WebResourceResponse album=new GalleryStore(MainActivity.this).read(request);if(album!=null)return album;
                    Uri target=request.getUrl();
                    if("https".equals(target.getScheme())&&LobbyAssets.HOST.equals(target.getHost())&&target.getPort()==-1&&target.getUserInfo()==null&&target.getQuery()==null&&target.getFragment()==null&&"/api/messages".equals(target.getPath())&&!request.isForMainFrame())return new WebResourceResponse("application/json","UTF-8",200,"OK",householdHeaders(),new ByteArrayInputStream(messages.display()));
                    WeatherProxy.Reply climate=forecast.read(request.getUrl().toString(),request.getMethod(),request.isForMainFrame());
                    if(climate!=null)return new WebResourceResponse("application/json","UTF-8",climate.status,climate.status==200?"OK":"Unavailable",householdHeaders(),new ByteArrayInputStream(climate.body));
                    HouseholdProxy.Reply reply = feed == null ? null : feed.read(request.getUrl().toString(), request.getMethod(), request.isForMainFrame());
                    if (reply != null) return new WebResourceResponse(reply.mime, "image/jpeg".equals(reply.mime) ? null : "UTF-8", reply.status, reply.status == 200 ? "OK" : "Unavailable", householdHeaders(), new ByteArrayInputStream(reply.body));
                    return packagedAsset(request.getUrl());
                }
                return sameOrigin(request.getUrl()) ? null : blockedAsset();
            }
            @Override public void onPageFinished(WebView view, String url) {
                if(view!=web)return;
                CookieManager.getInstance().flush();
                if(lobby){if(!homeDeadline.waiting(navigation))return;homeDocumentFinished=true;completeLobbyLoad(view,navigation,0);}else ready=true;
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) { if (view == web && request.isForMainFrame()) runOnUiThread(() -> { if (view == web) {if(lobby)failHome(view,navigation,WebDiagnostics.Reason.HTTP_ERROR,false);else fallback("家庭服务返回错误，请核对服务地址。");} }); }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) { if (view == web && request.isForMainFrame()) runOnUiThread(() -> { if (view != web) return; if (lobby) failHome(view,navigation,WebDiagnostics.Reason.MAIN_DOCUMENT_ERROR,false); else fallback("暂时连不上家庭服务。直播、影视应用和原桌面仍可使用。"); }); }
            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if(view==web){
                    if(lobby)failHome(view,navigation,WebDiagnostics.Reason.RENDERER_GONE,true);
                    else{diagnostics.event("home","renderer-gone",0,WebDiagnostics.Reason.RENDERER_GONE);MainActivity.this.lobby.rendererGone();destroyWeb(true);nativeLobby();}
                }else WebViewDisposal.release(view,true);
                return true;
            }
        });
        if(lobby){
            web.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or_,ob)->syncLobbyViewport((WebView)view));
            web.setOverScrollMode(View.OVER_SCROLL_NEVER);web.setHorizontalScrollBarEnabled(false);web.setVerticalScrollBarEnabled(false);
            // OEM WebView may scroll its native viewport during accessibility/remote focus changes.
            // Child DOM lists retain their own scrolling; only the outer native viewport is pinned.
            web.setOnScrollChangeListener((view,x,y,oldX,oldY)->{if(view==web&&(x!=0||y!=0))view.scrollTo(0,0);});
        }
        if(lobby){homeLoading=new WebLoadingView(this,created,"正在打开首页","最迟等待 12 秒，可直接使用本机首页。","使用本机首页",()->failHome(created,navigation,WebDiagnostics.Reason.USER_CANCEL_LOAD,false));setContentView(homeLoading.root);}
        else{setContentView(web);web.requestFocus();}
        WebView current = web; display.bind(active -> webActive(current, active));
    }
    private void showLobby(String pane) {
        destinations.initializeShortcuts();
        LobbyMode.Choice choice=lobby.choose(webViewVersion(),preferences.getBoolean("simple_home",false));
        if(choice!=LobbyMode.Choice.GLASS){nativeLobby(choice==LobbyMode.Choice.NATIVE_OLD_ENGINE?WebDiagnostics.Home.NATIVE_OLD_ENGINE:choice==LobbyMode.Choice.NATIVE_SELECTED?WebDiagnostics.Home.NATIVE_SELECTED:WebDiagnostics.Home.NATIVE_RECOVERY);if("apps".equals(pane)&&scenicHome!=null)scenicHome.showApps();return;}
        try{createWeb(true);}catch(RuntimeException unavailable){
            diagnostics.event("home","fallback",0,WebDiagnostics.Reason.WEBVIEW_CREATE_ERROR);lobby.webFailed();nativeLobby();return;
        }
        Uri.Builder uri = Uri.parse(LOBBY).buildUpon().appendQueryParameter("edition", BuildConfig.PARENTS_EDITION ? "parents" : "standard");
        for (String slot : new String[] {"live", "cinema", "system", "quick1", "quick2", "quick3", "quick4"}) uri.appendQueryParameter(slot, destinations.label(slot));
        uri.appendQueryParameter("assistant", assistant.label());
        uri.appendQueryParameter("photos",FeaturePolicy.photos()?"1":"0");
        if("apps".equals(pane))uri.appendQueryParameter("screen","apps");
        if("information".equals(pane))uri.appendQueryParameter("section","information");
        if ("quick1".equals(pane) || "quick2".equals(pane) || "quick3".equals(pane) || "quick4".equals(pane)) uri.appendQueryParameter("focus", pane);
        if ("live".equals(pane) || "cinema".equals(pane) || "system".equals(pane) || "settings".equals(pane) || "voice".equals(pane)) uri.appendQueryParameter("pane", pane);
        try{web.loadUrl(uri.build().toString());}catch(RuntimeException unavailable){failHome(web,homeTicket,WebDiagnostics.Reason.MAIN_DOCUMENT_ERROR,false);}
    }
    private void syncAmbientAwake(){if(ambientAwake&&lobbyMode&&display.active())getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    private void messageSettings(){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);TextView note=new TextView(this);note.setText("由 WorkBuddy 更新留言文件，电视每分钟读取。只显示内容与时间。");note.setTextColor(TvUi.INK);note.setTextSize(18);form.addView(note);EditText input=new EditText(this);input.setTextColor(TvUi.INK);input.setTextSize(18);input.setSingleLine(true);input.setHint("HTTPS 留言源地址");input.setText(messages.source());form.addView(input);
        CheckBox pause=new CheckBox(this);pause.setText("暂停播报");pause.setTextColor(TvUi.INK);pause.setChecked(messages.paused());pause.setOnCheckedChangeListener((v,checked)->messages.paused(checked));form.addView(pause);
        Button save=TvUi.button(this,"保存留言源",()->{try{messages.configure(input.getText().toString());note.setText(input.getText().toString().trim().isEmpty()?"已断开留言源":"已保存，正在读取");}catch(Exception invalid){note.setText("请输入有效的 HTTPS 地址");}});form.addView(save);TvDialogs.panel(this,"留言源",form);
    }
    private void action(String path) {
        if("/add-shortcut".equals(path)&&!BuildConfig.PARENTS_EDITION){destinations.addShortcut();return;}
        if("/message-edit".equals(path)&&!BuildConfig.PARENTS_EDITION){startActivity(new Intent(this,MessageActivity.class));return;}
        if("/wall-edit".equals(path)&&!BuildConfig.PARENTS_EDITION){destinations.editWall();return;}
        if(path!=null&&path.startsWith("/open-wall")&&!BuildConfig.PARENTS_EDITION){destinations.launchWall(path.substring(6));return;}
        if("/gallery".equals(path)&&!BuildConfig.PARENTS_EDITION){startActivity(new Intent(this,GalleryActivity.class));return;}
        if ("/family-live".equals(path) || "/family-series".equals(path) || "/family-movies".equals(path) || "/family-variety".equals(path)) android.util.Log.i("KaishiHome", "action=" + path.substring(1));
        if("/message-settings".equals(path)){messageSettings();return;}
        if("/ambient-awake-on".equals(path)||"/ambient-awake-off".equals(path)){ambientAwake=path.endsWith("-on");syncAmbientAwake();return;}
        if ("/ambient-system".equals(path)) { try { startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS)); } catch(RuntimeException unavailable) { new AlertDialog.Builder(this).setMessage("当前电视未提供系统屏保选择，可使用开视首页屏保。").setPositiveButton("返回",null).show(); } return; }
        if (path != null && path.startsWith("/preset-")) { destinations.addPreset(path.substring(8)); return; }
        if ("/cinema-settings".equals(path)) { cinema("settings"); return; }
        if ("/nas-photos".equals(path)&&!FeaturePolicy.photos())return;
        if ("/nas".equals(path) || "/nas-photos".equals(path)) {
            Intent intent=new Intent(this,NasActivity.class).putExtra("filter","/nas-photos".equals(path)?"photo":"all");
            if("/nas-photos".equals(path)){try{if(new NasVault(this).library().route("photos")!=null)intent.putExtra("category","photos");}catch(Exception unavailable){nasUnavailable();return;}}
            startActivity(intent); return;
        }
        if ("/choose-shortcuts".equals(path)) { destinations.shortcuts(); return; }
        if ("/install-apps".equals(path)) { installation(); return; }
        if ("/open-quick1".equals(path) || "/open-quick2".equals(path) || "/open-quick3".equals(path) || "/open-quick4".equals(path)) { destinations.launchShortcut(path.substring(6)); return; }
        if ("/open-assistant".equals(path)) { assistant.open(); return; }
        if ("/hub-connect".equals(path)) { hubConnection(); return; }
        if ("/account-settings".equals(path)) { accountSettings(); return; }
        if ("/voice-help".equals(path)) { assistant.help(); return; }
        if ("/voice-settings".equals(path)) { assistant.settings(); return; }
        if ("/home-mode".equals(path)) { homeMode(); return; }
        if ("/family".equals(path)) { family(null); return; }
        if ("/family-live".equals(path)) { family("live"); return; }
        if ("/family-series".equals(path)) { family("series"); return; }
        if ("/family-movies".equals(path)) { family("movies"); return; }
        if ("/family-variety".equals(path)) { family("variety"); return; }
        if ("/family-library".equals(path)) { family("library"); return; }
        if ("/open-live".equals(path)) cinema("live"); else if ("/open-cinema".equals(path)) cinema("library");
        else if ("/external-live".equals(path)) destinations.launch("live"); else if ("/external-cinema".equals(path)) destinations.launch("cinema"); else if ("/open-system".equals(path)) destinations.launch("system");
        else if ("/choose-live".equals(path)) destinations.pick("live", false); else if ("/choose-cinema".equals(path)) destinations.pick("cinema", false); else if ("/choose-system".equals(path)) destinations.pick("system", false);
        else if ("/home-settings".equals(path)) destinations.settings(true); else if ("/configure".equals(path)) setup(); else if ("/apps".equals(path)) destinations.all();
    }
    private void accountSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
            android.content.pm.ResolveInfo resolved = getPackageManager().resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved == null || resolved.activityInfo == null || !resolved.activityInfo.exported || !resolved.activityInfo.enabled ||
                (resolved.activityInfo.applicationInfo.flags & (android.content.pm.ApplicationInfo.FLAG_SYSTEM | android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0) throw new IllegalStateException();
            intent.setClassName(resolved.activityInfo.packageName, resolved.activityInfo.name); startActivity(intent);
        }
        catch (RuntimeException unavailable) {
            new AlertDialog.Builder(this).setTitle("小米账号在原系统管理").setMessage("请切回原来的桌面，在系统设置里查看或登录小米账号。米家设备需要在自己的 Home Assistant 中另行授权，开视不读取电视账号凭证。")
                .setPositiveButton("回原桌面", (dialog, which) -> destinations.launch("system")).setNegativeButton("返回", null).show();
        }
    }
    private void family(String view) {
        if (view == null) {if(FeaturePolicy.photos())familyServer(null);else showLobby("information");} else cinema(view);
    }
    private void cinema(String view) { startActivityForResult(new Intent(this,CinemaActivity.class).putExtra("view",view),4602); }
    private void familyServer(String view) {
        if(view==null&&!FeaturePolicy.photos()){showLobby("information");return;}
        String origin = preferences.getString("origin", ""); if (origin.isEmpty() || !HouseholdProxy.allowedOrigin(origin,preferences.getBoolean("allowLanHttp",false))) { setup(); return; }
        if (!lobby.webAvailable(webViewVersion())) { fallback(lobby.recoveryRequired() ? "网页显示组件刚刚退出，已保留简洁本机首页。请先使用本机应用或NAS；完全关闭开视后重新打开，再尝试家庭屏。" : "当前内核无法显示家庭屏，请从原系统更新WebView。其他应用入口仍可使用。"); return; }
        if (view != null && webViewVersion() < 89) { fallback("家庭影院需要 WebView 89 或更新版本。请在原系统更新内核；暂时可回原桌面使用原有播放器。"); return; }
        trusted = Uri.parse(origin); createWeb(false);
        boolean lite = BuildConfig.PARENTS_EDITION || webViewVersion() < 89 || preferences.getBoolean("lite", false);
        String page = view != null ? "/cinema?from=home&native=1&view=" + view : (lite ? "/tv-lite?native=1" : "/tv?native=1");
        web.loadUrl(origin + page + (BuildConfig.PARENTS_EDITION ? "&edition=parents" : ""));
    }
    private void nasUnavailable() {
        new AlertDialog.Builder(this).setTitle("请家人检查片库").setMessage("本机NAS入口记录暂不可读取。请到NAS设置检查连接，不会自动打开其他目录。")
            .setPositiveButton("NAS设置",(d,w)->startActivity(new Intent(this,NasActivity.class))).setNegativeButton("返回",null).show();
    }
    private void nasLabels() {
        if(!lobbyMode||web==null||!ready)return;
        String movies="家人统一配置电影",series="家人统一配置剧集";JSONObject summary=new JSONObject();
        try {NasLibrary library=new NasVault(this).library();NasLibrary.Route movie=library.route("movies"),serial=library.route("series");if(movie!=null)movies="NAS · "+movie.connection.optString("name");if(serial!=null)series="NAS · "+serial.connection.optString("name");
            summary.put("nasCount",library.connections().length()).put("nasPhotos",FeaturePolicy.photos()&&library.route("photos")!=null).put("nasMovies",movie!=null).put("nasSeries",serial!=null);
        }catch(Exception unavailable){movies="请家人检查片库";series="请家人检查片库";}
        JSONObject apps=destinations.homeApps();web.evaluateJavascript("(function(){if(window.apertureNasLabels)window.apertureNasLabels("+JSONObject.quote(movies)+","+JSONObject.quote(series)+");if(window.apertureHomeSources)window.apertureHomeSources("+summary+");if(window.apertureHomeApps)window.apertureHomeApps("+apps+");})()",null);
    }
    private LinearLayout column(String title) {
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(TvUi.dp(this,12), TvUi.dp(this,10), TvUi.dp(this,12), TvUi.dp(this,10));
        TextView label = TvUi.text(this,title,24,false); label.setPadding(0, 0, 0, TvUi.dp(this,12)); layout.addView(label); return layout;
    }
    private Button button(LinearLayout layout, String text, Runnable action) {
        float density = TvUi.density(this);
        Button button = new Button(this); button.setText(text); button.setAllCaps(false);
        TvUi.styleButton(this,button);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, Math.round(6 * density), 0, Math.round(6 * density));
        button.setOnClickListener(view -> action.run()); layout.addView(button, params); return button;
    }
    private void showNative(LinearLayout layout) { ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(layout); setContentView(scroll); }
    private void homeMode() {
        String reason = lobby.recoveryRequired() ? "网页首页本次未能正常显示，已使用本机风景首页。关闭开视后重开，可再尝试玻璃首页；原因见本机诊断。" : !lobby.glassAvailable(webViewVersion()) ? "当前网页内核较旧，已使用本机风景首页。壁纸、观看入口和常用应用仍可用。" : "玻璃首页支持家庭面板与动效；本机风景首页保留壁纸和常用入口，不运行网页。";
        new TvDialogs.SideBuilder(this).setTitle("首页样式").setMessage(reason)
            .setPositiveButton("本机风景首页", (d,w) -> { preferences.edit().putBoolean("simple_home", true).apply(); showLobby(""); })
            .setNeutralButton(lobby.glassAvailable(webViewVersion()) ? "玻璃首页" : "保留自动选择", (d,w) -> { preferences.edit().remove("simple_home").apply(); showLobby(""); })
            .setNegativeButton("返回", null).show();
    }
    private void nativeLobby(){nativeLobby(WebDiagnostics.Home.NATIVE_RECOVERY);}
    private void nativeLobby(WebDiagnostics.Home mode) {
        destroyWeb(); lobbyMode = true;diagnostics.mode(mode);
        scenicHome=new ScenicNativeHome(this,destinations,route->{
            if("settings".equals(route))quickMenu();
            else if("diagnostics".equals(route))showDiagnostics();
            else if("apps".equals(route))destinations.all();
            else if("gallery".equals(route))action("/gallery");
            else if("information".equals(route))action("/message-edit");
            else if("nas".equals(route))action("/nas");
            else if("system".equals(route))destinations.launch("system");
            else if("weather".equals(route)||"ambient".equals(route))startActivity(new Intent(this,AmbientActivity.class).putExtra("panel","weather".equals(route)?"weather":"settings"));
            else cinema(route);
        });
        setContentView(scenicHome);
        if(messages!=null)scenicHome.messages(messages.display());
    }
    private void nativeAction(LinearLayout row,String title,Runnable run){Button control=TvUi.button(this,title,run);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,TvUi.dp(this,44),1);p.setMargins(0,0,TvUi.dp(this,10),0);row.addView(control,p);}
    private void setup() {
        if (!lobby.webAvailable(webViewVersion())) {
            new AlertDialog.Builder(this).setTitle("暂时不能打开家庭网页").setMessage(lobby.recoveryRequired() ? "网页显示组件刚刚退出。请先使用本机应用或NAS；在系统应用设置结束开视后重开，或重启电视，再尝试连接。" : "这台电视的网页组件不可用。请在原系统检查WebView；本机应用、原桌面和NAS仍可使用。")
                .setPositiveButton("返回客厅入口", (d,w) -> showLobby("")).setNegativeButton("返回", null).show(); return;
        }
        LinearLayout layout = column("请输入电脑或 NAS 的服务地址");
        final AlertDialog[] panel=new AlertDialog[1];
        EditText input = new EditText(this); input.setText(preferences.getString("origin", "")); input.setHint("https://screen.example.com 或局域网IP地址"); input.setSingleLine(true); TvUi.textSize(this,input,17); input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); layout.addView(input);
        CheckBox lan=new CheckBox(this);lan.setText("允许家庭局域网HTTP（明文连接）");TvUi.textSize(this,lan,17);lan.setTextColor(Color.WHITE);lan.setChecked(preferences.getBoolean("allowLanHttp",false));layout.addView(lan);
        button(layout, "保存并打开家庭屏", () -> {
            String raw = input.getText().toString().trim().replaceAll("/+$", ""); Uri uri = Uri.parse(raw);
            boolean scheme = HouseholdProxy.allowedOrigin(raw,lan.isChecked());
            if (!scheme || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty()) || uri.getPort() > 65535 || uri.getPort() == 0) { input.setError("请输入HTTPS地址；HTTP需勾选并使用家庭私网IP，不含路径或账号"); return; }
            Runnable saved = () -> { preferences.edit().putString("origin", raw).putBoolean("allowLanHttp",lan.isChecked()).apply(); panel[0].dismiss(); family(null); };
            if (!raw.equals(preferences.getString("origin", ""))) CookieManager.getInstance().removeAllCookies(success -> saved.run()); else saved.run();
        });
        panel[0]=TvDialogs.panel(this,"家庭服务",layout); input.requestFocus();
    }
    private void fallback(String message) {
        nativeLobby();
        new AlertDialog.Builder(this).setTitle("家庭服务暂不可用").setMessage(message)
            .setPositiveButton("返回首页",(d,w)->showLobby(""))
            .setNeutralButton("服务设置",(d,w)->setup()).setNegativeButton("返回",null).show();
    }
    private void installation() {
        new TvDialogs.SideBuilder(this).setTitle("安装应用").setItems(new String[]{"选择本机APK", "从NAS选择APK", "WorkBuddy安装应用", "WorkBuddy卸载应用", "安装说明与应用管理"}, (dialog, which) -> {
            if(which==0)installer.choose();else if(which==1)startActivity(new Intent(this,NasActivity.class));else if(which==2||which==3)startActivity(new Intent(this,AppAgentActivity.class).putExtra("kind",which==2?"install":"uninstall"));else destinations.installation();
        }).setNegativeButton("返回",null).show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        if(request==4602&&result==RESULT_OK&&data!=null){String view=data.getStringExtra("cinemaServer");if("live".equals(view)||"series".equals(view)||"movies".equals(view)||"variety".equals(view)||"library".equals(view))familyServer(view);return;}
        if (installer != null && installer.result(request, result, data)) return;
        super.onActivityResult(request, result, data);
    }
    private void hubConnection(){new TvDialogs.SideBuilder(this).setTitle("智小策 Hub").setMessage("未连接\n\n电影 · 体重 · 旅行 · 账本\n\n扫码登录服务尚未接入。连接后，由你选择允许显示的内容。").setPositiveButton("返回",null).show();}
    private void showDiagnostics(){
        View returnTo=settingsMenuDialog!=null&&settingsMenuDialog.isShowing()?settingsMenuDialog.getCurrentFocus():getCurrentFocus();
        if(diagnosticsDialog!=null)diagnosticsDialog.dismiss();diagnosticsDialog=diagnostics.show(this,returnTo);
    }
    private void featureMenu(String title,String[] options,android.content.DialogInterface.OnClickListener listener){
        java.util.List<String> names=new java.util.ArrayList<>();java.util.List<Integer> indices=new java.util.ArrayList<>();
        for(int i=0;i<options.length;i++){if(!FeaturePolicy.photos()&&"NAS相册".equals(options[i]))continue;names.add(options[i]);indices.add(i);}
        int diagnosticAt=names.indexOf("本机诊断");if(!BuildConfig.PARENTS_EDITION&&diagnosticAt>1){String name=names.remove(diagnosticAt);int index=indices.remove(diagnosticAt);names.add(1,name);indices.add(1,index);}
        if(settingsMenuDialog!=null)settingsMenuDialog.dismiss();
        settingsMenuDialog=TvDialogs.menu(this,title,names.toArray(new String[0]),(dialog,index)->listener.onClick(dialog,indices.get(index)),names.indexOf("本机诊断"));
    }
    private void quickMenu() {
        if (BuildConfig.PARENTS_EDITION) {
            featureMenu("设置",new String[] {"返回客厅入口", "连接 / 更换家庭服务", "切回原生桌面", "选择原生桌面", "默认桌面设置", FeaturePolicy.photos()?"家人留言与相册":"安排与留言", "小爱同学与唤醒", "管理常用应用", "所有应用", "NAS", "NAS相册", "首页样式", "安装应用", "直播与影视来源", "智小策 Hub", "局域网控制"}, (dialog, which) -> {
                if (which == 0) showLobby(""); else if (which == 1) setup(); else if (which == 2) destinations.launch("system"); else if (which == 3) destinations.pick("system", false); else if (which == 4) destinations.settings(true); else if (which == 5) family(null); else if (which == 6) assistant.help(); else if (which == 7) destinations.shortcuts(); else if (which == 9) action("/nas"); else if (which == 10) action("/nas-photos"); else if (which == 11) homeMode(); else if (which == 12) installation(); else if (which == 13) cinema("settings"); else if (which == 14) hubConnection(); else if (which == 15) TvLocalControl.open(this); else destinations.all();
            }); return;
        }
        String[] options = {"默认桌面与开机", "打开直播", "打开影视仓", "切回原生桌面", "选择直播应用", "选择影视应用", "返回首页", "更换家庭服务地址", "所有应用", FeaturePolicy.photos()?"切换轻量 / 完整窗景":"安排与留言", "小爱同学与语音唤醒", "NAS", "首页样式", "安装应用", "直播与影视来源", "智小策 Hub", "局域网控制", "本机诊断", "扫码编辑留言"};
        featureMenu("设置",options, (dialog, which) -> {
            if (which == 0) destinations.settings(true); else if (which == 1) cinema("live"); else if (which == 2) cinema("library"); else if (which == 3) destinations.launch("system");
            else if (which == 4) destinations.pick("live", false); else if (which == 5) destinations.pick("cinema", false); else if (which == 6) showLobby(""); else if (which == 7) setup(); else if (which == 8) destinations.all();
            else if (which == 10) assistant.help();
            else if (which == 11) action("/nas");
            else if (which == 12) homeMode();
            else if (which == 13) installation();
            else if (which == 14) cinema("settings");
            else if (which == 15) hubConnection(); else if (which == 16) TvLocalControl.open(this);else if(which==17)showDiagnostics();else if(which==18)action("/message-edit");
            else if (BuildConfig.PARENTS_EDITION) family(null);
            else { preferences.edit().putBoolean("lite", !preferences.getBoolean("lite", false)).apply(); family(null); }
        });
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code=event.getKeyCode();
        boolean remote=code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT||code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN||code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_NUMPAD_ENTER||code==KeyEvent.KEYCODE_BUTTON_A;
        // Handle TV remote keys before WebView consumes them; native dialogs keep their own window focus.
        if(remote&&web!=null&&ready&&getWindow().getDecorView().hasWindowFocus()&&getCurrentFocus()==web){
            if(event.getAction()==KeyEvent.ACTION_DOWN)return onKeyDown(code,event);
            if(event.getAction()==KeyEvent.ACTION_UP)return onKeyUp(code,event);
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        // OEM assistant owns these events, including repeat/long-press and the unmodified key-up.
        if (VoiceAssistant.systemVoiceKey(keyCode)) return super.onKeyDown(keyCode, event);
        if (keyCode == KeyEvent.KEYCODE_MENU) { if (event.getRepeatCount() == 0) { if(web!=null&&ready)web.evaluateJavascript("!!((window.KaishiAmbient&&window.KaishiAmbient.key('ContextMenu'))||(window.KaishiWeatherUI&&window.KaishiWeatherUI.key('ContextMenu')))",value->{if(!"true".equals(value))quickMenu();});else quickMenu(); } return true; }
        if (keyCode == KeyEvent.KEYCODE_BACK) { if (event.getRepeatCount() == 0) onBackPressed(); return true; }
        String key = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? "ArrowLeft" : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? "ArrowRight" : keyCode == KeyEvent.KEYCODE_DPAD_UP ? "ArrowUp" : keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? "ArrowDown" : (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A) ? "Enter" : null;
        if (key != null && web != null && ready) { if ("Enter".equals(key) && event.getRepeatCount() > 0) return true; web.evaluateJavascript("!!(window.apertureRemoteKey && window.apertureRemoteKey(" + JSONObject.quote(key) + "))", null); return true; }
        return super.onKeyDown(keyCode, event);
    }
    private void leavePage() {
        if (!lobbyMode) { showLobby(""); return; }
        if(scenicHome!=null&&scenicHome.back())return;
        if(returnHomeDialog!=null&&returnHomeDialog.isShowing())return;
        returnHomeDialog=new AlertDialog.Builder(this).setTitle("返回系统原生桌面？")
            .setNegativeButton("留在开视",(dialog,which)->{})
            .setPositiveButton("返回原桌面",(dialog,which)->destinations.launch("system")).create();
        returnHomeDialog.setOnDismissListener(dialog->returnHomeDialog=null);
        returnHomeDialog.show();
        returnHomeDialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }
    @Override public boolean onKeyUp(int keyCode,KeyEvent event){
        if(VoiceAssistant.systemVoiceKey(keyCode))return super.onKeyUp(keyCode,event);
        if(web!=null&&ready)web.evaluateJavascript("window.KaishiAmbient&&window.KaishiAmbient.releaseKey()",null);
        if(web!=null&&ready&&(keyCode==KeyEvent.KEYCODE_DPAD_LEFT||keyCode==KeyEvent.KEYCODE_DPAD_RIGHT||keyCode==KeyEvent.KEYCODE_DPAD_UP||keyCode==KeyEvent.KEYCODE_DPAD_DOWN||keyCode==KeyEvent.KEYCODE_DPAD_CENTER||keyCode==KeyEvent.KEYCODE_ENTER||keyCode==KeyEvent.KEYCODE_NUMPAD_ENTER||keyCode==KeyEvent.KEYCODE_BUTTON_A||keyCode==KeyEvent.KEYCODE_MENU||keyCode==KeyEvent.KEYCODE_BACK))return true;
        return super.onKeyUp(keyCode,event);
    }
    @Override public void onBackPressed() { if(web!=null&&lobbyMode&&!ready){failHome(web,homeTicket,WebDiagnostics.Reason.USER_CANCEL_LOAD,false);leavePage();return;} if (web != null && ready) { WebView current = web; current.evaluateJavascript("!!(window.apertureBack && window.apertureBack())", result -> { if (current == web && !"true".equals(result)) leavePage(); }); } else leavePage(); }
    @Override protected void onPause() { resumed=false;if(scenicHome!=null)scenicHome.active(false);homeDeadline.active(false);if(homeProbeRetry!=null){homeTasks.cancel(homeProbeRetry);homeProbeRetry=null;} display.resumed(false); if (web != null) CookieManager.getInstance().flush(); super.onPause(); }
    @Override protected void onResume() { super.onResume();resumed=true; HomeGuardService.resumedHome(this); display.resumed(true);if(scenicHome!=null)scenicHome.active(display.active()); nasLabels();if(destinations!=null)destinations.refreshHomeSettings(); if (display.active()) immersive(); }
    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); display.focused(hasFocus);if(scenicHome!=null)scenicHome.active(display.active()); if (display.active()) immersive(); }
    @Override protected void onDestroy() { closed=true;resumed=false;if(homeDeadline!=null)homeDeadline.cancel();if(diagnosticsDialog!=null)diagnosticsDialog.dismiss();if(returnHomeDialog!=null)returnHomeDialog.dismiss();if(settingsMenuDialog!=null)settingsMenuDialog.dismiss();if(destinations!=null)destinations.dismissHomeSettings();if(installer!=null)installer.destroy();if(messages!=null)messages.close(); destroyWeb(); super.onDestroy(); }
}
