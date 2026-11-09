package org.zhixiaoce.aperture;
import android.content.*;
import android.content.pm.*;
import java.util.*;

/** Production discovery with explicit PackageManager doubles, not an installed-device claim. */
public final class InstalledAppsTest {
    private static int count;
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);count++;}
    private static ResolveInfo app(String pkg,String name,String label){ResolveInfo app=new ResolveInfo();app.label=label;app.activityInfo=new ActivityInfo();app.activityInfo.packageName=pkg;app.activityInfo.name=name;app.activityInfo.exported=true;app.activityInfo.enabled=true;app.activityInfo.applicationInfo=new ApplicationInfo();app.activityInfo.applicationInfo.enabled=true;return app;}
    public static void main(String[] args){
        PackageManager pm=new PackageManager();Context owner=new Context(pm,"org.zhixiaoce.kaishi");
        ResolveInfo unknown=app("org.family.custom","org.family.custom.TV","家里的自制播放器"),mobile=app("org.family.files","org.family.files.Main","文件管理"),known=app("com.tencent.tv","com.tencent.tv.TV","设备上的真实名称");
        pm.tv.add(known);pm.tv.add(unknown);pm.regular.add(unknown);pm.regular.add(mobile);
        pm.tv.add(app(owner.getPackageName(),"self.Home","开视"));
        ResolveInfo disabled=app("org.disabled","Disabled","停用应用");disabled.activityInfo.enabled=false;pm.tv.add(disabled);
        ResolveInfo disabledPackage=app("org.disabled.package","Main","停用包");disabledPackage.activityInfo.applicationInfo.enabled=false;pm.regular.add(disabledPackage);
        ResolveInfo internal=app("org.internal","Main","内部组件");internal.activityInfo.exported=false;pm.regular.add(internal);
        pm.tv.add(null);pm.tv.add(new ResolveInfo());ResolveInfo invalid=app("org.invalid","Main","损坏条目");invalid.activityInfo.applicationInfo=null;pm.tv.add(invalid);
        List<ResolveInfo> apps=InstalledApps.read(owner);
        check(apps.size()==3,"only three real enabled exported launch targets returned");
        check(apps.contains(unknown)&&apps.contains(mobile),"non-preset TV and ordinary launcher applications included");
        check(apps.contains(known)&&known.loadLabel(pm).equals("设备上的真实名称"),"PackageManager label retained rather than a brand label");
        check(Collections.frequency(apps,unknown)==1,"same component from two launcher categories deduplicated");
        check(apps.stream().noneMatch(a->a.activityInfo.packageName.contains("netflix")),"uninstalled brand never fabricated");
        check(apps.stream().noneMatch(a->a.activityInfo.packageName.equals(owner.getPackageName())),"own launcher omitted from shortcut targets");
        check(!apps.contains(disabled)&&!apps.contains(disabledPackage)&&!apps.contains(internal),"disabled and private components omitted");
        check(pm.calls.equals(Arrays.asList(Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER)),"both launcher discovery queries used");
        List<ResolveInfo> sorted=new ArrayList<>(apps);sorted.sort((a,b)->a.loadLabel(pm).toString().compareTo(b.loadLabel(pm).toString()));check(sorted.equals(apps),"actual labels determine ordering");
        check(pm.tv.size()==7&&pm.regular.size()==4,"provider collections not mutated");
        pm.failTv=true;pm.calls.clear();apps=InstalledApps.read(owner);check(apps.contains(unknown)&&apps.contains(mobile)&&apps.size()==2,"restricted TV query still attempts ordinary launchers");
        pm.failRegular=true;check(InstalledApps.read(owner).isEmpty(),"restricted discovery never substitutes recommendations");
        pm.failTv=false;pm.failRegular=false;pm.tv.clear();pm.regular.clear();check(InstalledApps.read(owner).isEmpty(),"empty installed set remains empty");
        System.out.println(count+" production installed-app discovery assertions passed with PackageManager doubles; Android device not executed");
    }
}
