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
    private boolean ready = false, lobbyMode = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); preferences = getSharedPreferences("aperture", MODE_PRIVATE);
        destinations = new DestinationApps(this, preferences, slot -> showLobby(slot));
        assistant = new VoiceAssistant(this);
        installer = new ApkInstaller(this, () -> destinations.shortcuts());
        messages=new HomeMessages(this,()->{if(web!=null&&ready&&lobbyMode)web.evaluateJavascript("window.apertureMessagesRefresh&&window.apertureMessagesRefresh()",null);});
        immersive(); showLobby("");
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); showLobby(""); }
    private int webViewVersion() {
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
        if (weather != null) { weather.close(); weather = null; }
        if (household != null) { household.close(); household = null; }
        ambientAwake=false;getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(messages!=null)messages.active(false);
        ready = false; display.bind(null); WebView previous = web; web = null;
        if (previous != null) {
            // A dead renderer cannot be used again; detach before destroy, without stopLoading.
            if (!rendererGone) previous.stopLoading();
            if (previous.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) previous.getParent()).removeView(previous);
            previous.destroy();
        }
    }
    private void webActive(WebView current, boolean active) {
        if (current != web) return;
        if(messages!=null)messages.active(active&&lobbyMode);syncAmbientAwake();
        if (household != null) household.active(active);
        if (weather != null) weather.active(active);
        if (active) { current.onResume(); current.resumeTimers(); }
        // Best effort: stop carousel/media before yielding the window; never auto-resume playback.
        current.evaluateJavascript("(function(){if(window.apertureNativeFocus)window.apertureNativeFocus(" + active + ");" + (active ? "" : "var m=document.querySelectorAll('video,audio');for(var i=0;i<m.length;i++)m[i].pause();") + "})()", null);
        if (!active) { current.onPause(); current.pauseTimers(); }
    }
    private void createWeb(boolean lobby) {
        destroyWeb(); lobbyMode = lobby; web = new WebView(this);
        final WeatherProxy forecast=lobby?new WeatherProxy():null;weather=forecast;
        String householdOrigin = preferences.getString("origin", "");
        final HouseholdProxy feed = lobby && !BuildConfig.PARENTS_EDITION ? new HouseholdProxy(householdOrigin, householdOrigin.isEmpty() ? "" : CookieManager.getInstance().getCookie(householdOrigin), preferences.getBoolean("allowLanHttp", false)) : null;
        household = feed;
        WebSettings settings = web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setAllowFileAccessFromFileURLs(false); settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false); settings.setSupportMultipleWindows(false); settings.setTextZoom(100);
        settings.setSupportZoom(false); settings.setBuiltInZoomControls(false); settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true); settings.setLoadWithOverviewMode(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false); WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) { if (view == web) ready = false; }
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
            @Override public void onPageFinished(WebView view, String url) { if (view == web) { ready = true; CookieManager.getInstance().flush(); if (lobby) { webActive(view, display.active()); nasLabels(); } } }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) { if (view == web && request.isForMainFrame()) runOnUiThread(() -> { if (view == web) fallback("家庭服务返回错误，请核对服务地址。"); }); }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) { if (view == web && request.isForMainFrame()) runOnUiThread(() -> { if (view != web) return; if (lobby) nativeLobby(); else fallback("暂时连不上家庭服务。直播、影视应用和原桌面仍可使用。"); }); }
            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (view == web) { MainActivity.this.lobby.rendererGone(); destroyWeb(true); nativeLobby(); }
                return true;
            }
        });
        if(lobby){
            web.setOverScrollMode(View.OVER_SCROLL_NEVER);web.setHorizontalScrollBarEnabled(false);web.setVerticalScrollBarEnabled(false);
            // OEM WebView may scroll its native viewport during accessibility/remote focus changes.
            // Child DOM lists retain their own scrolling; only the outer native viewport is pinned.
            web.setOnScrollChangeListener((view,x,y,oldX,oldY)->{if(view==web&&(x!=0||y!=0))view.scrollTo(0,0);});
        }
        setContentView(web); web.requestFocus();
        WebView current = web; display.bind(active -> webActive(current, active));
    }
    private void showLobby(String pane) {
        destinations.initializeShortcuts();
        if (lobby.choose(webViewVersion(), preferences.getBoolean("simple_home", false)) != LobbyMode.Choice.GLASS) { nativeLobby(); return; }
        createWeb(true);
        Uri.Builder uri = Uri.parse(LOBBY).buildUpon().appendQueryParameter("edition", BuildConfig.PARENTS_EDITION ? "parents" : "standard");
        for (String slot : new String[] {"live", "cinema", "system", "quick1", "quick2", "quick3", "quick4"}) uri.appendQueryParameter(slot, destinations.label(slot));
        uri.appendQueryParameter("assistant", assistant.label());
        uri.appendQueryParameter("photos",FeaturePolicy.photos()?"1":"0");
        if("information".equals(pane))uri.appendQueryParameter("section","information");
        if ("quick1".equals(pane) || "quick2".equals(pane) || "quick3".equals(pane) || "quick4".equals(pane)) uri.appendQueryParameter("focus", pane);
        if ("live".equals(pane) || "cinema".equals(pane) || "system".equals(pane) || "settings".equals(pane) || "voice".equals(pane)) uri.appendQueryParameter("pane", pane);
        web.loadUrl(uri.build().toString());
    }
    private void syncAmbientAwake(){if(ambientAwake&&lobbyMode&&display.active())getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    private void messageSettings(){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);TextView note=new TextView(this);note.setText("由 WorkBuddy 更新留言文件，电视每分钟读取。只显示内容与时间。");note.setTextColor(TvUi.INK);note.setTextSize(18);form.addView(note);EditText input=new EditText(this);input.setTextColor(TvUi.INK);input.setTextSize(18);input.setSingleLine(true);input.setHint("HTTPS 留言源地址");input.setText(messages.source());form.addView(input);
        CheckBox pause=new CheckBox(this);pause.setText("暂停播报");pause.setTextColor(TvUi.INK);pause.setChecked(messages.paused());pause.setOnCheckedChangeListener((v,checked)->messages.paused(checked));form.addView(pause);
        Button save=TvUi.button(this,"保存留言源",()->{try{messages.configure(input.getText().toString());note.setText(input.getText().toString().trim().isEmpty()?"已断开留言源":"已保存，正在读取");}catch(Exception invalid){note.setText("请输入有效的 HTTPS 地址");}});form.addView(save);TvDialogs.panel(this,"留言源",form);
    }
    private void action(String path) {
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
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(60, 34, 60, 34); layout.setBackgroundColor(Color.rgb(11, 16, 23));
        TextView label = new TextView(this); label.setText(title); label.setTextSize(BuildConfig.PARENTS_EDITION ? 28 : 24); label.setTextColor(Color.rgb(240, 246, 251)); label.setPadding(0, 0, 0, 20); layout.addView(label); return layout;
    }
    private Button button(LinearLayout layout, String text, Runnable action) {
        float density = getResources().getDisplayMetrics().density;
        Button button = new Button(this); button.setText(text); button.setAllCaps(false);
        TvUi.styleButton(this,button);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, Math.round(6 * density), 0, Math.round(6 * density));
        button.setOnClickListener(view -> action.run()); layout.addView(button, params); return button;
    }
    private void showNative(LinearLayout layout) { ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(layout); setContentView(scroll); }
    private void homeMode() {
        String reason = lobby.recoveryRequired() ? "网页显示组件刚刚退出，本次先使用简洁首页。完全关闭开视后重新打开，才能再尝试玻璃首页。" : !lobby.glassAvailable(webViewVersion()) ? "当前WebView版本不满足玻璃首页要求，已自动使用简洁首页。家庭轻量屏与NAS分别判断，不会清除配置。" : "玻璃首页保留观看、应用与家庭内容；简洁首页不运行网页或背景动效，适合感觉操作吃力的电视。此设置不改变系统默认桌面。";
        new AlertDialog.Builder(this).setTitle("首页样式").setMessage(reason)
            .setPositiveButton("简洁首页", (d,w) -> { preferences.edit().putBoolean("simple_home", true).apply(); showLobby(""); })
            .setNeutralButton(lobby.glassAvailable(webViewVersion()) ? "玻璃首页" : "保留自动选择", (d,w) -> { preferences.edit().remove("simple_home").apply(); showLobby(""); })
            .setNegativeButton("返回", null).show();
    }
    private void nativeLobby() {
        destroyWeb(); lobbyMode = true;
        LinearLayout layout = column(BuildConfig.PARENTS_EDITION ? "开视 · 爸妈版" : "开视");
        layout.setPadding(TvUi.dp(this,48),TvUi.dp(this,30),TvUi.dp(this,48),TvUi.dp(this,30));layout.setBackground(TvUi.background());
        LobbyMode.Choice choice = lobby.choose(webViewVersion(), preferences.getBoolean("simple_home", false));
        layout.addView(TvUi.text(this,choice == LobbyMode.Choice.NATIVE_RECOVERY ? "网页已退出 · 简洁首页" : "简洁首页",20,true));
        LinearLayout watch=new LinearLayout(this);watch.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,-2);wp.setMargins(0,TvUi.dp(this,38),0,TvUi.dp(this,20));layout.addView(watch,wp);
        String[] titles={"看电视","看电视剧","看电影","看综艺"},views={"live","series","movies","variety"};
        Button first=null;
        for(int i=0;i<titles.length;i++){final String view=views[i];Button entry=TvUi.button(this,titles[i],()->cinema(view));entry.setTextSize(BuildConfig.PARENTS_EDITION?22:20);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,TvUi.dp(this,84),1);cp.setMargins(0,0,TvUi.dp(this,10),0);watch.addView(entry,cp);if(first==null)first=entry;}
        destinations.homeApps();
        LinearLayout shortcuts=new LinearLayout(this);shortcuts.setOrientation(LinearLayout.HORIZONTAL);
        for(String slot:new String[]{"quick1","quick2","quick3","quick4"}){
            String name=destinations.label(slot);if(name.isEmpty())continue;
            Button shortcut=TvUi.button(this,name,()->destinations.launchShortcut(slot));shortcut.setTextSize(16);shortcut.setMaxLines(1);shortcut.setEllipsize(android.text.TextUtils.TruncateAt.END);
            byte[] bytes=destinations.icon(slot);if(bytes!=null){android.graphics.drawable.Drawable icon=android.graphics.drawable.Drawable.createFromStream(new java.io.ByteArrayInputStream(bytes),null);if(icon!=null){icon.setBounds(0,0,TvUi.dp(this,32),TvUi.dp(this,32));shortcut.setCompoundDrawables(null,icon,null,null);shortcut.setCompoundDrawablePadding(TvUi.dp(this,6));}}
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(TvUi.dp(this,136),TvUi.dp(this,84));sp.setMargins(0,0,TvUi.dp(this,10),0);shortcuts.addView(shortcut,sp);
        }
        if(shortcuts.getChildCount()>0){layout.addView(TvUi.text(this,"常用应用",22,true));layout.addView(shortcuts);}
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.setMargins(0,TvUi.dp(this,28),0,0);layout.addView(actions,ap);
        nativeAction(actions,"所有应用",destinations::all);nativeAction(actions,"NAS",()->action("/nas"));
        if(!BuildConfig.PARENTS_EDITION)nativeAction(actions,"家庭屏",()->family(null));
        nativeAction(actions,"原桌面",()->destinations.launch("system"));nativeAction(actions,"设置",this::quickMenu);
        LinearLayout ambientActions=new LinearLayout(this);ambientActions.setOrientation(LinearLayout.HORIZONTAL);layout.addView(ambientActions);
        nativeAction(ambientActions,"天气",()->startActivity(new Intent(this,AmbientActivity.class).putExtra("panel","weather")));nativeAction(ambientActions,"屏保",()->startActivity(new Intent(this,AmbientActivity.class).putExtra("panel","settings")));
        showNative(layout);if(first!=null)first.requestFocus();
    }
    private void nativeAction(LinearLayout row,String title,Runnable run){Button control=TvUi.button(this,title,run);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,TvUi.dp(this,44),1);p.setMargins(0,0,TvUi.dp(this,10),0);row.addView(control,p);}
    private void setup() {
        if (!lobby.webAvailable(webViewVersion())) {
            new AlertDialog.Builder(this).setTitle("暂时不能打开家庭网页").setMessage(lobby.recoveryRequired() ? "网页显示组件刚刚退出。请先使用本机应用或NAS；在系统应用设置结束开视后重开，或重启电视，再尝试连接。" : "这台电视的网页组件不可用。请在原系统检查WebView；本机应用、原桌面和NAS仍可使用。")
                .setPositiveButton("返回客厅入口", (d,w) -> showLobby("")).setNegativeButton("返回", null).show(); return;
        }
        LinearLayout layout = column("请输入电脑或 NAS 的服务地址");
        final AlertDialog[] panel=new AlertDialog[1];
        EditText input = new EditText(this); input.setText(preferences.getString("origin", "")); input.setHint("https://screen.example.com 或局域网IP地址"); input.setSingleLine(true); input.setTextSize(20); input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); layout.addView(input);
        CheckBox lan=new CheckBox(this);lan.setText("允许家庭局域网HTTP（明文连接）");lan.setTextSize(20);lan.setTextColor(Color.WHITE);lan.setChecked(preferences.getBoolean("allowLanHttp",false));layout.addView(lan);
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
        destroyWeb(); lobbyMode = false; LinearLayout layout = column("开视\n" + message);
        button(layout, "返回客厅入口", () -> showLobby("")); button(layout, "重试家庭屏", () -> family(null)); button(layout, "更换家庭服务地址", this::setup); button(layout, "切回原生桌面", () -> destinations.launch("system")); showNative(layout);
    }
    private void installation() {
        new AlertDialog.Builder(this).setTitle("安装应用").setItems(new String[]{"选择本机APK", "从NAS选择APK", "WorkBuddy安装应用", "WorkBuddy卸载应用", "安装说明与应用管理"}, (dialog, which) -> {
            if(which==0)installer.choose();else if(which==1)startActivity(new Intent(this,NasActivity.class));else if(which==2||which==3)startActivity(new Intent(this,AppAgentActivity.class).putExtra("kind",which==2?"install":"uninstall"));else destinations.installation();
        }).setNegativeButton("返回",null).show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        if(request==4602&&result==RESULT_OK&&data!=null){String view=data.getStringExtra("cinemaServer");if("live".equals(view)||"series".equals(view)||"movies".equals(view)||"variety".equals(view)||"library".equals(view))familyServer(view);return;}
        if (installer != null && installer.result(request, result, data)) return;
        super.onActivityResult(request, result, data);
    }
    private void hubConnection(){new AlertDialog.Builder(this).setTitle("智小策 Hub").setMessage("未连接\n\n电影 · 体重 · 旅行 · 账本\n\n扫码登录服务尚未接入。连接后，由你选择允许显示的内容。").setPositiveButton("返回",null).show();}
    private void featureMenu(String title,String[] options,android.content.DialogInterface.OnClickListener listener){
        java.util.List<String> names=new java.util.ArrayList<>();java.util.List<Integer> indices=new java.util.ArrayList<>();
        for(int i=0;i<options.length;i++){if(!FeaturePolicy.photos()&&"NAS相册".equals(options[i]))continue;names.add(options[i]);indices.add(i);}
        TvDialogs.menu(this,title,names.toArray(new String[0]),(dialog,index)->listener.onClick(dialog,indices.get(index)));
    }
    private void quickMenu() {
        if (BuildConfig.PARENTS_EDITION) {
            featureMenu("设置",new String[] {"返回客厅入口", "连接 / 更换家庭服务", "切回原生桌面", "选择原生桌面", "默认桌面设置", FeaturePolicy.photos()?"家人留言与相册":"安排与留言", "小爱同学与唤醒", "管理常用应用", "所有应用", "NAS", "NAS相册", "玻璃 / 简洁本机首页", "安装应用", "直播与影视来源", "智小策 Hub"}, (dialog, which) -> {
                if (which == 0) showLobby(""); else if (which == 1) setup(); else if (which == 2) destinations.launch("system"); else if (which == 3) destinations.pick("system", false); else if (which == 4) destinations.settings(true); else if (which == 5) family(null); else if (which == 6) assistant.help(); else if (which == 7) destinations.shortcuts(); else if (which == 9) action("/nas"); else if (which == 10) action("/nas-photos"); else if (which == 11) homeMode(); else if (which == 12) installation(); else if (which == 13) cinema("settings"); else if (which == 14) hubConnection(); else destinations.all();
            }); return;
        }
        String[] options = {"返回客厅入口", "打开直播", "打开影视仓", "切回原生桌面", "选择直播应用", "选择影视应用", "默认桌面设置", "更换家庭服务地址", "所有应用", FeaturePolicy.photos()?"切换轻量 / 完整窗景":"安排与留言", "小爱同学与语音唤醒", "NAS", "玻璃 / 简洁本机首页", "安装应用", "直播与影视来源", "智小策 Hub"};
        featureMenu("设置",options, (dialog, which) -> {
            if (which == 0) showLobby(""); else if (which == 1) cinema("live"); else if (which == 2) cinema("library"); else if (which == 3) destinations.launch("system");
            else if (which == 4) destinations.pick("live", false); else if (which == 5) destinations.pick("cinema", false); else if (which == 6) destinations.settings(true); else if (which == 7) setup(); else if (which == 8) destinations.all();
            else if (which == 10) assistant.help();
            else if (which == 11) action("/nas");
            else if (which == 12) homeMode();
            else if (which == 13) installation();
            else if (which == 14) cinema("settings");
            else if (which == 15) hubConnection();
            else if (BuildConfig.PARENTS_EDITION) family(null);
            else { preferences.edit().putBoolean("lite", !preferences.getBoolean("lite", false)).apply(); family(null); }
        });
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        // OEM assistant owns these events, including repeat/long-press and the unmodified key-up.
        if (VoiceAssistant.systemVoiceKey(keyCode)) return super.onKeyDown(keyCode, event);
        if (keyCode == KeyEvent.KEYCODE_MENU) { if (event.getRepeatCount() == 0) { if(web!=null&&ready)web.evaluateJavascript("!!((window.KaishiAmbient&&window.KaishiAmbient.key('ContextMenu'))||(window.KaishiWeatherUI&&window.KaishiWeatherUI.key('ContextMenu')))",value->{if(!"true".equals(value))quickMenu();});else quickMenu(); } return true; }
        if (keyCode == KeyEvent.KEYCODE_BACK) { if (event.getRepeatCount() == 0) onBackPressed(); return true; }
        String key = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? "ArrowLeft" : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? "ArrowRight" : keyCode == KeyEvent.KEYCODE_DPAD_UP ? "ArrowUp" : keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? "ArrowDown" : (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) ? "Enter" : null;
        if (key != null && web != null && ready) { if ("Enter".equals(key) && event.getRepeatCount() > 0) return true; web.evaluateJavascript("!!(window.apertureRemoteKey && window.apertureRemoteKey(" + JSONObject.quote(key) + "))", null); return true; }
        return super.onKeyDown(keyCode, event);
    }
    private void leavePage() {
        if (!lobbyMode) { showLobby(""); return; }
        destinations.launch("system");
    }
    @Override public boolean onKeyUp(int keyCode,KeyEvent event){
        if(VoiceAssistant.systemVoiceKey(keyCode))return super.onKeyUp(keyCode,event);
        if(web!=null&&ready)web.evaluateJavascript("window.KaishiAmbient&&window.KaishiAmbient.releaseKey()",null);
        if(web!=null&&ready&&(keyCode==KeyEvent.KEYCODE_DPAD_LEFT||keyCode==KeyEvent.KEYCODE_DPAD_RIGHT||keyCode==KeyEvent.KEYCODE_DPAD_UP||keyCode==KeyEvent.KEYCODE_DPAD_DOWN||keyCode==KeyEvent.KEYCODE_DPAD_CENTER||keyCode==KeyEvent.KEYCODE_ENTER||keyCode==KeyEvent.KEYCODE_MENU||keyCode==KeyEvent.KEYCODE_BACK))return true;
        return super.onKeyUp(keyCode,event);
    }
    @Override public void onBackPressed() { if (web != null && ready) { WebView current = web; current.evaluateJavascript("!!(window.apertureBack && window.apertureBack())", result -> { if (current == web && !"true".equals(result)) leavePage(); }); } else leavePage(); }
    @Override protected void onPause() { display.resumed(false); if (web != null) CookieManager.getInstance().flush(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); display.resumed(true); nasLabels(); if (display.active()) immersive(); }
    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); display.focused(hasFocus); if (display.active()) immersive(); }
    @Override protected void onDestroy() { if(installer!=null)installer.destroy();if(messages!=null)messages.close(); destroyWeb(); super.onDestroy(); }
}
