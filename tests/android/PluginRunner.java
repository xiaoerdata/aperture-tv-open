package org.zhixiaoce.aperture.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import org.json.*;

/** Device execution of original, provider-free plugin bundles. Never packaged in product. */
public final class PluginRunner extends Instrumentation {
    private Bundle options;private String server,selectedKind="all",stage="setup";private File output;private Object plugins;
    private final JSONArray checks=new JSONArray();private final JSONObject runtimes=new JSONObject(),diagnostic=new JSONObject();
    interface Work { void run()throws Exception; }
    @Override public void onCreate(Bundle args){options=args;start();}
    @Override public void onStart(){
        Bundle result=new Bundle();JSONObject report=new JSONObject();boolean passed=false;
        try{server=options.getString("server","http://10.0.2.2:4399");selectedKind=options.getString("kind","all");output=new File(getTargetContext().getFilesDir(),"s50-plugin-tests");output.mkdirs();if(!Arrays.asList("all","jar","javascript","python").contains(selectedKind))throw new IllegalArgumentException("kind must be all, jar, javascript, or python");if("true".equals(options.getString("pythonDiagnostic","false"))){if(!selectedKind.equals("python"))throw new IllegalArgumentException("pythonDiagnostic requires kind=python");pythonDiagnostic();}for(String kind:new String[]{"jar","javascript","python"})if(selectedKind.equals("all")||selectedKind.equals(kind))runtime(kind);stage="completed";passed=true;}
        catch(Throwable error){try{report.put("error",android.util.Log.getStackTraceString(error));JSONArray threads=new JSONArray();for(Map.Entry<Thread,StackTraceElement[]> thread:Thread.getAllStackTraces().entrySet())if(thread.getKey().getName().contains("cinema-native-plugin")||thread.getKey().getName().contains("cinema-python-prepare")){JSONArray stack=new JSONArray();for(StackTraceElement frame:thread.getValue())stack.put(frame.toString());threads.put(new JSONObject().put("name",thread.getKey().getName()).put("state",thread.getKey().getState().toString()).put("stack",stack));}report.put("runtimeThreadsAtFailure",threads);}catch(Exception ignored){}}
        finally{if(plugins!=null)try{call(plugins,"clear",new Class<?>[0]);}catch(Exception ignored){}}
        try{report.put("passed",passed).put("stage",stage).put("selectedKind",selectedKind).put("checks",checks).put("runtimes",runtimes).put("targetPackage",getTargetContext().getPackageName()).put("syntheticFixtures",true).put("realXiaomi",false).put("mediaPlayed",false);}catch(Exception error){result.putString("coreReportError",error.toString());}
        // Return core results even if optional environment or diagnostic collection fails.
        result.putString("report",report.toString());
        try{report.put("environment",environment());}catch(Exception error){try{report.put("environmentError",error.toString()).put("metadataIncomplete",true);}catch(Exception ignored){}}
        try{if(diagnostic.length()>0){File traces=new File(output,"python-stack.txt");if(traces.isFile()){ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream stream=new FileInputStream(traces)){byte[] block=new byte[8192];int count;while((count=stream.read(block))!=-1&&bytes.size()<262144)bytes.write(block,0,Math.min(count,262144-bytes.size()));}diagnostic.put("pythonStack",new String(bytes.toByteArray(),"UTF-8"));}report.put("diagnostic",diagnostic);}}catch(Exception error){try{report.put("diagnosticCollectionError",error.toString());}catch(Exception ignored){}}
        result.putString("report",report.toString());
        try{try(FileOutputStream stream=new FileOutputStream(new File(output,"report.json"))){stream.write(report.toString(2).getBytes("UTF-8"));}}catch(Exception error){result.putString("writeError",error.toString());}
        finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private Class<?> type(String name)throws Exception{return getTargetContext().getClassLoader().loadClass("org.zhixiaoce.aperture."+name);}
    private void pythonDiagnostic()throws Exception{
        stage="diagnostic: packaged Python bootstrap";Bundle progress=new Bundle();progress.putString("stage",stage);sendStatus(0,progress);diagnostic.put("prestartedPython",true).put("productionColdStartAcceptance",false).put("productionCallTimeoutChanged",false);
        ClassLoader loader=getTargetContext().getClassLoader();Class<?> python=loader.loadClass("com.chaquo.python.Python");boolean already=(Boolean)python.getMethod("isStarted").invoke(null);long started=android.os.SystemClock.uptimeMillis();
        if(!already){Class<?> platform=loader.loadClass("com.chaquo.python.android.AndroidPlatform");Object p=platform.getConstructor(Context.class).newInstance(getTargetContext());python.getMethod("start",loader.loadClass("com.chaquo.python.Python$Platform")).invoke(null,p);}
        diagnostic.put("alreadyStarted",already).put("packagedBootstrapMs",android.os.SystemClock.uptimeMillis()-started);
        Object py=python.getMethod("getInstance").invoke(null),builtins=python.getMethod("getModule",String.class).invoke(py,"builtins");String code="import faulthandler\n_kaishi_diag_file = open("+JSONObject.quote(new File(output,"python-stack.txt").getAbsolutePath())+", 'w')\nfaulthandler.dump_traceback_later(8, repeat=True, file=_kaishi_diag_file)\n";
        builtins.getClass().getMethod("callAttr",String.class,Object[].class).invoke(builtins,"exec",new Object[]{code});
    }
    private JSONObject environment()throws Exception{
        android.content.pm.PackageManager manager=getTargetContext().getPackageManager();android.content.pm.PackageInfo product=manager.getPackageInfo(getTargetContext().getPackageName(),0);
        JSONObject value=new JSONObject().put("api",android.os.Build.VERSION.SDK_INT).put("abi",new JSONArray(Arrays.asList(android.os.Build.SUPPORTED_ABIS))).put("versionCode",product.versionCode).put("versionName",product.versionName);
        android.content.pm.PackageInfo provider=android.webkit.WebView.getCurrentWebViewPackage();
        if(provider!=null){
            JSONObject web=new JSONObject().put("packageName",provider.packageName).put("versionName",provider.versionName).put("versionCode",provider.versionCode);value.put("webView",web);
            String source="getCurrentWebViewPackage";
            try{
                android.content.pm.Signature[] signatures=provider.signatures;
                if(signatures==null||signatures.length==0){source="PackageManager.GET_SIGNATURES";signatures=manager.getPackageInfo(provider.packageName,android.content.pm.PackageManager.GET_SIGNATURES).signatures;}
                if(signatures==null||signatures.length==0)throw new IOException("provider signatures unavailable");
                JSONArray signers=new JSONArray();for(android.content.pm.Signature signature:signatures){StringBuilder hex=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()))hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));signers.put(hex.toString());}
                web.put("signerSha256",signers).put("signerCollectionStatus","collected").put("signerCollectionSource",source);
            }catch(Exception error){web.put("signerCollectionStatus","unavailable").put("signerCollectionSource",source).put("signerCollectionError",error.toString());}
        }else value.put("metadataIncomplete",true).put("environmentError","getCurrentWebViewPackage returned null");return value;
    }
    private Object call(Object owner,String name,Class<?>[] types,Object... args)throws Exception{Method method=owner.getClass().getDeclaredMethod(name,types);method.setAccessible(true);try{return method.invoke(owner,args);}catch(InvocationTargetException wrapped){Throwable cause=wrapped.getCause();if(cause instanceof Exception)throw (Exception)cause;throw wrapped;}}
    private Object field(Object owner,String name)throws Exception{Field value=owner.getClass().getDeclaredField(name);value.setAccessible(true);return value.get(owner);}
    private void check(boolean value,String message){if(!value)throw new AssertionError(stage+": "+message);checks.put(message);Bundle progress=new Bundle();progress.putString("check",message);progress.putString("stage",stage);sendStatus(0,progress);}
    private void denied(Work operation,String message)throws Exception{boolean failed=false;try{operation.run();}catch(IOException expected){failed=true;}check(failed,message);}
    private void revision(int value)throws Exception{HttpURLConnection request=(HttpURLConnection)new URL(server+"/control/revision?value="+value).openConnection();request.setConnectTimeout(3000);request.setReadTimeout(3000);try{if(request.getResponseCode()!=200)throw new IOException("fixture control failed");}finally{request.disconnect();}}
    private Object spec(String kind)throws Exception{Class<?> type=type("CinemaPlugins$Spec");Method from=type.getDeclaredMethod("from",String.class,String.class,String.class,String.class,String.class,boolean.class);from.setAccessible(true);String api=kind.equals("jar")?"csp_Fixture":server+(kind.equals("python")?"/catalogue.py":"/catalogue.js");return from.invoke(null,api,"S50 ",kind.equals("jar")?server+"/fixture.jar":"","",server+"/profile.json",true);}
    private Object prepare(Object spec)throws Exception{return call(plugins,"prepare",new Class<?>[]{type("CinemaPlugins$Spec")},spec);}
    private boolean approved(Object candidate)throws Exception{return (Boolean)call(plugins,"approved",new Class<?>[]{type("CinemaPlugins$Candidate")},candidate);}
    private void approve(Object candidate)throws Exception{call(plugins,"approve",new Class<?>[]{type("CinemaPlugins$Candidate")},candidate);}
    private JSONObject execute(Object candidate,String operation,Object... args)throws Exception{return new JSONObject((String)call(plugins,"call",new Class<?>[]{type("CinemaPlugins$Candidate"),String.class,Object[].class},candidate,operation,args));}
    private void runtime(String kind)throws Exception{
        stage=kind+": prepare and trust";revision(0);Constructor<?> constructor=type("CinemaPlugins").getDeclaredConstructor(Context.class);constructor.setAccessible(true);plugins=constructor.newInstance(getTargetContext());call(plugins,"revokeAll",new Class<?>[0]);Object spec=spec(kind),candidate=prepare(spec);
        check(field(candidate,"kind").equals(kind),kind+" production detects correct runtime");check(!approved(candidate),kind+" starts unapproved");denied(()->execute(candidate,"home",false),kind+" refuses execution before approval");approve(candidate);check(approved(candidate),kind+" stores explicit approval");
        if(kind.equals("javascript")&&"true".equals(options.getString("expectUnsupported","false"))){
            stage="javascript: unsupported WebView";String error="";long started=android.os.SystemClock.uptimeMillis();try{execute(candidate,"home",false);}catch(IOException expected){error=expected.getMessage();}
            check(error!=null&&error.contains("WebView")&&error.contains("61"),"old WebView returns explicit JS engine requirement");
            runtimes.put(kind,new JSONObject().put("executed",false).put("expectedUnavailable",true).put("error",error).put("elapsedMs",android.os.SystemClock.uptimeMillis()-started));
            call(plugins,"revokeAll",new Class<?>[0]);call(plugins,"clear",new Class<?>[0]);plugins=null;revision(0);return;
        }
        stage=kind+": execute runtime";JSONObject outputs=new JSONObject(),runtime=new JSONObject().put("executed",false).put("digest",field(candidate,"digest"));runtimes.put(kind,runtime);
        long firstHomeStarted=android.os.SystemClock.uptimeMillis();JSONObject home;
        try{home=execute(candidate,"home",false);}finally{runtime.put("firstHomeElapsedMs",android.os.SystemClock.uptimeMillis()-firstHomeStarted);}
        check(home.getJSONArray("class").getJSONObject(0).getString("type_id").equals("films")&&home.getJSONArray("list").getJSONObject(0).getString("vod_name").startsWith("S50 "),kind+" native runtime home/init executes");outputs.put("home",home);
        JSONObject category=execute(candidate,"category","films","2",false,new HashMap<String,String>());check(category.getInt("page")==2&&category.getJSONArray("list").getJSONObject(0).getString("vod_id").equals("films-one"),kind+" category receives page and category");outputs.put("category",category);
        JSONObject detail=execute(candidate,"detail",Collections.singletonList("one"));check(detail.getJSONArray("list").getJSONObject(0).getString("vod_play_url").contains("opaque-episode-2"),kind+" detail returns episode IDs");outputs.put("detail",detail);
        JSONObject search=execute(candidate,"search","周末",false,"2");check(search.getInt("page")==2&&search.getJSONArray("list").getJSONObject(0).getString("vod_name").equals("周末"),kind+" search passes Unicode and page");outputs.put("search",search);
        JSONObject play=execute(candidate,"play","测试线路","opaque-episode-1",Collections.emptyList());check(play.getInt("parse")==0&&play.getString("url").equals("https://media.example.test/opaque-episode-1.mp4")&&play.getJSONObject("header").has("Referer"),kind+" player resolves opaque ID and request headers");outputs.put("play",play);
        runtime.put("executed",true).put("operations",outputs);
        stage=kind+": changed bundle approval";revision(1);Object changed=prepare(spec);check(!field(candidate,"digest").equals(field(changed,"digest")),kind+" changed bundle/module bytes change approval digest");runtimes.getJSONObject(kind).put("changedDigest",field(changed,"digest"));check(!approved(changed),kind+" changed bytes are not implicitly approved");denied(()->execute(changed,"home",false),kind+" changed bundle refuses execution before renewed approval");approve(changed);check(execute(changed,"home",false).has("list"),kind+" approved changed bundle executes");
        stage=kind+": revoke";call(plugins,"revokeAll",new Class<?>[0]);check(!approved(candidate)&&!approved(changed),kind+" revocation removes both approvals");denied(()->execute(changed,"home",false),kind+" revocation blocks next call");call(plugins,"clear",new Class<?>[0]);plugins=null;revision(0);
    }
}
