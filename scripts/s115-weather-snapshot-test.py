"""Native compact weather model with synthetic provider readings, no network or UI."""
from pathlib import Path
import hashlib,json,subprocess,tempfile
r=Path(__file__).resolve().parents[1];jdk=r/'.android-local/jdk17/bin';lib=next((r/'.android-local/gradle-user/caches/modules-2/files-2.1/org.json/json/20160810').rglob('*.jar'));sources=[r/'android/app/src/main/java/org/zhixiaoce/aperture/HomeWeatherSnapshot.java',r/'tests/java/HomeWeatherSnapshotTest.java']
with tempfile.TemporaryDirectory(prefix='kaishi-s115-weather-') as d:
 subprocess.run([str(jdk/'javac'),'-encoding','UTF-8','-cp',str(lib),'-d',d,*map(str,sources)],check=True)
 result=subprocess.run([str(jdk/'java'),'-cp',d+':'+str(lib),'org.zhixiaoce.aperture.HomeWeatherSnapshotTest'],capture_output=True,text=True);assert result.returncode==0,result.stdout+result.stderr
 (r/'docs/verification/s115-weather-snapshot.json').write_text(json.dumps({'passed':True,'output':result.stdout.strip(),'syntheticProviderReadings':True,'realXiaomi':False,'sources':{str(p.relative_to(r)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}},indent=2)+'\n');print(result.stdout.strip())
