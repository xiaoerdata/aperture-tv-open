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
    DestinationApps(Activity activity, SharedPreferences preferences, Changed changed) { this.activity = activity; this.preferences = preferences; this.changed = changed; }
    private boolean quick(String slot) { return "quick1".equals(slot) || "quick2".equals(slot) || "quick3".equals(slot) || "quick4".equals(slot); }
    private boolean valid(String slot) { return quick(slot) || "live".equals(slot) || "cinema".equals(slot) || "system".equals(slot); }
    private String title(String slot) { if (quick(slot)) return "常用应用 " + slot.substring(5); return "system".equals(slot) ? "原生桌面" : "live".equals(slot) ? "直播应用" : "影视仓 / 播放器"; }
    private ComponentName component(ResolveInfo info) { return new ComponentName(info.activityInfo.packageName, info.activityInfo.name); }
    private List<ResolveInfo> available(boolean homes) {
        LinkedHashMap<String, ResolveInfo> result = new LinkedHashMap<>();
        String[] categories = homes ? new String[] {Intent.CATEGORY_HOME} : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String category : categories) for (ResolveInfo info : activity.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
            String name = info.activityInfo.packageName;
            if (!info.activityInfo.exported || !info.activityInfo.enabled || !info.activityInfo.applicationInfo.enabled || name.equals(activity.getPackageName())) continue;
            if (homes && (name.equals("org.zhixiaoce.aperture") || name.equals("org.zhixiaoce.aperture.parents"))) continue;
            result.put(component(info).flattenToString(), info);
        }
        List<ResolveInfo> list = new ArrayList<>(result.values());
        list.sort((a, b) -> a.loadLabel(activity.getPackageManager()).toString().compareTo(b.loadLabel(activity.getPackageManager()).toString())); return list;
    }
    private ResolveInfo selected(String slot) {
        if (!valid(slot)) return null;
        String saved = preferences.getString("destination_" + slot, ""); List<ResolveInfo> list = available("system".equals(slot));
        for (ResolveInfo info : list) if (component(info).flattenToString().equals(saved)) return info;
        if ("system".equals(slot) && saved.isEmpty()) {
            ResolveInfo current = activity.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
            if (current != null) for (ResolveInfo info : list) if (component(info).equals(component(current))) return info;
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
            preferences.edit().putString("destination_" + slot, component(apps.get(which)).flattenToString()).apply(); changed.onChanged(slot);
            if (openAfter) launch(slot);
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
            new AlertDialog.Builder(activity).setTitle(title(slot)).setItems(new String[] {"选择 / 更换应用", "移除此快捷入口"}, (choice, index) -> {
                if (index == 0) pick(slot, false);
                else { preferences.edit().remove("destination_" + slot).apply(); changed.onChanged(slot); }
            }).setNegativeButton("返回", (choice, index) -> shortcuts()).show();
        }).setNegativeButton("返回", null).show();
    }
    void all() {
        List<ResolveInfo> apps = available(false); String[] labels = new String[apps.size()]; for (int i = 0; i < labels.length; i++) labels[i] = apps.get(i).loadLabel(activity.getPackageManager()).toString();
        new AlertDialog.Builder(activity).setTitle(apps.isEmpty() ? "没有发现可启动的应用" : "本机应用").setItems(labels, (dialog, which) -> launchInfo(apps.get(which), false)).setNegativeButton("返回", null).show();
    }
    void installation() {
        new AlertDialog.Builder(activity).setTitle("安装应用")
            .setItems(new String[] {"安装方式", "本机应用", "管理应用", "设置直播入口", "设置影视入口", "设置常用应用"}, (dialog, which) -> {
                if (which == 0) {
                    new AlertDialog.Builder(activity).setTitle("通过原系统安装")
                        .setMessage("从原系统应用商店安装电视版应用。安装APK时，用原系统文件管理打开U盘文件，按系统提示确认。部分固件限制外部安装，以电视提示为准。\n\n安装后回到开窗，在直播、影视或常用应用里添加入口。原应用的账号和数据仍由它自己管理。")
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
        Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(component(info)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED); if (home) intent.addCategory(Intent.CATEGORY_HOME);
        try { activity.startActivity(intent); }
        catch (RuntimeException error) { new AlertDialog.Builder(activity).setMessage("这个入口暂时无法打开。请重新选择应用，或从原系统检查是否已停用。").setPositiveButton("知道了", null).show(); }
    }
    void settings(boolean home) {
        if (home) { new HomeEntryController(activity).show(); return; }
        try { activity.startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        catch (RuntimeException error) { new AlertDialog.Builder(activity).setMessage("当前固件未提供可打开的系统设置入口，请用电视遥控器的设置键进入。").setPositiveButton("知道了", null).show(); }
    }
}
