package org.zhixiaoce.aperture;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;
import android.webkit.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/** The platform JS engine owns its UI thread; callers await callbacks only on a worker. */
final class CinemaJsPlugin {
    // The host uses static ES modules (Chromium 61) and async functions (55).
    static final int MIN_WEBVIEW_MAJOR=61;
    private final Context context;private final CinemaPlugins.Candidate candidate;private final CinemaPlugins owner;
    private final Handler main=new Handler(Looper.getMainLooper());private final CountDownLatch ready=new CountDownLatch(1);
    private volatile WebView view;private volatile boolean closed;private volatile String startError="";private volatile Request current;private long sequence;
    private static final class Request {final long id;final CountDownLatch done=new CountDownLatch(1);volatile String result="",error="";Request(long id){this.id=id;}}
    CinemaJsPlugin(Context context,CinemaPlugins.Candidate candidate,CinemaPlugins owner)throws Exception {
        this.context=context;this.candidate=candidate;this.owner=owner;
        String runtime;try(InputStream input=context.getAssets().open("kaishi-plugin-runtime.js")){runtime=new String(CinemaPlugins.read(input,65536),StandardCharsets.UTF_8);}
        final String page=html(candidate.spec.api,candidate.extension,runtime);
        main.post(()->{if(closed)return;try{
            PackageInfo provider=WebView.getCurrentWebViewPackage();requireProvider(provider==null?null:provider.versionName);
            WebView web=new WebView(this.context);view=web;WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setDomStorageEnabled(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setMediaPlaybackRequiresUserGesture(true);
            web.addJavascriptInterface(new Bridge(),"KaishiPlugin");web.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView unused,WebResourceRequest request){return resource(request.getUrl().toString());}
                @Override public boolean shouldOverrideUrlLoading(WebView unused,WebResourceRequest request){return true;}
                @Override public void onReceivedError(WebView unused,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame()){startError="脚本引擎无法初始化";ready.countDown();}}
            });web.loadDataWithBaseURL(candidate.spec.api,"<!doctype html><meta charset=utf-8>"+page,"text/html","UTF-8",null);
        }catch(IOException incompatible){startError=incompatible.getMessage();ready.countDown();}catch(Throwable failure){startError="脚本引擎不可用，请检查电视系统的 WebView；JAR 和 Python 来源可继续使用";ready.countDown();}});
        if(!ready.await(20,TimeUnit.SECONDS)){close();throw new IOException("脚本初始化超时");}if(!startError.isEmpty()){close();throw new IOException(startError);}
    }
    static void requireProvider(String version)throws IOException {
        int major=0;try{if(version!=null)major=Integer.parseInt(version.split("\\.",2)[0]);}catch(NumberFormatException ignored){}
        if(major>=MIN_WEBVIEW_MAJOR)return;
        String current=major>0?"当前 WebView "+version.replaceAll("[^A-Za-z0-9._-]",""):"无法识别当前 WebView 版本";
        throw new IOException(current+"；JS 插件需要 WebView "+MIN_WEBVIEW_MAJOR+" 或以上（ES 模块）。请检查电视系统或官方 WebView 更新；JAR 和 Python 来源可继续使用");
    }
    static String html(String api,String extension,String runtime)throws Exception {
        String init="let spider=module.__jsEvalReturn?module.__jsEvalReturn():(module.default||globalThis.__JS_SPIDER__);if(typeof spider==='function')spider=spider();if(!spider)throw new Error('NoPlugin');globalThis.__kaishiSpider=spider;let extension="+JSONObject.quote(extension)+";try{extension=JSON.parse(extension);}catch(e){}if(typeof spider.init==='function')await spider.init(module.__jsEvalReturn?{stype:3,skey:'kaishi',ext:extension}:extension);KaishiPlugin.ready('');";
        return "<script>var globalThis=window;"+runtime.replace("</script","<\\/script")+"</script><script type=module>import * as module from "+JSONObject.quote(api)+";(async function(){try{"+init.replace("</script","<\\/script")+"}catch(e){KaishiPlugin.ready(String(e.name||'PluginError'));}})();</script>";
    }
    private WebResourceResponse resource(String url){byte[] data=candidate.modules.get(url);Map<String,String> headers=new HashMap<>();headers.put("Access-Control-Allow-Origin","*");headers.put("Cache-Control","no-store");
        if(data==null)return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",headers,new ByteArrayInputStream(new byte[0]));
        String code=new String(data,StandardCharsets.UTF_8);try{code=code.replace("assets://js/lib/",new URI(candidate.spec.api).resolve(".").toString());}catch(Exception ignored){}if(code.contains("__JS_SPIDER__")&&!code.contains("export default"))code=code.replaceAll("(?<![A-Za-z0-9_.$])__JS_SPIDER__\\s*=","globalThis.__JS_SPIDER__ =");
        return new WebResourceResponse("text/javascript","UTF-8",200,"OK",headers,new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8)));
    }
    synchronized String call(String operation,Object[] supplied)throws Exception {
        if(closed)throw new IOException("脚本已关闭");List<Object> values=new ArrayList<>(Arrays.asList(supplied));if(operation.equals("detail"))values=Collections.singletonList(((List<?>)supplied[0]).get(0));
        Request request=new Request(++sequence);current=request;String script="globalThis.__kaishiCall("+request.id+","+JSONObject.quote(operation)+","+new JSONArray(values).toString()+");";
        main.post(()->{if(!closed&&view!=null)view.evaluateJavascript(script,null);});
        if(!request.done.await(20,TimeUnit.SECONDS)){close();throw new IOException("脚本执行超时");}current=null;if(!request.error.isEmpty())throw new IOException("脚本执行失败（"+request.error+"）");return request.result;
    }
    void close(){closed=true;ready.countDown();Request request=current;if(request!=null){request.error="Cancelled";request.done.countDown();}main.post(()->{WebView web=view;view=null;if(web!=null){web.stopLoading();web.removeJavascriptInterface("KaishiPlugin");web.destroy();}});}
    final class Bridge {
        @JavascriptInterface public void ready(String error){startError=error.isEmpty()?"":"脚本初始化失败（"+error.replaceAll("[^A-Za-z]","")+"）";ready.countDown();}
        @JavascriptInterface public void done(long id,String value,String error){Request r=current;if(closed||r==null||r.id!=id)return;if(value==null||value.length()>524288){r.error="TooLarge";}else{r.result=value;r.error=error==null?"PluginError":error.replaceAll("[^A-Za-z]","");}r.done.countDown();}
        @JavascriptInterface public String request(String url,String options){if(closed)return "{\"error\":\"Cancelled\"}";try{return owner.request(candidate,url,options);}catch(Exception error){return "{\"error\":\"RequestFailed\"}";}}
        @JavascriptInterface public String hash(String value,String algorithm){try{if(!algorithm.equals("MD5")&&!algorithm.equals("SHA-256"))return "";return CinemaPlugins.hash(value.getBytes(StandardCharsets.UTF_8),algorithm);}catch(Exception error){return "";}}
        @JavascriptInterface public String html(String content,String rule,boolean array,String base){try{
            if(content.length()>CinemaPlugins.MAX_CODE||rule.length()>4096)return array?"[]":"";String[] parts=rule.split("&&");Elements selected=Jsoup.parse(content,base).getAllElements();Element root=selected.first();
            int steps=array?parts.length:parts.length-1;
            for(int i=0;i<steps;i++){String selector=parts[i].trim();if(selector.equals("body"))selected=new Elements(root);else{Elements next=new Elements();for(Element node:selected)next.addAll(node.select(selector));selected=next;}if(i<steps-1&&selected.size()>0&&!array)selected=new Elements(selected.first());}
            if(array){JSONArray values=new JSONArray();for(Element element:selected){if(values.length()>=500)break;values.put(element.outerHtml());}return values.toString();}
            Element first=selected.first();if(first==null)return "";String property=parts[parts.length-1].trim();String output=property.equalsIgnoreCase("Text")?first.text():property.equalsIgnoreCase("Html")?first.html():first.attr(property);
            if(!base.isEmpty()&&!output.isEmpty()&&!property.equalsIgnoreCase("Text")&&!property.equalsIgnoreCase("Html"))output=new URI(base).resolve(output).toString();return output;
        }catch(Exception error){return array?"[]":"";}}
    }
}
