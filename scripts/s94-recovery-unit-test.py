"""Deterministic production deadline and home-routing tests; no device and no wall-clock sleeps."""
from pathlib import Path
import json,subprocess,tempfile
r=Path(__file__).resolve().parents[1];jdk=r/'.android-local/jdk17/bin';outputs=[]
with tempfile.TemporaryDirectory(prefix='kaishi-recovery-') as tmp:
 sources=['android/app/src/main/java/org/zhixiaoce/aperture/PageDeadline.java','android/app/src/main/java/org/zhixiaoce/aperture/LobbyMode.java','tests/java/PageDeadlineTest.java','tests/java/LobbyModeTest.java','android/app/src/main/java/org/zhixiaoce/aperture/DisplayLifecycle.java','tests/java/HomeDeadlineFocusTest.java']
 subprocess.run([str(jdk/'javac'),'-d',tmp,*sources],cwd=r,check=True)
 for cls in ['PageDeadlineTest','LobbyModeTest','HomeDeadlineFocusTest']:outputs.append(subprocess.check_output([str(jdk/'java'),'-cp',tmp,'org.zhixiaoce.aperture.'+cls],text=True))
result={'productionCode':True,'testClock':True,'deviceTested':False,'outputs':outputs}
(r/'docs/verification/s94-recovery-unit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print('\n'.join(outputs))
