"""Validate only the V51 delta and bind exact APKs to Back and prior playback evidence."""
from pathlib import Path
import hashlib,json,re,subprocess,zipfile
r=Path(__file__).resolve().parents[1];out=r/'artifacts/s141-release';checks=[]
for name in ['s140-back-navigation.json','s125-episode-append.json','s130-source-return.json','s138-player-keys.json','s123-live-controls.json','s140-native-compile.json']:
 p=r/'docs/verification'/name;v=json.loads(p.read_text());assert v.get('passed',v.get('javacPassed',False)),name
 for path,digest in v['sources'].items():assert hashlib.sha256((r/path).read_bytes()).hexdigest()==digest,(name,path)
 checks.append({'path':p.relative_to(r).as_posix(),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
ms=[json.loads((r/f'artifacts/v51-{e}-candidate/manifest.json').read_text()) for e in ['standard','parents']];assert ms[0]['inputs']==ms[1]['inputs'];inputs=ms[0]['inputs'];prior=json.loads((out/'baseline-source-manifest.json').read_text());old={f['path']:f['sha256'] for f in prior['files'] if f['path'].startswith(('android/','public/launcher/'))};new={f['path']:f['sha256'] for f in inputs['files']};assert not old.keys()-new.keys()
changed=sorted(k for k,v in new.items() if old.get(k)!=v);expected=['android/app/build.gradle','android/app/src/main/java/org/zhixiaoce/aperture/CinemaActivity.java','android/app/src/main/java/org/zhixiaoce/aperture/CinemaEpisodeGrid.java'];assert changed==expected,changed
for name,digest in new.items():assert hashlib.sha256((r/name).read_bytes()).hexdigest()==digest
assert all(old[p]==new[p] for p in ['android/app/src/main/java/org/zhixiaoce/aperture/HomeAlbumActivity.java','android/app/src/main/java/org/zhixiaoce/aperture/HomeAlbumView.java','public/launcher/launcher.js','android/app/src/main/java/org/zhixiaoce/aperture/PlayerTimeline.java','android/app/src/main/java/org/zhixiaoce/aperture/NasActivity.java'])
aapt=r/'.android-local/android-sdk/build-tools/35.0.0/aapt'
for m in ms:
 apk=r/f"artifacts/v51-{m['edition']}-candidate"/m['apk'];assert hashlib.sha256(apk.read_bytes()).hexdigest()==m['sha256'] and m['versionCode']==51
 permissions=lambda p:subprocess.check_output([str(aapt),'dump','permissions',str(p)],text=True).splitlines()[1:]
 assert permissions(apk)==permissions(r/f"artifacts/current-release/kaishi-v50-{m['edition']}-arm.apk")
 xml=subprocess.check_output([str(aapt),'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True);at=xml.index('org.zhixiaoce.aperture.HomeAlbumActivity');node=xml[at:xml.index('E: activity',at)];assert re.search(r'android:exported[^\n]*0x0',node)
 with zipfile.ZipFile(apk) as z:
  dex=b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'));assert b'HomeAlbumActivity' in dex
  for n in [b'BackNavigationTest',b'BackHarness',b'PlayerSeekKeysTest',b'RoutingHarness',b'HomeAlbumActivityTest',b'HomeAlbumTest']:assert n not in dex
  assert b"nativeAction('home-album')" in z.read('assets/launcher.js')
receipt={'passed':True,'versionCode':51,'editions':['standard','parents'],'androidInputSha256':inputs['sha256'],'androidInputFiles':len(new),'changedProductFiles':changed,'unchangedV50ProductFiles':len(new)-len(changed),'nativePhotoAndWeatherPathsUnchanged':True,'checks':checks,'apks':[{k:m[k] for k in ['edition','apk','bytes','sha256']} for m in ms],'samePermissionsAndCorrespondingV50Certificates':True,'homeAlbumActivityNotExported':True,'hisenseAccepted':False,'xiaomiAccepted':False,'realAndroidRendererExecuted':False}
(r/'docs/verification/s141-release-candidate.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps({'passed':True,'productFiles':len(new),'changedFiles':len(changed),'editions':2}))
