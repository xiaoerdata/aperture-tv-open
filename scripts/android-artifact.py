"""Validate actual Android tool output; never substitutes for installation evidence."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
from android_abis import verify_apk_abis

parser = argparse.ArgumentParser()
parser.add_argument('--directory', required=True)
args = parser.parse_args()
root = Path(args.directory)
apk = root / 'aperture-tv-debug.apk'
package = (root / 'package.txt').read_text()
signature = (root / 'signature.txt').read_text()
fields = dict(re.findall(r"(\w+)='([^']*)'", package.splitlines()[0]))
configuration = (Path(__file__).resolve().parents[1] / 'android/app/build.gradle').read_text()
expected_version = re.search(r"versionName\s+'([^']+)'", configuration).group(1)
expected_code = re.search(r'versionCode\s+(\d+)', configuration).group(1)
edition = os.environ.get('ANDROID_EDITION', 'standard')
assert edition in ('standard', 'parents'), 'unexpected edition'
assert fields.get('name') == re.search(r"applicationId\s+'([^']+)'", configuration).group(1) + ('.parents' if edition == 'parents' else ''), 'unexpected application id'
assert fields.get('versionName') == expected_version + ('-parents' if edition == 'parents' else ''), 'unexpected version'
assert fields.get('versionCode') == expected_code, 'unexpected version code'
assert "sdkVersion:'26'" in package, 'unexpected min SDK'
assert "targetSdkVersion:'35'" in package, 'unexpected target SDK'
assert 'launchable-activity:' in package, 'missing launcher activity'
assert 'Verifies' in signature, 'apksigner did not verify'
cert = re.search(r'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+)', signature)
assert cert and len(cert.group(1)) == 64, 'missing certificate SHA-256'
abis = verify_apk_abis(apk)
sha = hashlib.sha256(apk.read_bytes()).hexdigest()
metadata = {
    'format': 'aperture-private-android/v1',
    'applicationId': fields['name'],
    'edition': edition,
    'version': fields['versionName'],
    'versionCode': fields.get('versionCode'),
    'minSdk': 26,
    'targetSdk': 35,
    'variant': 'debug',
    'abis': abis,
    'buildEnvironment': os.environ.get('BUILD_ENVIRONMENT', 'github-actions'),
    'workingTreeDirty': {'true': True, 'false': False}.get(os.environ.get('WORKTREE_DIRTY', 'false')),
    'androidSourceSha256': os.environ.get('ANDROID_SOURCE_SHA256'),
    'privateTestingOnly': True,
    'sourceCommit': os.environ.get('SOURCE_SHA') or None,
    'buildCommit': os.environ.get('BUILD_SHA') or None,
    'apk': apk.name,
    'apkBytes': apk.stat().st_size,
    'sha256': sha,
    'certificateSha256': cert.group(1).lower(),
    'signatureVerified': True,
    'deviceInstalled': False,
    'xiaomiTested': False,
}
if os.environ.get('SOURCE_PROVENANCE'):
    metadata['sourceProvenance'] = json.loads(os.environ['SOURCE_PROVENANCE'])
(root / 'manifest.json').write_text(json.dumps(metadata, indent=2) + '\n')
(root / 'SHA256SUMS').write_text(sha + '  ' + apk.name + '\n')
print(json.dumps(metadata, indent=2))
