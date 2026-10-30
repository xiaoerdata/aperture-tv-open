"""Start an isolated emulator and run system-input checks; never take over a connected device."""
from pathlib import Path
import subprocess,os,json,time,hashlib,shutil
root=Path(__file__).resolve().parents[1];out=root/'artifacts/s74-native';out.mkdir(exist_ok=True)
env=dict(os.environ,APERTURE_ANDROID_RUNTIME=str(root/'.android-local'),JAVA_HOME=str(root/'.android-local/jdk17'),ANDROID_HOME=str(root/'.android-local/android-sdk'),ANDROID_USER_HOME=str(root/'.android-local/android-user'))
adb=root/'.android-local/android-sdk/platform-tools/adb';serial='emulator-5568'
assert serial.encode() not in subprocess.check_output([str(adb),'devices']), 'Do not take over an existing emulator'
env.update(ANDROID_I_WANT_MY_TCG='yes',ANDROID_AVD_HOME=str(root/'.android-local/avd'))
emulator=subprocess.Popen([str(root/'.android-local/android-sdk/emulator/emulator'),'-avd','kaishi-s53-tv28','-port','5568','-no-window','-no-audio','-no-snapshot','-accel','off','-gpu','swangle','-feature','-Vulkan,-GLAsyncSwap,-GLESDynamicVersion','-read-only','-memory','2048','-cores','4','-no-boot-anim'],cwd=root,env=env,stdout=(out/'emulator.log').open('wb'),stderr=subprocess.STDOUT)
(out/'owned.json').write_text(json.dumps({'pid':emulator.pid,'serial':serial}))
def run(args,timeout=60):
 try:return subprocess.check_output([str(x) for x in args],cwd=root,env=env,stderr=subprocess.STDOUT,timeout=timeout)
 except subprocess.CalledProcessError as e:
  print(e.output.decode(errors='replace'),flush=True);raise
def ad(*args,timeout=30):return run([adb,'-s',serial,*args],timeout)
fixture=None
try:
 end=time.time()+1000
 while time.time()<end:
  try:
   boot=ad('shell','getprop','sys.boot_completed',timeout=15).strip()==b'1'
   if boot or (b'package:' in ad('shell','pm','path','android',timeout=15) and b'found' in ad('shell','service','check','activity',timeout=15)):
    (out/'readiness.json').write_text(json.dumps({'sysBootCompleted':boot,'packageAndActivityServicesReady':True,'fullSystemBootAcceptance':False}));break
  except (subprocess.CalledProcessError,subprocess.TimeoutExpired):pass
  time.sleep(2)
 else:raise RuntimeError('emulator not ready')
 print('emulator services ready',flush=True)
 end=time.time()+900
 while 'BUILD SUCCESSFUL' not in (root/'artifacts/s74-debug-build-complete.log').read_text():
  if time.time()>end:raise RuntimeError('build not ready')
  time.sleep(2)
 fixture=subprocess.Popen(['node','scripts/s74-media-fixture.mjs'],cwd=root,env=env,stdout=(out/'fixture.log').open('wb'),stderr=subprocess.STDOUT)
 time.sleep(1);base=json.loads((out/'fixture.json').read_text())['base'];reports=[]
 for edition in ['standard','parents']:
  print('install',edition,flush=True);package='org.zhixiaoce.kaishi'+('.parents' if edition=='parents' else '');apk=root/f'android/app/build/outputs/apk/{edition}/debug/app-{edition}-debug.apk';sha=hashlib.sha256(apk.read_bytes()).hexdigest()
  ad('install','--no-streaming','-r',apk,timeout=600)
  manifest=json.loads(run(['python3','scripts/build-revision-emulator-tests.py','--suite','remote','--edition',edition],timeout=120));ad('install','--no-streaming','-r',manifest['apk'],timeout=180)
  d=out/edition;d.mkdir(exist_ok=True);print('system-input regression',edition,flush=True)
  with (d/'instrument.log').open('wb') as stream:subprocess.run([str(adb),'-s',serial,'shell','am','instrument','-w','-e','fixture',base,manifest['instrumentation']],cwd=root,env=env,stdout=stream,stderr=subprocess.STDOUT,timeout=750,check=True)
  report=json.loads(ad('exec-out','run-as',package,'cat','files/s74-report.json'));report['debugApkSha256']=sha;report['edition']=edition;reports.append(report);(d/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(report,ensure_ascii=False),flush=True)
  for name in report['images']:
   target=root/f'docs/images/s74-native-{edition}-{name}.png';target.write_bytes(ad('exec-out','run-as',package,'cat',f'files/s74-{name}.png',timeout=60))
 (root/'docs/verification/s74-native.json').write_text(json.dumps({'passed':all(r['passed'] for r in reports),'reports':reports},ensure_ascii=False,indent=2)+'\n')
 if not all(r['passed'] for r in reports):raise AssertionError('system-input regression failed')
finally:
 if fixture:
  fixture.terminate()
  try:fixture.wait(timeout=10)
  except subprocess.TimeoutExpired:fixture.kill()
 try:ad('emu','kill',timeout=10)
 except Exception:emulator.terminate()
 try:emulator.wait(timeout=10)
 except subprocess.TimeoutExpired:emulator.kill()
