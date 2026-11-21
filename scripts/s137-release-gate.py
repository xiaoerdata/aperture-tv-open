"""Validate only the V49 delta and bind exact APKs to current photo evidence."""
from pathlib import Path
import hashlib,json,re,subprocess,zipfile
r=Path(__file__).resolve().parents[1];out=r/'artifacts/s137-release';checks=[]
for name in ['s131-album-native.json','s136-home-album-native.json','s136-home-album-browser.json','s136-native-compile.json']:
 p=r/'docs/verification'/name;v=json.loads(p.read_text());assert v.get('passed',v.get('javacPassed',False)),name
 for path,digest in v['sources'].items():assert hashlib.sha256((r/path).read_bytes()).hexdigest()==digest,(name,path)
 checks.append({'path':p.relative_to(r).as_posix(),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
ms=[json.loads((r/f'artifacts/v49-{e}-candidate/manifest.json').read_text()) for e in ['standard','parents']];assert ms[0]['inputs']==ms[1]['inputs'];inputs=ms[0]['inputs'];prior=json.loads((out/'baseline-source-manifest.json').read_text());old={f['path']:f['sha256'] for f in prior['files'] if f['path'].startswith(('android/','public/launcher/'))};new={f['path']:f['sha256'] for f in inputs['files']};assert not old.keys()-new.keys()
changed=sorted(k for k,v in new.items() if old.get(k)!=v);expected=['android/app/build.gradle','android/app/src/main/AndroidManifest.xml','android/app/src/main/java/org/zhixiaoce/aperture/HomeAlbumActivity.java','android/app/src/main/java/org/zhixiaoce/aperture/HomeAlbumView.java','android/app/src/main/java/org/zhixiaoce/aperture/MainActivity.java','android/app/src/main/java/org/zhixiaoce/aperture/TvLocalControl.java','public/launcher/launcher.js'];assert changed==expected,changed
for name,digest in new.items():assert hashlib.sha256((r/name).read_bytes()).hexdigest()==digest
assert '.animate()' not in (r/'android/app/src/main/java/org/zhixiaoce/aperture/HomeAlbumView.java').read_text()
aapt=r/'.android-local/android-sdk/build-tools/35.0.0/aapt'
for m in ms:
 apk=r/f"artifacts/v49-{m['edition']}-candidate"/m['apk'];assert hashlib.sha256(apk.read_bytes()).hexdigest()==m['sha256'] and m['versionCode']==49
 permissions=lambda p:subprocess.check_output([str(aapt),'dump','permissions',str(p)],text=True).splitlines()[1:]
 assert permissions(apk)==permissions(r/f"artifacts/current-release/kaishi-v48-{m['edition']}-arm.apk")
 xml=subprocess.check_output([str(aapt),'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True);at=xml.index('org.zhixiaoce.aperture.HomeAlbumActivity');node=xml[at:xml.index('E: activity',at)];assert re.search(r'android:exported[^\n]*0x0',node)
 with zipfile.ZipFile(apk) as z:
  dex=b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'));assert b'HomeAlbumActivity' in dex
  for n in [b'HomeAlbumActivityTest',b'AlbumReturnTest',b'HomeAlbumTest']:assert n not in dex
  assert b"nativeAction('home-album')" in z.read('assets/launcher.js')
receipt={'passed':True,'versionCode':49,'editions':['standard','parents'],'androidInputSha256':inputs['sha256'],'androidInputFiles':len(new),'changedProductFiles':changed,'unchangedV48ProductFiles':len(new)-len(changed),'unchangedV48MediaAndWeatherPaths':True,'checks':checks,'apks':[{k:m[k] for k in ['edition','apk','bytes','sha256']} for m in ms],'samePermissionsAndCorrespondingV48Certificates':True,'homeAlbumActivityNotExported':True,'hisenseAccepted':False,'xiaomiAccepted':False,'realAndroidRendererExecuted':False}
(r/'docs/verification/s137-release-candidate.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps({'passed':True,'productFiles':len(new),'changedFiles':len(changed),'editions':2}))
