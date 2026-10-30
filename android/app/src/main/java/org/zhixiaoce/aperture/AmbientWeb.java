package org.zhixiaoce.aperture;

import android.content.Context;
import android.webkit.*;
import java.io.ByteArrayInputStream;
import java.util.Collections;

/** Isolated shared screensaver page; only packaged assets and the fixed weather transport. */
final class AmbientWeb implements AutoCloseable {
    final WebView web;private final WeatherProxy weather=new WeatherProxy();private boolean active,closed,dead;
    AmbientWeb(Context context,String panel,Runnable finish){
        web=new WebView(context);web.setBackgroundColor(0xff020409);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setAllowFileAccessFromFileURLs(false);settings.setAllowUniversalAccessFromFileURLs(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setTextZoom(100);settings.setSupportZoom(false);settings.setMediaPlaybackRequiresUserGesture(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){if(request.isForMainFrame()&&"aperture://ambient/close".equals(request.getUrl().toString()))finish.run();return true;}
            public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                WeatherProxy.Reply reply=weather.read(request.getUrl().toString(),request.getMethod(),request.isForMainFrame());if(reply!=null)return response("application/json",reply.status,reply.body);
                String file=LobbyAssets.file(request.getUrl().getScheme(),request.getUrl().getHost(),request.getUrl().getPort(),request.getUrl().getPath());
                if("GET".equals(request.getMethod())&&file!=null&&(request.getUrl().getQuery()==null||("ambient.html".equals(file)&&request.getUrl().getQuery().matches("panel=(weather|settings)|dream=1")))&&request.getUrl().getFragment()==null&&request.getUrl().getUserInfo()==null)try{return new WebResourceResponse(LobbyAssets.mime(file),"UTF-8",200,"OK",Collections.singletonMap("Cache-Control","no-store"),context.getAssets().open(file));}catch(Exception ignored){}
                return response("text/plain",403,new byte[0]);
            }
            public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){dead=true;finish.run();return true;}
            public void onPageFinished(WebView view,String url){if(!closed)active(active);}
        });
        String query="weather".equals(panel)?"?panel=weather":"settings".equals(panel)?"?panel=settings":"dream".equals(panel)?"?dream=1":"";
        web.loadUrl("https://"+LobbyAssets.HOST+"/assets/ambient.html"+query);
    }
    private WebResourceResponse response(String mime,int status,byte[] bytes){return new WebResourceResponse(mime,"UTF-8",status,status==200?"OK":"Unavailable",Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(bytes));}
    void active(boolean value){if(closed)return;active=value;weather.active(value);if(value){web.onResume();web.resumeTimers();}web.evaluateJavascript("window.apertureNativeFocus&&window.apertureNativeFocus("+value+")",null);if(!value){web.onPause();web.pauseTimers();}}
    public void close(){if(closed)return;closed=true;weather.close();if(!dead)web.stopLoading();if(web.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)web.getParent()).removeView(web);web.destroy();}
}
