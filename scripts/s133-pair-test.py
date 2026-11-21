from pathlib import Path
import hashlib,json,subprocess,tempfile
r=Path(__file__).resolve().parents[1];base=r/'android/app/src/main/java/org/zhixiaoce/aperture';jdk=r/'.android-local/jdk17/bin';test=r/'tests/java/PairWindowTest.java'
with tempfile.TemporaryDirectory(prefix='kaishi-pair-window-') as d:
 subprocess.run([str(jdk/'javac'),'-d',d,str(base/'LocalControlSession.java'),str(test)],check=True)
 q=subprocess.check_output([str(jdk/'java'),'-cp',d,'org.zhixiaoce.aperture.PairWindowTest'],text=True);print(q.strip())
 listener=(base/'TvLocalControl.java').read_text();assert '+LocalControlSession.PAIR_WINDOW_MS' in listener and 'postDelayed(expiry,LocalControlSession.PAIR_WINDOW_MS)' in listener;assert '5分钟有效' in (base/'LocalControlActivity.java').read_text()
 files=[base/(n+'.java') for n in ['LocalControlSession','TvLocalControl','LocalControlActivity']]+[test,Path(__file__).resolve()]
 (r/'docs/verification/s133-pair-window.json').write_text(json.dumps({'passed':True,'output':q.strip(),'productionSessionExecuted':True,'listenerTimerAndUiSourceAssertions':True,'realLanHandshakeExecuted':False,'sources':{str(p.relative_to(r)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files}},indent=2)+'\n')
