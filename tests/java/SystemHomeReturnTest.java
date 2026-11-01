package org.zhixiaoce.aperture;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.*;
import java.util.*;

/** Android boundary doubles exercise the production routing code; no device success is inferred. */
public final class SystemHomeReturnTest {
    static int count;
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);count++;}
    static ResolveInfo info(String pkg,String cls){ResolveInfo r=new ResolveInfo();r.activityInfo=new ActivityInfo();r.activityInfo.packageName=pkg;r.activityInfo.name=cls;r.activityInfo.exported=true;r.activityInfo.enabled=true;r.activityInfo.applicationInfo=new ApplicationInfo();r.activityInfo.applicationInfo.enabled=true;r.activityInfo.applicationInfo.flags=ApplicationInfo.FLAG_SYSTEM;return r;}
    static ComponentName component(ResolveInfo r){return new ComponentName(r.activityInfo.packageName,r.activityInfo.name);}
    static final class PM extends PackageManager {
        ResolveInfo current;List<ResolveInfo> homes=new ArrayList<>();Map<String,Intent> tv=new HashMap<>();Map<ComponentName,ResolveInfo> known=new HashMap<>();
        public ResolveInfo resolveActivity(Intent i,int flags){return i.getComponent()==null?current:known.get(i.getComponent());}
        public List<ResolveInfo> queryIntentActivities(Intent i,int flags){return homes;}
        public Intent getLeanbackLaunchIntentForPackage(String pkg){return tv.get(pkg);}
        public Intent getLaunchIntentForPackage(String pkg){return null;}
        void add(ResolveInfo r){homes.add(r);known.put(component(r),r);}
    }
    static final class Host extends Activity {
        final PM pm=new PM();final List<Intent> attempted=new ArrayList<>();Set<ComponentName> denied=new HashSet<>();boolean denyImplicit;
        public PackageManager getPackageManager(){return pm;}
        public String getPackageName(){return "org.zhixiaoce.kaishi";}
        public void startActivity(Intent i){attempted.add(i);if(i.getComponent()==null?denyImplicit:denied.contains(i.getComponent()))throw new SecurityException();}
    }
    public static void main(String[] args){
        ResolveInfo original=info("com.mitv.tvhome","Home"),stale=info("com.mitv.tvhome","OldEntry"),self=info("org.zhixiaoce.kaishi","HomeEntry");
        Host host=new Host();host.pm.add(original);host.pm.add(stale);host.pm.current=original;host.denied.add(component(stale));
        SystemHomeReturn.Result result=SystemHomeReturn.open(host,stale,host.pm.homes);
        check(component(original).equals(result.component),"working current HOME supersedes stale stored target");
        check(host.attempted.size()==1&&host.attempted.get(0).getComponent()==null,"system HOME dispatch attempted first");
        check(host.attempted.get(0).getCategories().contains(Intent.CATEGORY_HOME),"canonical HOME category");
        check((host.attempted.get(0).getFlags()&Intent.FLAG_ACTIVITY_CLEAR_TOP)==0,"does not clear the system task");

        host=new Host();host.pm.add(self);host.pm.add(original);host.pm.current=self;
        result=SystemHomeReturn.open(host,original,host.pm.homes);
        check(component(original).equals(result.component),"when Kaishi is default explicitly launch original home");
        check(host.attempted.size()==1&&component(original).equals(host.attempted.get(0).getComponent()),"never implicit HOME into self");

        host=new Host();host.pm.add(self);host.pm.add(stale);host.pm.add(original);host.pm.current=self;host.denied.add(component(stale));
        result=SystemHomeReturn.open(host,stale,host.pm.homes);
        check(component(original).equals(result.component),"failed saved target retries another resolved Xiaomi home");
        check(host.attempted.size()==2,"failure is not terminal after one attempt");

        host=new Host();ResolveInfo tv=info("com.mitv.tvhome","TvLauncher");host.pm.current=self;host.pm.add(self);host.pm.known.put(component(tv),tv);
        host.pm.tv.put(tv.activityInfo.packageName,new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setComponent(component(tv)));
        result=SystemHomeReturn.open(host,stale,Arrays.asList(tv));
        check(component(tv).equals(result.component),"use actual launcher component when saved component differs");
        check(host.attempted.get(0).getCategories().contains(Intent.CATEGORY_LEANBACK_LAUNCHER)&&!host.attempted.get(0).getCategories().contains(Intent.CATEGORY_HOME),"retain genuine TV launcher category");

        host=new Host();host.pm.add(original);host.pm.current=original;host.denyImplicit=true;
        result=SystemHomeReturn.open(host,original,host.pm.homes);
        check(component(original).equals(result.component)&&host.attempted.size()==2,"implicit denial retries explicit HOME");
        host=new Host();host.pm.add(original);host.pm.current=original;host.denyImplicit=true;host.denied.add(component(original));
        result=SystemHomeReturn.open(host,original,host.pm.homes);
        check(result.component==null&&"SecurityException".equals(result.failure),"all failures retain specific error class");

        host=new Host();ResolveInfo chooser=info("android","ResolverActivity");host.pm.current=chooser;host.pm.add(chooser);
        result=SystemHomeReturn.open(host,null,host.pm.homes);
        check(result.component==null&&host.attempted.isEmpty(),"do not launch a chooser as original home");
        for(String pkg:Arrays.asList("org.zhixiaoce.kaishi","org.zhixiaoce.kaishi.parents","org.zhixiaoce.aperture","org.zhixiaoce.aperture.parents")){
            host=new Host();ResolveInfo own=info(pkg,"HomeEntry");host.pm.add(own);host.pm.current=own;
            result=SystemHomeReturn.open(host,own,host.pm.homes);
            check(result.component==null&&host.attempted.isEmpty(),"exclude all current and legacy self homes");
        }
        System.out.println(count+" system-home routing assertions passed (Android boundary doubles; Xiaomi untested)");
    }
}
