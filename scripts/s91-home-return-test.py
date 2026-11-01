"""Execute the production SystemHomeReturn against minimal Android boundary doubles."""
from pathlib import Path
import json, os, subprocess, tempfile

root=Path(__file__).resolve().parents[1]
jdk=Path(os.environ.get('JAVA_HOME',root/'.android-local/jdk17'))
stubs={
'android/content/ComponentName.java':'''package android.content;public final class ComponentName{private final String p,c;public ComponentName(String p,String c){this.p=p;this.c=c;}public String getPackageName(){return p;}public String flattenToString(){return p+"/"+c;}public String flattenToShortString(){return flattenToString();}public boolean equals(Object o){return o instanceof ComponentName&&p.equals(((ComponentName)o).p)&&c.equals(((ComponentName)o).c);}public int hashCode(){return java.util.Objects.hash(p,c);}}''',
'android/content/Intent.java':'''package android.content;public class Intent{public static final String ACTION_MAIN="android.intent.action.MAIN",CATEGORY_HOME="android.intent.category.HOME",CATEGORY_LEANBACK_LAUNCHER="android.intent.category.LEANBACK_LAUNCHER";public static final int FLAG_ACTIVITY_NEW_TASK=0x10000000,FLAG_ACTIVITY_RESET_TASK_IF_NEEDED=0x00200000,FLAG_ACTIVITY_CLEAR_TOP=0x04000000;private ComponentName c;private int flags;private java.util.Set<String> categories=new java.util.LinkedHashSet<>();public Intent(String a){}public Intent(Intent i){c=i.c;flags=i.flags;categories.addAll(i.categories);}public Intent addCategory(String s){categories.add(s);return this;}public java.util.Set<String> getCategories(){return categories;}public Intent addFlags(int f){flags|=f;return this;}public int getFlags(){return flags;}public Intent setComponent(ComponentName v){c=v;return this;}public ComponentName getComponent(){return c;}}''',
'android/content/pm/ApplicationInfo.java':'''package android.content.pm;public class ApplicationInfo{public static final int FLAG_SYSTEM=1,FLAG_UPDATED_SYSTEM_APP=128;public boolean enabled;public int flags;}''',
'android/content/pm/ActivityInfo.java':'''package android.content.pm;public class ActivityInfo{public ApplicationInfo applicationInfo;public String packageName,name,targetActivity;public boolean exported,enabled;}''',
'android/content/pm/ResolveInfo.java':'''package android.content.pm;public class ResolveInfo{public ActivityInfo activityInfo;}''',
'android/content/pm/PackageManager.java':'''package android.content.pm;import android.content.Intent;public abstract class PackageManager{public static final int MATCH_DEFAULT_ONLY=0x10000;public abstract ResolveInfo resolveActivity(Intent i,int f);public abstract java.util.List<ResolveInfo> queryIntentActivities(Intent i,int f);public abstract Intent getLeanbackLaunchIntentForPackage(String p);public abstract Intent getLaunchIntentForPackage(String p);}''',
'android/app/Activity.java':'''package android.app;import android.content.*;import android.content.pm.PackageManager;public abstract class Activity{public abstract PackageManager getPackageManager();public abstract String getPackageName();public abstract void startActivity(Intent i);}''',
'android/util/Log.java':'''package android.util;public final class Log{public static int i(String t,String m){return 0;}public static int w(String t,String m){return 0;}}'''
}
with tempfile.TemporaryDirectory(prefix='kaishi-home-return-') as temp:
    out=Path(temp);sources=[]
    for name,content in stubs.items():
        path=out/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content);sources.append(str(path))
    sources += [str(root/'android/app/src/main/java/org/zhixiaoce/aperture/SystemHomeReturn.java'),str(root/'tests/java/SystemHomeReturnTest.java')]
    subprocess.run([str(jdk/'bin/javac'),'-d',str(out/'classes'),*sources],check=True)
    result=subprocess.check_output([str(jdk/'bin/java'),'-cp',str(out/'classes'),'org.zhixiaoce.aperture.SystemHomeReturnTest'],text=True)
    (root/'docs/verification/s91-home-return.json').write_text(json.dumps({'productionRoutingCode':True,'androidBoundaryDoubles':True,'realXiaomi':False,'output':result.strip()},indent=2)+'\n')
    print(result.strip())
