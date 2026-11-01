from pathlib import Path
import os,subprocess,zipfile
r=Path(__file__).resolve().parents[1];out=r/'artifacts/s92-ui-native';out.mkdir(exist_ok=True);sdk=r/'.android-local/android-sdk';jdk=r/'.android-local/jdk17';tools=sdk/'build-tools/35.0.0';jar=sdk/'platforms/android-35/android.jar';classes=r/'android/app/build/intermediates/javac/standardRelease/compileStandardReleaseJavaWithJavac/classes'
for d in ['classes','dex']:(out/d).mkdir(exist_ok=True)
(out/'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.zhixiaoce.test.albumui"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="28"/><uses-permission android:name="android.permission.INTERNET"/><application android:debuggable="true" android:theme="@android:style/Theme.Material.NoActionBar"><activity android:name="org.zhixiaoce.aperture.AlbumUiProbe" android:exported="true" android:screenOrientation="landscape"/></application></manifest>''')
def run(a):subprocess.run([str(x) for x in a],check=True,env=dict(os.environ,JAVA_HOME=str(jdk)))
run([jdk/'bin/javac','-cp',str(jar)+':'+str(classes),'-d',out/'classes',r/'tests/android/AlbumUiProbe.java']);run([tools/'d8','--lib',jar,'--min-api','26','--output',out/'dex',*sorted(classes.rglob('*.class')),*sorted((out/'classes').rglob('*.class'))]);run([tools/'aapt','package','-f','-M',out/'AndroidManifest.xml','-I',jar,'-F',out/'resources.apk'])
with zipfile.ZipFile(out/'resources.apk') as src,zipfile.ZipFile(out/'unsigned.apk','w') as dest:
 for name in src.namelist():dest.writestr(name,src.read(name))
 for dex in (out/'dex').glob('*.dex'):dest.write(dex,dex.name)
run([tools/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk']);run([tools/'apksigner','sign','--ks',r/'.android-local/android-user/debug.keystore','--ks-pass','pass:android','--out',out/'probe.apk',out/'aligned.apk'])
