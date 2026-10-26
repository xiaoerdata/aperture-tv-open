"""Prepare a history-free local publication directory. Never pushes or changes visibility."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tarfile
import tempfile
from urllib.parse import quote, unquote, urlsplit, urlunsplit


def markdown_links(text):
    """Repository inline/image links and reference definitions; not a CommonMark parser."""
    masked, fence = [], None
    for line in text.splitlines(keepends=True):
        marker = re.match(r' {0,3}(`{3,}|~{3,})', line)
        if fence:
            masked.append(re.sub(r'[^\r\n]', ' ', line))
            if marker and marker[1][0] == fence[0] and len(marker[1]) >= len(fence):
                fence = None
        elif marker:
            fence = marker[1]
            masked.append(re.sub(r'[^\r\n]', ' ', line))
        else:
            masked.append(re.sub(r'(`+)([^`]|(?!\1)`)*?\1', lambda m: ' ' * len(m[0]), line))
    visible = ''.join(masked)
    patterns = [r'!?\[[^\]\n]*\]\(\s*(?:<([^>\n]+)>|([^\s)]+))',
                r'(?m)^[ \t]{0,3}\[[^\]\n]+\]:\s*(?:<([^>\n]+)>|([^\s]+))']
    for pattern in patterns:
        for match in re.finditer(pattern, visible):
            group = 1 if match[1] is not None else 2
            start, end = match.span(group)
            yield {'target': text[start:end], 'start': start, 'end': end,
                   'line': text.count('\n', 0, start) + 1}


def local_link(document, target):
    url = urlsplit(target)
    # Leading / paths are household-service routes, not repository files.
    if url.scheme or url.netloc or not url.path or url.path.startswith('/'):
        return None
    return (document.parent / unquote(url.path)).resolve()


def replace_links(text, change):
    for link in sorted(markdown_links(text), key=lambda item: item['start'], reverse=True):
        value = change(link['target'])
        if value is not None:
            text = text[:link['start']] + value + text[link['end']:]
    return text


def rebase_readme(template, destination):
    def change(target):
        path = local_link(template, target)
        if path is None:
            return None
        url = urlsplit(target)
        relative = Path(os.path.relpath(path, destination.parent)).as_posix()
        return urlunsplit(('', '', quote(relative, safe='/._-'), url.query, url.fragment))
    return replace_links(template.read_text(), change)


def public_download_links(source):
    """Keep historical text; only replace unavailable private download destinations."""
    private = source / 'private-preview'
    download = source / 'docs/DOWNLOADS.md'
    if not download.is_file():
        raise ValueError('Public download instructions are missing')
    changed = []
    for document in sorted(source.rglob('*.md')):
        paths = []
        def change(target):
            path = local_link(document, target)
            if path is None or not (path == private or private in path.parents):
                return None
            paths.append(target)
            return Path(os.path.relpath(download, document.parent)).as_posix()
        original = document.read_text()
        updated = replace_links(original, change)
        if updated != original:
            document.write_text(updated)
            changed.append({'path': document.relative_to(source).as_posix(), 'links': len(paths)})
    return changed


def documentation_links(source):
    source = source.resolve()
    missing, checked, documents = [], 0, 0
    for document in sorted(source.rglob('*.md')):
        documents += 1
        for link in markdown_links(document.read_text()):
            path = local_link(document, link['target'])
            if path is None:
                continue
            checked += 1
            if source not in path.parents or not path.is_file():
                missing.append({'document': document.relative_to(source).as_posix(),
                                'line': link['line'], 'target': link['target']})
    return {'documents': documents, 'localFileLinksChecked': checked, 'missing': missing,
            'scope': 'Inline Markdown links/images and reference definitions; file existence only. Excludes code, anchors, service routes and external URL availability.'}


def safe_members(archive):
    seen, total, members = set(), 0, []
    for item in archive.getmembers():
        parts = PurePosixPath(item.name).parts
        if not parts or parts[0] != 'aperture-tv' or '..' in parts or item.name.startswith('/') or item.name in seen:
            raise ValueError('Invalid or duplicate archive path')
        seen.add(item.name)
        if not (item.isdir() or item.isfile()) or item.size < 0:
            raise ValueError('Archive links and special files are forbidden')
        if any(p in {'.git', 'private-preview', '.data', '.env', 'node_modules', '.android-local', 'local.properties'} for p in parts):
            raise ValueError('Private/runtime path in source')
        total += item.size
        # Development screenshots and page-by-page reference art are part of the public record.
        # Keep a bounded extraction budget as that record grows beyond the v19 600 MiB limit.
        if total > 768 * 1024 * 1024 or len(seen) > 10000:
            raise ValueError('Archive exceeds publication budget')
        members.append(item)
    return members


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path)
    parser.add_argument('--directory', type=Path)
    parser.add_argument('--android-sha256')
    parser.add_argument('--check-links', type=Path, help='Read-only documentation link check in an extracted public directory')
    args = parser.parse_args()
    if args.check_links is not None:
        if args.archive is not None or args.directory is not None or args.android_sha256 is not None or not args.check_links.is_dir():
            parser.error('--check-links requires an existing directory and no preparation options')
        result = documentation_links(args.check_links)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        raise SystemExit(1 if result['missing'] else 0)
    if args.archive is None or args.directory is None or args.android_sha256 is None:
        parser.error('Preparation requires --archive, --directory and --android-sha256')
    root = Path(__file__).resolve().parents[1]
    artifacts = (root / 'artifacts').resolve()
    output = args.directory.resolve()
    if output == artifacts or artifacts not in output.parents or not all(c in '0123456789abcdef' for c in args.android_sha256) or len(args.android_sha256) != 64:
        raise ValueError('Use a new directory inside artifacts and an Android input digest')
    bundle = output.with_name(output.name + '.tar.gz')
    receipt = output.with_name(output.name + '.json')
    if any(p.exists() or p.is_symlink() for p in [output, bundle, receipt]):
        raise ValueError('Publication output already exists; preserved unchanged')
    artifacts.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='aperture-public-', dir=artifacts) as temp:
        temp = Path(temp)
        with tarfile.open(args.archive, 'r:gz') as archive:
            for item in safe_members(archive):
                path = temp / item.name
                if item.isdir():
                    path.mkdir(parents=True, exist_ok=True)
                else:
                    path.parent.mkdir(parents=True, exist_ok=True)
                    with path.open('xb') as target, archive.extractfile(item) as source:
                        shutil.copyfileobj(source, target)
                    path.chmod(0o755 if item.mode & 0o111 else 0o644)
        source = temp / 'aperture-tv'
        manifest = json.loads((source / 'SOURCE-MANIFEST.json').read_text())
        expected = {item['path']: item for item in manifest['files']}
        actual = {p.relative_to(source).as_posix(): p for p in source.rglob('*') if p.is_file() and p.name != 'SOURCE-MANIFEST.json'}
        if len(expected) != len(manifest['files']) or set(actual) != set(expected):
            raise ValueError('Source manifest inventory mismatch')
        for name, path in actual.items():
            if path.stat().st_size != expected[name]['bytes'] or hashlib.sha256(path.read_bytes()).hexdigest() != expected[name]['sha256']:
                raise ValueError('Source manifest content mismatch')
        (source / 'README.md').write_text(rebase_readme(source / 'docs/release/README-public.md', source / 'README.md'))
        install = source / 'INSTALL.md'
        text = install.read_text()
        old = '内测候选见仓库的[下载索引](private-preview/README.md)，公开源码不包含该私有目录；没有发行包时可按[免费本地构建](docs/ANDROID-LOCAL.md)自己生成。'
        if old not in text:
            raise ValueError('Public installation index needs review')
        install.write_text(text.replace(old, 'Alpha测试下载见[下载与版本](docs/DOWNLOADS.md)，也可按[免费本地构建](docs/ANDROID-LOCAL.md)自己生成。'))
        rewritten = public_download_links(source)
        links = documentation_links(source)
        if links['missing']:
            raise ValueError('Public documentation has missing local files: ' + json.dumps(links['missing'], ensure_ascii=False))
        module = (root / 'scripts/android-source.mjs').as_uri()
        code = 'import {androidSource} from ' + json.dumps(module) + ';process.stdout.write((await androidSource(process.argv[1])).sha256)'
        android = subprocess.check_output(['node', '--input-type=module', '-e', code, str(source)], text=True).strip()
        if android != args.android_sha256:
            raise ValueError('Public Android inputs differ from the testing APK source')
        entries = [{'path': p.relative_to(source).as_posix(), 'bytes': p.stat().st_size, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()}
                   for p in sorted(source.rglob('*')) if p.is_file() and p.name != 'SOURCE-MANIFEST.json']
        new_manifest = {'format': 'aperture-public-source/v1', 'version': manifest['version'], 'source': manifest['source'],
                        'derivation': {'readmeReplaced': True, 'publicInstallationIndex': True, 'historyIncluded': False,
                                       'privateDownloadLinksReplaced': rewritten}, 'files': entries}
        (source / 'SOURCE-MANIFEST.json').write_text(json.dumps(new_manifest, ensure_ascii=False, indent=2) + '\n')
        archive_path = temp / 'public.tar.gz'
        with tarfile.open(archive_path, 'w:gz') as archive:
            archive.add(source, arcname='aperture-tv')
        # Exclusive directory reservation and hard-link publication keep older outputs immutable.
        output.mkdir()
        try:
            for child in source.iterdir():
                shutil.move(str(child), output / child.name)
            os.link(archive_path, bundle)
            result = {'version': manifest['version'], 'upstreamSource': manifest['source'], 'publicSourceFiles': len(entries),
                      'androidSourceSha256': android, 'androidMatchesTestingApks': True, 'archiveBytes': bundle.stat().st_size,
                      'archiveSha256': hashlib.sha256(bundle.read_bytes()).hexdigest(), 'historyIncluded': False,
                      'privatePreviewIncluded': False, 'documentationLinks': links, 'privateDownloadLinksReplaced': rewritten,
                      'remotePublished': False, 'hardwareAcceptance': 'maintainer to test WorkBuddy and Xiaomi TV'}
            with receipt.open('x') as file:
                json.dump(result, file, ensure_ascii=False, indent=2)
                file.write('\n')
        except Exception:
            shutil.rmtree(output)
            raise
        print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
