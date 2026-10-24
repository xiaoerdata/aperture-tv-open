"""Offline, redacted pre-publication inventory. This does not approve a release.

Review every locally reachable commit with --history, or a frozen --archive.
Only file paths, Git object IDs and rule names are recorded, never matched values.
Binary assets and unknown credential formats still require human review.
"""
import argparse
import hashlib
import json
import re
import subprocess
import tarfile
from pathlib import Path, PurePosixPath

RULES = [
    ('github-token-pattern', re.compile(rb'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})\b')),
    ('provider-key-pattern', re.compile(rb'\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{40,}\b')),
    ('aws-access-id-pattern', re.compile(rb'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b')),
    ('private-key-block', re.compile(rb'-----BEGIN (?:(?:RSA|EC|DSA|OPENSSH|ENCRYPTED) )?PRIVATE KEY-----')),
    ('private-ci-evidence', re.compile(re.escape(b'recent account payments ' + b'have failed'))),
]
PRIVATE_PARTS = {'.data', '.env', '.venv', 'node_modules', '.android-local', 'integrations', '.git', '.gradle', '__pycache__'}
PRIVATE_SUFFIXES = {'.jks', '.keystore', '.pem', '.p12', '.pfx', '.key'}
BINARY_SUFFIXES = {'.png', '.jpg', '.jpeg', '.woff2', '.otf', '.ttf', '.mp4', '.webm', '.apk', '.zip', '.gz', '.pdf', '.jar'}
MAX_TEXT_BYTES = 2 * 1024 * 1024


