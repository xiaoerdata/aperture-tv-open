"""Sign the two actual release variants using a separately managed stable identity.

Never generates keys, prints passwords, overwrites an output directory, or uploads.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile
from io import BytesIO
from PIL import Image

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', required=True, type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
out = args.output.resolve()
assert root / 'artifacts' in out.parents, 'Output must be under artifacts'
assert not out.exists(), 'Preserve existing outputs; choose a new directory'
env = os.environ
required = ['JAVA_HOME', 'ANDROID_HOME', 'APERTURE_RELEASE_KEYSTORE', 'APERTURE_RELEASE_ALIAS',
            'APERTURE_RELEASE_STORE_PASSWORD_FILE', 'APERTURE_RELEASE_KEY_PASSWORD_FILE', 'APERTURE_RELEASE_CERT_SHA256']
for name in required:
    assert env.get(name), 'Missing environment setting: ' + name
for name in ['APERTURE_RELEASE_KEYSTORE', 'APERTURE_RELEASE_STORE_PASSWORD_FILE', 'APERTURE_RELEASE_KEY_PASSWORD_FILE']:
    path = Path(env[name]).resolve()
    assert root not in path.parents, 'Signing secrets must be outside source and artifacts'
    assert path.is_file() and path.stat().st_mode & 0o077 == 0, 'Signing file must be private'
expected_cert = env['APERTURE_RELEASE_CERT_SHA256'].lower()
assert re.fullmatch('[a-f0-9]{64}', expected_cert), 'Invalid expected certificate'
tools = Path(env['ANDROID_HOME']) / 'build-tools/35.0.0'
configuration = (root / 'android/app/build.gradle').read_text()
app_id = re.search(r"applicationId\s+'([^']+)'", configuration)[1]
code = int(re.search(r'versionCode\s+(\d+)', configuration)[1])
version = re.search(r"versionName\s+'([^']+)'", configuration)[1]
inputs = json.loads(subprocess.check_output(['node', '--input-type=module', '-e',
    "import {androidSource} from './scripts/android-source.mjs';console.log(JSON.stringify(await androidSource(process.cwd())))"], cwd=root))

def run(arguments):
    return subprocess.check_output([str(value) for value in arguments], cwd=root, env=env, stderr=subprocess.STDOUT).decode()

out.mkdir()
editions = []
for edition in ['standard', 'parents']:
    source = root / f'android/app/build/outputs/apk/{edition}/release/app-{edition}-release-unsigned.apk'
    aligned = out / f'{edition}-aligned-unsigned.apk'
    apk = out / f'kaishi-v{code}-{edition}.apk'
    run([tools/'zipalign', '-P', '16', '4', source, aligned])
    run([tools/'apksigner', 'sign', '--ks', env['APERTURE_RELEASE_KEYSTORE'], '--ks-key-alias', env['APERTURE_RELEASE_ALIAS'],
         '--ks-pass', 'file:'+env['APERTURE_RELEASE_STORE_PASSWORD_FILE'], '--key-pass', 'file:'+env['APERTURE_RELEASE_KEY_PASSWORD_FILE'],
         '--debuggable-apk-permitted', 'false', '--v2-signing-enabled', 'true', '--v4-signing-enabled', 'false',
         '--alignment-preserved', 'true', '--in', aligned, '--out', apk])
    run([tools/'zipalign', '-c', '-P', '16', '4', apk])
    signature = run([tools/'apksigner', 'verify', '--min-sdk-version', '26', '--verbose', '--print-certs', apk])
    cert = re.search(r'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+)', signature)[1].lower()
    assert cert == expected_cert, 'Unexpected signing identity'
    package = run([tools/'aapt', 'dump', 'badging', apk])
    fields = dict(re.findall(r"(\w+)='([^']*)'", package.splitlines()[0]))
    assert fields['name'] == app_id + ('.parents' if edition == 'parents' else '')
    assert fields['versionName'] == version + ('-parents' if edition == 'parents' else '')
    assert fields['versionCode'] == str(code)
    assert "sdkVersion:'26'" in package and "targetSdkVersion:'35'" in package
    assert 'application-debuggable' not in package, 'Release must not be debuggable'
    resources = run([tools/'aapt', 'dump', '--values', 'resources', apk])
    binary_manifest = run([tools/'aapt', 'dump', 'xmltree', apk, 'AndroidManifest.xml'])
    asset_count = 0
    with zipfile.ZipFile(apk) as archive:
        for path in (root/'public/launcher').rglob('*'):
            if path.is_file():
                relative = path.relative_to(root/'public/launcher').as_posix()
                assert archive.read('assets/'+relative) == path.read_bytes(), 'Packaged asset mismatch: '+relative
                asset_count += 1
        for name in ['kaishi_icon_v2.png','kaishi_banner_v2.png']:
            resource = re.search(r'(?m)^\s*resource (0x[0-9a-f]+) [^\n]+:drawable/'+re.escape(name[:-4])+r':[^\n]*\n\s*\(string8\) \"([^\"]+)\"', resources)
            assert resource, 'Missing packaged brand resource: '+name
            attribute = 'icon' if name.startswith('kaishi_icon') else 'banner'
            assert re.search(r'android:'+attribute+r'\([^)]*\)=@'+re.escape(resource[1]), binary_manifest), 'Manifest does not reference selected brand resource'
            packaged = resource[2]
            override = root/f'android/app/src/{edition}/res/drawable-nodpi'/name
            original = override if override.is_file() else root/'android/app/src/main/res/drawable-nodpi'/name
            with Image.open(BytesIO(archive.read(packaged))) as actual, Image.open(original) as expected:
                assert actual.size == expected.size and actual.convert('RGBA').tobytes() == expected.convert('RGBA').tobytes(), 'Packaged brand image mismatch: '+name
    directory = out / edition
    directory.mkdir()
    (directory/'signature.txt').write_text(signature)
    (directory/'package.txt').write_text(package)
    # Per-edition metadata can also be consumed by the binary manifest inspector.
    metadata = {'applicationId':fields['name'], 'edition':edition, 'version':fields['versionName'], 'versionCode':code,
                'variant':'release', 'debuggable':False, 'minSdk':26, 'targetSdk':35, 'apk':apk.name,
                'apkBytes':apk.stat().st_size, 'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
                'certificateSha256':cert, 'signatureVerified':True, 'androidSourceSha256':inputs['sha256'],
                'packagedBrandPixelsVerified':True, 'packagedLauncherFilesVerified':asset_count, 'xiaomiTested':False, 'workBuddyClientTested':False}
    (directory/'manifest.json').write_text(json.dumps({**metadata,'apk':'../'+apk.name},indent=2)+'\n')
    editions.append(metadata)
    aligned.unlink()
manifest = {'format':'kaishi-alpha-release/v1', 'version':version, 'versionCode':code, 'xiaomiOnly':True,
            'migration':'side-by-side; original v17 data retained; pair and configure NAS again', 'editions':editions,
            'androidInputs':inputs, 'githubHostedRunnerUsed':False, 'uploaded':False}
(out/'testing-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
files = [out/item['apk'] for item in editions]+[out/'testing-manifest.json']
(out/'SHA256SUMS').write_text(''.join(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name+'\n' for path in files))
print(json.dumps({'output':str(out),'version':version,'certificateSha256':expected_cert,'editions':editions},indent=2))
