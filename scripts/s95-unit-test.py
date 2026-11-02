"""Round2 deterministic checks. Outputs are retained; no temporary directory cleanup or network."""
from pathlib import Path
import subprocess,json,hashlib
r=Path(__file__).resolve().parents[1];out=r/'artifacts/s95-unit';out.mkdir(exist_ok=True);jdk=r/'.android-local/jdk17/bin'
sources=['android/app/src/main/java/org/zhixiaoce/aperture/UploadConsent.java','tests/android/evidence38/FrameEvidenceGate.java','tests/java/UploadConsentTest.java','tests/java/FrameEvidenceGateTest.java','android/app/src/main/java/org/zhixiaoce/aperture/PageDeadline.java','android/app/src/main/java/org/zhixiaoce/aperture/DisplayLifecycle.java','android/app/src/main/java/org/zhixiaoce/aperture/LobbyMode.java','tests/java/PageDeadlineTest.java','tests/java/HomeDeadlineFocusTest.java','tests/java/LobbyModeTest.java']
subprocess.run([str(jdk/'javac'),'-d',str(out),*[str(r/p) for p in sources]],check=True)
outputs={}
for cls in ['UploadConsentTest','FrameEvidenceGateTest','PageDeadlineTest','HomeDeadlineFocusTest','LobbyModeTest']:
 p=subprocess.run([str(jdk/'java'),'-cp',str(out),'org.zhixiaoce.aperture.'+cls],capture_output=True,text=True);(out/(cls+'.log')).write_text(p.stdout+p.stderr);assert p.returncode==0,p.stderr;outputs[cls]=p.stdout.strip();print(p.stdout.strip(),flush=True)
receipt={'passed':True,'outputs':outputs,'actualFrameCaptureExecuted':False,'actualUploadListenerStarted':False,'androidActivityExecuted':False,'productionConsentModel':True,'testOnlyFrameGate':True,'sources':[{'path':p,'sha256':hashlib.sha256((r/p).read_bytes()).hexdigest()} for p in sources]}
(r/'docs/verification/s95-unit.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n')
