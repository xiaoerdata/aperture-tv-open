"""Targeted production models and disk persistence; no Gradle build or Android decoder."""
from pathlib import Path
import hashlib,json,subprocess,tempfile

root=Path(__file__).resolve().parents[1]
jdk=root/'.android-local/jdk17/bin'
lib=next((root/'.android-local/gradle-user/caches/modules-2/files-2.1/org.json/json/20160810').rglob('*.jar'))
base=root/'android/app/src/main/java/org/zhixiaoce/aperture'
names=['CinemaCatalog','CinemaResourcePack','CinemaFormats','CinemaShelf','CinemaShelfStore','CinemaCipher','CinemaResume','CinemaPlaybackPlan','NasLocation','NasNavigation','NasLibrary','NasDraft','NasWatchRoute']
sources=[base/(name+'.java') for name in names]
tests=[root/'tests/java'/(name+'.java') for name in ['PlaybackMemoryTest','ShelfOverviewTest','NasViewingTest']]
checks=[]
with tempfile.TemporaryDirectory(prefix='kaishi-s110-memory-') as directory:
 classes=Path(directory)/'classes'
 subprocess.run([str(jdk/'javac'),'-encoding','UTF-8','-cp',str(lib),'-d',str(classes),*[str(p) for p in [*sources,*tests]]],cwd=root,check=True)
 for name in ['PlaybackMemoryTest','ShelfOverviewTest','NasViewingTest']:
  result=subprocess.run([str(jdk/'java'),'-cp',f'{classes}:{lib}','org.zhixiaoce.aperture.'+name,str(Path(directory)/'shelf.json')],cwd=root,capture_output=True,text=True)
  if result.returncode:raise RuntimeError(result.stdout+result.stderr)
  checks.append(result.stdout.strip())
 files=[*sources,*tests,base/'CinemaActivity.java',base/'CinemaVault.java',base/'NasActivity.java',Path(__file__).resolve()]
 receipt={'passed':True,'checks':checks,'productionJavaModels':True,'realTemporaryFilePersistence':True,'androidKeyStoreExecuted':False,'androidPlayerExecuted':False,'realXiaomi':False,'apkBuilt':False,'published':False,'sources':{str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files}}
 (root/'docs/verification/s110-playback-memory.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n')
 print('\n'.join(checks))
