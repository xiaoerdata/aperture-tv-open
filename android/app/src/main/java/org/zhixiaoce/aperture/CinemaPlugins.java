package org.zhixiaoce.aperture;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import dalvik.system.DexClassLoader;
import java.io.*;
import java.lang.reflect.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.zip.ZipFile;
import okhttp3.*;
import org.json.*;

/** Opt-in execution of pinned user plugins. Trusted code has this app's UID; this is not a sandbox. */
final class CinemaPlugins {
    static final int MAX_CODE=2*1024*1024, MAX_JAR=16*1024*1024, MAX_MODULES=24;
    private static final CinemaPythonPreparation PYTHON_PREPARATION=new CinemaPythonPreparation();
    static final class Spec {
        final String api,ext,jar,kind,base;final boolean allowHttp;
        private Spec(String api,String ext,String jar,String kind,String base,boolean allowHttp){this.api=api;this.ext=ext;this.jar=jar;this.kind=kind;this.base=base;this.allowHttp=allowHttp;}
        static Spec from(String api,String ext,String jar,String spider,String base,boolean allowHttp)throws Exception {
            api=field(api,4096);ext=CinemaCatalog.extension(ext,262144);jar=field(jar,4096);spider=field(spider,4096);
            String kind,entry;
            if(api.matches("csp_[A-Za-z0-9_$]+")){kind="jar";entry=api;jar=jar.isEmpty()?spider:jar;if(jar.isEmpty())throw new IOException("此插件没有提供 spider/JAR 地址");jar=resource(jar,base,allowHttp);}
            else{entry=url(api,base,allowHttp);String path=new URI(entry).getPath().toLowerCase(Locale.ROOT);if(path.endsWith(".py"))kind="python";else if(path.endsWith(".js")||path.endsWith(".mjs"))kind="javascript";else throw new IOException("插件地址需要 csp_ 类名、.js 或 .py 文件");jar="";}
            if(ext.startsWith("./")||ext.startsWith("../"))ext=url(ext,base,allowHttp);
            return new Spec(entry,ext,jar,kind,base==null?"":base,allowHttp);
        }
        String identity(){return kind+'\n'+api+'\n'+jar+'\n'+ext+'\n'+allowHttp;}
    }
    static final class Candidate {
        final Spec spec;final String digest,origin,kind;final File entry;final String entryDigest;final Map<String,byte[]> modules;final String extension;
        Candidate(Spec spec,String digest,String origin,File entry,String entryDigest,Map<String,byte[]> modules,String extension){this.spec=spec;this.digest=digest;this.origin=origin;this.kind=spec.kind;this.entry=entry;this.entryDigest=entryDigest;this.modules=Collections.unmodifiableMap(modules);this.extension=extension;}
    }
    private final Context context;private final File directory;private final SharedPreferences trust;
    private final Map<String,Object> instances=new ConcurrentHashMap<>();private final Map<String,CinemaJsPlugin> scripts=new ConcurrentHashMap<>();
    private final OkHttpClient client=new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(6,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS).build();
    private volatile int epoch;private volatile Call pending;private volatile ExecutorService nativeWorker;private volatile Future<String> nativeTask;private volatile boolean nativeFailed;
    CinemaPlugins(Context context){this.context=context.getApplicationContext();directory=new File(this.context.getCodeCacheDir(),"cinema-plugins");trust=this.context.getSharedPreferences("cinema-plugin-trust-v1",Context.MODE_PRIVATE);}
    Candidate prepare(Spec spec)throws Exception {
        worker();int version=epoch;Map<String,byte[]> modules=new LinkedHashMap<>();String uri=spec.kind.equals("jar")?stripDigest(spec.jar):spec.api;byte[] entry=fetch(uri,spec.allowHttp,spec.kind.equals("jar")?MAX_JAR:MAX_CODE);
        if(spec.kind.equals("jar")){String md5=expectedDigest(spec.jar);if(!md5.isEmpty()&&!hash(entry,"MD5").equalsIgnoreCase(md5))throw new IOException("JAR 校验值与来源声明不一致");}
        modules.put(uri,entry);
        if(spec.kind.equals("javascript"))collectModules(uri,entry,spec,modules,0);
        String extension=spec.ext;
        // Pin remote JS rules before approval. Runtime req() serves these reviewed bytes.
        if((extension.startsWith("https://")||extension.startsWith("http://"))&&(spec.kind.equals("javascript")||spec.kind.equals("python"))){
            String extUrl=url(extension,spec.base,spec.allowHttp);byte[] ext=fetch(extUrl,spec.allowHttp,MAX_CODE);modules.put(extUrl,ext);
            if(spec.kind.equals("javascript")&&new URI(extUrl).getPath().endsWith(".js"))collectModules(extUrl,ext,spec,modules,0);
        }
        if(version!=epoch)throw new IOException("插件准备已取消");MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(spec.identity().getBytes(StandardCharsets.UTF_8));
        for(Map.Entry<String,byte[]> module:modules.entrySet()){digest.update(("\n"+module.getKey()+"\n").getBytes(StandardCharsets.UTF_8));digest.update(module.getValue());}
        String sha=hex(digest.digest()),entrySha=hash(entry,"SHA-256");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建插件目录");
        File file=new File(directory,entrySha+(spec.kind.equals("jar")?".jar":spec.kind.equals("python")?".py":".js"));
        if(!file.exists())try(FileOutputStream output=new FileOutputStream(file)){if(!file.setReadOnly())throw new IOException("无法保护插件文件");output.write(entry);output.getFD().sync();}
        if(!hash(read(file,spec.kind.equals("jar")?MAX_JAR:MAX_CODE),"SHA-256").equals(entrySha))throw new IOException("缓存插件校验失败");
        if(spec.kind.equals("jar"))try(ZipFile zip=new ZipFile(file)){if(zip.getEntry("classes.dex")==null)throw new IOException("此 JAR 未包含 Android classes.dex，请提供 TVBox 插件包");}
        URI address=new URI(uri);String origin=address.getScheme()+"://"+address.getAuthority();return new Candidate(spec,sha,origin,file,entrySha,modules,extension);
    }
    private void collectModules(String base,byte[] content,Spec spec,Map<String,byte[]> modules,int depth)throws Exception {
        if(depth>8||modules.size()>MAX_MODULES)throw new IOException("脚本模块超过24项或8层");String code=new String(content,StandardCharsets.UTF_8);
        Matcher imports=Pattern.compile("(?m)(?:^|;)\\s*(?:import\\s+(?:(?:[^;\\n]*?)\\s+from\\s*)?|export\\s+[^;\\n]*?\\s+from\\s*)['\"]([^'\"]+)['\"]").matcher(code);
        while(imports.find()){String target=moduleUrl(imports.group(1),base,spec);if(!modules.containsKey(target)){byte[] data=fetch(target,spec.allowHttp,MAX_CODE);modules.put(target,data);collectModules(target,data,spec,modules,depth+1);}}
        long bytes=0;for(byte[] value:modules.values())bytes+=value.length;if(bytes>8*1024*1024)throw new IOException("脚本模块总量超过8MB");
    }
    static String moduleUrl(String raw,String base,Spec spec)throws Exception {
        // TVBox's asset aliases name standard companion modules. Resolve beside the entry and pin them too.
        if(raw.startsWith("assets://js/lib/")){String name=raw.substring("assets://js/lib/".length());if(!name.matches("[A-Za-z0-9._-]+\\.js"))throw new IOException("脚本资源别名无效");return url(name,spec.api,spec.allowHttp);}
        return url(raw,base,spec.allowHttp);
    }
    boolean approved(Candidate candidate){return trust.getBoolean(candidate.digest,false);}
    void approve(Candidate candidate)throws IOException {if(!trust.edit().putBoolean(candidate.digest,true).commit())throw new IOException("未能保存插件授权");}
    void revokeAll(){trust.edit().clear().commit();clear();}
    String call(Candidate candidate,String operation,Object... args)throws Exception {
        worker();int turn=epoch;if(!approved(candidate))throw new IOException("此插件尚未确认信任");if(!Arrays.asList("home","homeVod","category","detail","search","play").contains(operation))throw new IOException("插件操作无效");
        if(!hash(read(candidate.entry,candidate.kind.equals("jar")?MAX_JAR:MAX_CODE),"SHA-256").equals(candidate.entryDigest))throw new IOException("插件内容已改变，请重新导入并确认");
        String output;
        if(candidate.kind.equals("javascript")){CinemaJsPlugin js=scripts.get(candidate.digest);if(js==null){js=new CinemaJsPlugin(context,candidate,this);if(turn!=epoch){js.close();throw new IOException("插件操作已取消");}scripts.put(candidate.digest,js);}output=js.call(operation,args);}
        else {
            Object pythonModule=null;
            if(candidate.kind.equals("python")){
                // Capture only the application context: shared preparation receives no user path or config.
                Context application=context;
                pythonModule=PYTHON_PREPARATION.await(()->preparePython(application),()->active(candidate,turn));
            }
            active(candidate,turn);
            output=nativeCall(candidate,operation,args,turn,pythonModule);
        }
        if(turn!=epoch)throw new IOException("插件操作已取消");if(output==null||output.getBytes(StandardCharsets.UTF_8).length>524288)throw new IOException("插件结果为空或超过512KB");new JSONObject(output);return output;
    }
    private void active(Candidate candidate,int turn)throws IOException {
        if(Thread.currentThread().isInterrupted()||turn!=epoch)throw new IOException("插件操作已取消");
        if(!approved(candidate))throw new IOException("此插件的执行授权已撤销");
    }
    private String nativeCall(Candidate candidate,String operation,Object[] args,int turn,Object pythonModule)throws Exception {
        final Future<String> task;
        synchronized(this){active(candidate,turn);if(nativeFailed)throw new IOException("插件上次执行超时，请退出影视后重试");if(nativeWorker==null||nativeWorker.isShutdown())nativeWorker=Executors.newSingleThreadExecutor(r->{Thread worker=new Thread(r,"cinema-native-plugin");worker.setDaemon(true);return worker;});
            task=nativeWorker.submit(()->{active(candidate,turn);try{return candidate.kind.equals("python")?python(pythonModule,candidate,operation,args):jar(candidate,operation,args);}catch(LinkageError failure){throw new IOException("插件缺少运行依赖（"+failure.getClass().getSimpleName()+"）");}});nativeTask=task;
        }
        try{return task.get(20,TimeUnit.SECONDS);}
        catch(TimeoutException timeout){nativeFailed=true;task.cancel(true);throw new IOException("插件执行超时，请退出影视后重试");}
        catch(InterruptedException cancelled){task.cancel(true);Thread.currentThread().interrupt();throw new IOException("插件操作已取消");}
        catch(CancellationException cancelled){throw new IOException("插件操作已取消");}
        catch(ExecutionException failure){Throwable cause=failure.getCause();if(cause instanceof Exception)throw (Exception)cause;throw new IOException("插件执行失败");}
        finally{if(nativeTask==task)nativeTask=null;}
    }
    private String jar(Candidate c,String operation,Object[] args)throws Exception {
        Object spider=instances.get(c.digest);
        if(spider==null){DexClassLoader loader=new DexClassLoader(c.entry.getAbsolutePath(),directory.getAbsolutePath(),null,context.getClassLoader());
            spider=CinemaPluginAbi.open(loader,context,c.spec.api,c.extension);instances.put(c.digest,spider);
        }
        return CinemaPluginAbi.invoke(spider,operation,args);
    }
    private static Object preparePython(Context context)throws Exception {
        // Reflection keeps the protocol/API harness independent of the packaged CPython runtime.
        Class<?> python=Class.forName("com.chaquo.python.Python");synchronized(CinemaPlugins.class){if(!(Boolean)python.getMethod("isStarted").invoke(null)){Class<?> platform=Class.forName("com.chaquo.python.android.AndroidPlatform");Object p=platform.getConstructor(Context.class).newInstance(context);python.getMethod("start",Class.forName("com.chaquo.python.Python$Platform")).invoke(null,p);}}
        Object py=python.getMethod("getInstance").invoke(null);Method getModule=python.getMethod("getModule",String.class);
        Object module=getModule.invoke(py,"kaishi_plugins");getModule.invoke(py,"base.spider");getModule.invoke(py,"requests");return module;
    }
    private String python(Object module,Candidate c,String operation,Object[] args)throws Exception {
        // Loading the approved .py, Spider.init and the requested operation share the original 20s budget.
        Object result=module.getClass().getMethod("callAttr",String.class,Object[].class).invoke(module,"call",new Object[]{c.entry.getAbsolutePath(),c.entryDigest,c.digest,c.extension,operation,new JSONArray(Arrays.asList(args)).toString()});return result.toString();
    }
    String request(Candidate candidate,String raw,String options)throws Exception {
        String checked=url(raw,candidate.spec.api.startsWith("csp_")?candidate.spec.base:candidate.spec.api,candidate.spec.allowHttp);byte[] pinned=candidate.modules.get(checked);
        if(pinned!=null)return new JSONObject().put("code",200).put("content",new String(pinned,StandardCharsets.UTF_8)).put("headers",new JSONObject()).toString();
        JSONObject opts=options==null||options.isEmpty()?new JSONObject():new JSONObject(options);String method=opts.optString("method","GET").toUpperCase(Locale.ROOT);if(!method.equals("GET")&&!method.equals("POST")&&!method.equals("HEAD"))throw new IOException("请求方法不支持");
        Request.Builder request=new Request.Builder().url(checked).header("User-Agent","Kaishi-Plugin/1");JSONObject supplied=opts.optJSONObject("headers"),safeHeaders=supplied==null?new JSONObject():new JSONObject(supplied.toString());String contentType="application/x-www-form-urlencoded";if(supplied!=null){Iterator<String> names=supplied.keys();while(names.hasNext()){String name=names.next();if(name.equalsIgnoreCase("Content-Type")){contentType=supplied.optString(name);safeHeaders.remove(name);}}}if(contentType.length()>200||contentType.indexOf('\r')>=0||contentType.indexOf('\n')>=0)throw new IOException("内容类型无效");Map<String,String> headers=CinemaFormats.headers(new JSONObject().put("header",safeHeaders));for(Map.Entry<String,String> header:headers.entrySet())request.header(header.getKey(),header.getValue());
        if(method.equals("POST")){String body=opts.optString("data",opts.optString("body",""));if(body.length()>MAX_CODE)throw new IOException("请求正文过大");request.post(RequestBody.create(body,MediaType.get(contentType)));}else request.method(method,null);
        try(Response response=client.newCall(request.build()).execute()){String content=response.body()==null?"":new String(read(response.body().byteStream(),MAX_CODE),StandardCharsets.UTF_8);JSONObject responseHeaders=new JSONObject();for(String name:response.headers().names())responseHeaders.put(name,response.header(name));return new JSONObject().put("code",response.code()).put("content",content).put("headers",responseHeaders).toString();}
    }
    private byte[] fetch(String raw,boolean allowHttp,int limit)throws Exception {String current=url(raw,null,allowHttp);int version=epoch;
        for(int hop=0;hop<4;hop++){if(version!=epoch)throw new IOException("插件下载已取消");Call call=client.newCall(new Request.Builder().url(current).header("User-Agent","Kaishi-Plugin/1").build());pending=call;
            try(Response response=call.execute()){if(Arrays.asList(301,302,303,307,308).contains(response.code())){current=url(response.header("Location"),current,allowHttp);continue;}if(!response.isSuccessful()||response.body()==null)throw new IOException("插件下载失败（"+response.code()+"）");if(response.body().contentLength()>limit)throw new IOException("插件文件过大");return read(response.body().byteStream(),limit);}finally{if(pending==call)pending=null;}}
        throw new IOException("插件重定向超过3次");
    }
    synchronized void cancelPending(){
        epoch++;Call call=pending;if(call!=null)call.cancel();Future<String> task=nativeTask;if(task!=null)task.cancel(true);
        // The next guard stops Python preparation waiting; it never interrupts the shared built-in task.
    }
    void clear(){
        cancelPending();ExecutorService worker; synchronized(this){worker=nativeWorker;nativeWorker=null;}if(worker!=null)worker.shutdownNow();
        for(CinemaJsPlugin js:scripts.values())js.close();scripts.clear();
        List<Object> removed=new ArrayList<>(instances.values());instances.clear();
        // Third-party destroy() may block; never run it on the TV UI thread.
        if(!removed.isEmpty()){Thread cleanup=new Thread(()->{for(Object spider:removed)try{spider.getClass().getMethod("destroy").invoke(spider);}catch(Throwable ignored){}},"cinema-plugin-cleanup");cleanup.setDaemon(true);cleanup.start();}
    }
    static String field(String input,int length)throws IOException {String value=input==null?"":input.trim();if(value.length()>length||value.indexOf('\0')>=0)throw new IOException("插件字段无效");return value;}
    static String url(String input,String base,boolean http)throws IOException {return CinemaCatalog.url(input,base,http);}
    static String resource(String input,String base,boolean http)throws IOException {String[] parts=input.split(";md5;",-1);if(parts.length>2||parts.length==2&&!parts[1].matches("[a-fA-F0-9]{32}"))throw new IOException("插件 MD5 格式无效");return url(parts[0],base,http)+(parts.length==2?";md5;"+parts[1]:"");}
    static String stripDigest(String source){return source.split(";md5;",-1)[0];}
    static String expectedDigest(String source){String[] parts=source.split(";md5;",-1);return parts.length>1?parts[1]:"";}
    static String hash(byte[] bytes,String algorithm)throws Exception{return hex(MessageDigest.getInstance(algorithm).digest(bytes));}
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    static byte[] read(File file,int limit)throws IOException {try(InputStream in=new FileInputStream(file)){return read(in,limit);}}
    static byte[] read(InputStream input,int limit)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=input.read(buffer,0,Math.min(buffer.length,limit+1-out.size())))!=-1){out.write(buffer,0,n);if(out.size()>limit)throw new IOException("插件数据超过限制");}return out.toByteArray();}
    private static void worker(){if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Plugin operations require a worker thread");}
}
