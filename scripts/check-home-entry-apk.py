"""Inspect the packaged binary manifest; does not install or select a home."""
import argparse
import json
import re
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--aapt', required=True, type=Path)
parser.add_argument('--directory', required=True, type=Path)
args = parser.parse_args()
metadata = json.loads((args.directory / 'manifest.json').read_text())
apk = args.directory / metadata['apk']
text = subprocess.check_output([str(args.aapt), 'dump', 'xmltree', str(apk), 'AndroidManifest.xml'], text=True)
stack, nodes = [], []
for line in text.splitlines():
    element = re.match(r'(\s*)E: ([\w-]+)', line)
    if element:
        depth = len(element[1])
        while stack and stack[-1]['depth'] >= depth:
            stack.pop()
        node = {'name': element[2], 'depth': depth, 'attrs': {}, 'children': []}
        if stack:
            stack[-1]['children'].append(node)
        nodes.append(node)
        stack.append(node)
    else:
        attr = re.match(r'\s*A: ([\w:]+)(?:\([^)]*\))?=(.*)', line)
        if attr and stack:
            value = attr[2]
            stack[-1]['attrs'][attr[1]] = value.split('"')[1] if value.startswith('"') else value

def values(node, element, attribute):
    return [child['attrs'].get(attribute) for child in node['children'] if child['name'] == element]

main_name = 'org.zhixiaoce.aperture.MainActivity'
main = next(n for n in nodes if n['name'] == 'activity' and n['attrs'].get('android:name') == main_name)
aliases = [n for n in nodes if n['name'] == 'activity-alias']
assert len(aliases) == 1
alias = aliases[0]
assert alias['attrs']['android:name'] == 'org.zhixiaoce.aperture.HomeEntry'
assert alias['attrs']['android:targetActivity'] == main_name
assert alias['attrs']['android:enabled'] == '(type 0x12)0x0'
assert alias['attrs']['android:exported'] == '(type 0x12)0xffffffff'
assert not any('android.intent.category.HOME' in values(f, 'category', 'android:name') for f in main['children'])
filters = [f for f in alias['children'] if f['name'] == 'intent-filter']
assert len(filters) == 1
assert values(filters[0], 'action', 'android:name') == ['android.intent.action.MAIN']
assert set(values(filters[0], 'category', 'android:name')) == {'android.intent.category.HOME', 'android.intent.category.DEFAULT'}
assert not any(n['name'] in ('service', 'receiver') for n in nodes)
permissions = [n['attrs']['android:name'] for n in nodes if n['name'] == 'uses-permission']
assert permissions == ['android.permission.INTERNET', 'android.permission.REQUEST_INSTALL_PACKAGES', 'android.permission.REQUEST_DELETE_PACKAGES']
provider = next(n for n in nodes if n['name'] == 'provider' and n['attrs'].get('android:name') == 'org.zhixiaoce.aperture.InstallFileProvider')
assert provider['attrs']['android:exported'] == '(type 0x12)0x0'
assert provider['attrs']['android:grantUriPermissions'] == '(type 0x12)0xffffffff'
assert provider['attrs']['android:authorities'] == metadata['applicationId'] + '.install'
agent = next(n for n in nodes if n['name'] == 'activity' and n['attrs'].get('android:name') == 'org.zhixiaoce.aperture.AppAgentActivity')
assert agent['attrs']['android:exported'] == '(type 0x12)0x0'
assert agent['attrs']['android:excludeFromRecents'] == '(type 0x12)0xffffffff'
report = {'installProviderExported': False, 'agentActivityExported': False, 'method': 'aapt actual APK binary manifest', 'applicationId': metadata['applicationId'],
          'apkSha256': metadata['sha256'], 'homeAliasDisabledByDefault': True,
          'mainActivityHasHomeFilter': False, 'servicesOrReceivers': False,
          'permissions': permissions, 'deviceExecuted': False}
(args.directory / 'home-entry-check.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
