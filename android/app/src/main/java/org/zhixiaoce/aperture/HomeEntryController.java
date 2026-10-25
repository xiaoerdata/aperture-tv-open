package org.zhixiaoce.aperture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

/** Changes only our own optional alias. The system and user choose the default home. */
final class HomeEntryController {
    private final Activity activity;
    private final ComponentName entry;
    HomeEntryController(Activity activity) {
        this.activity = activity;
        // Parents has a different applicationId but shares the Java namespace.
        entry = new ComponentName(activity.getPackageName(), MainActivity.class.getPackage().getName() + ".HomeEntry");
    }
    private boolean enabled() {
        return activity.getPackageManager().getComponentEnabledSetting(entry) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }
    void show() {
        boolean active = enabled();
        new AlertDialog.Builder(activity).setTitle("默认桌面")
            .setItems(new String[] {active ? "移除本版桌面候选" : "允许本版作为桌面", "系统桌面选择"}, (dialog, which) -> {
                if (which == 1) systemSettings(); else confirm(!active);
            }).setNegativeButton("返回", null).show();
    }
    private void confirm(boolean enable) {
        new AlertDialog.Builder(activity).setTitle(enable ? "允许本版作为桌面？" : "移除本版桌面候选？")
            .setMessage(enable ? "开启后，请在系统设置中选择默认桌面。部分小米固件不支持更换；开视仍可作为普通应用使用。" : "仅移除本版开视。若当前用它作为默认桌面，请在系统设置中重新选择原桌面。")
            .setPositiveButton(enable ? "开启并选择" : "移除并选择", (dialog, which) -> {
                try {
                    activity.getPackageManager().setComponentEnabledSetting(entry,
                        enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
                    systemSettings();
                } catch (RuntimeException error) {
                    new AlertDialog.Builder(activity).setMessage("当前系统无法更改桌面候选，请使用系统桌面设置。")
                        .setPositiveButton("系统设置", (notice, index) -> systemSettings()).setNegativeButton("返回", null).show();
                }
            }).setNegativeButton("返回", null).show();
    }
    private void systemSettings() {
        try { activity.startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
        catch (RuntimeException error) {
            try { activity.startActivity(new Intent(Settings.ACTION_SETTINGS)); return; } catch (RuntimeException unavailable) {}
            new AlertDialog.Builder(activity).setMessage("当前固件未提供桌面选择页面。请用遥控器进入原系统设置；开视仍可从应用列表打开。")
                .setPositiveButton("知道了", null).show();
        }
    }
}
