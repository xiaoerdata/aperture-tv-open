package org.zhixiaoce.aperture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.provider.Settings;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Manual APK staging and OEM installer handoff. No remote or silent install API. */
final class ApkInstaller {
    static final String MIME="application/vnd.android.package-archive";
    private static final int PICK=6301, SOURCES=6302, INSTALL=6303;
    interface Open { InstallCache.Source open() throws Exception; }
    interface BeforeSystem { void check(Runnable allowed, Runnable denied); }
    private final Activity activity;
    private final Runnable installed;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private volatile boolean destroyed, cancelled;
    private boolean copying;
    private File pending;
    private String description;
    private String expectedHash, expectedPackage, stagedPackage;
    private long stagedVersion;
    private BeforeSystem beforeSystem = (allowed,denied)->allowed.run();
    private java.util.function.Consumer<String> receipt;
    ApkInstaller(Activity activity, Runnable installed) { this.activity=activity; this.installed=installed; }
    void prepareVerified(Open source,String sha256,String packageName,BeforeSystem guard,java.util.function.Consumer<String> report) {
        if(copying||pending!=null){report.accept("failed");return;}
        expectedHash=sha256;expectedPackage=packageName;beforeSystem=guard;receipt=report;prepare(source);
    }
    void choose() {
        if(copying || pending!=null) { message("请先完成或取消当前安装"); return; }
        for(String action:new String[]{Intent.ACTION_OPEN_DOCUMENT,Intent.ACTION_GET_CONTENT}) {
            Intent intent=new Intent(action).addCategory(Intent.CATEGORY_OPENABLE).setType(MIME).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { activity.startActivityForResult(intent,PICK); return; } catch(RuntimeException unavailable) { /* Try the other public picker. */ }
        }
        message("当前固件没有可用的文件选择器。可在安装应用中选择NAS，或用原系统文件管理打开U盘APK。");
    }
    void fromNas(NasReader reader, NasReader.Entry entry) {
        if(!InstallCache.apkName(entry.name)) return;
        new AlertDialog.Builder(activity).setTitle("安装这个APK？").setMessage(entry.name+"\n复制到电视后，由原系统安装器确认。")
            .setPositiveButton("读取APK",(d,w)->prepare(()->{
                NasReader.Resource file=reader.open(entry.path);
                return new InstallCache.Source(){long position;public long size(){return file.size();}public int read(byte[] data)throws IOException{if(position>=file.size())return -1;int count=file.readAt(position,data,0,(int)Math.min(data.length,file.size()-position));if(count<=0)throw new IOException("APK未读取完整");position+=count;return count;}public void close()throws IOException{file.close();}};
            })).setNegativeButton("返回",null).show();
    }
    void prepare(Open source) {
        if(copying || pending!=null) { message("请先完成或取消当前安装"); return; }
        copying=true;cancelled=false;
        AlertDialog progress=new AlertDialog.Builder(activity).setTitle("读取APK").setMessage("正在复制到电视，最多200MB。")
            .setNegativeButton("取消",(d,w)->cancelled=true).setOnCancelListener(d->cancelled=true).create();progress.show();
        io.execute(()->{
            File staged=null;String detail=null;String error=null;
            try {
                File directory=InstallCache.directory(activity.getCacheDir());
                File[] old=directory.listFiles();if(old!=null)for(File f:old)if(f.isFile()&&f.lastModified()<System.currentTimeMillis()-86400000L)f.delete();
                staged=InstallCache.copy(directory,source.open(),()->cancelled||destroyed);
                PackageInfo info=activity.getPackageManager().getPackageArchiveInfo(staged.getAbsolutePath(),0);
                if(info==null || info.packageName==null)throw new IOException("这不是可解析的独立APK。APKS、XAPK和拆分安装包暂不支持。");
                String digest=InstallCache.sha256(staged);
                if((expectedHash!=null&&!expectedHash.equals(digest))||(expectedPackage!=null&&!expectedPackage.equals(info.packageName)))throw new IOException("APK的包名或SHA-256与WorkBuddy请求不一致，未发起安装。");
                stagedPackage=info.packageName;stagedVersion=version(info);
                detail=info.packageName+"\n版本 "+info.versionName+"\n大小 "+staged.length()+" 字节\nSHA-256\n"+digest;
            }catch(Exception failure){error=failure instanceof IOException?failure.getMessage():"无法读取这个APK，请检查文件或连接。";}
            File file=staged;String text=detail,problem=error;
            activity.runOnUiThread(()->{
                copying=false;if(!destroyed&&progress.isShowing())progress.dismiss();
                if(destroyed||cancelled||problem!=null){if(file!=null)file.delete();notifyOutcome(cancelled?"cancelled":"failed");if(!destroyed&&!cancelled)message(problem);return;}
                pending=file;description=text;review();
            });
        });
    }
    private void review() {
        if(pending==null)return;
        new AlertDialog.Builder(activity).setTitle("核对安装包").setMessage(description)
            .setPositiveButton("交给系统安装",(d,w)->permission()).setNegativeButton("取消",(d,w)->clear()).setOnCancelListener(d->clear()).show();
    }
    private void permission() {
        if(pending==null)return;
        if(activity.getPackageManager().canRequestPackageInstalls()){handoff();return;}
        new AlertDialog.Builder(activity).setTitle("允许开视发起安装")
            .setMessage("请在原系统设置中允许此来源，返回后再确认安装。此权限不会允许静默安装。")
            .setPositiveButton("系统授权",(d,w)->{
                try{activity.startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())),SOURCES);}
                catch(RuntimeException unavailable){clear();message("当前固件未提供来源授权入口，请从原系统设置授权或使用原文件管理安装。");}
            }).setNegativeButton("取消",(d,w)->clear()).setOnCancelListener(d->clear()).show();
    }
    private boolean system(ResolveInfo info) {
        if(info==null||info.activityInfo==null||info.activityInfo.applicationInfo==null)return false;
        ApplicationInfo app=info.activityInfo.applicationInfo;
        return info.activityInfo.exported&&info.activityInfo.enabled&&app.enabled
            &&(app.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0
            &&(info.activityInfo.permission==null||activity.checkSelfPermission(info.activityInfo.permission)==PackageManager.PERMISSION_GRANTED);
    }
    private void handoff() {
        beforeSystem.check(this::handoffReady,()->{end("cancelled");message("本次操作授权已失效，没有打开系统安装器。");});
    }
    private void handoffReady() {
        if(pending==null)return;
        Uri uri=Uri.parse("content://"+activity.getPackageName()+".install/"+pending.getName());
        Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT,true);
        intent.setClipData(ClipData.newRawUri("APK",uri));
        List<ResolveInfo> choices=new ArrayList<>();
        try{for(ResolveInfo info:activity.getPackageManager().queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY))if(system(info))choices.add(info);}catch(RuntimeException ignored){}
        if(choices.isEmpty()){end("failed");message("没有找到可用的系统安装器，请使用原系统安装方式。");return;}
        if(choices.size()==1){launch(intent,choices.get(0));return;}
        String[] labels=new String[choices.size()];for(int i=0;i<labels.length;i++)labels[i]=choices.get(i).loadLabel(activity.getPackageManager()).toString();
        new AlertDialog.Builder(activity).setTitle("选择系统安装器").setItems(labels,(d,w)->launch(intent,choices.get(w)))
            .setNegativeButton("取消",(d,w)->clear()).setOnCancelListener(d->clear()).show();
    }
    private void launch(Intent intent,ResolveInfo info) {
        try{activity.startActivityForResult(intent.setComponent(new ComponentName(info.activityInfo.packageName,info.activityInfo.name)),INSTALL);}
        catch(RuntimeException unavailable){end("failed");message("系统安装器无法打开，请从原系统检查安装权限。");}
    }
    boolean result(int request,int result,Intent data) {
        if(request==PICK){
            if(result!=Activity.RESULT_OK||data==null||data.getData()==null)return true;
            Uri uri=data.getData();if(!"content".equals(uri.getScheme())){message("请选择文件管理中的APK文件。");return true;}
            String name=null;try(Cursor cursor=activity.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}catch(RuntimeException ignored){}
            if(!InstallCache.apkName(name)){message("请选择独立的.apk文件，暂不支持APKS或XAPK。");return true;}
            prepare(()->{InputStream input=activity.getContentResolver().openInputStream(uri);if(input==null)throw new IOException("无法读取文件");return new InstallCache.Source(){public long size(){return -1;}public int read(byte[] buffer)throws IOException{return input.read(buffer);}public void close()throws IOException{input.close();}};});return true;
        }
        if(request==SOURCES){if(pending==null){message("请重新选择APK文件。");return true;}if(activity.getPackageManager().canRequestPackageInstalls())review();else{clear();message("尚未允许此来源，未发起安装。");}return true;}
        if(request==INSTALL){
            String outcome="cancelled";
            if(result==Activity.RESULT_OK){outcome="unknown";try{if(version(activity.getPackageManager().getPackageInfo(stagedPackage,0))==stagedVersion)outcome="succeeded";}catch(RuntimeException|PackageManager.NameNotFoundException unavailable){}}
            end(outcome);if(result==Activity.RESULT_OK)new AlertDialog.Builder(activity).setTitle("系统报告安装完成").setMessage("现在可以把应用加到常用入口。")
            .setPositiveButton("添加常用应用",(d,w)->installed.run()).setNegativeButton("返回",null).show();else message("安装未完成或已取消，可在系统应用管理中检查。");return true;}
        return false;
    }
    private static long version(PackageInfo info){return android.os.Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
    private void notifyOutcome(String state){java.util.function.Consumer<String> report=receipt;receipt=null;if(report!=null)report.accept(state);}
    private void end(String state){if(pending!=null)pending.delete();pending=null;description=null;notifyOutcome(state);}
    private void clear(){end("cancelled");}
    private void message(String text){if(!destroyed)new AlertDialog.Builder(activity).setMessage(text).setPositiveButton("知道了",null).show();}
    void destroy(){destroyed=true;cancelled=true;io.shutdownNow();/* In-flight installer grants may still need its staged file; old files expire on next use. */}
}
