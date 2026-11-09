"""Run the actual installed-app discovery against a small PackageManager double."""
from pathlib import Path
import hashlib,json,subprocess,tempfile
root=Path(__file__).resolve().parents[1];jdk=root/'.android-local/jdk17/bin'
stubs={
 'android/content/Intent.java':'''package android.content;public class Intent {public static final String ACTION_MAIN="android.intent.action.MAIN",CATEGORY_LEANBACK_LAUNCHER="android.intent.category.LEANBACK_LAUNCHER",CATEGORY_LAUNCHER="android.intent.category.LAUNCHER";public String category;public Intent(String action){}public Intent addCategory(String value){category=value;return this;}}''',
 'android/content/Context.java':'''package android.content;public class Context {private final android.content.pm.PackageManager pm;private final String name;public Context(android.content.pm.PackageManager pm,String name){this.pm=pm;this.name=name;}public android.content.pm.PackageManager getPackageManager(){return pm;}public String getPackageName(){return name;}}''',
 'android/content/pm/ApplicationInfo.java':'''package android.content.pm;public class ApplicationInfo {public boolean enabled;}''',
 'android/content/pm/ActivityInfo.java':'''package android.content.pm;public class ActivityInfo {public String packageName,name;public boolean exported,enabled;public ApplicationInfo applicationInfo;}''',
 'android/content/pm/ResolveInfo.java':'''package android.content.pm;public class ResolveInfo {public ActivityInfo activityInfo;public String label;public CharSequence loadLabel(PackageManager pm){return label;}}''',
 'android/content/pm/PackageManager.java':'''package android.content.pm;import java.util.*;import android.content.Intent;public class PackageManager {public final List<ResolveInfo> tv=new ArrayList<>(),regular=new ArrayList<>();public final List<String> calls=new ArrayList<>();public boolean failTv,failRegular;public List<ResolveInfo> queryIntentActivities(Intent intent,int flags){calls.add(intent.category);boolean isTv=Intent.CATEGORY_LEANBACK_LAUNCHER.equals(intent.category);if(isTv?failTv:failRegular)throw new SecurityException("synthetic firmware restriction");return isTv?tv:regular;}}''',
}
source=root/'android/app/src/main/java/org/zhixiaoce/aperture/InstalledApps.java';test=root/'tests/java/InstalledAppsTest.java'
with tempfile.TemporaryDirectory(prefix='kaishi-s113-apps-') as directory:
 temp=Path(directory);files=[]
 for name,body in stubs.items():
  p=temp/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body);files.append(p)
 subprocess.run([str(jdk/'javac'),'-encoding','UTF-8','-d',str(temp/'classes'),*[str(p) for p in [*files,source,test]]],cwd=root,check=True)
 result=subprocess.run([str(jdk/'java'),'-cp',str(temp/'classes'),'org.zhixiaoce.aperture.InstalledAppsTest'],cwd=root,check=True,capture_output=True,text=True)
 receipt={'passed':True,'output':result.stdout.strip(),'productionDiscoveryExecuted':True,'packageManagerDouble':True,'realAndroidExecuted':False,'realXiaomi':False,'sources':{str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [source,test,Path(__file__).resolve()]}}
 (root/'docs/verification/s113-installed-apps.json').write_text(json.dumps(receipt,indent=2)+'\n');print(result.stdout.strip())
