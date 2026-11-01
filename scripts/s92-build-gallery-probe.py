"""Small diagnostic APK: production Android gallery store/upload, isolated from user files."""
from pathlib import Path
import os,shutil,subprocess,zipfile
r=Path(__file__).resolve().parents[1];out=r/'artifacts/s92-gallery-native';out.mkdir(exist_ok=True);sdk=r/'.android-local/android-sdk';jdk=r/'.android-local/jdk17';tools=sdk/'build-tools/35.0.0';jar=sdk/'platforms/android-35/android.jar'
for d in ['classes','dex','assets','src']:(out/d).mkdir(exist_ok=True)
shutil.copyfile(r/'public/launcher/gallery-upload.html',out/'assets/gallery-upload.html')
(out/'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.zhixiaoce.test.gallery"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="28"/><uses-permission android:name="android.permission.INTERNET"/><application android:debuggable="true" android:usesCleartextTraffic="true" android:theme="@android:style/Theme.Material.NoActionBar"><activity android:name="org.zhixiaoce.aperture.GalleryProbe" android:exported="true" android:screenOrientation="landscape"/></application></manifest>''')
(out/'src/LobbyAssets.java').write_text('package org.zhixiaoce.aperture;final class LobbyAssets{static final String HOST="appassets.androidplatform.net";}')
(out/'src/TvLocalControl.java').write_text('package org.zhixiaoce.aperture;final class TvLocalControl{static boolean privatePeer(java.net.InetAddress a){return a.isLoopbackAddress()||a.isSiteLocalAddress();}}')
def run(a):subprocess.run([str(x) for x in a],check=True,env=dict(os.environ,JAVA_HOME=str(jdk)))
run([jdk/'bin/javac','-source','8','-target','8','-cp',jar,'-d',out/'classes',*sorted((out/'src').glob('*.java')),r/'tests/android/GalleryProbe.java',r/'android/app/src/main/java/org/zhixiaoce/aperture/GalleryStore.java',r/'android/app/src/main/java/org/zhixiaoce/aperture/GalleryUpload.java'])
run([tools/'d8','--lib',jar,'--min-api','26','--output',out/'dex',*sorted((out/'classes').rglob('*.class'))]);run([tools/'aapt','package','-f','-M',out/'AndroidManifest.xml','-A',out/'assets','-I',jar,'-F',out/'resources.apk'])
with zipfile.ZipFile(out/'resources.apk') as src,zipfile.ZipFile(out/'unsigned.apk','w') as dest:
 for name in src.namelist():dest.writestr(name,src.read(name))
 for dex in (out/'dex').glob('*.dex'):dest.write(dex,dex.name)
run([tools/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk']);run([tools/'apksigner','sign','--ks',r/'.android-local/android-user/debug.keystore','--ks-pass','pass:android','--out',out/'probe.apk',out/'aligned.apk'])
