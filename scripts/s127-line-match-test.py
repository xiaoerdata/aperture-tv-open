"""Current playback-plan matching plus existing disk/progress regressions; no network/TV."""
from pathlib import Path
import hashlib,json,subprocess,tempfile
r=Path(__file__).resolve().parents[1];jdk=r/'.android-local/jdk17/bin';lib=next((r/'.android-local/gradle-user/caches/modules-2/files-2.1/org.json/json/20160810').rglob('*.jar'));base=r/'android/app/src/main/java/org/zhixiaoce/aperture'
sources=[base/(n+'.java') for n in ['CinemaCatalog','CinemaDates','CinemaResourcePack','CinemaFormats','CinemaPlaybackPlan','CinemaResume','CinemaShelf','CinemaShelfStore','CinemaCipher']];tests=[r/'tests/java'/(n+'.java') for n in ['PlaybackLineMatchTest','PlaybackMemoryTest']];outputs=[]
with tempfile.TemporaryDirectory(prefix='kaishi-s127-line-') as d:
 classes=Path(d)/'classes';subprocess.run([str(jdk/'javac'),'-encoding','UTF-8','-cp',str(lib),'-d',str(classes),*map(str,[*sources,*tests])],check=True)
 for test in tests:
  args=[str(Path(d)/'watch-state.json')] if test.stem=='PlaybackMemoryTest' else []
  q=subprocess.run([str(jdk/'java'),'-cp',str(classes)+':'+str(lib),'org.zhixiaoce.aperture.'+test.stem,*args],capture_output=True,text=True);assert q.returncode==0,q.stdout+q.stderr;outputs.append(q.stdout.strip());print(q.stdout.strip())
 (r/'docs/verification/s127-line-match.json').write_text(json.dumps({'passed':True,'outputs':outputs,'productionPlanResumeAndDiskStore':True,'syntheticTitlesAndUrls':True,'mediaDecoded':False,'realXiaomi':False,'sources':{str(p.relative_to(r)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [*sources,*tests,Path(__file__).resolve()]}},indent=2)+'\n')
