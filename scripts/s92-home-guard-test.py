"""Policy transition tests plus grant-list preservation, without any connected TV."""
from pathlib import Path
import importlib.util,json,subprocess,tempfile,xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1];jdk=r/'.android-local/jdk17/bin'
with tempfile.TemporaryDirectory(prefix='kaishi-guard-policy-') as tmp:
 subprocess.run([str(jdk/'javac'),'-d',tmp,str(r/'android/app/src/main/java/org/zhixiaoce/aperture/HomeGuardPolicy.java'),str(r/'tests/java/HomeGuardPolicyTest.java')],check=True)
 result=subprocess.check_output([str(jdk/'java'),'-cp',tmp,'org.zhixiaoce.aperture.HomeGuardPolicyTest'],text=True).strip()
spec=importlib.util.spec_from_file_location('helper',r/'scripts/kaishi-home-accessibility.py');h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)
assert h.merge('null',True)==h.SERVICE
assert h.merge('vendor.reader/.Reader:'+h.SERVICE,False)=='vendor.reader/.Reader'
assert h.merge('vendor.reader/.Reader:other.voice/.Voice',True)=='vendor.reader/.Reader:other.voice/.Voice:'+h.SERVICE
assert h.merge(h.SERVICE+':'+h.SERVICE,True)==h.SERVICE
xml=ET.parse(r/'android/app/src/main/res/xml/kaishi_home_guard.xml').getroot();ns='{http://schemas.android.com/apk/res/android}'
assert xml.get(ns+'canRetrieveWindowContent')=='false'
assert xml.get(ns+'canRequestFilterKeyEvents')=='false'
assert xml.get(ns+'accessibilityEventTypes')=='typeWindowStateChanged'
assert xml.get(ns+'packageNames')=='com.mitv.tvhome,com.xiaomi.mitv.tvhome'
receipt={'policyOutput':result,'grantListPreservationChecks':4,'manifestCapabilityChecks':4,'backgroundActivityHandoffOnXiaomi':False,'firmwareAdRemoval':False}
(r/'docs/verification/s92-home-guard.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n');print(json.dumps(receipt,ensure_ascii=False))
