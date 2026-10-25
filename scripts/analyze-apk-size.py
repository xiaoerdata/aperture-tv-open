"""Read existing APK ZIP sizes and estimate ABI pruning; never rebuilds or rewrites APKs."""
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile
from android_abis import POLICY, configured_abis, payload_abi, verify_apk_abis

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--apk', type=Path, nargs='+', required=True)
parser.add_argument('--abis', help='Target ABIs; defaults to the project policy')
parser.add_argument('--output', type=Path)
args = parser.parse_args()
selected = configured_abis(args.abis)
if args.output:
    if args.output.suffix != '.json' or args.output.exists():
        parser.error('Choose a new .json output; existing evidence is preserved')
reports = []
for apk in args.apk:
    digest = hashlib.sha256()
    with apk.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024*1024), b''):
            digest.update(chunk)
    groups = defaultdict(lambda: {'compressedBytes': 0, 'uncompressedBytes': 0, 'files': 0})
    with ZipFile(apk) as archive:
        entries = [item for item in archive.infolist() if not item.is_dir()]
    actual = sorted({item.filename.split('/')[1] for item in entries if item.filename.startswith('lib/')})
    verify_apk_abis(apk, actual)
    if not set(selected).issubset(actual):
        raise ValueError('Cannot estimate a target ABI missing from the baseline APK')
    removed = []
    for item in entries:
        name, abi = item.filename, payload_abi(item.filename)
        if abi:
            category = ('native/' if name.startswith('lib/') else 'python/') + abi
            if abi not in selected:
                removed.append(item)
        elif name.startswith('assets/chaquopy/'):
            category = 'python/shared'
        elif name.startswith('assets/') and name.endswith('-4k.jpg'):
            category = 'wallpapers/4k'
        elif name.startswith('assets/'):
            category = 'launcher-and-other-assets'
        elif name.endswith('.dex'):
            category = 'java-and-dependencies'
        else:
            category = 'other'
        group = groups[category]
        group['compressedBytes'] += item.compress_size
        group['uncompressedBytes'] += item.file_size
        group['files'] += 1
    size = apk.stat().st_size
    saving = sum(item.compress_size for item in removed)
    reports.append({'apk': apk.name, 'sha256': digest.hexdigest(), 'apkBytes': size, 'actualAbis': actual,
                    'targetAbis': selected, 'measuredRemovableCompressedBytes': saving,
                    'estimatedRemainingBytes': size-saving, 'estimatedReductionPercent': round(100*saving/size, 2),
                    'removedEntryCount': len(removed), 'baselinePayloadComplete': True,
                    'groups': dict(sorted(groups.items(), key=lambda pair: pair[1]['compressedBytes'], reverse=True)),
                    'largestEntries': [{'path': item.filename, 'compressedBytes': item.compress_size} for item in sorted(entries, key=lambda entry: entry.compress_size, reverse=True)[:12]]})
report = {'format': 'kaishi-apk-size-analysis/v1', 'method': 'Read-only central-directory compressed byte accounting of existing signed APKs',
          'policy': POLICY, 'packages': reports, 'apkBuilt': False, 'apkModified': False, 'uploaded': False,
          'estimateLimit': 'Not a new APK size measurement. ZIP alignment, generated metadata, signatures and current unpublished artwork can change the eventual bytes.'}
text = json.dumps(report, ensure_ascii=False, indent=2)+'\n'
if args.output:
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(text)
print(text)
