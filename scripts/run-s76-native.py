"""Uses only the S76-owned emulator on 5570. All input assertions are through Android InputManager."""
from pathlib import Path
import subprocess,os,json,time,hashlib
root=Path(__file__).resolve().parents[1];out=root/'artifacts/s76-native';out.mkdir(exist_ok=True)
adb=root/'.android-local/android-sdk/platform-tools/adb';serial='emulator-5570'
def ad(*args,timeout=40):
 return subprocess.check_output([str(adb),'-s',serial,*map(str,args)],cwd=root,stderr=subprocess.STDOUT,timeout=timeout)
fixture=None
try:
 assert b'1'==ad('shell','getprop','sys.boot_completed').strip(), 'Wait for the owned emulator to boot normally'
 (out/'fixture.json').unlink(missing_ok=True)
 fixture=subprocess.Popen(['node','scripts/s76-media-fixture.mjs'],cwd=root,stdout=(out/'fixture.log').open('wb'),stderr=subprocess.STDOUT)
 for n in range(30):
  if (out/'fixture.json').exists():break
  time.sleep(1)
 base=json.loads((out/'fixture.json').read_text())['base'];reports=[]
 for edition in ['standard','parents']:
  print('install',edition,flush=True);package='org.zhixiaoce.kaishi'+('.parents' if edition=='parents' else '');apk=root/f'android/app/build/outputs/apk/{edition}/debug/app-{edition}-debug.apk';driver=json.loads((root/f'artifacts/s76-{edition}-driver.json').read_text());directory=out/edition;directory.mkdir(exist_ok=True)
  for label,path in [('app',apk),('driver',Path(driver['apk']))]:
   if label=='app' and edition=='standard' and os.environ.get('S76_REUSE_STANDARD')=='1':
    (directory/(label+'-reuse.json')).write_text(json.dumps({'installedBy':'s76-native-attempt1','apkSha256':hashlib.sha256(path.read_bytes()).hexdigest(),'unchangedArtifact':True}));continue
   remote=f'/data/local/tmp/s76-{edition}-{label}.apk';ad('push',path,remote,timeout=120);result=ad('shell','pm','install','-r','-t',remote,timeout=180);(directory/(label+'-install.log')).write_bytes(result);assert b'Success' in result,result
  print('run system input',edition,flush=True)
  with (directory/'instrument.log').open('wb') as stream:subprocess.run([str(adb),'-s',serial,'shell','am','instrument','-w','-e','fixture',base,driver['instrumentation']],cwd=root,stdout=stream,stderr=subprocess.STDOUT,timeout=1000,check=True)
  report=json.loads(ad('exec-out','run-as',package,'cat','files/s76-report.json'));report.update(edition=edition,debugApkSha256=hashlib.sha256(apk.read_bytes()).hexdigest());reports.append(report);(directory/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps(report,ensure_ascii=False),flush=True)
  for name in report['images']:
   data=ad('exec-out','run-as',package,'cat',f'files/s76-{name}.png',timeout=60);(directory/(name+'.png')).write_bytes(data);(root/f'docs/images/s76-native-{edition}-{name}.png').write_bytes(data)
  (directory/'foreground.txt').write_bytes(ad('shell','dumpsys','activity','activities'))
  if not report['passed']:raise AssertionError('Native regression failed; retain screenshots/logs')
 (root/'docs/verification/s76-native.json').write_text(json.dumps(dict(passed=True,reports=reports,xiaomiHardware=False),ensure_ascii=False,indent=2))
finally:
 if fixture:
  fixture.terminate()
  try:fixture.wait(timeout=10)
  except subprocess.TimeoutExpired:fixture.kill()
 # Emulator is kept for a focused fix/retest on failure; root agent owns its final cleanup.