def git(root, *args):
    # Never return Git stderr, which may contain private configuration or remote URLs.
    result = subprocess.run(['git', '-C', str(root), *args], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    if result.returncode:
        raise RuntimeError('Git read failed: ' + args[0])
    return result.stdout


def scan(data):
    return [{'rule': label, 'count': len(pattern.findall(data))} for label, pattern in RULES if pattern.search(data)]


def redacted_path(path):
    data = path.encode('utf-8', errors='surrogateescape')
    for label, pattern in RULES:
        data = pattern.sub(('<redacted:' + label + '>').encode(), data)
    return data.decode('utf-8', errors='surrogateescape')


def sensitive_path(path):
    parts = PurePosixPath(path).parts
    return any(part in PRIVATE_PARTS or part.startswith('.env.') for part in parts) or PurePosixPath(path).suffix.lower() in PRIVATE_SUFFIXES


def inspect(entries, get_bytes):
    findings, sensitive, evidence_paths, manual, scanned = [], [], [], [], 0
    for entry in entries:
        path = entry['path']
        location = {key: redacted_path(value) if key in ('path', 'member') else value
                    for key, value in entry.items() if key != 'size'}
        if sensitive_path(path):
            sensitive.append(location)
        if path.startswith('private-preview/process/'):
            evidence_paths.append(location)
        if PurePosixPath(path).suffix.lower() in BINARY_SUFFIXES or entry['size'] > MAX_TEXT_BYTES:
            manual.append({'path': path, 'object': entry.get('object'), 'reason': 'binary-or-large'})
            continue
        data = get_bytes(entry)
        if b'\0' in data:
            manual.append({'path': path, 'object': entry.get('object'), 'reason': 'binary-content'})
            continue
        scanned += 1
        matches = scan(data)
        if matches:
            findings.append({**location, 'rules': matches})
    return {'textEntriesScanned': scanned, 'patternFindings': findings, 'sensitivePaths': sensitive,
            'privateEvidencePaths': evidence_paths, 'binaryOrLargeEntriesNotTextScanned': len(manual),
            'manualAssetReviewRequired': True}


def review_git(root, history):
    root = root.resolve()
    if Path(git(root, 'rev-parse', '--show-toplevel').decode().strip()).resolve() != root:
        raise ValueError('Run from the independent Aperture repository')
    commits = git(root, 'rev-list', '--all' if history else 'HEAD').decode().splitlines()
    if not history:
        commits = commits[:1]
    objects = {}
    private_distribution_paths = set()
    special = []
    for commit in commits:
        for raw in git(root, 'ls-tree', '-rz', '--full-tree', commit).split(b'\0'):
            if not raw:
                continue
            info, path = raw.split(b'\t', 1)
            mode, kind, oid = info.decode().split()
            path = path.decode('utf-8', errors='surrogateescape')
            if path.startswith('private-preview/'):
                private_distribution_paths.add(path)
            if kind != 'blob' or mode == '120000':
                special.append({'path': redacted_path(path), 'commit': commit, 'mode': mode})
                continue
            key = (oid, path)
            item = objects.setdefault(key, {'object': oid, 'path': path, 'commits': []})
            item['commits'].append(commit)
    unique_ids = sorted({item['object'] for item in objects.values()})
    process = subprocess.run(['git', '-C', str(root), 'cat-file', '--batch-check=%(objectname) %(objectsize)'],
                             input=('\n'.join(unique_ids) + '\n').encode(), stdout=subprocess.PIPE,
                             stderr=subprocess.DEVNULL, check=True)
    size_map = {line.split()[0]: int(line.split()[1]) for line in process.stdout.decode().splitlines()}
    for item in objects.values():
        item['size'] = size_map[item['object']]
    # A blob can appear at several paths/commits. Cache only text candidates.
    cache = {}
    def read(entry):
        oid = entry['object']
        if oid not in cache:
            cache[oid] = git(root, 'cat-file', 'blob', oid)
        return cache[oid]
    report = inspect(objects.values(), read)
    return {'scope': 'all-local-refs' if history else 'HEAD', 'head': git(root, 'rev-parse', 'HEAD').decode().strip(),
            'commitsVisited': len(commits), 'uniqueBlobsInventoried': len(unique_ids),
            'pathAndBlobVersions': len(objects), 'privateDistributionPaths': len(private_distribution_paths),
            'specialGitEntries': special, **report}


def review_archive(path):
    path = path.resolve()
    with tarfile.open(path, 'r:gz') as archive:
        entries, seen = [], set()
        for member in archive.getmembers():
            parts = PurePosixPath(member.name).parts
            if member.name.startswith('/') or '..' in parts or member.name in seen or member.issym() or member.islnk():
                raise ValueError('Unsafe or duplicate archive member')
            seen.add(member.name)
            if member.isdir():
                continue
            if not member.isfile() or not parts or parts[0] != 'aperture-tv':
                raise ValueError('Unexpected archive member')
            entries.append({'path': '/'.join(parts[1:]), 'size': member.size, 'member': member.name})
        report = inspect(entries, lambda entry: archive.extractfile(entry['member']).read())
        # No temporary extraction or filesystem writes are necessary.
        for finding in report['patternFindings'] + report['sensitivePaths'] + report['privateEvidencePaths']:
            finding.pop('member', None)
        return {'scope': 'frozen-source-archive', 'archiveName': redacted_path(path.name),
                'archiveSha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'filesInventoried': len(entries),
                'privateDistributionPaths': sum(item['path'].startswith('private-preview/') for item in entries), **report}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--history', action='store_true')
    mode.add_argument('--archive', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = review_archive(args.archive) if args.archive else review_git(Path.cwd(), args.history)
    report = {'format': 'aperture-publication-inventory/v1', **report,
              'patternRules': [label for label, _ in RULES], 'maxTextBytes': MAX_TEXT_BYTES,
              'remoteRefsFetched': False, 'unreachableGitObjectsReviewed': False,
              'matchedValuesIncluded': False, 'publicationApproved': False,
              'limitations': ['Pattern matches require classification; a match is not a confirmed credential.',
                              'Pattern detection cannot prove absence of secrets or private information.',
                              'Binary assets, provenance, consent and licenses require human review.',
                              'Local refs do not establish the complete remote ref inventory.']}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=True, indent=2) + '\n')
    print(json.dumps({key: report[key] for key in ['scope', 'textEntriesScanned', 'privateDistributionPaths', 'binaryOrLargeEntriesNotTextScanned', 'publicationApproved']} | {
        'patternFindingLocations': len(report['patternFindings']), 'sensitivePathLocations': len(report['sensitivePaths']),
        'privateEvidenceLocations': len(report['privateEvidencePaths'])}, indent=2))


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError, tarfile.TarError):
        raise SystemExit('Publication inventory failed; no matched file content is printed.')
