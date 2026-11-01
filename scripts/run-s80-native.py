"""Run a bounded scope on the task-owned physical-1080 Android emulator; never Xiaomi acceptance."""
from pathlib import Path
import subprocess,os,json,time,hashlib,argparse
p=argparse.ArgumentParser();p.add_argument('--edition',choices=['standard','parents'],required=True);p.add_argument('--scope',choices=['full','extras','connection','home','tail','numeric','livegroups'],required=True);args=p.parse_args()
root=Path(__file__).resolve().parents[1];out=root/f'artifacts/s80-native-{args.edition}-{args.scope}';out.mkdir(exist_ok=False)
adb=root/'.android-local/android-sdk/platform-tools/adb';serial='emulator-5570'
def ad(*arguments,timeout=60):return subprocess.check_output([str(adb),'-s',serial,*map(str,arguments)],cwd=root,stderr=subprocess.STDOUT,timeout=timeout)
fixture=None
try:
 assert ad('shell','getprop','sys.boot_completed').strip()==b'1'
 env=os.environ.copy();env['KAISHI_NATIVE_OUTPUT']=str(out)
 fixture=subprocess.Popen(['node','scripts/s79-media-fixture.mjs'],cwd=root,env=env,stdout=(out/'fixture.log').open('wb'),stderr=subprocess.STDOUT)
 for n in range(30):
  if (out/'fixture.json').exists():break
  time.sleep(1)
 base=json.loads((out/'fixture.json').read_text())['base'];edition=args.edition;package='org.zhixiaoce.kaishi'+('.parents' if edition=='parents' else '');apk=root/f'android/app/build/outputs/apk/{edition}/debug/app-{edition}-debug.apk';driver=json.loads((root/f'artifacts/s79-readiness-instrumentation/{edition}/manifest.json').read_text())
 for label,path in [('app',apk),('driver',Path(driver['apk']))]:
  expected=hashlib.sha256(path.read_bytes()).hexdigest();pkg=package if label=='app' else driver['instrumentation'].split('/')[0]
  try:
   installed=ad('shell','pm','path',pkg).decode().strip().splitlines()[0].removeprefix('package:');actual=ad('shell','sha256sum',installed).decode().split()[0]
  except (IndexError,subprocess.CalledProcessError):actual=''
  if actual!=expected:
   remote=f'/data/local/tmp/s80-{edition}-{label}.apk';ad('push',path,remote,timeout=120);result=ad('shell','pm','install','-r','-t',remote,timeout=300);(out/(label+'-install.log')).write_bytes(result);assert b'Success' in result
  (out/(label+'-identity.json')).write_text(json.dumps({'sha256':expected,'priorInstalledSha256':actual}))
 ad('shell','run-as',package,'rm','-f','files/s79-report.json')
 print('run',edition,args.scope,flush=True)
 with (out/'instrument.log').open('wb') as stream:subprocess.run([str(adb),'-s',serial,'shell','am','instrument','-w','-e','fixture',base,'-e','scope',args.scope,driver['instrumentation']],cwd=root,stdout=stream,stderr=subprocess.STDOUT,timeout=1800,check=True)
 instrumentation=(out/'instrument.log').read_text();assert 'INSTRUMENTATION_CODE: -1' in instrumentation,'Instrumentation did not finish successfully; stale reports cannot pass'
 report=json.loads(ad('exec-out','run-as',package,'cat','files/s79-report.json'));assert report.get('scope')==args.scope,'Driver scope mismatch';report.update(edition=edition,scope=args.scope,physicalSize=[1920,1080],density=320,debugApkSha256=hashlib.sha256(apk.read_bytes()).hexdigest());(out/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps(report,ensure_ascii=False),flush=True)
 for name in report['images']:
  data=ad('exec-out','run-as',package,'cat',f'files/s79-{name}.png');(out/(name+'.png')).write_bytes(data);(root/f'docs/images/s80-native-{edition}-{name}.png').write_bytes(data)
 if report['passed']:(root/f'docs/verification/s80-native-{edition}-{args.scope}.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
 assert report['passed'],'Retain failed native scope evidence'
finally:
 if fixture:
  fixture.terminate()
  try:fixture.wait(timeout=10)
  except subprocess.TimeoutExpired:fixture.kill()
