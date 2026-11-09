"""Execute source date parsing and sorting with actual JSON/XML resource import."""
from pathlib import Path
import hashlib,json,subprocess,tempfile
r=Path(__file__).resolve().parents[1];jdk=r/'.android-local/jdk17/bin';lib=next((r/'.android-local/gradle-user/caches/modules-2/files-2.1/org.json/json/20160810').rglob('*.jar'))
sources=[r/'android/app/src/main/java/org/zhixiaoce/aperture'/(n+'.java') for n in ['CinemaCatalog','CinemaDates','CinemaOrder','CinemaResourcePack','CinemaFormats']]+[r/'tests/java/CinemaDatesTest.java']
with tempfile.TemporaryDirectory(prefix='kaishi-s114-dates-') as d:
 subprocess.run([str(jdk/'javac'),'-encoding','UTF-8','-cp',str(lib),'-d',d,*map(str,sources)],check=True)
 result=subprocess.run([str(jdk/'java'),'-cp',d+':'+str(lib),'org.zhixiaoce.aperture.CinemaDatesTest'],capture_output=True,text=True)
 assert result.returncode==0,result.stdout+result.stderr
 receipt={'passed':True,'output':result.stdout.strip(),'productionJsonXmlExecuted':True,'syntheticMetadata':True,'realXiaomi':False,'sources':{str(p.relative_to(r)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}}
 (r/'docs/verification/s114-cinema-dates.json').write_text(json.dumps(receipt,indent=2)+'\n');print(result.stdout.strip())
