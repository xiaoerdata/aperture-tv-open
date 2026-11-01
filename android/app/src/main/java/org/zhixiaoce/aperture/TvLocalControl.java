package org.zhixiaoce.aperture;

import android.app.*;
import android.os.*;
import android.content.Context;
import android.graphics.*;
import android.net.nsd.*;
import android.security.keystore.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import java.security.*;
import java.security.cert.X509Certificate;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in HTTPS control of this app's foreground only. Domain writes reuse the native resource importer. */
final class TvLocalControl implements Application.ActivityLifecycleCallbacks {
    private static TvLocalControl instance;
    static synchronized void install(Application app){if(instance==null){instance=new TvLocalControl(app);app.registerActivityLifecycleCallbacks(instance);}}
    static TvLocalControl get(){if(instance==null)throw new IllegalStateException("本机控制未初始化");return instance;}
    static void open(Activity activity){activity.startActivity(new android.content.Intent(activity,LocalControlActivity.class));}
    private final Application app;private final Handler main=new Handler(Looper.getMainLooper());
    final LocalControlSession session=new LocalControlSession(SystemClock::elapsedRealtime);
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,20,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4));
    private final ExecutorService probeWorker=Executors.newSingleThreadExecutor();private final AtomicBoolean probing=new AtomicBoolean();
    private volatile WeakReference<Activity> foreground=new WeakReference<>(null);private volatile WeakReference<LocalControlActivity> owner=new WeakReference<>(null);
    private final Runnable expiry=this::expire;
    private volatile SSLServerSocket listener;private volatile long until;private NsdManager.RegistrationListener registration;private String fingerprint="",deviceId="";private int port;
    private TvLocalControl(Application app){this.app=app;}
    synchronized JSONObject start(LocalControlActivity activity)throws Exception{
        close();SSLContext tls=tls();SSLServerSocket server=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket(0,4);server.setEnabledProtocols(new String[]{"TLSv1.2"});listener=server;port=server.getLocalPort();owner=new WeakReference<>(activity);String pin=session.begin();until=SystemClock.elapsedRealtime()+120000;
        Thread accept=new Thread(()->{while(listener==server)try{SSLSocket socket=(SSLSocket)server.accept();socket.setSoTimeout(12000);if(!privatePeer(socket.getInetAddress())){socket.close();continue;}try{workers.execute(()->serve(socket));}catch(RejectedExecutionException busy){socket.close();}}catch(IOException stopped){break;}},"Kaishi-local-control");accept.setDaemon(true);accept.start();
        advertise();main.postDelayed(expiry,120000);return new JSONObject().put("pin",pin).put("port",port).put("fingerprint",fingerprint).put("deviceId",deviceId);
    }
    synchronized void close(){main.removeCallbacks(expiry);session.close();until=0;SSLServerSocket old=listener;listener=null;if(old!=null)try{old.close();}catch(IOException ignored){}if(registration!=null){try{((NsdManager)app.getSystemService(Context.NSD_SERVICE)).unregisterService(registration);}catch(RuntimeException ignored){}registration=null;}owner.clear();}
    private void expire(){long left=until-SystemClock.elapsedRealtime();if(left<=0)close();else main.postDelayed(expiry,Math.min(left,60000));}
    private SSLContext tls()throws Exception{
        String alias="kaishi-local-control-tls-v2";KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(alias)){
            KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA","AndroidKeyStore");generator.initialize(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY|KeyProperties.PURPOSE_DECRYPT).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1).setKeySize(2048).setDigests(KeyProperties.DIGEST_NONE,KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA384,KeyProperties.DIGEST_SHA512).setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,KeyProperties.SIGNATURE_PADDING_RSA_PSS).setCertificateSubject(new X500Principal("CN=Kaishi Local Control")).setCertificateSerialNumber(java.math.BigInteger.ONE).setCertificateNotBefore(new Date(System.currentTimeMillis()-86400000)).setCertificateNotAfter(new Date(System.currentTimeMillis()+10L*365*86400000)).build());generator.generateKeyPair();
        }
        X509Certificate cert=(X509Certificate)store.getCertificate(alias);StringBuilder digest=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()))digest.append(String.format(Locale.ROOT,"%02x",b&255));fingerprint=digest.toString();deviceId=fingerprint.substring(0,12);
        X509KeyManager manager=new X509KeyManager(){public String[] getClientAliases(String k,java.security.Principal[] p){return null;}public String chooseClientAlias(String[] k,java.security.Principal[] p,Socket s){return null;}public String[] getServerAliases(String k,java.security.Principal[] p){return "RSA".equals(k)?new String[]{alias}:null;}public String chooseServerAlias(String k,java.security.Principal[] p,Socket s){return "RSA".equals(k)?alias:null;}public X509Certificate[] getCertificateChain(String a){return alias.equals(a)?new X509Certificate[]{cert}:null;}public PrivateKey getPrivateKey(String a){try{return alias.equals(a)?(PrivateKey)store.getKey(alias,null):null;}catch(Exception e){return null;}}};
        SSLContext context=SSLContext.getInstance("TLS");context.init(new KeyManager[]{manager},null,new SecureRandom());return context;
    }
    private void advertise(){try{
        NsdServiceInfo info=new NsdServiceInfo();info.setServiceName("Kaishi-"+deviceId+(BuildConfig.PARENTS_EDITION?"-parents":""));info.setServiceType("_kaishi-tv._tcp.");info.setPort(port);info.setAttribute("api","1");info.setAttribute("id",deviceId);info.setAttribute("tls-sha256",fingerprint);info.setAttribute("edition",BuildConfig.PARENTS_EDITION?"parents":"standard");
        registration=new NsdManager.RegistrationListener(){public void onServiceRegistered(NsdServiceInfo s){}public void onRegistrationFailed(NsdServiceInfo s,int code){}public void onServiceUnregistered(NsdServiceInfo s){}public void onUnregistrationFailed(NsdServiceInfo s,int code){}};((NsdManager)app.getSystemService(Context.NSD_SERVICE)).registerService(info,NsdManager.PROTOCOL_DNS_SD,registration);
    }catch(RuntimeException unavailable){registration=null;}}
    static boolean privatePeer(InetAddress address){byte[] b=address.getAddress();return address.isLoopbackAddress()||address.isSiteLocalAddress()||b.length==16&&((b[0]&0xfe)==0xfc);}
    private static String line(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<4096;n++){int c=in.read();if(c<0)throw new EOFException();if(c==10)return out.toString("US-ASCII").replaceFirst("\\r$","");out.write(c);}throw new IOException("请求行过长");}
    private static final class Reply {final String type;final byte[] bytes;Reply(JSONObject json){type="application/json; charset=utf-8";bytes=json.toString().getBytes(StandardCharsets.UTF_8);}Reply(byte[] png){type="image/png";bytes=png;}}
    private void serve(SSLSocket socket){try(SSLSocket client=socket){
        try{client.startHandshake();}catch(javax.net.ssl.SSLException failure){android.util.Log.w("KaishiLocalTLS","Handshake failed",failure);return;}
        InputStream in=client.getInputStream();String[] request=line(in).split(" ");if(request.length!=3||!request[2].equals("HTTP/1.1"))throw new IOException("请求无效");Map<String,String> headers=new HashMap<>();for(int n=0;n<32;n++){String l=line(in);if(l.isEmpty())break;int at=l.indexOf(':');if(at<=0)throw new IOException();String key=l.substring(0,at).toLowerCase(Locale.ROOT);if(headers.put(key,l.substring(at+1).trim())!=null)throw new IOException();if(n==31)throw new IOException();}
        String host=client.getLocalAddress().getHostAddress()+":"+port;
        if(!host.equals(headers.get("host"))||headers.containsKey("origin")||headers.containsKey("forwarded")||headers.containsKey("x-forwarded-for")||headers.containsKey("transfer-encoding"))throw new SecurityException("仅允许直接局域网客户端");
        String method=request[0],path=request[1];if(!Arrays.asList("GET","POST").contains(method)||!path.matches("/[a-z/-]+"))throw new IOException();int length=Integer.parseInt(headers.getOrDefault("content-length","0"));if(length<0||length>(path.equals("/resources")?CinemaCatalog.MAX_BYTES+65536:524288))throw new IOException("请求超过允许大小");
        if(path.equals("/resources"))require(headers.getOrDefault("authorization","").replaceFirst("^Bearer ",""));
        if(method.equals("POST")&&!headers.getOrDefault("content-type","").toLowerCase(Locale.ROOT).startsWith("application/json"))throw new IOException("需要JSON");byte[] bytes=new byte[length];new DataInputStream(in).readFully(bytes);JSONObject body=length==0?new JSONObject():new JSONObject(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString());Reply reply;int status=200;
        try{reply=route(method,path,headers.getOrDefault("authorization","").replaceFirst("^Bearer ",""),body,client.getInetAddress().getHostAddress());}catch(SecurityException denied){status=403;reply=new Reply(new JSONObject().put("error",denied.getMessage()));}catch(Exception invalid){status=409;reply=new Reply(new JSONObject().put("failureType",invalid.getCause()==null?invalid.getClass().getSimpleName():invalid.getCause().getClass().getSimpleName()).put("error",invalid instanceof IllegalArgumentException?invalid.getMessage():"当前页面不能执行，请返回开视首页或片单后重试"));}
        OutputStream out=client.getOutputStream();out.write(("HTTP/1.1 "+status+(status==200?" OK":" Request rejected")+"\r\nContent-Type: "+reply.type+"\r\nContent-Length: "+reply.bytes.length+"\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(reply.bytes);out.flush();
    }catch(Exception rejected){/* No request payload, credentials or private UI data in logs. */}}
    private Reply route(String method,String path,String token,JSONObject body,String peer)throws Exception{
        if(method.equals("POST")&&path.equals("/pair/request")){
            String ticket=session.request(body.optString("pin"));LocalControlActivity activity=owner.get();if(activity==null)throw new SecurityException("请在电视重新开启配对");main.post(()->activity.confirm(peer,ticket,()->{session.approve(ticket,true);until=SystemClock.elapsedRealtime()+1800000;},()->session.approve(ticket,false)));
            return new Reply(new JSONObject().put("ticket",ticket).put("verification",ticket.substring(ticket.length()-6)).put("state","awaiting_tv"));
        }
        if(method.equals("POST")&&path.equals("/pair/result")){String approved=session.claim(body.optString("ticket"));return new Reply(new JSONObject().put("state",approved.isEmpty()?"awaiting_tv":"paired").put("token",approved).put("expiresInMs",session.remaining()));}
        require(token);
        if(method.equals("POST")&&path.equals("/close")){main.post(this::close);return new Reply(new JSONObject().put("closed",true));}
        if(method.equals("GET")&&path.equals("/status"))return new Reply(ui(()->{Activity a=active();return new JSONObject().put("deviceId",deviceId).put("edition",BuildConfig.PARENTS_EDITION?"parents":"standard").put("screen",a.getClass().getSimpleName()).put("width",a.getWindow().getDecorView().getWidth()).put("height",a.getWindow().getDecorView().getHeight()).put("screenshotMaxWidth",1280).put("expiresInMs",session.remaining()).put("playing",a instanceof CinemaActivity&&((CinemaActivity)a).localPlaying());}));
        if(method.equals("GET")&&path.equals("/ui")){
            CompletableFuture<JSONArray> webNodes=new CompletableFuture<>();
            JSONArray nativeNodes=ui(()->{Activity current=active();View root=current.getWindow().getDecorView();android.webkit.WebView web=LocalControlSemantics.ownWeb(root);if(web==null)webNodes.complete(new JSONArray());else{int[] origin=new int[2];web.getLocationOnScreen(origin);web.evaluateJavascript(LocalControlSemantics.WEB_SCRIPT,value->{try{if(foreground.get()!=current||!session.authorized(token)){webNodes.complete(new JSONArray());return;}JSONArray data=new JSONArray(value);for(int i=0;i<data.length();i++){JSONObject node=data.getJSONObject(i);double scale=web.getWidth()/Math.max(1,node.optDouble("viewportWidth",web.getWidth()));for(String key:new String[]{"x","y","width","height"})node.put(key,node.getDouble(key)*scale+(key.equals("x")?origin[0]:key.equals("y")?origin[1]:0));node.remove("viewportWidth");}webNodes.complete(data);}catch(Exception unavailable){webNodes.complete(new JSONArray());}});}return LocalControlSemantics.nativeNodes(root);});
            JSONArray web;try{web=webNodes.get(3,TimeUnit.SECONDS);}catch(Exception unavailable){web=new JSONArray();}return new Reply(new JSONObject().put("nodes",nativeNodes).put("webNodes",web).put("scope","own foreground; packaged-home DOM; input values/passwords omitted"));
        }
        if(method.equals("GET")&&path.equals("/screenshot")){
            java.util.concurrent.atomic.AtomicReference<Bitmap> held=new java.util.concurrent.atomic.AtomicReference<>();
            try{Bitmap bitmap=ui(()->{View view=active().getWindow().getDecorView();float scale=Math.min(1,1280f/Math.max(1,view.getWidth()));Bitmap frame=Bitmap.createBitmap(Math.max(1,(int)(view.getWidth()*scale)),Math.max(1,(int)(view.getHeight()*scale)),Bitmap.Config.RGB_565);held.set(frame);Canvas canvas=new Canvas(frame);canvas.scale(scale,scale);view.draw(canvas);return frame;},10000);
                // Encoding a photo-heavy screen is CPU work; never hold the UI thread for PNG compression.
                ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,out);return new Reply(out.toByteArray());
            }finally{main.post(()->{Bitmap bitmap=held.getAndSet(null);if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();});}
        }
        if(method.equals("POST")&&path.equals("/commands")){
            JSONArray commands=body.getJSONArray("commands");if(commands.length()<1||commands.length()>24)throw new IllegalArgumentException("一次1至24个操作");
            long waitBudget=0;for(int i=0;i<commands.length();i++){JSONObject c=commands.getJSONObject(i);String op=c.getString("type");if(op.equals("wait")){int ms=c.getInt("ms");if(ms<0||ms>1000||(waitBudget+=ms)>6000)throw new IllegalArgumentException("等待超出限制");}else if(op.equals("key"))keyCode(c.getString("key"));else if(op.equals("text")){if(c.getString("text").length()>512)throw new IllegalArgumentException("文本过长");}else if(!op.equals("tap")||!Double.isFinite(c.getDouble("x"))||!Double.isFinite(c.getDouble("y")))throw new IllegalArgumentException("操作无效");}
            for(int i=0;i<commands.length();i++){require(token);final JSONObject c=commands.getJSONObject(i);if(c.getString("type").equals("wait")){Thread.sleep(c.getInt("ms"));continue;}ui(()->{Activity a=active();String op=c.getString("type");if(op.equals("key")){int key=keyCode(c.getString("key"));long time=SystemClock.uptimeMillis();a.dispatchKeyEvent(new KeyEvent(time,time,KeyEvent.ACTION_DOWN,key,0));a.dispatchKeyEvent(new KeyEvent(time,time+1,KeyEvent.ACTION_UP,key,0));}else if(op.equals("tap")){float x=(float)c.getDouble("x"),y=(float)c.getDouble("y");View view=a.getWindow().getDecorView();if(x<0||y<0||x>=view.getWidth()||y>=view.getHeight())throw new IllegalArgumentException("坐标越界");long time=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(time,time,0,x,y,0),up=MotionEvent.obtain(time,time+1,1,x,y,0);try{a.dispatchTouchEvent(down);a.dispatchTouchEvent(up);}finally{down.recycle();up.recycle();}}else{View focused=a.getCurrentFocus();if(!(focused instanceof EditText)||secretInput((EditText)focused))throw new IllegalArgumentException("仅允许当前非密码输入框");((EditText)focused).setText(c.getString("text"));}return true;});}
            return new Reply(new JSONObject().put("executed",commands.length()).put("systemInjection",false));
        }
        if(method.equals("POST")&&path.equals("/resources")){
            ui(()->{Activity a=active();if(a instanceof CinemaActivity&&((CinemaActivity)a).localPlaying())throw new IllegalStateException();return true;});
            String raw=body.getJSONObject("pack").toString();CinemaCatalog.Config next=CinemaResourcePack.read(new JSONObject(raw),body.optBoolean("allowHttp",false),raw);require(token);
            boolean changed=ui(()->{Activity a=active();if(a instanceof CinemaActivity&&((CinemaActivity)a).localPlaying())throw new IllegalStateException();boolean applied=new CinemaVault(a).importConfig(next);if(a instanceof CinemaActivity)a.recreate();return applied;},30000);return new Reply(new JSONObject().put("packId",next.packId).put("revision",next.packRevision).put("sha256",next.packSha256).put("changed",changed));
        }
        if(method.equals("POST")&&path.equals("/probe")){
            ui(()->{active();return true;});String address=CinemaCatalog.url(body.getString("url"),null,body.optBoolean("allowHttp",false));boolean http=body.optBoolean("allowHttp",false);if(!probing.compareAndSet(false,true))throw new IllegalStateException("探测正在执行");
            Future<JSONObject> result=probeWorker.submit(()->{android.media.MediaMetadataRetriever reader=new android.media.MediaMetadataRetriever();try{CinemaCatalog.Episode media=new CinemaHttp().media(new CinemaCatalog.Episode("探测",address),http);reader.setDataSource(media.url,new HashMap<>(media.headers));Bitmap frame=reader.getFrameAtTime(0,android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);try{return new JSONObject().put("width",reader.extractMetadata(18)).put("height",reader.extractMetadata(19)).put("firstFrameDecoded",frame!=null).put("deviceDecoder",true).put("continuousPlaybackTested",false);}finally{if(frame!=null)frame.recycle();}}finally{try{reader.release();}catch(RuntimeException ignored){}probing.set(false);}});
            try{return new Reply(result.get(18,TimeUnit.SECONDS));}catch(TimeoutException timeout){throw new IllegalStateException("电视解码探测超时；底层返回前不再启动新探测");}
        }
        throw new IllegalArgumentException("不存在此接口");
    }
    private void require(String token){if(listener==null||!session.authorized(token))throw new SecurityException("请在电视开启并确认限时连接");}
    private Activity active(){Activity a=foreground.get();if(!(a instanceof MainActivity||a instanceof CinemaActivity)||a.isFinishing()||!a.hasWindowFocus()||(a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0)throw new SecurityException("仅允许开视前台非敏感页面");return a;}
    private <T>T ui(Callable<T> work)throws Exception{return ui(work,4000);}
    private <T>T ui(Callable<T> work,int milliseconds)throws Exception{FutureTask<T> task=new FutureTask<>(work);main.post(task);try{return task.get(milliseconds,TimeUnit.MILLISECONDS);}catch(Exception failure){task.cancel(false);throw failure;}}
    private static boolean secretInput(EditText text){int input=text.getInputType(),variation=input&0xff0;return text.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod||variation==0x80||variation==0x90||variation==0xe0||(input&15)==2&&variation==0x10;}
    private int keyCode(String name){String[] names={"up","down","left","right","ok","back","menu","play_pause"};int[] codes={19,20,21,22,23,4,82,85};for(int i=0;i<names.length;i++)if(names[i].equals(name))return codes[i];throw new IllegalArgumentException("仅支持开视方向键、确认、返回、菜单和播放键");}
    public void onActivityResumed(Activity a){foreground=new WeakReference<>(a);}public void onActivityPaused(Activity a){if(foreground.get()==a)foreground.clear();}public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){if(foreground.get()==a)foreground.clear();}
}
