"""Shared ABI policy and inspection of actual APK payloads; no APK mutation."""
import json
import os
from pathlib import Path
import re
from zipfile import ZipFile

POLICY = json.loads((Path(__file__).resolve().parents[1] / 'android/abi-policy.json').read_text())


def configured_abis(value=None):
    if value is None:
        value = os.environ.get('APERTURE_ANDROID_ABIS')
    selected = list(POLICY['default']) if value is None else [item.strip() for item in value.split(',')]
    if not selected or len(set(selected)) != len(selected) or any(item not in POLICY['supported'] for item in selected):
        raise ValueError('Invalid APERTURE_ANDROID_ABIS; use a nonempty unique list from ' + ', '.join(POLICY['supported']))
    return selected


def payload_abi(name):
    match = re.match(r'^(?:lib|assets/chaquopy/bootstrap-native)/([^/]+)/', name)
    if match:
        return match[1]
    match = re.fullmatch(r'assets/chaquopy/(?:requirements|stdlib)-([^/]+)\.imy', name)
    return match[1] if match and match[1] != 'common' else None


def verify_apk_abis(apk, expected=None):
    expected = set(configured_abis() if expected is None else expected)
    with ZipFile(apk) as archive:
        names = [item.filename for item in archive.infolist() if not item.is_dir()]
    native = {name.split('/')[1] for name in names if name.startswith('lib/')}
    python = {payload_abi(name) for name in names if name.startswith('assets/chaquopy/') and payload_abi(name)}
    if native != expected or python != expected:
        raise ValueError('APK ABI payload differs from selected policy: native=' + str(sorted(native)) + ', python=' + str(sorted(python)) + ', expected=' + str(sorted(expected)))
    for abi in expected:
        for name in ['lib/'+abi+'/libpython3.10.so', 'assets/chaquopy/stdlib-'+abi+'.imy',
                     'assets/chaquopy/requirements-'+abi+'.imy', 'assets/chaquopy/bootstrap-native/'+abi+'/java/chaquopy.so']:
            if name not in names:
                raise ValueError('Incomplete Python ABI payload: ' + name)
    return sorted(expected)
