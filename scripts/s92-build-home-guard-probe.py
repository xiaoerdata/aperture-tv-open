"""Isolated Android probe for production HomeGuardService; no playback or user data."""
from pathlib import Path
import os,subprocess,zipfile
r=Path(__file__).resolve().parents[1];out=r/'artifacts/s92-home-guard-native';out.mkdir(exist_ok=True)
sdk=r/'.android-local/android-sdk';jdk=r/'.android-local/jdk17';bt=sdk/'build-tools/35.0.0';jar=sdk/'platforms/android-35/android.jar'
for d in ['classes','dex','res/xml','src/org/zhixiaoce/aperture']:(out/d).mkdir(parents=True,exist_ok=True)
(out/'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.zhixiaoce.test.homeguard"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="28"/><application android:debuggable="true" android:theme="@android:style/Theme.Material.NoActionBar"><activity android:name="org.zhixiaoce.aperture.MainActivity" android:exported="true"/><service android:name="org.zhixiaoce.aperture.HomeGuardService" android:exported="true" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"><intent-filter><action android:name="android.accessibilityservice.AccessibilityService"/></intent-filter><meta-data android:name="android.accessibilityservice" android:resource="@xml/guard"/></service></application></manifest>''')
xml=(r/'android/app/src/main/res/xml/kaishi_home_guard.xml').read_text().replace('    android:description="@string/kaishi_home_guard_description"','')
(out/'res/xml/guard.xml').write_text(xml)
(out/'src/org/zhixiaoce/aperture/MainActivity.java').write_text('''package org.zhixiaoce.aperture;public class MainActivity extends android.app.Activity{private final android.os.Handler h=new android.os.Handler();public void onCreate(android.os.Bundle s){super.onCreate(s);HomeGuardService.setRequested(this,true);android.widget.TextView v=new android.widget.TextView(this);v.setText("Isolated guard probe");setContentView(v);h.postDelayed(()->{try{org.json.JSONObject j=new org.json.JSONObject();j.put("requested",HomeGuardService.requested(this));j.put("authorized",HomeGuardService.authorized(this));j.put("connected",HomeGuardService.connected());j.put("realXiaomi",false);try(java.io.FileOutputStream f=openFileOutput("result.json",0)){f.write(j.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}}catch(Exception e){throw new RuntimeException(e);}},1800);}}''')
def run(a):subprocess.run([str(x) for x in a],check=True,env=dict(os.environ,JAVA_HOME=str(jdk)))
sources=[r/'android/app/src/main/java/org/zhixiaoce/aperture'/n for n in ['HomeGuardService.java','HomeGuardPolicy.java']]+[out/'src/org/zhixiaoce/aperture/MainActivity.java']
run([jdk/'bin/javac','-cp',jar,'-d',out/'classes',*sources]);run([bt/'d8','--lib',jar,'--min-api','26','--output',out/'dex',*sorted((out/'classes').rglob('*.class'))]);run([bt/'aapt','package','-f','-M',out/'AndroidManifest.xml','-S',out/'res','-I',jar,'-F',out/'resources.apk'])
with zipfile.ZipFile(out/'resources.apk') as src,zipfile.ZipFile(out/'unsigned.apk','w') as dest:
 for name in src.namelist():dest.writestr(name,src.read(name))
 for dex in (out/'dex').glob('*.dex'):dest.write(dex,dex.name)
run([bt/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk']);run([bt/'apksigner','sign','--ks',r/'.android-local/android-user/debug.keystore','--ks-pass','pass:android','--out',out/'probe.apk',out/'aligned.apk'])
