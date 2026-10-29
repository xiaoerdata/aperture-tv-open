"""Owned local emulator + synthetic fixture regression. Stops only processes it starts."""
from pathlib import Path
import subprocess, os, json, time, shutil, hashlib
root=Path(__file__).resolve().parents[1];out=root/'artifacts/s70-native';out.mkdir(exist_ok=True,parents=True)
env=dict(os.environ,ANDROID_I_WANT_MY_TCG="yes",APERTURE_ANDROID_RUNTIME=str(root/'.android-local'),JAVA_HOME=str(root/'.android-local/jdk17'),ANDROID_HOME=str(root/'.android-local/android-sdk'),ANDROID_AVD_HOME=str(root/'.android-local/avd'),ANDROID_USER_HOME=str(root/'.android-local/android-user'))
adb=str(root/'.android-local/android-sdk/platform-tools/adb');serial='emulator-5568'
def run(args,timeout=120):return subprocess.check_output([str(x) for x in args],cwd=root,env=env,stderr=subprocess.STDOUT,timeout=timeout)
def ad(*args,timeout=120):return run([adb,'-s',serial,*args],timeout)
emulator=fixture=None;reports=[]
try:
 reuse=os.environ.get('KAISHI_REUSE_OWNED_EMULATOR')=='1'
 if not reuse:
  assert serial not in run([adb,'devices']).decode(),'Do not take over an existing device'
  logpath=out/'emulator.log'
  if logpath.exists():logpath.rename(out/('emulator-attempt-'+str(time.time_ns())+'.log'))
  log=logpath.open('wb');emulator=subprocess.Popen([str(root/'.android-local/android-sdk/emulator/emulator'),'-avd','kaishi-s53-tv28','-port','5568','-no-window','-no-audio','-no-snapshot','-accel','off','-gpu','swangle','-feature','-Vulkan,-GLAsyncSwap,-GLESDynamicVersion','-read-only','-memory','2048','-cores','4','-no-boot-anim'],cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT)
 ad('wait-for-device',timeout=180);end=time.time()+1200
 while time.time()<end:
  try:
   booted=ad('shell','getprop','sys.boot_completed',timeout=15).strip()==b'1'
   services=booted or (b'package:' in ad('shell','pm','path','android',timeout=15) and b'found' in ad('shell','service','check','activity',timeout=15))
   if services:
    (out/'readiness.json').write_text(json.dumps({'sysBootCompleted':booted,'packageAndActivityServicesReady':True,'fullSystemBootAcceptance':False}))
    break
  except (subprocess.CalledProcessError,subprocess.TimeoutExpired):pass
  time.sleep(2)
 else:raise RuntimeError('Emulator boot timed out')
 # Optional UI settings must never abort a ready emulator.
 for setting in ['window_animation_scale','transition_animation_scale']:
  try:ad('shell','settings','put','global',setting,'0',timeout=15)
  except (subprocess.CalledProcessError,subprocess.TimeoutExpired):pass
 flog=(out/'fixture.log').open('wb');fixture=subprocess.Popen(['node','scripts/s70-media-fixture.mjs'],cwd=root,env=env,stdout=flog,stderr=subprocess.STDOUT)
 time.sleep(1);base=json.loads((out/'fixture.json').read_text())['base']
 end=time.time()+240
 while 'BUILD SUCCESSFUL' not in (root/'artifacts/s70-debug-verified-build.log').read_text():
  if time.time()>end:raise RuntimeError('Final debug build not ready')
  time.sleep(2)
 editions=os.environ.get('KAISHI_NATIVE_EDITIONS','standard,parents').split(',');scopes=os.environ.get('KAISHI_NATIVE_SCOPES','ui,media,live').split(',')
 assert all(e in ['standard','parents'] for e in editions) and all(s in ['ui','live','media'] for s in scopes)
 for edition in editions:
  edition_dir=out/edition;edition_dir.mkdir(exist_ok=True);source_apk=root/f'android/app/build/outputs/apk/{edition}/debug/app-{edition}-debug.apk';expected=hashlib.sha256(source_apk.read_bytes()).hexdigest();apk=edition_dir/('candidate-'+expected[:16]+'.apk')
  if not apk.exists():shutil.copy2(source_apk,apk)
  package='org.zhixiaoce.kaishi'+('.parents' if edition=='parents' else '')
  classes=root/f'android/app/build/intermediates/javac/{edition}Debug/compile{edition.capitalize()}DebugJavaWithJavac/classes'
  class_hashes={str(p.relative_to(classes)):hashlib.sha256(p.read_bytes()).hexdigest() for p in classes.rglob('*.class')}
  try:
   installed=next(p.removeprefix('package:').strip() for p in ad('shell','pm','path',package).decode().splitlines() if p.endswith('base.apk'));same=ad('shell','sha256sum',installed).decode().startswith(expected)
  except (subprocess.CalledProcessError,StopIteration):same=False
  if not same:ad('install','--no-streaming','-r',str(apk),timeout=600)
  manifest=json.loads(run(['python3','scripts/build-revision-emulator-tests.py','--suite','browse','--edition',edition]));ad('install','--no-streaming','-r',manifest['apk'],timeout=180)
  for scope in scopes:
   d=edition_dir/scope;d.mkdir(exist_ok=True);print('START',edition,scope,flush=True)
   ad('shell','run-as',package,'rm','-f',f'files/s70-report-{scope}.json')
   with (d/'instrument.log').open('wb') as stream:
    subprocess.run([adb,'-s',serial,'shell','am','instrument','-w','-e','fixture',base,'-e','scope',scope,manifest['instrumentation']],cwd=root,env=env,stdout=stream,stderr=subprocess.STDOUT,timeout=600,check=True)
   report=json.loads(ad('exec-out','run-as',package,'cat',f'files/s70-report-{scope}.json'));reports.append(dict(edition=edition,**report));(d/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(edition,scope,json.dumps(report,ensure_ascii=False),flush=True)
   # Keep independent phase evidence even if one phase fails.
   if not report['passed']:continue
   for image in d.glob('*.png'):shutil.copy2(image,root/f'docs/images/s70-native-{edition}-{scope}-{image.name}')
  remote=next(p.removeprefix('package:').strip() for p in ad('shell','pm','path',package).decode().splitlines() if p.endswith('base.apk'));local=edition_dir/'installed-final-readback.apk';ad('pull',remote,local)
  assert hashlib.sha256(local.read_bytes()).hexdigest()==expected;local.unlink()
  (edition_dir/'final-binding.json').write_text(json.dumps({'localDebugApkSha256':expected,'installedCompleteReadbackMatched':True,'emulator':True,'sourceClasses':class_hashes},indent=2))
 (out/'summary.json').write_text(json.dumps({'passed':all(r['passed'] for r in reports),'reports':reports},ensure_ascii=False,indent=2))
 if not all(r['passed'] for r in reports):raise AssertionError('One or more native phases failed; see summary.json')
finally:
 if fixture is not None:
  fixture.terminate()
  try:fixture.wait(timeout=10)
  except subprocess.TimeoutExpired:fixture.kill()
 if emulator is not None:
  try:ad('emu','kill',timeout=15)
  except Exception:emulator.terminate()
  try:emulator.wait(timeout=15)
  except subprocess.TimeoutExpired:emulator.kill()
