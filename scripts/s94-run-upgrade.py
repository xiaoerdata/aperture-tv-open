"""Real package-manager v36 to v37 upgrade in the dedicated fresh-data emulator. No user devices."""
from pathlib import Path
import subprocess,json,time,re
r=Path(__file__).resolve().parents[1];out=r/'artifacts/s94-upgrade';owned=json.loads((out/'owned.json').read_text());assert owned['serial']=='emulator-5584' and owned['readOnlyAvd'] and (owned.get('customDataCopy') or owned.get('runtimeOverlay'));base=[str(r/'.android-local/android-sdk/platform-tools/adb'),'-s',owned['serial']]
def adb(*args,timeout=40):return subprocess.check_output(base+list(args),stderr=subprocess.STDOUT,timeout=timeout).decode(errors='replace')
def installed():
 p=subprocess.run(base+['shell','pm','path','org.zhixiaoce.kaishi'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=30)
 text=p.stdout.decode(errors='replace').strip()
 if p.returncode==0:return text
 if p.returncode==1 and not text:return ''
 raise RuntimeError('Could not query isolated package presence')
end=time.monotonic()+600
while time.monotonic()<end:
 try:
  if adb('shell','getprop','sys.boot_completed',timeout=15).strip()=='1':break
 except (subprocess.TimeoutExpired,subprocess.CalledProcessError):pass
 time.sleep(2)
else:raise RuntimeError('Fresh-data emulator did not boot')
existing=installed()
if existing:
 assert owned.get('runtimeOverlay') and owned.get('fixtureRemovalInRuntimeOnly')
 dump=adb('shell','dumpsys','package','org.zhixiaoce.kaishi');assert 'versionCode=32 ' in dump
 expected=json.loads((out/'existing-package.json').read_text());assert not expected['sameCertificate']
 text=adb('uninstall','org.zhixiaoce.kaishi',timeout=90);(out/'remove-v32-fixture-in-runtime-overlay.log').write_text(text);assert 'Success' in text
 assert not installed()
versions={}
for folder,phase in [('s94-v36-baseline','seed'),('s94-v37-x86','verify')]:
 m=json.loads((r/'artifacts'/folder/'manifest.json').read_text());apk=r/'artifacts'/folder/m['apk'];text=adb('install','--no-streaming','-r',str(apk),timeout=240);(out/('install-'+phase+'.log')).write_text(text);assert 'Success' in text
 if phase=='seed':
  text=adb('install','--no-streaming','-r',str(out/'probe.apk'),timeout=150);(out/'instrumentation-install.log').write_text(text);assert 'Success' in text
 text=adb('shell','am','instrument','-w','-e','phase',phase,'org.zhixiaoce.test.upgrade37/org.zhixiaoce.aperture.UpgradeRunner',timeout=240);(out/(phase+'.log')).write_text(text)
 match=re.search(r'^INSTRUMENTATION_RESULT: result=(.+)$',text,re.M);assert match,text[-1000:];d=json.loads(match[1]);assert d['ok'],d;versions[phase]={'result':d,'apkSha256':m['sha256'],'versionCode':m['versionCode'],'certificateSha256':m['certificateSha256']}
 print('Real x86 APK '+phase+' verified',flush=True)
assert versions['seed']['certificateSha256']==versions['verify']['certificateSha256']
result={'ok':True,'platform':'Android28 x86 task-owned read-only runtime overlay','actualProductApks':True,'upgrade':'36 -> 37','results':versions,'userNetworkOrPhotosAccessed':False,'realXiaomi':False,'nasCredentialsTested':False,'defaultHomeChanged':False,'productionSecurityOrDisplaySettingsChanged':False,'fixtureV32RemovedOnlyInDiscardableOverlay':bool(owned.get('runtimeOverlay'))}
(r/'docs/verification/s94-upgrade.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print('Actual v36-to-v37 configuration/photo upgrade checks passed',flush=True)
