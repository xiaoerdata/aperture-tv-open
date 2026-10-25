"""Build a small instrumentation driver; never part of the product APK or release assets."""
from pathlib import Path
import argparse, os, subprocess, zipfile, hashlib, json

parser = argparse.ArgumentParser()
parser.add_argument('--edition', required=True, choices=['standard', 'parents'])
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
runtime = Path(os.environ.get('APERTURE_ANDROID_RUNTIME', '/tmp/aperture-android-runtime'))
sdk = Path(os.environ.get('ANDROID_HOME', runtime / 'android-sdk'))
jdk = Path(os.environ.get('JAVA_HOME', runtime / 'jdk17'))
tools = sdk / 'build-tools/35.0.0'
out = root / 'artifacts/s50-plugin-instrumentation' / args.edition
out.mkdir(parents=True, exist_ok=True)
(out / 'classes').mkdir(exist_ok=True)
(out / 'dex').mkdir(exist_ok=True)
target = 'org.zhixiaoce.aperture' + ('.parents' if args.edition == 'parents' else '')
package = 'org.zhixiaoce.aperture.test.plugins.' + args.edition
(out / 'AndroidManifest.xml').write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="26"/>
<uses-permission android:name="android.permission.INTERNET"/>
<application android:label="开视开发测试驱动" android:usesCleartextTraffic="true"/>
<instrumentation android:name="org.zhixiaoce.aperture.test.PluginRunner" android:targetPackage="{target}" android:label="Native plugin runtime checks"/>
</manifest>''')
env = dict(os.environ, JAVA_HOME=str(jdk))
def run(command):
    subprocess.run([str(item) for item in command], cwd=root, env=env, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
android = sdk / 'platforms/android-35/android.jar'
run([jdk/'bin/javac', '-source', '8', '-target', '8', '-cp', android, '-d', out/'classes', root/'tests/android/PluginRunner.java'])
run([tools/'d8', '--lib', android, '--min-api', '26', '--output', out/'dex', *sorted((out/'classes').rglob('*.class'))])
run([tools/'aapt', 'package', '-f', '-M', out/'AndroidManifest.xml', '-I', android, '-F', out/'resources.apk'])
with zipfile.ZipFile(out/'resources.apk') as source, zipfile.ZipFile(out/'unsigned.apk', 'w') as apk:
    for name in source.namelist(): apk.writestr(name, source.read(name))
    for dex in (out/'dex').glob('*.dex'): apk.write(dex, dex.name)
run([tools/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk'])
run([tools/'apksigner','sign','--ks',runtime/'android-user/debug.keystore','--ks-pass','pass:android','--key-pass','pass:android','--out',out/'test.apk',out/'aligned.apk'])
run([tools/'apksigner','verify','--min-sdk-version','26',out/'test.apk'])
result = {'targetPackage':target,'instrumentation':package+'/org.zhixiaoce.aperture.test.PluginRunner','apk':str(out/'test.apk'),'sha256':hashlib.sha256((out/'test.apk').read_bytes()).hexdigest(),'shippedInProduct':False}
(out/'manifest.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
