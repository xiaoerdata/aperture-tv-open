package org.zhixiaoce.aperture;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Returns through real HOME resolution first; never changes the default or targets our own home. */
final class SystemHomeReturn {
    static final class Result {
        final ComponentName component;
        final String failure;
        Result(ComponentName component,String failure){this.component=component;this.failure=failure;}
    }
    private static final class Route {
        final String name;
        final Intent intent;
        final ResolveInfo target;
        Route(String name,Intent intent,ResolveInfo target){this.name=name;this.intent=intent;this.target=target;}
    }
    private static ComponentName component(ResolveInfo info){return new ComponentName(info.activityInfo.packageName,info.activityInfo.name);}
    private static boolean usable(ResolveInfo info){return info!=null&&info.activityInfo!=null&&info.activityInfo.applicationInfo!=null&&info.activityInfo.exported&&info.activityInfo.enabled&&info.activityInfo.applicationInfo.enabled;}
    private static boolean own(Activity activity,ResolveInfo info){
        String pkg=info.activityInfo.packageName,target=info.activityInfo.targetActivity;
        return pkg.equals(activity.getPackageName())||pkg.equals("org.zhixiaoce.aperture")||pkg.equals("org.zhixiaoce.aperture.parents")||pkg.equals("org.zhixiaoce.kaishi")||pkg.equals("org.zhixiaoce.kaishi.parents")||(target!=null&&target.startsWith("org.zhixiaoce.aperture."));
    }
    private static Intent home(){return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);}
    private static void add(Activity activity,List<Route> routes,Set<String> seen,String name,Intent intent,ResolveInfo info){
        if(intent==null||!usable(info)||own(activity,info))return;
        // A chooser is not a home activity. Never accidentally reopen our alias through one.
        if("android".equals(info.activityInfo.packageName))return;
        Intent copy=new Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        String key=(copy.getComponent()==null?"implicit":copy.getComponent().flattenToString())+"/"+copy.getCategories();
        if(seen.add(key))routes.add(new Route(name,copy,info));
    }
    private static void addExplicit(Activity activity,List<Route> routes,Set<String> seen,ResolveInfo selected,List<ResolveInfo> homes){
        if(!usable(selected)||own(activity,selected))return;
        ComponentName target=component(selected);
        for(ResolveInfo info:homes)if(usable(info)&&target.equals(component(info))){
            add(activity,routes,seen,"explicit-home",home().setComponent(target),info);break;
        }
        // Only use the package manager's real launcher intents, preserving their category/flags.
        PackageManager pm=activity.getPackageManager();
        for(Intent intent:new Intent[]{pm.getLeanbackLaunchIntentForPackage(target.getPackageName()),pm.getLaunchIntentForPackage(target.getPackageName())}){
            if(intent!=null&&target.getPackageName().equals(intent.getComponent()==null?null:intent.getComponent().getPackageName()))
                add(activity,routes,seen,"package-launcher",intent,pm.resolveActivity(intent,0));
        }
    }
    static Result open(Activity activity,ResolveInfo selected,List<ResolveInfo> alternatives){
        List<Route> routes=new ArrayList<>();Set<String> seen=new HashSet<>();String failure="NoHomeEntry";
        PackageManager pm=activity.getPackageManager();
        try{
            Intent home=home();ResolveInfo current=pm.resolveActivity(home,PackageManager.MATCH_DEFAULT_ONLY);
            List<ResolveInfo> homes=pm.queryIntentActivities(home,0);
            // Preserve system dispatch when the original desktop is still the current default.
            // When we are default, this route is deliberately excluded to prevent a return loop.
            boolean concrete=false;
            if(usable(current))for(ResolveInfo candidate:homes)if(usable(candidate)&&component(candidate).equals(component(current))){concrete=true;break;}
            if(concrete)add(activity,routes,seen,"current-system-home",home,current);
            addExplicit(activity,routes,seen,selected,homes);
            for(ResolveInfo info:alternatives){
                if(!usable(info))continue;
                String pkg=info.activityInfo.packageName;
                if((pkg.equals("com.mitv.tvhome")||pkg.equals("com.xiaomi.mitv.tvhome"))&&
                    (info.activityInfo.applicationInfo.flags&(android.content.pm.ApplicationInfo.FLAG_SYSTEM|android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0)
                    addExplicit(activity,routes,seen,info,homes);
            }
        }catch(RuntimeException error){
            failure=error.getClass().getSimpleName();Log.w("KaishiHome","return-home discovery failed type="+failure);
        }
        for(Route route:routes){
            try{
                activity.startActivity(route.intent);
                ComponentName target=component(route.target);
                Log.i("KaishiHome","return-home started route="+route.name+" target="+target.flattenToShortString());
                return new Result(target,"");
            }catch(RuntimeException error){
                failure=error.getClass().getSimpleName();
                Log.w("KaishiHome","return-home failed route="+route.name+" target="+component(route.target).flattenToShortString()+" type="+failure);
            }
        }
        return new Result(null,failure);
    }
}
