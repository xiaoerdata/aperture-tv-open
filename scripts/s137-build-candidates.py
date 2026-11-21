"""Build and sign standard/parents V49; existing identity only, no publication."""
from pathlib import Path
import hashlib,json,os,re,subprocess,zipfile
from android_abis import verify_apk_abis
r=Path(__file__).resolve().parents[1];runtime=r/'.android-local';out=r/'artifacts/s137-release';out.mkdir(exist_ok=True);env=dict(os.environ,JAVA_HOME=str(runtime/'jdk17'),ANDROID_HOME=str(runtime/'android-sdk'),ANDROID_USER_HOME=str(runtime/'android-user'),GRADLE_USER_HOME=str(runtime/'gradle-user'));env['PATH']=str(runtime/'jdk17/bin')+os.pathsep+env['PATH']
inputs=json.loads(subprocess.check_output(['node','--input-type=module','-e',"import {androidSource} from './scripts/android-source.mjs';console.log(JSON.stringify(await androidSource(process.cwd())))"],cwd=r));(out/'android-inputs.json').write_text(json.dumps(inputs,indent=2)+'\n')
with (out/'build.log').open('w') as log:result=subprocess.run([str(runtime/'gradle-8.9/bin/gradle'),'--offline','--no-daemon','--console=plain','-PkaishiAbis=armeabi-v7a,arm64-v8a','-p','android',':app:assembleStandardRelease',':app:assembleParentsRelease'],cwd=r,env=env,stdout=log,stderr=subprocess.STDOUT)
(out/'build-exit.json').write_text(json.dumps({'returncode':result.returncode,'editions':['standard','parents'],'githubHostedRunner':False})+'\n');assert result.returncode==0,'Inspect build.log'
assert all(hashlib.sha256((r/i['path']).read_bytes()).hexdigest()==i['sha256'] for i in inputs['files'])
private=Path('/home/ubuntu/.local/share/kaishi/signing')
for name in ['kaishi-release.jks','store-password','key-password']:assert (private/name).is_file() and (private/name).stat().st_mode&0o077==0
listing=subprocess.check_output([str(runtime/'jdk17/bin/keytool'),'-list','-v','-keystore',str(private/'kaishi-release.jks'),'-storepass:file',str(private/'store-password')],env=env,text=True,stderr=subprocess.DEVNULL);alias=re.search(r'Alias name: ([^\n]+)',listing)[1];bt=runtime/'android-sdk/build-tools/35.0.0'
def run(*args):return subprocess.check_output(list(map(str,args)),env=env,stderr=subprocess.STDOUT,text=True)
for edition in ['standard','parents']:
 candidate=r/('artifacts/v49-'+edition+'-candidate');assert not candidate.exists();candidate.mkdir();unsigned=r/f'android/app/build/outputs/apk/{edition}/release/app-{edition}-release-unsigned.apk';verify_apk_abis(unsigned,['armeabi-v7a','arm64-v8a']);aligned=candidate/'aligned.apk';apk=candidate/f'kaishi-v49-{edition}-arm.apk'
 configs=list((r/f'android/app/build/generated/source/buildConfig/{edition}/release').rglob('BuildConfig.java'));assert len(configs)==1 and ('PARENTS_EDITION = '+str(edition=='parents').lower()) in configs[0].read_text()
 run(bt/'zipalign','-P','16','4',unsigned,aligned);run(bt/'apksigner','sign','--ks',private/'kaishi-release.jks','--ks-key-alias',alias,'--ks-pass','file:'+str(private/'store-password'),'--key-pass','file:'+str(private/'key-password'),'--debuggable-apk-permitted','false','--v4-signing-enabled','false','--alignment-preserved','true','--in',aligned,'--out',apk)
 signature=run(bt/'apksigner','verify','--min-sdk-version','26','--verbose','--print-certs',apk);cert=re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)',signature)[1];prior=run(bt/'apksigner','verify','--print-certs',r/f'artifacts/current-release/kaishi-v48-{edition}-arm.apk');assert cert==re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)',prior)[1]=='8176c1580418563f290e57d6f4b3f05ed6e1503748f52feca0a0c437da87ec33'
 package=run(bt/'aapt','dump','badging',apk);fields=dict(re.findall(r"(\w+)='([^']*)'",package.splitlines()[0]));name='org.zhixiaoce.kaishi'+('.parents' if edition=='parents' else '');version='0.2.0-alpha.46'+('-parents' if edition=='parents' else '');assert fields['name']==name and fields['versionCode']=='49' and fields['versionName']==version;assert 'application-debuggable' not in package and "sdkVersion:'26'" in package and "targetSdkVersion:'35'" in package
 with zipfile.ZipFile(apk) as z:
  for i in inputs['files']:
   if i['path'].startswith('public/launcher/'):assert hashlib.sha256(z.read('assets/'+i['path'][16:])).hexdigest()==i['sha256'],i['path']
  assert b'top:0;right:0;bottom:0;left:0;inset:0' in z.read('assets/gallery.css');assert b'.gallery-empty[hidden]{display:none}' in z.read('assets/gallery.css')
  dex=b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'))
  for n in ['native-gallery-manage','NativeGallery','CinemaPaging','HomeWeatherSnapshot','parseConfig','moveGroup','sourceIndex','prepend','matchEpisode','HomeAlbumActivity','CinemaPlayer','ExoPlayer','CacheDataSource','SimpleCache']:assert n.encode() in dex,n
  for old in ['超过12个站点','超过12个直播来源','站点超过100项','仓库超过64项']:assert old.encode() not in dex,old
  assert '展开后的配置超过4MiB'.encode() in dex
  for cls in ['CinemaEpisodeGrid','CinemaChoicePanel']:
   compiled=r/f'android/app/build/intermediates/javac/{edition}Release/compile{edition.title()}ReleaseJavaWithJavac/classes/org/zhixiaoce/aperture/{cls}.class';assert '下一组'.encode() not in compiled.read_bytes()
  assert '年份 · 评分降序'.encode() in dex
  for n in ['HomeAlbumActivityTest','AlbumReturnTest','CinemaStreamTest','SourceReturnHarness','LivePanelTest','EpisodeAppendTest','YearScoreAppendTest','PlaybackLineMatchTest','ResourceCapacityTest','NativeGalleryManagementTest','FrameEvidenceGate','R3NativeGalleryProbe','PlaybackMemoryTest','org.zhixiaoce.kaishi.phototest']:assert n.encode() not in dex,n
 m={'version':version,'versionCode':49,'applicationId':name,'edition':edition,'apk':apk.name,'bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'certificateSha256':cert,'abis':['armeabi-v7a','arm64-v8a'],'debuggable':False,'inputs':inputs,'upgradesCorrespondingV48WithSameCertificate':True,'flavorBuildConfigVerified':True,'packagedAssetsMatchInputs':True,'photoFixPreservedInApk':True,'resourceSizeBoundInApk':True,'liveSourceGroupPickerAndPrimaryLinesInApk':True,'media3StreamingBufferAndVodCacheInApk':True,'testClassesNotInProduct':True,'published':False,'xiaomiAccepted':False};(candidate/'manifest.json').write_text(json.dumps(m,indent=2)+'\n');(candidate/'signature.txt').write_text(signature);(candidate/'package.txt').write_text(package);aligned.unlink();print(json.dumps({k:m[k] for k in ['edition','versionCode','version','bytes','sha256']}),flush=True)
