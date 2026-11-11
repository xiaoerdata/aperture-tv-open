"""Build/sign the v45 standard ARM candidate with the existing private key.
Never generates keys or publishes. Passwords stay in private files outside the repo.
"""
from pathlib import Path
import hashlib,json,os,re,subprocess,zipfile

root=Path(__file__).resolve().parents[1];runtime=root/'.android-local';out=root/'artifacts/s116-release';out.mkdir(exist_ok=True)
candidate=root/'artifacts/v45-candidate';assert not candidate.exists(),'Preserve existing candidate'
env=dict(os.environ,JAVA_HOME=str(runtime/'jdk17'),ANDROID_HOME=str(runtime/'android-sdk'),ANDROID_USER_HOME=str(runtime/'android-user'),GRADLE_USER_HOME=str(runtime/'gradle-user'))
env['PATH']=str(runtime/'jdk17/bin')+os.pathsep+env['PATH']
inputs=json.loads(subprocess.check_output(['node','--input-type=module','-e',"import {androidSource} from './scripts/android-source.mjs';console.log(JSON.stringify(await androidSource(process.cwd())))"],cwd=root))
(out/'android-inputs.json').write_text(json.dumps(inputs,indent=2)+'\n')
args=[str(runtime/'gradle-8.9/bin/gradle'),'--offline','--no-daemon','--console=plain','-PkaishiAbis=armeabi-v7a,arm64-v8a','-p','android',':app:assembleStandardRelease']
with (out/'build.log').open('w') as log:
 result=subprocess.run(args,cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT)
(out/'build-exit.json').write_text(json.dumps({'returncode':result.returncode,'standardOnly':True,'githubHostedRunner':False})+'\n')
assert result.returncode==0,'Standard release build failed; inspect build.log'
assert all(hashlib.sha256((root/f['path']).read_bytes()).hexdigest()==f['sha256'] for f in inputs['files']),'Inputs changed while building'
private=Path('/home/ubuntu/.local/share/kaishi/signing')
for name in ['kaishi-release.jks','store-password','key-password']:assert (private/name).stat().st_mode&0o077==0
listing=subprocess.check_output([str(runtime/'jdk17/bin/keytool'),'-list','-v','-keystore',str(private/'kaishi-release.jks'),'-storepass:file',str(private/'store-password')],env=env,text=True)
alias=re.search(r'Alias name: ([^\n]+)',listing)[1]
env.update(APERTURE_RELEASE_KEYSTORE=str(private/'kaishi-release.jks'),APERTURE_RELEASE_ALIAS=alias,APERTURE_RELEASE_STORE_PASSWORD_FILE=str(private/'store-password'),APERTURE_RELEASE_KEY_PASSWORD_FILE=str(private/'key-password'),APERTURE_RELEASE_CERT_SHA256='8176c1580418563f290e57d6f4b3f05ed6e1503748f52feca0a0c437da87ec33')
subprocess.run(['python3','scripts/s97-sign-candidate.py','--input','android/app/build/outputs/apk/standard/release/app-standard-release-unsigned.apk','--output',str(candidate),'--inputs',str(out/'android-inputs.json'),'--abis','armeabi-v7a,arm64-v8a','--code','45','--role','arm-candidate'],cwd=root,env=env,check=True)
manifest=json.loads((candidate/'manifest.json').read_text());assert manifest['version']=='0.2.0-alpha.42'
bt=runtime/'android-sdk/build-tools/35.0.0'
prior=subprocess.check_output([str(bt/'apksigner'),'verify','--print-certs',str(root/'artifacts/current-release/kaishi-v44-standard-arm.apk')],env=env,text=True)
assert re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)',prior)[1]==manifest['certificateSha256']
with zipfile.ZipFile(candidate/manifest['apk']) as archive:
 dex=b''.join(archive.read(n) for n in archive.namelist() if re.fullmatch(r'classes[0-9]*\.dex',n))
 for name in ['HomeWeatherSnapshot','InstalledApps','CinemaDates','CinemaPaging','CinemaBrowsePanel','CinemaResume','CinemaOrder','AppWallView','GalleryUploadHttp']:assert name.encode() in dex,name
 for name in ['CinemaBrowsePanelTest','PlaybackMemoryTest','AmbientReturnTest','HomeUpdatesTest']:assert name.encode() not in dex,name
manifest.update(upgradesCurrentReleaseWithSameCertificate=True,standardOnlyBuilt=True,newProductionClassesPresent=True)
(candidate/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
(candidate/'aligned.apk').unlink()
print(json.dumps({k:manifest[k] for k in ['versionCode','version','bytes','sha256','upgradesCurrentReleaseWithSameCertificate']}))
