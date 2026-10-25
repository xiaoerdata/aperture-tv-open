package org.zhixiaoce.aperture;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.graphics.Color;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.widget.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Foreground one-operation grant. WorkBuddy and people use the same native installer. */
public final class AppAgentActivity extends Activity {
    private static final int DELETE=6401;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final String id=UUID.randomUUID().toString();
    private final String secret=secret();
    private String kind,hash,packageName;
    private HouseholdCommandClient client;
    private ApkInstaller installer;
    private TextView status;
    private volatile boolean closed;
    private boolean terminal,processing,started,pollScheduled,resumed,polling;
    private volatile int revision;
    private long expiresAt;
    private final Set<String> targets=new HashSet<>();
    private static String secret(){byte[] value=new byte[32];new SecureRandom().nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
    private static String digest(String value)throws Exception{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder result=new StringBuilder();for(byte b:bytes)result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();}
    private JSONObject identity()throws Exception{return new JSONObject().put("id",id).put("secret",secret);}
    private static String name(String value){String clean=value.replaceAll("[\\p{Cntrl}]","");return clean.substring(0,Math.min(100,clean.length()));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        kind="uninstall".equals(getIntent().getStringExtra("kind"))?"uninstall":"install";
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(48,32,48,32);layout.setBackgroundColor(Color.rgb(12,23,32));
        label(layout,"WorkBuddy · "+(kind.equals("install")?"安装应用":"卸载应用"),32);
        status=label(layout,"正在准备本次操作授权…",24);
        label(layout,"仅适配小米电视 · 本次窗口10分钟有效，只接收一个操作。\n核对电视校验码后，再让WorkBuddy提交。系统安装或卸载确认会保留。",22);
        Button back=new Button(this);back.setText("取消并返回");back.setTextSize(26);back.setMinHeight(Math.round(72*getResources().getDisplayMetrics().density));back.setOnClickListener(v->finish());layout.addView(back);
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);setContentView(scroll);
        installer=new ApkInstaller(this,()->new DestinationApps(this,getSharedPreferences("aperture",MODE_PRIVATE),slot->{}).shortcuts());
        try{
            JSONArray offered=offered();if(offered.length()==0){status.setText(kind.equals("install")?"请先在开视连接一个NAS，用它存放APK。":"没有可供此次卸载的非系统电视应用。");return;}
            String origin=getSharedPreferences("aperture",MODE_PRIVATE).getString("origin","");
            client=new HouseholdCommandClient(origin,origin.isEmpty()?"":CookieManager.getInstance().getCookie(origin),BuildConfig.DEBUG);
            hash=digest(secret);label(layout,"校验码  "+hash.substring(0,4)+" "+hash.substring(4,8)+" "+hash.substring(8,12),28);
            // Keep the v13 wire label so renamed APKs still work with older household servers.
            JSONObject hello=new JSONObject().put("id",id).put("secretHash",hash).put("kind",kind).put("label",BuildConfig.PARENTS_EDITION?"开窗 · 爸妈版":"开窗 · 标准版").put("targets",offered);
            io.execute(()->{try{JSONObject response=client.call("app-operations","start",hello);if(!id.equals(response.getString("id"))||!hash.equals(response.getString("fingerprint")))throw new IOException();expiresAt=response.getLong("expiresAt");main.post(()->{if(closed)return;started=true;status.setText("等待已授权的WorkBuddy提交操作。\n"+(kind.equals("install")?"安装范围：本次列出的NAS连接，包名和SHA-256必须一致。":"卸载范围：本次列出的非系统应用，不含开视与原生系统组件。"));schedule();});}catch(Exception failure){main.post(()->failure("无法开启操作窗口，请检查家庭HTTPS连接和电视配对。"));}});
        }catch(Exception failure){status.setText("请先连接并配对可信HTTPS家庭服务，再开启WorkBuddy操作。");}
    }
    private TextView label(LinearLayout parent,String text,int size){TextView view=new TextView(this);view.setText(text);view.setTextColor(Color.WHITE);view.setTextSize(size);view.setPadding(0,12,0,16);parent.addView(view);return view;}
    private boolean userApp(String pkg){
        if(pkg==null||pkg.equals("org.zhixiaoce.aperture")||(pkg.equals("org.zhixiaoce.aperture.parents")||pkg.equals("org.zhixiaoce.kaishi")||pkg.equals("org.zhixiaoce.kaishi.parents")))return false;
        try{ApplicationInfo info=getPackageManager().getApplicationInfo(pkg,0);return info.enabled&&(info.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))==0;}catch(PackageManager.NameNotFoundException absent){return false;}
    }
    private boolean installable(String pkg){
        if(pkg.equals("org.zhixiaoce.aperture")||(pkg.equals("org.zhixiaoce.aperture.parents")||pkg.equals("org.zhixiaoce.kaishi")||pkg.equals("org.zhixiaoce.kaishi.parents")))return false;
        try{ApplicationInfo info=getPackageManager().getApplicationInfo(pkg,0);return (info.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))==0;}catch(PackageManager.NameNotFoundException absent){return true;}
    }
    private JSONArray offered()throws Exception{
        JSONArray result=new JSONArray();
        if(kind.equals("install")){JSONArray connections=new NasVault(this).read();for(int i=0;i<connections.length();i++){JSONObject c=connections.getJSONObject(i);targets.add(c.getString("id"));result.put(new JSONObject().put("id",c.getString("id")).put("name",name(c.getString("name"))));}}
        else{LinkedHashMap<String,String> packages=new LinkedHashMap<>();for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER})for(ResolveInfo info:getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0))if(userApp(info.activityInfo.packageName))packages.put(info.activityInfo.packageName,name(info.loadLabel(getPackageManager()).toString()));for(Map.Entry<String,String> item:packages.entrySet()){if(result.length()>=100)break;targets.add(item.getKey());result.put(new JSONObject().put("id",item.getKey()).put("name",item.getValue()));}}
        return result;
    }
    private void schedule(){if(closed||terminal||processing||pollScheduled||polling||!started||!resumed)return;pollScheduled=true;main.postDelayed(()->{pollScheduled=false;poll();},2500);}
    private void poll(){
        if(closed||terminal||processing||!resumed)return;
        if(System.currentTimeMillis()>=expiresAt){failure("本次窗口已过期，请返回后重新开启。");return;}
        polling=true;io.execute(()->{try{JSONObject response=client.call("app-operations","poll",identity());revision=response.getInt("revision");String state=response.getString("state");
            if(state.equals("waiting")){main.post(()->{polling=false;schedule();});return;}
            if(!state.equals("delivered")){main.post(()->{polling=false;failure("本次操作已结束或状态未知，请先让WorkBuddy查询原请求结果。");});return;}
            JSONObject command=response.getJSONObject("command");JSONObject leased=client.call("app-operations","stage",identity().put("state","staging").put("revision",revision));revision=leased.getInt("revision");
            main.post(()->{polling=false;if(closed)return;processing=true;packageName=command.optString("packageName");status.setText("收到操作："+packageName+"\n正在本机核对…");if(kind.equals("install"))install(command);else uninstall();});
        }catch(Exception failure){main.post(()->{polling=false;failure("操作连接或授权已失效，请先查询原请求状态；不要重复提交。");});}});
    }
    private void install(JSONObject command){
        try{
            String connectionId=command.getString("connectionId"),path=NasLocation.relative(command.getString("path"));
            if(!targets.contains(connectionId)||!InstallCache.apkName(path)||!installable(packageName))throw new IllegalArgumentException();
            JSONObject saved=new NasVault(this).library().find(connectionId);if(saved==null)throw new IllegalArgumentException();
            installer.prepareVerified(()->{
                char[] password=saved.optString("password").toCharArray();NasReader reader;
                try{reader=NasReader.connect(new NasLocation(saved.getString("root")),saved.getString("user"),password,saved.optString("domain"));}finally{Arrays.fill(password,'\0');}
                NasReader.Resource file;try{file=reader.open(path);}catch(Exception error){reader.close();throw error;}
                return new InstallCache.Source(){long position;public long size(){return file.size();}public int read(byte[] buffer)throws IOException{if(position>=file.size())return -1;int count=file.readAt(position,buffer,0,(int)Math.min(buffer.length,file.size()-position));if(count<=0)throw new IOException("APK未读取完整");position+=count;return count;}public void close()throws IOException{try{file.close();}finally{reader.close();}}};
            },command.getString("sha256"),packageName,(allowed,denied)->preflight(()->{if(installable(packageName))allowed.run();else denied.run();},denied),state->{if(!closed)report(state);});
        }catch(Exception failure){report("failed");}
    }
    private void preflight(Runnable allowed,Runnable denied){
        io.execute(()->{try{JSONObject response=client.call("app-operations","stage",identity().put("state","awaiting_system").put("revision",revision));revision=response.getInt("revision");main.post(()->{if(!closed){status.setText("等待原系统确认。WorkBuddy可查询当前状态。");allowed.run();}});}catch(Exception failure){main.post(()->{if(!closed)denied.run();});}});
    }
    private boolean system(ResolveInfo info){return info!=null&&info.activityInfo!=null&&info.activityInfo.exported&&info.activityInfo.enabled&&info.activityInfo.applicationInfo.enabled&&(info.activityInfo.applicationInfo.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0&&(info.activityInfo.permission==null||checkSelfPermission(info.activityInfo.permission)==PackageManager.PERMISSION_GRANTED);}
    private void uninstall(){
        if(!targets.contains(packageName)||!userApp(packageName)){report("failed");return;}
        new AlertDialog.Builder(this).setTitle("卸载这个应用？").setMessage(packageName+"\n卸载可能清除该应用数据，下一步由原系统确认。")
            .setPositiveButton("继续",(d,w)->preflight(()->{
                if(!userApp(packageName)){report("failed");return;}
                Intent intent=new Intent(Intent.ACTION_UNINSTALL_PACKAGE,Uri.parse("package:"+packageName)).putExtra(Intent.EXTRA_RETURN_RESULT,true);
                List<ResolveInfo> options=getPackageManager().queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY);ResolveInfo target=null;for(ResolveInfo info:options)if(system(info)){target=info;break;}
                if(target==null){report("failed");return;}
                try{startActivityForResult(intent.setComponent(new ComponentName(target.activityInfo.packageName,target.activityInfo.name)),DELETE);}catch(RuntimeException error){report("failed");}
            },()->report("cancelled"))).setNegativeButton("取消",(d,w)->report("cancelled")).setOnCancelListener(d->report("cancelled")).show();
    }
    private static String stateLabel(String state){return "succeeded".equals(state)?"已完成":"cancelled".equals(state)?"已取消":"failed".equals(state)?"未完成":"结果待核对";}
    private void report(String state){
        if(closed||terminal)return;terminal=true;status.setText("电视结果："+stateLabel(state)+"。正在发送回执…");
        io.execute(()->{try{client.call("app-operations","finish",identity().put("state",state).put("revision",revision));main.post(()->{if(!closed)status.setText("电视结果："+stateLabel(state)+"。WorkBuddy可查询本次回执。");});}catch(Exception failure){main.post(()->{if(!closed)status.setText("电视结果："+stateLabel(state)+"。回执未确认；请让WorkBuddy查询原请求，不要再次执行安装或卸载。");});}});
    }
    private void failure(String text){if(!closed){terminal=true;status.setText(text);}}
    @Override protected void onActivityResult(int request,int result,Intent data){
        if(installer!=null&&installer.result(request,result,data))return;
        if(request==DELETE){boolean present=true;try{getPackageManager().getPackageInfo(packageName,0);}catch(PackageManager.NameNotFoundException absent){present=false;}report(result==RESULT_OK&&!present?"succeeded":!present?"unknown":result==RESULT_CANCELED?"cancelled":"failed");return;}
        super.onActivityResult(request,result,data);
    }
    @Override protected void onResume(){super.onResume();resumed=true;schedule();}
    @Override protected void onPause(){resumed=false;super.onPause();}
    @Override protected void onDestroy(){
        closed=true;main.removeCallbacksAndMessages(null);if(installer!=null)installer.destroy();
        if(started&&!terminal&&client!=null)io.execute(()->{try{client.call("app-operations","finish",identity().put("state",processing?"unknown":"cancelled").put("revision",revision));}catch(Exception ignored){}});
        io.shutdown();super.onDestroy();
    }
}
