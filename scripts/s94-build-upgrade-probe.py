"""Build same-signature instrumentation for the two real x86 APKs in the owned emulator only."""
from pathlib import Path
import os,subprocess,zipfile,hashlib,json
r=Path(__file__).resolve().parents[1];out=r/'artifacts/s94-upgrade';out.mkdir(exist_ok=True);jdk=Path(os.environ['JAVA_HOME']);sdk=Path(os.environ['ANDROID_HOME']);bt=sdk/'build-tools/35.0.0';jar=sdk/'platforms/android-35/android.jar';prod=r/'android/app/build/intermediates/javac/standardRelease/compileStandardReleaseJavaWithJavac/classes'
for d in ['classes','dex']:(out/d).mkdir(exist_ok=True)
(out/'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.zhixiaoce.test.upgrade37"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="28"/><application/><instrumentation android:name="org.zhixiaoce.aperture.UpgradeRunner" android:targetPackage="org.zhixiaoce.kaishi"/></manifest>''')
def run(args):subprocess.run([str(x) for x in args],check=True)
run([jdk/'bin/javac','-cp',str(jar)+':'+str(prod),'-d',out/'classes',r/'tests/android/recovery37/UpgradeRunner.java']);run([bt/'d8','--lib',jar,'--min-api','26','--output',out/'dex',*sorted((out/'classes').rglob('*.class'))]);run([bt/'aapt','package','-f','-M',out/'AndroidManifest.xml','-I',jar,'-F',out/'resources.apk'])
with zipfile.ZipFile(out/'resources.apk') as src,zipfile.ZipFile(out/'unsigned.apk','w') as dest:
 for name in src.namelist():dest.writestr(name,src.read(name))
 for dex in (out/'dex').glob('*.dex'):dest.write(dex,dex.name)
run([bt/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk']);env=os.environ
run([bt/'apksigner','sign','--ks',env['APERTURE_RELEASE_KEYSTORE'],'--ks-key-alias',env['APERTURE_RELEASE_ALIAS'],'--ks-pass','file:'+env['APERTURE_RELEASE_STORE_PASSWORD_FILE'],'--key-pass','file:'+env['APERTURE_RELEASE_KEY_PASSWORD_FILE'],'--v4-signing-enabled','false','--out',out/'probe.apk',out/'aligned.apk'])
(out/'manifest.json').write_text(json.dumps({'targetPackage':'org.zhixiaoce.kaishi','sha256':hashlib.sha256((out/'probe.apk').read_bytes()).hexdigest(),'sameExistingCandidateIdentity':True,'syntheticDataOnly':True,'shippedInProduct':False},indent=2)+'\n')
