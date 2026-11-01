package org.zhixiaoce.aperture;
import android.content.Context;import android.webkit.*;import android.net.Uri;import java.util.*;
final class ControlledWebView extends WebView {
    WebViewClient client;String url="",answer="true";boolean hold,holdBack,throwReadiness,fullscreen,destroyed,detachedAtDestroy;int stops,destroys,evalAfterDestroy;
    final List<ValueCallback<String>> pending=new ArrayList<>();
    final List<String> evaluated=new ArrayList<>();
    ControlledWebView(Context c){super(c);}
    @Override public void setWebViewClient(WebViewClient c){client=c;}
    @Override public void loadUrl(String value){url=value;}
    @Override public String getUrl(){return url;}
    @Override public void evaluateJavascript(String script,ValueCallback<String> callback){
        if(destroyed){evalAfterDestroy++;throw new AssertionError("JS called after destroy");}
        evaluated.add(script);
        if(callback==null)return;
        if(script.startsWith("typeof window.apertureRemoteKey")||script.startsWith("!!(window.KaishiGallerySettings")){if(throwReadiness)throw new IllegalStateException("Synthetic JS evaluation failure");if(hold)pending.add(callback);else callback.onReceiveValue(answer);}
        else if(script.contains("window.KaishiGallery.close())")){if(holdBack)pending.add(callback);else{callback.onReceiveValue(fullscreen?"true":"false");fullscreen=false;}}
        else callback.onReceiveValue("{}");
    }
    @Override public void stopLoading(){stops++;}
    @Override public void destroy(){detachedAtDestroy=getParent()==null;destroys++;if(!destroyed){destroyed=true;super.destroy();}}
    @Override public void onPause(){}@Override public void onResume(){}@Override public void pauseTimers(){}@Override public void resumeTimers(){}
    void finished(){client.onPageFinished(this,url);}
    void lateReady(){for(ValueCallback<String> c:new ArrayList<>(pending))c.onReceiveValue("true");pending.clear();}
    void rendererGone(){client.onRenderProcessGone(this,new RenderProcessGoneDetail(){public boolean didCrash(){return true;}public int rendererPriorityAtExit(){return 1;}});}
    void error(boolean main,boolean http){
        WebResourceRequest request=new WebResourceRequest(){public Uri getUrl(){return Uri.parse("https://appassets.androidplatform.net/assets/gallery.html?sharecode=SYNTHETIC_PRIVATE");}public boolean isForMainFrame(){return main;}public boolean isRedirect(){return false;}public boolean hasGesture(){return false;}public String getMethod(){return "GET";}public Map<String,String> getRequestHeaders(){return Collections.emptyMap();}};
        if(http)client.onReceivedHttpError(this,request,new WebResourceResponse("text/plain","UTF-8",503,"Unavailable",Collections.emptyMap(),new java.io.ByteArrayInputStream(new byte[0])));
        else client.onReceivedError(this,request,null);
    }
}
