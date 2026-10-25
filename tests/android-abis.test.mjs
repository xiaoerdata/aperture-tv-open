import test from 'node:test';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {androidAbis,androidAbiPolicy} from '../scripts/android-abis.mjs';

test('ABI override accepts explicit emulator targets and rejects empty, unknown or duplicate targets',()=>{
 assert.deepEqual(androidAbis('x86,x86_64'),['x86','x86_64']);
 assert.deepEqual(androidAbis(androidAbiPolicy.default.join(',')),['armeabi-v7a','arm64-v8a']);
 for(const value of ['',',','arm64-v8a,','arm64-v8a,arm64-v8a','riscv64','x86;other'])assert.throws(()=>androidAbis(value));
});

test('APK payload inspection keeps shared Python data and rejects stray architectures or incomplete runtimes',()=>{
 const output=execFileSync('python3',['-c',String.raw`
import sys,tempfile
from pathlib import Path
from zipfile import ZipFile
sys.path.insert(0,'scripts')
from android_abis import POLICY,payload_abi,verify_apk_abis,configured_abis
assert payload_abi('assets/chaquopy/requirements-common.imy') is None
assert payload_abi('assets/chaquopy/stdlib-common.imy') is None
assert configured_abis('x86')==['x86']
for invalid in ['',',','arm64-v8a,','x86,x86','riscv64']:
    try:configured_abis(invalid);raise AssertionError('invalid override accepted')
    except ValueError:pass
with tempfile.TemporaryDirectory() as d:
    apk=Path(d)/'synthetic.apk'
    def fixture(extra=None,omit=None):
        names=['assets/chaquopy/requirements-common.imy','assets/chaquopy/stdlib-common.imy']
        for abi in POLICY['default']:
            names.extend(['lib/'+abi+'/libpython3.10.so','assets/chaquopy/stdlib-'+abi+'.imy','assets/chaquopy/requirements-'+abi+'.imy','assets/chaquopy/bootstrap-native/'+abi+'/java/chaquopy.so'])
        if extra:names.append(extra)
        with ZipFile(apk,'w') as z:
            for name in names:
                if name!=omit:z.writestr(name,b'synthetic fixture only')
    fixture();assert verify_apk_abis(apk,POLICY['default'])==sorted(POLICY['default'])
    for extra,omit in [('assets/chaquopy/requirements-x86.imy',None),('lib/x86/libpython3.10.so',None),(None,'assets/chaquopy/requirements-arm64-v8a.imy')]:
        fixture(extra,omit)
        try:verify_apk_abis(apk,POLICY['default']);raise AssertionError('bad payload accepted')
        except ValueError:pass
print('Shared data retained; wrong ABI and incomplete Python payloads rejected. Synthetic ZIPs only.')
`],{encoding:'utf8'});
 assert.match(output,/Shared data retained/);
});
