"""Build isolated production-Activity fault-injection probe. Never shipped in the TV candidate."""
from pathlib import Path
import os,subprocess,zipfile,hashlib,json
r=Path(__file__).resolve().parents[1];out=r/'artifacts/s94-native';sdk=r/'.android-local/android-sdk';jdk=r/'.android-local/jdk17';bt=sdk/'build-tools/35.0.0';jar=sdk/'platforms/android-35/android.jar';production=r/'android/app/build/intermediates/javac/standardRelease/compileStandardReleaseJavaWithJavac/classes'
for d in ['classes','dex']: (out/d).mkdir(parents=True,exist_ok=True)
(out/'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.zhixiaoce.test.recovery37"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="28"/><application android:debuggable="true" android:theme="@android:style/Theme.Material.NoActionBar"><activity android:name="org.zhixiaoce.aperture.RecoveryHomeProbe" android:exported="true" android:screenOrientation="landscape"/><activity android:name="org.zhixiaoce.aperture.RecoveryGalleryProbe" android:exported="true" android:screenOrientation="landscape"/></application><instrumentation android:name="org.zhixiaoce.aperture.RecoveryRunner" android:targetPackage="org.zhixiaoce.test.recovery37"/></manifest>''')
classpath=r/'artifacts/nas-classpath.txt'
if not classpath.is_file():
 env=dict(os.environ,JAVA_HOME=str(jdk),ANDROID_HOME=str(sdk),GRADLE_USER_HOME=str(r/'.android-local/gradle-user'))
 subprocess.run([str(r/'.android-local/gradle-8.9/bin/gradle'),'--offline','--no-daemon','--max-workers=2','-p',str(r/'android'),':app:nasClasspath','-PnasClasspathFile='+str(classpath)],env=env,check=True)
libs=[Path(s) for s in classpath.read_text().strip().split(':')];assert all(p.is_file() for p in libs)
def run(args):subprocess.run([str(s) for s in args],cwd=r,env=dict(os.environ,JAVA_HOME=str(jdk)),check=True)
run([jdk/'bin/javac','-cp',str(jar)+':'+str(production),'-d',out/'classes',*[p for p in sorted((r/'tests/android/recovery37').glob('*.java')) if p.name!='UpgradeRunner.java']])
run([bt/'d8','--lib',jar,'--min-api','26','--output',out/'dex',*sorted(production.rglob('*.class')),*sorted((out/'classes').rglob('*.class')),*libs])
run([bt/'aapt','package','-f','-M',out/'AndroidManifest.xml','-A',r/'public/launcher','-I',jar,'-F',out/'resources.apk'])
with zipfile.ZipFile(out/'resources.apk') as src,zipfile.ZipFile(out/'unsigned.apk','w') as dest:
 for name in src.namelist():dest.writestr(name,src.read(name))
 for dex in (out/'dex').glob('*.dex'):dest.write(dex,dex.name)
run([bt/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk']);run([bt/'apksigner','sign','--ks',r/'.android-local/android-user/debug.keystore','--ks-pass','pass:android','--out',out/'probe.apk',out/'aligned.apk'])
(out/'probe-manifest.json').write_text(json.dumps({'package':'org.zhixiaoce.test.recovery37','sha256':hashlib.sha256((out/'probe.apk').read_bytes()).hexdigest(),'controlledWebView':True,'shippedInCandidate':False,'networkPermission':False},indent=2)+'\n')
