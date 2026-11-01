package org.zhixiaoce.aperture;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import java.util.HashSet;
import java.util.Set;

/** Opt-in redirect of verified Xiaomi HOME windows. Never reads text or filters remote keys. */
public final class HomeGuardService extends AccessibilityService {
    private static final String ENABLED="home_guard_enabled", VISITING="home_guard_visiting";
    private static HomeGuardService running;
    private final Set<String> stockHomes=new HashSet<>();
    private HomeGuardPolicy policy;
    private boolean registered;
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("aperture",Context.MODE_PRIVATE);}
    static boolean requested(Context c){return prefs(c).getBoolean(ENABLED,false);}
    static boolean connected(){return running!=null;}
    static boolean authorized(Context c){
        try {
            AccessibilityManager m=c.getSystemService(AccessibilityManager.class);
            if(m==null)return false;
            ComponentName own=new ComponentName(c,HomeGuardService.class);
            for(AccessibilityServiceInfo info:m.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)){
                ResolveInfo r=info.getResolveInfo();
                if(r!=null&&r.serviceInfo!=null&&own.equals(new ComponentName(r.serviceInfo.packageName,r.serviceInfo.name)))return true;
            }
        }catch(RuntimeException ignored){}
        return false;
    }
    static void setRequested(Context c,boolean enabled){
        prefs(c).edit().putBoolean(ENABLED,enabled).putBoolean(VISITING,false).apply();
        if(running!=null)running.policy.resumeHome();
    }
    private static int boot(Context c){try{return Settings.Global.getInt(c.getContentResolver(),"boot_count",-1);}catch(RuntimeException ignored){return -1;}}
    static void visitOriginal(Context c){
        prefs(c).edit().putBoolean(VISITING,true).putInt("home_guard_visit_boot",boot(c)).apply();
        if(running!=null)running.policy.visit(SystemClock.elapsedRealtime());
    }
    static void resumedHome(Context c){
        prefs(c).edit().putBoolean(VISITING,false).apply();
        if(running!=null)running.policy.resumeHome();
    }
    private final BroadcastReceiver systemEvents=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent intent){
            if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())){
                // The next wake can enter Kaishi; never wake the television ourselves.
                resumedHome(c);
            }else if(Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())&&"homekey".equals(intent.getStringExtra("reason"))){
                openIfNeeded(false,true);
            }
        }
    };
    @Override protected void onServiceConnected(){
        super.onServiceConnected();running=this;
        policy=new HomeGuardPolicy(prefs(this).getBoolean(VISITING,false)&&prefs(this).getInt("home_guard_visit_boot",-2)==boot(this));
        discoverHomes();
        IntentFilter f=new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);f.addAction(Intent.ACTION_SCREEN_OFF);
        try {
            if(Build.VERSION.SDK_INT>=33)registerReceiver(systemEvents,f,Context.RECEIVER_EXPORTED);
            else registerReceiver(systemEvents,f);
            registered=true;
        }catch(RuntimeException error){Log.w("KaishiHome","guard system events unavailable");}
        Log.i("KaishiHome","guard connected homes="+stockHomes.size());
    }
    private void discoverHomes(){
        stockHomes.clear();
        try {
            Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for(ResolveInfo r:getPackageManager().queryIntentActivities(home,0)){
                if(r.activityInfo==null||r.activityInfo.applicationInfo==null||r.priority<0)continue;
                String pkg=r.activityInfo.packageName;
                if(!pkg.equals("com.mitv.tvhome")&&!pkg.equals("com.xiaomi.mitv.tvhome"))continue;
                if((r.activityInfo.applicationInfo.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))==0)continue;
                if(!r.activityInfo.exported||!r.activityInfo.enabled||!r.activityInfo.applicationInfo.enabled)continue;
                stockHomes.add(pkg+"/"+r.activityInfo.name);
                if(r.activityInfo.targetActivity!=null)stockHomes.add(pkg+"/"+r.activityInfo.targetActivity);
            }
        }catch(RuntimeException error){Log.w("KaishiHome","guard home discovery unavailable");}
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null||event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||policy==null)return;
        if(event.getPackageName()==null||event.getClassName()==null)return;
        String pkg=event.getPackageName().toString();
        if(!pkg.equals("com.mitv.tvhome")&&!pkg.equals("com.xiaomi.mitv.tvhome"))return;
        if(stockHomes.isEmpty())discoverHomes();
        openIfNeeded(stockHomes.contains(pkg+"/"+event.getClassName()),false);
    }
    private void openIfNeeded(boolean stock,boolean homeKey){
        if(policy==null)return;
        PowerManager power=getSystemService(PowerManager.class);
        if(!policy.shouldOpen(requested(this),power!=null&&power.isInteractive(),stock,homeKey,SystemClock.elapsedRealtime()))return;
        prefs(this).edit().putBoolean(VISITING,false).apply();
        try {
            startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
            Log.i("KaishiHome","guard opened home reason="+(homeKey?"homekey":"stock-window"));
        }catch(RuntimeException error){Log.w("KaishiHome","guard start failed type="+error.getClass().getSimpleName());}
    }
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){
        if(running==this)running=null;
        if(registered)unregisterReceiver(systemEvents);
        super.onDestroy();
    }
}
