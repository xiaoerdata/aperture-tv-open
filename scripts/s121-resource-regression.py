"""Run existing resource import, HTTP and cipher checks using cached libraries; no APK build."""
from pathlib import Path
import hashlib,json,os,subprocess,tempfile
r=Path(__file__).resolve().parents[1]
jdk=Path(os.environ.get('JAVA_HOME',str(r/'.android-local/jdk17')))/'bin'
cache=r/'.android-local/gradle-user/caches/modules-2/files-2.1'
libs=[]
for coordinates in ['org.json/json/20160810','com.squareup.okhttp3/okhttp/4.12.0','com.squareup.okio/okio-jvm/3.6.0','org.jetbrains.kotlin/kotlin-stdlib/1.9.10']:
 matches=list((cache/coordinates).rglob('*.jar'))
 if len(matches)!=1:raise RuntimeError('Expected cached test dependency: '+coordinates)
 libs.append(matches[0])
cp=os.pathsep.join(map(str,libs))
sources=[r/'android/app/src/main/java/org/zhixiaoce/aperture'/(n+'.java') for n in ['CinemaCatalog','CinemaDates','CinemaResourcePack','CinemaFormats','CinemaHttp','CinemaShelf','CinemaCipher']]
tests=[r/'tests/java'/(n+'.java') for n in ['ResourcePackTest','EmbeddedCinemaTest']]
results=[]
with tempfile.TemporaryDirectory(prefix='kaishi-s121-regression-') as d:
 subprocess.run([str(jdk/'javac'),'-encoding','UTF-8','-cp',cp,'-d',d,*map(str,sources+tests)],cwd=r,check=True)
 for name,args in [('ResourcePackTest',['examples/resource-packs/family.kaishi.json','examples/resource-packs/tvbox.kaishi.json']),('EmbeddedCinemaTest',[])]:
  q=subprocess.run([str(jdk/'java'),'-cp',d+os.pathsep+cp,'org.zhixiaoce.aperture.'+name,*args],cwd=r,capture_output=True,text=True,timeout=60)
  if q.returncode:raise RuntimeError(q.stdout+q.stderr)
  print(q.stdout.strip());results.append({'test':name,'output':q.stdout.strip(),'passed':True})
receipt={'passed':True,'results':results,'productionJavaAndHttp':True,'isolatedLocalHttpOnly':True,'sources':{p.relative_to(r).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sources+tests},'libraries':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in libs},'apkBuilt':False,'published':False,'realXiaomi':False}
(r/'docs/verification/s121-resource-regression.json').write_text(json.dumps(receipt,indent=2)+'\n')
