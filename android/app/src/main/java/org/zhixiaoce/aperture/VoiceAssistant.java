package org.zhixiaoce.aperture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/** OEM assistant coexistence, using public exported system entries only. No microphone service. */
final class VoiceAssistant {
    private final Activity activity;
    VoiceAssistant(Activity activity) { this.activity = activity; }
    static boolean systemVoiceKey(int key) {
        return key == KeyEvent.KEYCODE_ASSIST || key == KeyEvent.KEYCODE_VOICE_ASSIST || key == KeyEvent.KEYCODE_SEARCH || key == KeyEvent.KEYCODE_HEADSETHOOK;
    }
    private boolean safe(ResolveInfo info) {
        if (info == null || info.activityInfo == null || info.activityInfo.applicationInfo == null) return false;
        ApplicationInfo app = info.activityInfo.applicationInfo;
        return info.activityInfo.exported && info.activityInfo.enabled && app.enabled
            && (app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            && !activity.getPackageName().equals(app.packageName)
            && (info.activityInfo.permission == null || activity.checkSelfPermission(info.activityInfo.permission) == PackageManager.PERMISSION_GRANTED);
    }
    private List<ResolveInfo> available(String action) {
        List<ResolveInfo> result = new ArrayList<>();
        try { for (ResolveInfo info : activity.getPackageManager().queryIntentActivities(new Intent(action), PackageManager.MATCH_DEFAULT_ONLY | PackageManager.MATCH_SYSTEM_ONLY)) if (safe(info)) result.add(info); }
        catch (RuntimeException ignored) { /* Restricted firmware: manual voice keys still belong to the OS. */ }
        return result;
    }
    private ResolveInfo preferred(String action) {
        try { return activity.getPackageManager().resolveActivity(new Intent(action), PackageManager.MATCH_DEFAULT_ONLY); }
        catch (RuntimeException ignored) { return null; }
    }
    String label() {
        for (String action : new String[] {Intent.ACTION_ASSIST, Intent.ACTION_VOICE_COMMAND}) {
            List<ResolveInfo> list = available(action);
            if (list.size() == 1) return list.get(0).loadLabel(activity.getPackageManager()).toString();
        }
        return "";
    }
    void open() {
        // ACTION_VOICE_ASSIST is a hidden/SystemApi contract. Do not forge a privileged wakeup.
        for (String action : new String[] {Intent.ACTION_ASSIST, Intent.ACTION_VOICE_COMMAND}) {
            List<ResolveInfo> list = available(action); if (list.isEmpty()) continue;
            ResolveInfo selected = preferred(action);
            if (safe(selected)) for (ResolveInfo candidate : list) if (candidate.activityInfo.packageName.equals(selected.activityInfo.packageName) && candidate.activityInfo.name.equals(selected.activityInfo.name)) { if (launch(action, candidate)) return; }
            if (list.size() == 1) { if (launch(action, list.get(0))) return; continue; }
            String[] labels = new String[list.size()]; for (int i = 0; i < labels.length; i++) labels[i] = list.get(i).loadLabel(activity.getPackageManager()) + " · " + list.get(i).activityInfo.packageName;
            new AlertDialog.Builder(activity).setTitle("选择电视的系统语音入口").setItems(labels, (dialog, which) -> { if (!launch(action, list.get(which))) help(); }).setNegativeButton("返回", null).show(); return;
        }
        help();
    }
    private boolean launch(String action, ResolveInfo info) {
        if (!safe(info)) return false;
        try { activity.startActivity(new Intent(action).setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name))); return true; }
        catch (RuntimeException ignored) { return false; }
    }
    void settings() {
        for (String action : new String[] {Settings.ACTION_VOICE_INPUT_SETTINGS, Settings.ACTION_SETTINGS}) {
            List<ResolveInfo> list = available(action); if (list.isEmpty()) continue;
            ResolveInfo info = preferred(action);
            if (safe(info) && launch(action, info)) return;
            if (list.size() == 1 && launch(action, list.get(0))) return;
        }
        new AlertDialog.Builder(activity).setTitle("打开小爱设置").setMessage("请用电视遥控器的设置键，或切回原桌面，打开小爱同学的语音唤醒设置。不同机型的路径可能不同。")
            .setPositiveButton("知道了", null).show();
    }
    void help() {
        String entry = label();
        String message = "遥控器：蓝牙语音遥控器需要按住语音键说话，说完再松开。开窗不把语音键当成确认键。\n\n远场：支持的电视需在原系统小爱同学中开启语音唤醒；检查电视麦克风开关和遮挡。协同唤醒可能由家里另一台设备应答。待机唤醒由电视固件和硬件决定。\n\n";
        message += entry.isEmpty() ? "当前没有确认可直接打开的公开系统语音入口，这不代表电视没有小爱；仍可用原遥控器语音键。" : "发现可打开的系统入口：" + entry + "。这不代表已确认远场或待机唤醒开启。";
        message += "\n\n设备：" + Build.MANUFACTURER + " / " + Build.MODEL + " · Android API " + Build.VERSION.SDK_INT + "\n开窗不录音，不修改小爱默认助手和唤醒开关。";
        new AlertDialog.Builder(activity).setTitle("小爱同学与语音唤醒").setMessage(message).setPositiveButton("语音设置", (dialog, which) -> settings()).setNegativeButton("返回", null).show();
    }
}
