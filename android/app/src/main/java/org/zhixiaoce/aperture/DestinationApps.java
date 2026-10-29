package org.zhixiaoce.aperture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Explicit local launch targets. No package names or arbitrary intents from web content. */
final class DestinationApps {
    interface Changed { void onChanged(String slot); }
    private final Activity activity;
    private final SharedPreferences preferences;
    private final Changed changed;
    DestinationApps(Activity activity, SharedPreferences preferences, Changed changed) { this.activity = activity; this.preferences = preferences; this.changed = changed; rememberOriginalHome(); }
    private volatile java.util.Map<String,byte[]> icons=java.util.Collections.emptyMap();
    private List<AppPresets.Entry> presets(){try(java.io.InputStream input=activity.getAssets().open("app-presets.json")){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1){if(out.size()+n>32768)throw new java.io.IOException();out.write(buffer,0,n);}return AppPresets.read(out.toString("UTF-8"));}catch(Exception unavailable){return new ArrayList<>();}}
    private ResolveInfo installed(AppPresets.Entry entry,List<ResolveInfo> apps){
        for(String pkg:entry.packages){Intent tv=activity.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);if(tv!=null&&tv.getComponent()!=null)for(ResolveInfo app:apps)if(component(app).equals(tv.getComponent()))return app;}
        for(String pkg:entry.packages)for(ResolveInfo app:apps)if(pkg.equals(app.activityInfo.packageName))return app;return null;
    }
    void initializeShortcuts(){
        List<ResolveInfo> apps=available(false);java.util.Map<String,String> existing=new java.util.LinkedHashMap<>();List<String> candidates=new ArrayList<>();
        for(int i=1;i<=4;i++)if(preferences.contains("destination_quick"+i))existing.put("quick"+i,preferences.getString("destination_quick"+i,""));
        for(AppPresets.Entry e:presets()){ResolveInfo app=installed(e,apps);if(app!=null)candidates.add(component(app).flattenToString());}
        SharedPreferences.Editor editor=preferences.edit();for(java.util.Map.Entry<String,String> entry:AppPresets.initialize(existing,candidates).entrySet())if(!existing.containsKey(entry.getKey()))editor.putString("destination_"+entry.getKey(),entry.getValue());editor.apply();
    }
    private void cacheIcon(java.util.Map<String,byte[]> cache,String key,ResolveInfo app){
        if(app==null)return;android.graphics.Bitmap bitmap=null;
        try{
            android.graphics.drawable.Drawable icon=app.loadIcon(activity.getPackageManager());
            // A 74 design-pixel icon spans 148 pixels on the 4K lobby. Keep headroom without stretching wide artwork.
            int size=256,width=icon.getIntrinsicWidth(),height=icon.getIntrinsicHeight();
            if(width<=0||height<=0){width=size;height=size;}
            float scale=Math.min((float)size/width,(float)size/height);
            int drawnWidth=Math.max(1,Math.round(width*scale)),drawnHeight=Math.max(1,Math.round(height*scale));
            int left=(size-drawnWidth)/2,top=(size-drawnHeight)/2;
            bitmap=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);
            icon.setBounds(left,top,left+drawnWidth,top+drawnHeight);icon.draw(canvas);
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);cache.put(key,out.toByteArray());
        }catch(RuntimeException ignored){}finally{if(bitmap!=null)bitmap.recycle();}
    }
    byte[] icon(String key){return icons.get(key);}
    org.json.JSONObject homeApps(){
        initializeShortcuts();org.json.JSONObject snapshot=new org.json.JSONObject();org.json.JSONArray appsJson=new org.json.JSONArray(),slots=new org.json.JSONArray();java.util.Map<String,byte[]> cache=new java.util.HashMap<>();List<ResolveInfo> apps=available(false);
        try{for(AppPresets.Entry e:presets()){ResolveInfo app=installed(e,apps);cacheIcon(cache,e.id,app);appsJson.put(new org.json.JSONObject().put("id",e.id).put("installed",app!=null).put("icon",cache.containsKey(e.id)?"/apps/icons/"+e.id+".png":""));}
            for(int i=1;i<=4;i++){String slot="quick"+i;ResolveInfo app=selected(slot);cacheIcon(cache,slot,app);String id="";if(app!=null)for(AppPresets.Entry e:presets())if(e.packages.contains(app.activityInfo.packageName))id=e.id;slots.put(new org.json.JSONObject().put("slot",slot).put("id",id).put("name",app==null?"":app.loadLabel(activity.getPackageManager()).toString()).put("icon",cache.containsKey(slot)?"/apps/icons/"+slot+".png":""));}
            snapshot.put("apps",appsJson).put("slots",slots);
        }catch(Exception ignored){}icons=java.util.Collections.unmodifiableMap(cache);return snapshot;
    }
    void addPreset(String id){
        AppPresets.Entry match=null;for(AppPresets.Entry e:presets())if(e.id.equals(id))match=e;if(match==null)return;
        final ResolveInfo app=installed(match,available(false));if(app==null){new AlertDialog.Builder(activity).setTitle(match.name).setMessage("未发现可启动的电视版应用。安装完成后返回开视即可添加。应用的地区、账号及订阅由应用本身决定。")
            .setPositiveButton("安装应用",(d,w)->installation()).setNegativeButton("返回",null).show();return;}
        String[] names=new String[4];for(int i=0;i<4;i++)names[i]="位置 "+(i+1)+" · "+(label("quick"+(i+1)).isEmpty()?"空位":label("quick"+(i+1)));
        new AlertDialog.Builder(activity).setTitle("放到首页").setItems(names,(d,which)->{String slot="quick"+(which+1);preferences.edit().putString("destination_"+slot,component(app).flattenToString()).apply();changed.onChanged(slot);}).setNegativeButton("返回",null).show();
    }
    private boolean quick(String slot) { return "quick1".equals(slot) || "quick2".equals(slot) || "quick3".equals(slot) || "quick4".equals(slot); }
    private boolean valid(String slot) { return quick(slot) || "live".equals(slot) || "cinema".equals(slot) || "system".equals(slot); }
    private String title(String slot) { if (quick(slot)) return "常用应用 " + slot.substring(5); return "system".equals(slot) ? "原生桌面" : "live".equals(slot) ? "直播应用" : "影视仓 / 播放器"; }
    private ComponentName component(ResolveInfo info) { return new ComponentName(info.activityInfo.packageName, info.activityInfo.name); }
    private boolean ourHome(ResolveInfo info){
        String pkg=info.activityInfo.packageName,target=info.activityInfo.targetActivity;
        return pkg.equals(activity.getPackageName())||pkg.equals("org.zhixiaoce.aperture")||(pkg.equals("org.zhixiaoce.aperture.parents")||pkg.equals("org.zhixiaoce.kaishi")||pkg.equals("org.zhixiaoce.kaishi.parents"))||(target!=null&&target.startsWith("org.zhixiaoce.aperture."));
    }
    private boolean usable(ResolveInfo info){return info!=null&&info.activityInfo!=null&&info.activityInfo.applicationInfo!=null&&info.activityInfo.exported&&info.activityInfo.enabled&&info.activityInfo.applicationInfo.enabled;}
    private void rememberOriginalHome(){
        if(!preferences.getString("destination_system","").isEmpty())return;
        ResolveInfo original=selected("system");if(original!=null)preferences.edit().putString("destination_system",component(original).flattenToString()).apply();
    }
    private List<ResolveInfo> available(boolean homes) {
        LinkedHashMap<String, ResolveInfo> result = new LinkedHashMap<>();
        String[] categories = homes ? new String[] {Intent.CATEGORY_HOME} : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String category : categories) for (ResolveInfo info : activity.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
            String name = info.activityInfo.packageName;
            if (!usable(info) || name.equals(activity.getPackageName())) continue;
            if (homes && ourHome(info)) continue;
            result.put(component(info).flattenToString(), info);
        }
        // Some Xiaomi firmware omits the original home from CATEGORY_HOME after a desktop switch.
        // Use only resolved, exported system-package entries; never guess an activity class.
        if(homes)for(String pkg:new String[]{"com.mitv.tvhome","com.xiaomi.mitv.tvhome"}){
            Intent intent=activity.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if(intent==null)intent=activity.getPackageManager().getLaunchIntentForPackage(pkg);
            ResolveInfo info=intent==null?null:activity.getPackageManager().resolveActivity(intent,0);
            if(usable(info)&&!ourHome(info)&&(info.activityInfo.applicationInfo.flags&(android.content.pm.ApplicationInfo.FLAG_SYSTEM|android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0)result.put(component(info).flattenToString(),info);
        }
        List<ResolveInfo> list = new ArrayList<>(result.values());
        list.sort((a, b) -> a.loadLabel(activity.getPackageManager()).toString().compareTo(b.loadLabel(activity.getPackageManager()).toString())); return list;
    }
    private ResolveInfo selected(String slot) {
        if (!valid(slot)) return null;
        String saved = preferences.getString("destination_" + slot, ""); List<ResolveInfo> list = available("system".equals(slot));
        for (ResolveInfo info : list) if (component(info).flattenToString().equals(saved)) return info;
        if ("system".equals(slot)) {
            ResolveInfo current = activity.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
            if (current != null) for (ResolveInfo info : list) if (component(info).equals(component(current))) return info;
            for(String pkg:new String[]{"com.mitv.tvhome","com.xiaomi.mitv.tvhome"})for(ResolveInfo info:list)if(pkg.equals(info.activityInfo.packageName)&&(info.activityInfo.applicationInfo.flags&(android.content.pm.ApplicationInfo.FLAG_SYSTEM|android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0)return info;
            if(list.size()==1)return list.get(0);
        }
        return null;
    }
    String label(String slot) { ResolveInfo info = selected(slot); return info == null ? "" : info.loadLabel(activity.getPackageManager()).toString(); }
    void launch(String slot) { if (!valid(slot)) return; ResolveInfo info = selected(slot); if (info == null) pick(slot, true); else launchInfo(info, "system".equals(slot)); }
    void pick(String slot, boolean openAfter) {
        if (!valid(slot)) return; List<ResolveInfo> apps = available("system".equals(slot));
        if (apps.isEmpty()) { new AlertDialog.Builder(activity).setTitle("没有找到" + title(slot)).setMessage("请先从原电视系统安装或启用应用。部分固件限制桌面入口，可在系统设置中切换。")
            .setPositiveButton("系统设置", (dialog, which) -> settings(false)).setNegativeButton("返回", null).show(); return; }
        String[] labels = new String[apps.size()]; for (int i = 0; i < labels.length; i++) labels[i] = apps.get(i).loadLabel(activity.getPackageManager()) + "  ·  " + apps.get(i).activityInfo.packageName;
        new AlertDialog.Builder(activity).setTitle("选择" + title(slot)).setItems(labels, (dialog, which) -> {
            preferences.edit().putString("destination_" + slot, component(apps.get(which)).flattenToString()).apply(); if (openAfter) launch(slot); else changed.onChanged(slot);
        }).setNegativeButton("返回", null).show();
    }
    void launchShortcut(String slot) {
        if (!quick(slot)) return;
        ResolveInfo info = selected(slot);
        if (info != null) { launchInfo(info, false); return; }
        new AlertDialog.Builder(activity).setTitle("这个常用应用还没准备好").setMessage("应用可能尚未添加、已卸载或停用。请家人在首页的家人设置里选择「管理常用应用」。")
            .setPositiveButton("知道了", null).show();
    }
    void shortcuts() {
        String[] labels = new String[4];
        for (int i = 0; i < 4; i++) { String name = label("quick" + (i + 1)); labels[i] = "位置 " + (i + 1) + " · " + (name.isEmpty() ? "待添加" : name); }
        new AlertDialog.Builder(activity).setTitle("管理首页常用应用").setItems(labels, (dialog, which) -> {
            String slot = "quick" + (which + 1);
            new AlertDialog.Builder(activity).setTitle(title(slot)).setItems(new String[] {"选择 / 更换应用", "移除此快捷入口", "向前移动", "向后移动"}, (choice, index) -> {
                if (index == 0) pick(slot, false);
                else if(index==1) { preferences.edit().putString("destination_" + slot, "").apply(); changed.onChanged(slot); }
                else { int next=which+(index==2?-1:1);if(next>=0&&next<4){String other="quick"+(next+1),a=preferences.getString("destination_"+slot,""),b=preferences.getString("destination_"+other,"");preferences.edit().putString("destination_"+slot,b).putString("destination_"+other,a).apply();changed.onChanged(other);} }
            }).setNegativeButton("返回", (choice, index) -> shortcuts()).show();
        }).setNegativeButton("返回", null).show();
    }
    private android.app.Dialog appsDialog;
    void all() {
        if(appsDialog!=null)appsDialog.dismiss();List<ResolveInfo> apps=available(false);appsDialog=AllAppsView.show(activity,apps,preferences.getString("recent_apps",""),app->launchInfo(app,false),app->{
            TvDialogs.menu(activity,app.loadLabel(activity.getPackageManager()).toString(),new String[]{"设为常用","卸载","应用信息"},(d,i)->{
                if(i==0)TvDialogs.menu(activity,"常用位置",new String[]{"位置1","位置2","位置3","位置4"},(x,at)->{String slot="quick"+(at+1);preferences.edit().putString("destination_"+slot,component(app).flattenToString()).apply();changed.onChanged(slot);});
                else try{activity.startActivity(new Intent(i==1?Intent.ACTION_DELETE:Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+app.activityInfo.packageName)));}catch(RuntimeException unavailable){new AlertDialog.Builder(activity).setMessage("请从原系统设置管理此应用").setPositiveButton("返回",null).show();}
            });
        });
    }
    void installation() {
        new AlertDialog.Builder(activity).setTitle("安装应用")
            .setItems(new String[] {"安装方式", "本机应用", "管理应用", "设置直播入口", "设置影视入口", "设置常用应用"}, (dialog, which) -> {
                if (which == 0) {
                    new AlertDialog.Builder(activity).setTitle("通过原系统安装")
                        .setMessage("从原系统应用商店安装电视版应用。安装APK时，用原系统文件管理打开U盘文件，按系统提示确认。部分固件限制外部安装，以电视提示为准。\n\n安装后回到开视，在直播、影视或常用应用里添加入口。原应用的账号和数据仍由它自己管理。")
                        .setPositiveButton("原桌面", (notice, index) -> launch("system")).setNegativeButton("返回", (notice, index) -> installation()).show();
                } else if (which == 1) all();
                else if (which == 2) {
                    try { activity.startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)); }
                    catch (RuntimeException unavailable) { settings(false); }
                } else if (which == 3) pick("live", false);
                else if (which == 4) pick("cinema", false);
                else shortcuts();
            }).setNegativeButton("返回", null).show();
    }
    private void launchInfo(ResolveInfo info, boolean home) {
        if(home&&(!usable(info)||ourHome(info))){pick("system",true);return;}
        Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(component(info)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | (home?Intent.FLAG_ACTIVITY_CLEAR_TOP:Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        if(home)intent.addCategory(Intent.CATEGORY_HOME);
        try {
            activity.startActivity(intent);
            if(!home){String key=info.activityInfo.packageName+"/"+info.activityInfo.name;List<String> recent=new ArrayList<>();recent.add(key);for(String old:preferences.getString("recent_apps","").split("\n"))if(!old.isEmpty()&&!old.equals(key)&&recent.size()<5)recent.add(old);preferences.edit().putString("recent_apps",String.join("\n",recent)).apply();}
            // Do not recreate the lobby during handoff. Remove our task only after the explicit launch succeeds.
            if(home){preferences.edit().putString("destination_system",component(info).flattenToString()).apply();activity.finishAndRemoveTask();}
        } catch (RuntimeException error) {
            if(home)new AlertDialog.Builder(activity).setTitle("原桌面暂时无法打开").setMessage("可重新选择原桌面，或到系统设置恢复默认桌面。无需重启开视。")
                .setPositiveButton("重新选择",(d,w)->pick("system",true)).setNeutralButton("系统桌面设置",(d,w)->settings(true)).setNegativeButton("返回",null).show();
            else new AlertDialog.Builder(activity).setMessage("这个入口暂时无法打开。请重新选择应用，或从原系统检查是否已停用。").setPositiveButton("知道了",null).show();
        }
    }
    void settings(boolean home) {
        if (home) { new HomeEntryController(activity).show(); return; }
        try { activity.startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        catch (RuntimeException error) { new AlertDialog.Builder(activity).setMessage("当前固件未提供可打开的系统设置入口，请用电视遥控器的设置键进入。").setPositiveButton("知道了", null).show(); }
    }
}
