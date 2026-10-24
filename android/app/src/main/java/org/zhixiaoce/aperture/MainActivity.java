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
        String file = LobbyAssets.file(uri.getScheme(), uri.getHost(), uri.getPort(), uri.getPath());
        if (file == null) return blockedAsset();
        try { return new WebResourceResponse(LobbyAssets.mime(file), file.endsWith(".woff2") || file.endsWith(".png") || file.endsWith(".jpg") ? null : "UTF-8", 200, "OK", Collections.singletonMap("Cache-Control", "no-store"), getAssets().open(file)); }
        catch (IOException error) { return blockedAsset(); }
    }
    private void destroyWeb() { destroyWeb(false); }
    private void destroyWeb(boolean rendererGone) {
        if (household != null) { household.close(); household = null; }
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
        if (household != null) household.active(active);
        if (active) { current.onResume(); current.resumeTimers(); }
        // Best effort: stop carousel/media before yielding the window; never auto-resume playback.
        current.evaluateJavascript("(function(){if(window.apertureNativeFocus)window.apertureNativeFocus(" + active + ");" + (active ? "" : "var m=document.querySelectorAll('video,audio');for(var i=0;i<m.length;i++)m[i].pause();") + "})()", null);
        if (!active) { current.onPause(); current.pauseTimers(); }
    }
    private void createWeb(boolean lobby) {
        destroyWeb(); lobbyMode = lobby; web = new WebView(this);
        String householdOrigin = preferences.getString("origin", "");
        final HouseholdProxy feed = lobby && !BuildConfig.PARENTS_EDITION ? new HouseholdProxy(householdOrigin, householdOrigin.isEmpty() ? "" : CookieManager.getInstance().getCookie(householdOrigin), BuildConfig.DEBUG) : null;
        household = feed;
        WebSettings settings = web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setAllowFileAccessFromFileURLs(false); settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false); settings.setSupportMultipleWindows(false); settings.setTextZoom(100);
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
        setContentView(web); web.requestFocus();
        WebView current = web; display.bind(active -> webActive(current, active));
    }
    private void showLobby(String pane) {
        if (lobby.choose(webViewVersion(), preferences.getBoolean("simple_home", false)) != LobbyMode.Choice.GLASS) { nativeLobby(); return; }
        createWeb(true);
        Uri.Builder uri = Uri.parse(LOBBY).buildUpon().appendQueryParameter("edition", BuildConfig.PARENTS_EDITION ? "parents" : "standard");
        for (String slot : new String[] {"live", "cinema", "system", "quick1", "quick2", "quick3", "quick4"}) uri.appendQueryParameter(slot, destinations.label(slot));
        uri.appendQueryParameter("assistant", assistant.label());
        if ("quick1".equals(pane) || "quick2".equals(pane) || "quick3".equals(pane) || "quick4".equals(pane)) uri.appendQueryParameter("focus", pane);
        if ("live".equals(pane) || "cinema".equals(pane) || "system".equals(pane) || "settings".equals(pane) || "voice".equals(pane)) uri.appendQueryParameter("pane", pane);
        web.loadUrl(uri.build().toString());
    }
    private void action(String path) {
        if ("/cinema-settings".equals(path)) { cinema("settings"); return; }
        if ("/nas".equals(path) || "/nas-photos".equals(path)) {
            Intent intent=new Intent(this,NasActivity.class).putExtra("filter","/nas-photos".equals(path)?"photo":"all");
            if("/nas-photos".equals(path)){try{if(new NasVault(this).library().route("photos")!=null)intent.putExtra("category","photos");}catch(Exception unavailable){nasUnavailable();return;}}
            startActivity(intent); return;
        }
        if ("/choose-shortcuts".equals(path)) { destinations.shortcuts(); return; }
        if ("/install-apps".equals(path)) { installation(); return; }
        if ("/open-quick1".equals(path) || "/open-quick2".equals(path) || "/open-quick3".equals(path) || "/open-quick4".equals(path)) { destinations.launchShortcut(path.substring(6)); return; }
        if ("/open-assistant".equals(path)) { assistant.open(); return; }
        if ("/account-settings".equals(path)) { accountSettings(); return; }
        if ("/voice-help".equals(path)) { assistant.help(); return; }
        if ("/voice-settings".equals(path)) { assistant.settings(); return; }
        if ("/home-mode".equals(path)) { homeMode(); return; }
        if ("/family".equals(path)) { family(null); return; }
        if ("/family-live".equals(path)) { family("live"); return; }
        if ("/family-series".equals(path)) { family("series"); return; }
        if ("/family-movies".equals(path)) { family("movies"); return; }
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
        if (view == null) familyServer(null); else cinema(view);
    }
    private void cinema(String view) { startActivityForResult(new Intent(this,CinemaActivity.class).putExtra("view",view),4602); }
    private void familyServer(String view) {
        String origin = preferences.getString("origin", ""); if (origin.isEmpty()) { setup(); return; }
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
        if(!BuildConfig.PARENTS_EDITION||!lobbyMode||web==null||!ready)return;
        String movies="家人统一配置电影",series="家人统一配置电视剧";
        try { NasLibrary library=new NasVault(this).library();NasLibrary.Route movie=library.route("movies"),serial=library.route("series");if(movie!=null)movies="NAS · "+movie.connection.optString("name");if(serial!=null)series="NAS · "+serial.connection.optString("name"); }
        catch(Exception unavailable){movies="请家人检查片库";series="请家人检查片库";}
        web.evaluateJavascript("window.apertureNasLabels&&window.apertureNasLabels("+JSONObject.quote(movies)+","+JSONObject.quote(series)+")",null);
    }
    private LinearLayout column(String title) {
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(60, 34, 60, 34); layout.setBackgroundColor(Color.rgb(11, 16, 23));
        TextView label = new TextView(this); label.setText(title); label.setTextSize(BuildConfig.PARENTS_EDITION ? 28 : 24); label.setTextColor(Color.rgb(240, 246, 251)); label.setPadding(0, 0, 0, 20); layout.addView(label); return layout;
    }
    private Button button(LinearLayout layout, String text, Runnable action) {
        float density = getResources().getDisplayMetrics().density;
        Button button = new Button(this); button.setText(text); button.setAllCaps(false);
        button.setTextSize(BuildConfig.PARENTS_EDITION ? 28 : 24); button.setMinHeight(Math.round(72 * density));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, Math.round(6 * density), 0, Math.round(6 * density));
        button.setOnClickListener(view -> action.run()); layout.addView(button, params); return button;
    }
    private void showNative(LinearLayout layout) { ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(layout); setContentView(scroll); }
    private void homeMode() {
        String reason = lobby.recoveryRequired() ? "网页显示组件刚刚退出，本次先使用简洁首页。完全关闭开视后重新打开，才能再尝试玻璃首页。" : !lobby.glassAvailable(webViewVersion()) ? "当前WebView版本不满足玻璃首页要求，已自动使用简洁首页。家庭轻量屏与NAS分别判断，不会清除配置。" : "玻璃首页保留照片、数据与信息分区；简洁首页不运行网页或背景动效，适合感觉操作吃力的电视。此设置不改变系统默认桌面。";
        new AlertDialog.Builder(this).setTitle("首页样式").setMessage(reason)
            .setPositiveButton("简洁首页", (d,w) -> { preferences.edit().putBoolean("simple_home", true).apply(); showLobby(""); })
            .setNeutralButton(lobby.glassAvailable(webViewVersion()) ? "玻璃首页" : "保留自动选择", (d,w) -> { preferences.edit().remove("simple_home").apply(); showLobby(""); })
            .setNegativeButton("返回", null).show();
    }
    private void nativeLobby() {
        destroyWeb(); lobbyMode = true; LinearLayout layout = column(BuildConfig.PARENTS_EDITION ? "开视 · 爸妈版" : "开视");
        LobbyMode.Choice choice = lobby.choose(webViewVersion(), preferences.getBoolean("simple_home", false));
        TextView note = new TextView(this); note.setText(choice == LobbyMode.Choice.NATIVE_RECOVERY ? "网页显示组件刚刚退出，先用这些本机入口。不会自动反复重开网页。" : choice == LobbyMode.Choice.NATIVE_OLD_ENGINE ? "当前使用简洁本机首页，适合这台电视的网页内核。" : "简洁本机首页 · 在设置中可切换"); note.setTextSize(18); note.setTextColor(Color.rgb(196,210,224)); layout.addView(note);
        if (BuildConfig.PARENTS_EDITION) {
            button(layout, "看电视", () -> family("live")); button(layout, "看电视剧", () -> family("series")); button(layout, "看电影", () -> family("movies"));
            for (String slot : new String[] {"quick1", "quick2", "quick3", "quick4"}) { String name = destinations.label(slot); Button shortcut = button(layout, name.isEmpty() ? "未设置" : name, () -> destinations.launchShortcut(slot)); shortcut.setTextSize(24); }
            button(layout, "所有应用", destinations::all); button(layout, "设置", this::quickMenu); showNative(layout); return;
        }
        button(layout, "直播", () -> cinema("live")); button(layout, "影视", () -> cinema("library")); button(layout, "原桌面", () -> destinations.launch("system"));
        if (!BuildConfig.PARENTS_EDITION) button(layout, "家庭屏", () -> family(null)); button(layout, "NAS", () -> action("/nas")); button(layout, "设置", this::quickMenu); showNative(layout);
    }
    private void setup() {
        if (!lobby.webAvailable(webViewVersion())) {
            new AlertDialog.Builder(this).setTitle("暂时不能打开家庭网页").setMessage(lobby.recoveryRequired() ? "网页显示组件刚刚退出。请先使用本机应用或NAS；在系统应用设置结束开视后重开，或重启电视，再尝试连接。" : "这台电视的网页组件不可用。请在原系统检查WebView；本机应用、原桌面和NAS仍可使用。")
                .setPositiveButton("返回客厅入口", (d,w) -> showLobby("")).setNegativeButton("返回", null).show(); return;
        }
        destroyWeb(); lobbyMode = false; LinearLayout layout = column("连接家庭服务\n请家人输入电脑或 NAS 的服务地址。");
        EditText input = new EditText(this); input.setText(preferences.getString("origin", "")); input.setHint(BuildConfig.DEBUG ? "https://screen.example.com 或内测局域网地址" : "https://screen.example.com"); input.setSingleLine(true); input.setTextSize(20); input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); layout.addView(input);
        button(layout, "保存并打开家庭屏", () -> {
            String raw = input.getText().toString().trim().replaceAll("/+$", ""); Uri uri = Uri.parse(raw);
            boolean scheme = "https".equals(uri.getScheme()) || (BuildConfig.DEBUG && "http".equals(uri.getScheme()));
            if (!scheme || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty()) || uri.getPort() > 65535 || uri.getPort() == 0) { input.setError("请输入完整服务源地址，不含路径或账号；正式版使用HTTPS"); return; }
            Runnable saved = () -> { preferences.edit().putString("origin", raw).apply(); family(null); };
            if (!raw.equals(preferences.getString("origin", ""))) CookieManager.getInstance().removeAllCookies(success -> saved.run()); else saved.run();
        });
        button(layout, "返回客厅入口", () -> showLobby("")); showNative(layout); input.requestFocus();
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
        if(request==4602&&result==RESULT_OK&&data!=null){String view=data.getStringExtra("cinemaServer");if("live".equals(view)||"series".equals(view)||"movies".equals(view)||"library".equals(view))familyServer(view);return;}
        if (installer != null && installer.result(request, result, data)) return;
        super.onActivityResult(request, result, data);
    }
    private void quickMenu() {
        if (BuildConfig.PARENTS_EDITION) {
            new AlertDialog.Builder(this).setTitle("设置").setItems(new String[] {"返回客厅入口", "连接 / 更换家庭服务", "切回原生桌面", "选择原生桌面", "默认桌面设置", "家人留言与相册", "小爱同学与唤醒", "管理常用应用", "所有应用", "NAS", "NAS相册", "玻璃 / 简洁本机首页", "安装应用", "直播与影视来源"}, (dialog, which) -> {
                if (which == 0) showLobby(""); else if (which == 1) setup(); else if (which == 2) destinations.launch("system"); else if (which == 3) destinations.pick("system", false); else if (which == 4) destinations.settings(true); else if (which == 5) family(null); else if (which == 6) assistant.help(); else if (which == 7) destinations.shortcuts(); else if (which == 9) action("/nas"); else if (which == 10) action("/nas-photos"); else if (which == 11) homeMode(); else if (which == 12) installation(); else if (which == 13) cinema("settings"); else destinations.all();
            }).setNegativeButton("返回", null).show(); return;
        }
        String[] options = {"返回客厅入口", "打开直播", "打开影视仓", "切回原生桌面", "选择直播应用", "选择影视应用", "默认桌面设置", "更换家庭服务地址", "所有应用", BuildConfig.PARENTS_EDITION ? "家人留言和相册" : "切换轻量 / 完整窗景", "小爱同学与语音唤醒", "NAS", "玻璃 / 简洁本机首页", "安装应用", "直播与影视来源"};
        new AlertDialog.Builder(this).setTitle("设置").setItems(options, (dialog, which) -> {
            if (which == 0) showLobby(""); else if (which == 1) cinema("live"); else if (which == 2) cinema("library"); else if (which == 3) destinations.launch("system");
            else if (which == 4) destinations.pick("live", false); else if (which == 5) destinations.pick("cinema", false); else if (which == 6) destinations.settings(true); else if (which == 7) setup(); else if (which == 8) destinations.all();
            else if (which == 10) assistant.help();
            else if (which == 11) action("/nas");
            else if (which == 12) homeMode();
            else if (which == 13) installation();
            else if (which == 14) cinema("settings");
            else if (BuildConfig.PARENTS_EDITION) family(null);
            else { preferences.edit().putBoolean("lite", !preferences.getBoolean("lite", false)).apply(); family(null); }
        }).setNegativeButton("返回", null).show();
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        // OEM assistant owns these events, including repeat/long-press and the unmodified key-up.
        if (VoiceAssistant.systemVoiceKey(keyCode)) return super.onKeyDown(keyCode, event);
        if (keyCode == KeyEvent.KEYCODE_MENU) { if (event.getRepeatCount() == 0) quickMenu(); return true; }
        if (keyCode == KeyEvent.KEYCODE_BACK) { if (event.getRepeatCount() == 0) onBackPressed(); return true; }
        String key = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? "ArrowLeft" : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? "ArrowRight" : keyCode == KeyEvent.KEYCODE_DPAD_UP ? "ArrowUp" : keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? "ArrowDown" : (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) ? "Enter" : null;
        if (key != null && web != null && ready) { if ("Enter".equals(key) && event.getRepeatCount() > 0) return true; web.evaluateJavascript("!!(window.apertureRemoteKey && window.apertureRemoteKey(" + JSONObject.quote(key) + "))", null); return true; }
        return super.onKeyDown(keyCode, event);
    }
    private void leavePage() {
        if (!lobbyMode || BuildConfig.PARENTS_EDITION) { showLobby(""); return; }
        new AlertDialog.Builder(this).setTitle("回到哪里？").setItems(new String[] {"留在开视", "切回原生桌面", "系统默认桌面设置"}, (dialog, which) -> { if (which == 1) destinations.launch("system"); else if (which == 2) destinations.settings(true); }).show();
    }
    @Override public void onBackPressed() { if (web != null && ready) { WebView current = web; current.evaluateJavascript("!!(window.apertureBack && window.apertureBack())", result -> { if (current == web && !"true".equals(result)) leavePage(); }); } else leavePage(); }
    @Override protected void onPause() { display.resumed(false); if (web != null) CookieManager.getInstance().flush(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); display.resumed(true); nasLabels(); if (display.active()) immersive(); }
    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); display.focused(hasFocus); if (display.active()) immersive(); }
    @Override protected void onDestroy() { if(installer!=null)installer.destroy(); destroyWeb(); super.onDestroy(); }
}
