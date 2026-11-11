"""Build/sign parents v45 from the exact, published standard v45 product inputs."""
from pathlib import Path
import hashlib,json,os,re,subprocess,zipfile
from android_abis import verify_apk_abis
r=Path(__file__).resolve().parents[1];runtime=r/'.android-local';out=r/'artifacts/s117-parents';candidate=r/'artifacts/v45-parents-candidate';assert not candidate.exists()
inputs=json.loads((out/'android-inputs.json').read_text());standard=json.loads((r/'artifacts/current-release/testing-manifest.json').read_text());assert inputs==standard['androidInputs'] and standard['versionCode']==45
assert all(hashlib.sha256((r/f['path']).read_bytes()).hexdigest()==f['sha256'] for f in inputs['files'])
env=dict(os.environ,JAVA_HOME=str(runtime/'jdk17'),ANDROID_HOME=str(runtime/'android-sdk'),ANDROID_USER_HOME=str(runtime/'android-user'),GRADLE_USER_HOME=str(runtime/'gradle-user'));env['PATH']=str(runtime/'jdk17/bin')+os.pathsep+env['PATH']
command=[str(runtime/'gradle-8.9/bin/gradle'),'--offline','--no-daemon','--console=plain','-PkaishiAbis=armeabi-v7a,arm64-v8a','-p','android',':app:assembleParentsRelease']
with (out/'build.log').open('w') as log:result=subprocess.run(command,cwd=r,env=env,stdout=log,stderr=subprocess.STDOUT)
(out/'build-exit.json').write_text(json.dumps({'returncode':result.returncode,'parentsOnly':True,'githubHostedRunner':False})+'\n');assert result.returncode==0,'Inspect build.log'
assert all(hashlib.sha256((r/f['path']).read_bytes()).hexdigest()==f['sha256'] for f in inputs['files'])
configs=list((r/'android/app/build/generated/source/buildConfig/parents/release').rglob('BuildConfig.java'));assert len(configs)==1 and 'PARENTS_EDITION = true' in configs[0].read_text()
private=Path('/home/ubuntu/.local/share/kaishi/signing')
for name in ['kaishi-release.jks','store-password','key-password']:assert (private/name).is_file() and (private/name).stat().st_mode&0o077==0
listing=subprocess.check_output([str(runtime/'jdk17/bin/keytool'),'-list','-v','-keystore',str(private/'kaishi-release.jks'),'-storepass:file',str(private/'store-password')],env=env,text=True,stderr=subprocess.DEVNULL);alias=re.search(r'Alias name: ([^\n]+)',listing)[1]
bt=runtime/'android-sdk/build-tools/35.0.0';unsigned=r/'android/app/build/outputs/apk/parents/release/app-parents-release-unsigned.apk';verify_apk_abis(unsigned,['armeabi-v7a','arm64-v8a']);candidate.mkdir();aligned=candidate/'aligned.apk';apk=candidate/'kaishi-v45-parents-arm.apk'
def run(*args):return subprocess.check_output(list(map(str,args)),env=env,stderr=subprocess.STDOUT,text=True)
run(bt/'zipalign','-P','16','4',unsigned,aligned)
run(bt/'apksigner','sign','--ks',private/'kaishi-release.jks','--ks-key-alias',alias,'--ks-pass','file:'+str(private/'store-password'),'--key-pass','file:'+str(private/'key-password'),'--debuggable-apk-permitted','false','--v4-signing-enabled','false','--alignment-preserved','true','--in',aligned,'--out',apk)
cert=run(bt/'apksigner','verify','--min-sdk-version','26','--verbose','--print-certs',apk);digest=re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)',cert)[1];prior=json.loads((out/'prior-parents-signature.json').read_text());assert digest==prior['certificateSha256']==standard['certificateSha256']
package=run(bt/'aapt','dump','badging',apk);fields=dict(re.findall(r"(\w+)='([^']*)'",package.splitlines()[0]));assert fields['name']=='org.zhixiaoce.kaishi.parents' and fields['versionCode']=='45' and fields['versionName']=='0.2.0-alpha.42-parents';assert 'application-debuggable' not in package and "sdkVersion:'26'" in package and "targetSdkVersion:'35'" in package and '开视·爸妈版' in package
with zipfile.ZipFile(apk) as z:
 for f in inputs['files']:
  if f['path'].startswith('public/launcher/'):assert hashlib.sha256(z.read('assets/'+f['path'][16:])).hexdigest()==f['sha256'],f['path']
 dex=b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'))
 for n in ['InstalledApps','CinemaDates','CinemaOrder','CinemaPaging','CinemaResume','HomeWeatherSnapshot']:assert n.encode() in dex,n
 for n in ['HomeWeatherSnapshotTest','AmbientReturnTest','FrameEvidenceGate','R3NativeGalleryProbe','PlaybackMemoryTest']:assert n.encode() not in dex,n
m={'version':fields['versionName'],'versionCode':45,'applicationId':fields['name'],'edition':'parents','apk':apk.name,'bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'certificateSha256':digest,'abis':['armeabi-v7a','arm64-v8a'],'debuggable':False,'inputs':inputs,'sourceCommit':standard['sourceCommit'],'sourceTree':standard['sourceTree'],'sameProductInputsAsStandardV45':True,'upgradesVerifiedParentsV32WithSameCertificate':True,'parentsBuildConfigTrue':True,'packagedAssetsMatchInputs':True,'testClassesNotInProduct':True,'published':False,'xiaomiAccepted':False}
(candidate/'manifest.json').write_text(json.dumps(m,indent=2)+'\n');(candidate/'signature.txt').write_text(cert);(candidate/'package.txt').write_text(package);aligned.unlink();print(json.dumps({k:m[k] for k in ['versionCode','version','bytes','sha256','upgradesVerifiedParentsV32WithSameCertificate']}))
