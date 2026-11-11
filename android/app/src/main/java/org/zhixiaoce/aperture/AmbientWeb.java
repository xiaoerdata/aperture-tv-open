package org.zhixiaoce.aperture;

import android.content.Context;
import android.webkit.*;
import java.io.ByteArrayInputStream;
import java.util.Collections;

/** Isolated shared screensaver page; only packaged assets and the fixed weather transport. */
final class AmbientWeb implements AutoCloseable {
    final WebView web;private final WeatherProxy weather;private final Runnable finish;
    private boolean active,closed,dead,leaving,disposed;
    AmbientWeb(Context context,String panel,Runnable finish){
        this.finish=finish;weather=new WeatherProxy(value->{String snapshot=HomeWeatherSnapshot.capture(value);if(!snapshot.isEmpty())context.getSharedPreferences("aperture",0).edit().putString("home_weather_snapshot",snapshot).apply();});
        web=new WebView(context);web.setBackgroundColor(0xff020409);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setAllowFileAccessFromFileURLs(false);settings.setAllowUniversalAccessFromFileURLs(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setTextZoom(100);settings.setSupportZoom(false);settings.setMediaPlaybackRequiresUserGesture(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){if(view==web&&request.isForMainFrame()&&"aperture://ambient/close".equals(request.getUrl().toString()))leave();return true;}
            public boolean shouldOverrideUrlLoading(WebView view,String url){if(view==web&&"aperture://ambient/close".equals(url))leave();return true;}
            public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                WebResourceResponse album=new GalleryStore(context).read(request);if(album!=null)return album;
                WeatherProxy.Reply reply=weather.read(request.getUrl().toString(),request.getMethod(),request.isForMainFrame());if(reply!=null)return response("application/json",reply.status,reply.body);
                String file=LobbyAssets.file(request.getUrl().getScheme(),request.getUrl().getHost(),request.getUrl().getPort(),request.getUrl().getPath());
                if("GET".equals(request.getMethod())&&file!=null&&(request.getUrl().getQuery()==null||("ambient.html".equals(file)&&request.getUrl().getQuery().matches("panel=(weather|settings)|dream=1")))&&request.getUrl().getFragment()==null&&request.getUrl().getUserInfo()==null)try{return new WebResourceResponse(LobbyAssets.mime(file),"UTF-8",200,"OK",Collections.singletonMap("Cache-Control","no-store"),context.getAssets().open(file));}catch(Exception ignored){}
                return response("text/plain",403,new byte[0]);
            }
            public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){if(view==web){dead=true;dispose();leave();}return true;}
            public void onPageFinished(WebView view,String url){if(view==web&&available())active(active);}
        });
        String query="weather".equals(panel)?"?panel=weather":"settings".equals(panel)?"?panel=settings":"dream".equals(panel)?"?dream=1":"";
        web.loadUrl("https://"+LobbyAssets.HOST+"/assets/ambient.html"+query);
    }
    private WebResourceResponse response(String mime,int status,byte[] bytes){return new WebResourceResponse(mime,"UTF-8",status,status==200?"OK":"Unavailable",Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(bytes));}
    boolean available(){return !closed&&!dead&&!leaving;}
    private void leave(){if(closed||leaving)return;leaving=true;active=false;weather.active(false);finish.run();}
    boolean evaluate(String script,ValueCallback<String> callback){
        if(!available())return false;
        try{web.evaluateJavascript(script,value->{if(available()&&callback!=null)callback.onReceiveValue(value);});return true;}
        catch(RuntimeException unavailable){leave();return false;}
    }
    void active(boolean value){
        if(!available())return;active=value;weather.active(value);
        try{
            if(value){web.onResume();web.resumeTimers();}
            evaluate("window.apertureNativeFocus&&window.apertureNativeFocus("+value+")",null);
            if(!value&&available())web.onPause();
        }catch(RuntimeException unavailable){leave();}
    }
    private void dispose(){if(disposed)return;disposed=true;WebViewDisposal.release(web,dead);}
    public void close(){if(closed)return;closed=true;active=false;weather.close();dispose();}
}
