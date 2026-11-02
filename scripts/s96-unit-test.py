"""Retained R1/R2 models plus R3 stage/budget checks. New output and receipt only."""
from pathlib import Path
import subprocess,json,hashlib,argparse
p=argparse.ArgumentParser();p.add_argument('--output',default='artifacts/s96-unit');p.add_argument('--receipt',default='docs/verification/s96-unit.json');a=p.parse_args()
r=Path(__file__).resolve().parents[1];out=(r/a.output).resolve();receipt=(r/a.receipt).resolve();assert r/'artifacts' in out.parents and r/'docs/verification' in receipt.parents and not out.exists() and not receipt.exists();out.mkdir();jdk=r/'.android-local/jdk17/bin'
sources=['android/app/src/main/java/org/zhixiaoce/aperture/'+name+'.java' for name in ['UploadConsent','PageDeadline','DisplayLifecycle','LobbyMode','GalleryTrace']]+['tests/android/evidence38/FrameEvidenceGate.java']+['tests/java/'+name+'.java' for name in ['UploadConsentTest','FrameEvidenceGateTest','PageDeadlineTest','HomeDeadlineFocusTest','LobbyModeTest','GalleryTraceTest']]
subprocess.run([str(jdk/'javac'),'-d',str(out),*[str(r/p) for p in sources]],check=True);outputs={}
for cls in ['UploadConsentTest','FrameEvidenceGateTest','PageDeadlineTest','HomeDeadlineFocusTest','LobbyModeTest','GalleryTraceTest']:
 p=subprocess.run([str(jdk/'java'),'-cp',str(out),'org.zhixiaoce.aperture.'+cls],capture_output=True,text=True);(out/(cls+'.log')).write_text(p.stdout+p.stderr);assert p.returncode==0,p.stderr;outputs[cls]=p.stdout.strip();print(p.stdout.strip(),flush=True)
receipt.write_text(json.dumps({'passed':True,'outputs':outputs,'androidExecuted':False,'realFrameCaptureExecuted':False,'realUploadListenerStarted':False,'sources':[{'path':p,'sha256':hashlib.sha256((r/p).read_bytes()).hexdigest()} for p in sources]},indent=2)+'\n')
