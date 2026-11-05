"""Compile current standard-edition Java against cached APIs, without Gradle/APK."""
from pathlib import Path
import hashlib, json, os, re, subprocess, tempfile

root=Path(__file__).resolve().parents[1]
jdk=root/'.android-local/jdk17/bin'
android=root/'.android-local/android-sdk/platforms/android-35/android.jar'
source=list(sorted((root/'android/app/src/main/java').rglob('*.java')))
libs=list(sorted((root/'.android-local/gradle-user/caches/modules-2/files-2.1').rglob('*.jar')))
version=(root/'android/app/build.gradle').read_text()
code=int(re.search(r'versionCode (\d+)',version)[1])
name=re.search(r"versionName '([^']+)'",version)[1]
out=root/os.environ.get('KAISHI_NATIVE_OUTPUT','artifacts/s101-home/native-compile');out.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(prefix='kaishi-s101-javac-') as directory:
    temp=Path(directory);config=temp/'BuildConfig.java';classes=temp/'classes'
    # Only generated Android build constants are replaced; product classes are real.
    config.write_text('package org.zhixiaoce.aperture; public final class BuildConfig {'
        'public static final boolean DEBUG=false,PARENTS_EDITION=false;'
        f'public static final int VERSION_CODE={code};public static final String VERSION_NAME="{name}";}}')
    result=subprocess.run([str(jdk/'javac'),'-encoding','UTF-8','-cp',':'.join(str(p) for p in [android,*libs]),'-d',str(classes),str(config),*[str(p) for p in source]],capture_output=True,text=True)
    (out/'javac-final.log').write_text(result.stdout+result.stderr)
    assert result.returncode==0,result.stderr
    subprocess.run([str(jdk/'javac'),'-cp',str(classes),'-d',str(classes),str(root/'tests/java/TvScaleTest.java')],check=True)
    scale=subprocess.check_output([str(jdk/'java'),'-cp',str(classes),'org.zhixiaoce.aperture.TvScaleTest'],text=True)
    receipt={'javacPassed':True,'sourceCount':len(source),'scaleTest':scale.strip(),
        'standardEdition':True,'generatedBuildConstants':True,'apkBuilt':False,'nativeActivityExecuted':False,'realXiaomi':False,
        'sources':{str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in source}}
    (root/os.environ.get('KAISHI_NATIVE_RECEIPT','docs/verification/s101-native-compile.json')).write_text(json.dumps(receipt,indent=2)+'\n')
    print(f'{len(source)} Android source files compiled; {scale.strip()}')
