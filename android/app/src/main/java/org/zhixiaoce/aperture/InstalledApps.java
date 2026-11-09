package org.zhixiaoce.aperture;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import java.util.*;

/** Discover real launchable applications; a brand registry is never an inventory. */
final class InstalledApps {
    static List<ResolveInfo> read(Context context){
        Map<String,ResolveInfo> result=new LinkedHashMap<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            List<ResolveInfo> found;
            try{found=context.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0);}catch(RuntimeException restricted){continue;}
            if(found==null)continue;
            for(ResolveInfo app:found){
                if(app==null||app.activityInfo==null||app.activityInfo.applicationInfo==null||!app.activityInfo.exported||!app.activityInfo.enabled||!app.activityInfo.applicationInfo.enabled)continue;
                String pkg=app.activityInfo.packageName,name=app.activityInfo.name;
                if(pkg==null||name==null||pkg.equals(context.getPackageName()))continue;
                result.putIfAbsent(pkg+"/"+name,app);
            }
        }
        List<ResolveInfo> apps=new ArrayList<>(result.values());apps.sort((a,b)->a.loadLabel(context.getPackageManager()).toString().compareTo(b.loadLabel(context.getPackageManager()).toString()));return apps;
    }
}
