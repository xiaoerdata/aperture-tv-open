"""Actual streaming policy/client on local HTTP fixtures; no synthetic decoder claim."""
from pathlib import Path
import hashlib,json,subprocess,tempfile
r=Path(__file__).resolve().parents[1];jdk=r/'.android-local/jdk17/bin';cache=r/'.android-local/gradle-user/caches/modules-2/files-2.1';libs=[]
for c in ['org.json/json/20160810','com.squareup.okhttp3/okhttp/4.12.0','com.squareup.okio/okio-jvm/3.6.0','org.jetbrains.kotlin/kotlin-stdlib/1.9.10']:libs.append(next((cache/c).rglob('*.jar')))
cp=':'.join(map(str,libs));base=r/'android/app/src/main/java/org/zhixiaoce/aperture';sources=[base/(n+'.java') for n in ['CinemaCatalog','CinemaDates','CinemaFormats','CinemaResourcePack','CinemaHttp','CinemaStreamPolicy','CinemaStreamHttp']];test=r/'tests/java/CinemaStreamTest.java'
with tempfile.TemporaryDirectory(prefix='kaishi-s129-stream-') as d:
 subprocess.run([str(jdk/'javac'),'-encoding','UTF-8','-cp',cp,'-d',d,*map(str,[*sources,test])],check=True);q=subprocess.run([str(jdk/'java'),'-cp',d+':'+cp,'org.zhixiaoce.aperture.CinemaStreamTest'],capture_output=True,text=True,timeout=30);assert q.returncode==0,q.stdout+q.stderr;print(q.stdout.strip())
 player=base/'CinemaPlayer.java';text=player.read_text();assert text.index('if(live)return upstream.createDataSource()')<text.index('CinemaStreamCache.get');assert 'setAudioAttributes' in text and ',false)' in text and 'FLAG_IGNORE_CACHE_ON_ERROR' in text and 'setEnableDecoderFallback(true)' in text
 tracked=[*sources,test,player,base/'CinemaStreamCache.java',Path(__file__).resolve()];(r/'docs/verification/s129-stream.json').write_text(json.dumps({'passed':True,'output':q.stdout.strip(),'productionStreamingPolicyAndHttp':True,'realLocalHttpRangeAndRedirects':True,'cacheAndDecoderWiringSourceAssertions':True,'media3Decoded':False,'realXiaomi':False,'realNetworkThroughputMeasured':False,'sources':{str(p.relative_to(r)):hashlib.sha256(p.read_bytes()).hexdigest() for p in tracked}},indent=2)+'\n')
