"""Compare an actual private APK with the launcher inputs and previous candidate.
No APK build, signing, installation, credentials or network calls are performed.
"""
import argparse
import hashlib
import json
import re
import zipfile
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--directory', type=Path, required=True)
parser.add_argument('--previous', type=Path, required=True, help='previous manifest for this edition')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
metadata = json.loads((args.directory / 'manifest.json').read_text())
previous = json.loads(args.previous.read_text())
apk = args.directory / metadata['apk']
assert apk.parent.resolve() == args.directory.resolve(), 'APK must be in candidate directory'
assert metadata['applicationId'] == previous['applicationId'], 'application id changed'
assert metadata['edition'] == previous['edition'], 'edition changed'
assert metadata['certificateSha256'] == previous['certificateSha256'], 'debug signing key changed; cannot claim in-place upgrade'
expected = int(re.search(r'versionCode\s+(\d+)', (root / 'android/app/build.gradle').read_text()).group(1))
assert int(metadata['versionCode']) == expected, 'APK version does not match source'
assert expected > int(previous['versionCode']), 'not a version upgrade'
assert metadata['workingTreeDirty'] is False, 'candidate must be from a clean source commit'
current_inputs = subprocess.check_output(['node', '--input-type=module', '-e', "import {androidSource} from './scripts/android-source.mjs';process.stdout.write((await androidSource(process.cwd())).sha256)"], cwd=root, text=True)
assert current_inputs == metadata['androidSourceSha256'], 'Android source inputs changed since APK build'
assert metadata['signatureVerified'] is True, 'build did not verify APK signature'
assert hashlib.sha256(apk.read_bytes()).hexdigest() == metadata['sha256'], 'APK hash mismatch'
assets = []
with zipfile.ZipFile(apk) as bundle:
    assert 'classes.dex' in bundle.namelist(), 'missing native program'
    for path in sorted((root / 'public/launcher').rglob('*')):
        if not path.is_file():
            continue
        name = 'assets/' + path.relative_to(root / 'public/launcher').as_posix()
        data = bundle.read(name)
        assert data == path.read_bytes(), 'stale bundled launcher: ' + name
        assets.append({'path': name, 'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)})
    notices = []
    for path in sorted((root / 'android/third-party').glob('*')):
        if not path.is_file():
            continue
        name = 'assets/' + path.name
        data = bundle.read(name)
        assert data == path.read_bytes(), 'stale bundled third-party notice: ' + name
        notices.append({'path': name, 'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)})
    dex = b''.join(bundle.read(name) for name in bundle.namelist() if re.fullmatch(r'classes\d*\.dex', name))
    native_types = ['org/zhixiaoce/aperture/CinemaActivity', 'org/zhixiaoce/aperture/CinemaCatalog', 'org/zhixiaoce/aperture/CinemaHttp', 'org/zhixiaoce/aperture/CinemaCipher', 'org/zhixiaoce/aperture/CinemaVault', 'org/zhixiaoce/aperture/NasActivity', 'org/zhixiaoce/aperture/NasVault', 'org/zhixiaoce/aperture/NasLibrary', 'org/zhixiaoce/aperture/NasCipher', 'org/zhixiaoce/aperture/NasNavigation', 'org/zhixiaoce/aperture/NasDraft', 'org/zhixiaoce/aperture/NasSetupActivity', 'org/zhixiaoce/aperture/NasSetupClient', 'org/zhixiaoce/aperture/NasSetupCrypto', 'org/zhixiaoce/aperture/NasSetupImport', 'org/zhixiaoce/aperture/PlaybackSession', 'org/zhixiaoce/aperture/PlaybackAudioFocus', 'org/zhixiaoce/aperture/HouseholdProxy', 'com/hierynomus/smbj/SMBClient', 'okhttp3/OkHttpClient', 'org/zhixiaoce/aperture/ApkInstaller', 'org/zhixiaoce/aperture/InstallCache', 'org/zhixiaoce/aperture/InstallFileProvider', 'org/zhixiaoce/aperture/AppAgentActivity', 'org/zhixiaoce/aperture/HouseholdCommandClient']
    for name in native_types:
        assert ('L' + name + ';').encode() in dex, 'missing packaged native type: ' + name
report = {
    'format': 'aperture-private-upgrade-check/v1',
    'edition': metadata['edition'], 'applicationId': metadata['applicationId'],
    'sourceCommit': metadata['sourceCommit'], 'versionCode': expected,
    'previousVersionCode': int(previous['versionCode']), 'sameSigningCertificate': True,
    'apkSha256': metadata['sha256'], 'assetsMatchSource': True, 'assets': assets,
    'thirdPartyNoticesMatchSource': True, 'notices': notices, 'nativeTypesPresent': native_types,
    'androidSourceInputsMatch': True,
    'deviceInstalled': False, 'actualUpgradeTested': False, 'published': False,
}
(args.directory / 'upgrade-check.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print(json.dumps({k: v for k, v in report.items() if k != 'assets'}, ensure_ascii=False, indent=2))
