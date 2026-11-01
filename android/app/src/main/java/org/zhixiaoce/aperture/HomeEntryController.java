package org.zhixiaoce.aperture;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Changes only our own optional alias. The system and user choose the default home. */
final class HomeEntryController {
    private final Activity activity;
    private final ComponentName entry;
    private AlertDialog dialog;
    private AlertDialog notice;
    private TextView status;
    private Button choose;
    private Button remove;
    private Button guard;
    private String failureNote="";
    private static final int REQUEST_HOME = 4107;
    HomeEntryController(Activity activity) {
        this.activity = activity;
        // Parents has a different applicationId but shares the Java namespace.
        entry = new ComponentName(activity.getPackageName(), MainActivity.class.getPackage().getName() + ".HomeEntry");
    }
    private boolean enabled() {
        try{return activity.getPackageManager().getComponentEnabledSetting(entry) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;}
        catch(RuntimeException unavailable){return false;}
    }
    private boolean isDefault() {
        try {
            ResolveInfo home=activity.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addCategory(Intent.CATEGORY_DEFAULT),PackageManager.MATCH_DEFAULT_ONLY);
            return home!=null&&home.activityInfo!=null&&activity.getPackageName().equals(home.activityInfo.packageName);
        } catch(RuntimeException unavailable) { return false; }
    }
    void show() {
        dismiss();
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        status=TvUi.text(activity,"",24,false);body.addView(status);
        TextView help=TvUi.text(activity,"系统支持时，开机和 HOME 键进入开视；首屏返回可打开小米桌面。\n系统不支持更换默认桌面时，可授权桌面守护。固件开机广告不在此功能范围。",20,true);
        help.setPadding(0,TvUi.dp(activity,10),0,TvUi.dp(activity,18));body.addView(help);
        choose=add(body,"设为默认桌面",this::requestDefault);
        guard=add(body,"开启桌面守护",this::toggleGuard);
        add(body,"辅助功能授权",this::accessibilitySettings);
        add(body,"恢复小米桌面",this::restoreSystem);
        remove=add(body,"取消桌面候选",this::removeCandidate);
        dialog=TvDialogs.panel(activity,"默认桌面与开机",body);refresh();
    }
    private Button add(LinearLayout body,String title,Runnable action){
        Button button=TvUi.button(activity,title,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,TvUi.dp(activity,5),0,TvUi.dp(activity,5));body.addView(button,p);return button;
    }
    void refresh(){
        if(dialog==null||!dialog.isShowing())return;
        boolean selected=isDefault(),candidate=enabled();
        status.setText(selected?"已设为默认桌面":(candidate?"等待在系统中选择开视":"未设为默认桌面")+(failureNote.isEmpty()?"":"\n"+failureNote));
        boolean requested=HomeGuardService.requested(activity);
        status.append("\n桌面守护："+(!requested?"关闭":!HomeGuardService.authorized(activity)?"等待辅助功能授权":HomeGuardService.connected()?"已连接":"已授权，等待系统连接"));
        guard.setText(requested?"关闭桌面守护":"开启桌面守护");
        choose.setText(selected?"更换默认桌面":"设为默认桌面");
        remove.setVisibility(candidate?android.view.View.VISIBLE:android.view.View.GONE);
    }
    void dismiss(){if(notice!=null){notice.dismiss();notice=null;}if(dialog!=null){dialog.dismiss();dialog=null;}}
    private void showNotice(AlertDialog next){
        if(notice!=null)notice.dismiss();notice=next;
        next.setOnDismissListener(ignored->{if(notice==next)notice=null;});next.show();
    }
    private void requestDefault(){
        if(isDefault()){systemSettings();return;}
        failureNote="";
        try{
            activity.getPackageManager().setComponentEnabledSetting(entry,PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
            refresh();
        }catch(RuntimeException unavailable){
            failureNote="系统未允许开视加入桌面候选";refresh();systemSettings();return;
        }
        try{
            if(Build.VERSION.SDK_INT>=29){
                RoleManager roles=activity.getSystemService(RoleManager.class);
                if(roles!=null&&roles.isRoleAvailable(RoleManager.ROLE_HOME)&&!roles.isRoleHeld(RoleManager.ROLE_HOME)){
                    activity.startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME),REQUEST_HOME);return;
                }
            }
        }catch(RuntimeException unavailable){
            // A locked OEM role controller may still expose its own desktop settings.
            failureNote="系统桌面授权未打开，请在系统设置中选择";refresh();
        }
        systemSettings();
    }
    private void toggleGuard(){
        if(HomeGuardService.requested(activity)){HomeGuardService.setRequested(activity,false);refresh();return;}
        showNotice(new AlertDialog.Builder(activity).setTitle("开启桌面守护？")
            .setMessage("小米桌面出现时打开开视。只识别窗口名称，不读取屏幕、通知或按键；不拦截音量和语音键。主动返回小米桌面时放行，可随时关闭。")
            .setPositiveButton("开启并授权",(d,w)->{HomeGuardService.setRequested(activity,true);refresh();accessibilitySettings();})
            .setNegativeButton("返回",null).create());
    }
    private void accessibilitySettings(){
        try{activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
        catch(RuntimeException error){failureNote="系统未提供辅助功能入口，可按使用说明通过已授权的 ADB 设置";refresh();}
    }
    private void restoreSystem(){
        HomeGuardService.setRequested(activity,false);refresh();
        showNotice(new AlertDialog.Builder(activity).setTitle("恢复小米桌面")
            .setMessage("桌面守护已关闭。在系统页面选择小米桌面作为默认；也可关闭“开视桌面守护”的辅助功能授权。")
            .setPositiveButton("选择系统桌面",(d,w)->systemSettings()).setNegativeButton("返回",null).create());
    }
    private void removeCandidate() {
        if(isDefault()){
            showNotice(new AlertDialog.Builder(activity).setTitle("先选择原桌面").setMessage("开视仍是默认桌面。请先在系统中更换，再取消候选。")
                .setPositiveButton("系统桌面设置",(d,w)->systemSettings()).setNegativeButton("返回",null).create());return;
        }
        showNotice(new AlertDialog.Builder(activity).setTitle("取消桌面候选？")
            .setMessage("开视仍可从电视的应用列表打开。")
            .setPositiveButton("取消候选", (notice, which) -> {
                try {
                    activity.getPackageManager().setComponentEnabledSetting(entry,PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
                    refresh();
                } catch (RuntimeException error) {
                    showNotice(new AlertDialog.Builder(activity).setMessage("当前系统无法更改桌面候选，请使用系统桌面设置。")
                        .setPositiveButton("系统设置", (message, index) -> systemSettings()).setNegativeButton("返回", null).create());
                }
            }).setNegativeButton("返回", null).create());
    }
    private void systemSettings() {
        String[] actions={Settings.ACTION_HOME_SETTINGS,Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,Settings.ACTION_SETTINGS};
        for(String action:actions)
            try { activity.startActivity(new Intent(action)); return; } catch (RuntimeException unavailable) {}
        failureNote="当前固件未提供可打开的桌面选择页面";refresh();
        showNotice(new AlertDialog.Builder(activity).setMessage("当前固件未提供桌面选择页面。请用遥控器进入原系统设置；开视仍可从应用列表打开。")
            .setPositiveButton("知道了", null).create());
    }
}
