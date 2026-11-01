"""Explicit-device ADB helper for the optional Kaishi accessibility grant. No shell or debloat."""
import argparse, json, subprocess

SERVICE='org.zhixiaoce.kaishi/org.zhixiaoce.aperture.HomeGuardService'
def normalized(value):
    if '/' not in value:return value
    pkg,name=value.split('/',1)
    return pkg+'/'+(pkg+name if name.startswith('.') else name)
def merge(raw,grant):
    result=[]
    for item in raw.strip().split(':'):
        if not item or item=='null' or normalized(item)==SERVICE:continue
        if item not in result:result.append(item)
    if grant:result.append(SERVICE)
    return ':'.join(result)
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True);p.add_argument('--adb',default='adb');p.add_argument('action',choices=['status','grant','revoke'],default='status',nargs='?');a=p.parse_args()
    def adb(*args):return subprocess.check_output([a.adb,'-s',a.serial,*args],text=True,stderr=subprocess.PIPE,timeout=25).strip()
    def get(k):return adb('shell','settings','get','secure',k)
    def put(k,v):
        # The remote adb shell still parses arguments. Only permit exact system-setting values.
        if k not in ['enabled_accessibility_services','accessibility_enabled'] or not all(c.isalnum() or c in '._/:' for c in v):raise ValueError('Invalid secure setting')
        adb('shell','settings','put','secure',k,v)
    if a.action=='grant' and not adb('shell','pm','path','org.zhixiaoce.kaishi').startswith('package:'):raise RuntimeError('Install standard Kaishi before granting')
    before=get('enabled_accessibility_services');enabled=get('accessibility_enabled');wanted=merge(before,a.action=='grant')
    if a.action!='status':
        if not wanted:
            # Removing the final service does not require disabling global accessibility.
            adb('shell','settings','delete','secure','enabled_accessibility_services')
        else:put('enabled_accessibility_services',wanted)
        if a.action=='grant' and enabled!='1':put('accessibility_enabled','1')
        after=get('enabled_accessibility_services')
        got={normalized(x) for x in after.split(':') if x and x!='null'}
        expected={normalized(x) for x in wanted.split(':') if x}
        if got!=expected or (a.action=='grant' and get('accessibility_enabled')!='1'):raise RuntimeError('System settings readback differs; inspect the television, do not assume granted')
    else:after=before
    print(json.dumps({'action':a.action,'kaishiListed':any(normalized(x)==SERVICE for x in after.split(':')),'systemAccessibilityEnabled':get('accessibility_enabled')=='1','deviceConnection':'explicit-serial','settingsReadbackOnly':True,'realHomeHandoffNotTested':True}))
if __name__=='__main__':main()
