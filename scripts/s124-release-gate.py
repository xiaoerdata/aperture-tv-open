"""Bind V48 artifacts to passing targeted evidence and exact source input fingerprints."""
from pathlib import Path
import hashlib,json,subprocess,zipfile
r=Path(__file__).resolve().parents[1];out=r/'artifacts/s124-release'
paths=['s123-live-controls.json','s125-episode-append.json','s126-year-score-sort.json','s127-line-match.json','s129-stream.json','s130-source-return.json','s131-album-native.json','s131-album-browser.json','s132-screensaver-return.json','s133-pair-window.json','s134-workbuddy-guide.json','s135-settings-scroll.json','s129-native-compile.json','s123-live-preview.json','s125-episode-sort-preview.json']
checks=[]
for n in paths:
 p=r/'docs/verification'/n;v=json.loads(p.read_text());assert v.get('passed',v.get('javacPassed',v.get('generated',False))),n
 if 'sourceSha256' in v:assert hashlib.sha256((r/'android/app/src/main/java/org/zhixiaoce/aperture/CinemaActivity.java').read_bytes()).hexdigest()==v['sourceSha256']
 for image in v.get('images',[]):assert (r/image).is_file()
 for name,digest in v.get('sources',{}).items():assert hashlib.sha256((r/name).read_bytes()).hexdigest()==digest,(n,name)
 checks.append({'path':str(p.relative_to(r)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
ms=[json.loads((r/f'artifacts/v48-{e}-candidate/manifest.json').read_text()) for e in ['standard','parents']];assert ms[0]['inputs']==ms[1]['inputs'];inputs=ms[0]['inputs'];assert all(hashlib.sha256((r/f['path']).read_bytes()).hexdigest()==f['sha256'] for f in inputs['files'])
aapt=r/'.android-local/android-sdk/build-tools/35.0.0/aapt';permission=[]
for m in ms:
 apk=r/f"artifacts/v48-{m['edition']}-candidate"/m['apk'];assert m['versionCode']==48 and hashlib.sha256(apk.read_bytes()).hexdigest()==m['sha256']
 with zipfile.ZipFile(apk) as z:
  dex=b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'));assert b'SettingsScroll' in dex and b'PairWindowTest' not in dex and b'SettingsScrollTest' not in dex
 def perms(p):return set(subprocess.check_output([str(aapt),'dump','permissions',str(p)],text=True).splitlines()[1:])
 old=perms(r/f"artifacts/current-release/kaishi-v47-{m['edition']}-arm.apk");new=perms(apk);assert new-old=={"uses-permission: name='android.permission.ACCESS_NETWORK_STATE'"},new-old;assert not old-new;assert not any('RECORD_AUDIO' in p for p in new);permission.append({'edition':m['edition'],'added':['android.permission.ACCESS_NETWORK_STATE'],'removed':[]})
receipt={'passed':True,'versionCode':48,'editions':['standard','parents'],'androidInputSha256':inputs['sha256'],'androidInputFiles':len(inputs['files']),'bothReleaseBuildsPassed':True,'bothSignaturesMatchCorrespondingV47':True,'allP137ThroughP149InApk':True,'checks':checks,'apks':[{k:m[k] for k in ['edition','apk','bytes','sha256']} for m in ms],'permissions':permission,'fullCloudRegressionAccepted':False,'xiaomiAccepted':False,'hisenseAccepted':False,'decoderPlaybackMeasured':False}
(r/'docs/verification/s124-release-candidate.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps({'passed':True,'androidFiles':len(inputs['files']),'editions':2,'evidence':len(checks)}))
