"""Read-only source comparison of released v27-v33 launcher geometry and routing.

Uses the existing reviewed public Git history, never checks out or changes product files.
Uses the published remote v33 commit and checks its recorded source tree.
"""
from pathlib import Path
import hashlib, json, re, subprocess, os

root = Path(__file__).resolve().parents[1]
repo = Path(os.environ.get('KAISHI_HISTORY_REPO', str(root)))
refs = {
    27: 'df4df3192765fa2904dcde083857ae92da477b68',
    28: 'eca34d613e0c80b28e23190ff32fecbd2c333781',
    29: '1836e72766028b27d65dacf93ee7614383eff074',
    30: '097e9911fbc17261d14559ffa59c7c77fdb53a38',
    33: 'a7024479461e59b28591491fcd7e7e8f94441ac3',
}
paths = ['public/launcher/index.html', 'public/launcher/launcher.js',
         'public/launcher/style.css', 'public/launcher/ambient.js',
         'android/app/src/main/java/org/zhixiaoce/aperture/MainActivity.java',
         'android/app/src/main/java/org/zhixiaoce/aperture/LobbyMode.java',
         'android/app/src/main/AndroidManifest.xml']
def git(*args):
    return subprocess.check_output(['git', '-C', str(repo), *args]).decode()
def sha(value):
    return hashlib.sha256(value.encode()).hexdigest()
records = []
for version, commit in refs.items():
    contents = {path: git('show', commit + ':' + path) for path in paths}
    main = contents[paths[4]]
    fit = re.search(r'function fitTelevision\(\).*?\n  }', contents[paths[1]], re.S).group(0)
    styles = {selector: re.search(re.escape(selector) + r'\{[^}]+}', contents[paths[2]]).group(0)
              for selector in ['.lobby', '.home-watch', '.watch-entry']}
    record = {
        'versionCode': version, 'sourceCommit': commit,
        'commitTree': git('rev-parse', commit + '^{tree}').strip(),
        'launcherTree': git('rev-parse', commit + ':public/launcher').strip(),
        'filesSha256': {path: sha(text) for path, text in contents.items()},
        'metaViewport': re.search(r'<meta name="viewport"[^>]+>', contents[paths[0]]).group(0),
        'deferredScriptOrder': re.findall(r'<script[^>]+src="([^"]+)"', contents[paths[0]]),
        'fitTelevision': fit, 'fitTelevisionSha256': sha(fit), 'coreStyleRules': styles,
        'webViewSettings': re.findall(r'settings\.set\w+\([^;]+\);', main),
        'nativeViewportSync': 'private void syncLobbyViewport(' in main,
        'showLobbySelection': next(line.strip() for line in main.splitlines() if 'lobby.choose(webViewVersion()' in line and 'nativeLobby(); return' in line),
        'ambientHasAwakeNavigation': 'function notifyAwake()' in contents[paths[3]],
        'nativeHomeImplementation': 'ScenicNativeHome' if 'new ScenicNativeHome(' in main else 'LinearLayout button home',
        'glassMinimumWebViewMajor': 89,
    }
    receipt = json.loads((root / f'docs/verification/public-alpha-v{version}.json').read_text())
    record['publishedCommit'] = receipt.get('releaseCommit', receipt.get('commit'))
    record['releaseTag'] = receipt.get('tag', 'v' + receipt['version'])
    if version == 33:
        assert record['commitTree'] == receipt['localReviewedTree']
    else:
        assert commit == record['publishedCommit']
    records.append(record)

before = next(r for r in records if r['versionCode'] == 29)
after = next(r for r in records if r['versionCode'] == 30)
assert before['launcherTree'] == after['launcherTree']
assert len({json.dumps(r['coreStyleRules'], sort_keys=True) for r in records}) == 1
assert len({r['filesSha256'][paths[5]] for r in records}) == 1
assert len({r['fitTelevisionSha256'] for r in records if r['versionCode'] <= 30}) == 1
assert len({json.dumps(r['webViewSettings']) for r in records}) == 1
report = {
    'scope': 'Read-only public release source comparison; no APK build or device mutation',
    'source': 'Existing local reviewed public Git history; release commits checked against release receipts',
    'reproduction': 'python3 scripts/s90-review-pre-v30.py',
    'records': records,
    'checks': {
        'v29AndV30EntirePackagedLauncherTreeIdentical': True,
        'v27ThroughV33CoreCanvasAndCardRulesIdentical': True,
        'v27ThroughV33WebViewSettingsIdentical': True,
        'v27ThroughV33LobbyModeFileIdentical': True,
        'v27ThroughV30FitFunctionIdentical': True,
        'v33AddsNativeViewportConstraint': True,
        'v30ChangesNativeFallbackAppearanceOnly': True,
    },
    'findings': [
        'v29 to v30 contains no change anywhere in public/launcher, including script order, CSS, fonts, images, and geometry.',
        'All inspected releases use a 1920x1080 canvas, 380x132 watch cards and a 344px watch-row offset. v33 preserves the design geometry and adds native width/density injection.',
        'setUseWideViewPort(true), setLoadWithOverviewMode(false), textZoom100 and other WebView settings are unchanged across the inspected releases.',
        'LobbyMode is byte-identical: WebView below89, simple_home preference or renderer recovery select native home. v30 changes that native home from plain LinearLayout buttons to measured ScenicNativeHome, without changing selection rules.',
        'notifyAwake startup scheme navigation first appears in inspected v29. v29, v30 and v33 ambient.js are byte-identical; this is not a new v30 change.',
        'v29 adds messages.js before launcher.js; ambient.js remains before launcher.js throughout. The potential startup navigation risk predates v30 and is not demonstrated as the Xiaomi root cause.',
    ],
    'limitations': [
        'No historical version was installed on the same Xiaomi TV during this comparison.',
        'Actual pre-v30 working version, chosen native/glass path, WebView provider, display settings, preferences and post-upgrade lifecycle have not been captured together.',
        'Source equality rules out a v29-to-v30 packaged-home geometry change; it does not refute the user-reported regression or establish its device-specific cause.',
        'The separate WebView66 diagnostic experiment is not Xiaomi hardware acceptance and cannot establish v34 fixes the reported OEM regression.',
    ],
}
target = root / 'docs/verification/s90-pre-v30-review.json'
target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print(json.dumps({'report': str(target), 'checks': report['checks']}, ensure_ascii=False))
