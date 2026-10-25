"""Execute our real source plugin through the production Python runner, no network."""
from pathlib import Path
import hashlib
import json
import sys
import tempfile
from types import SimpleNamespace
from unittest.mock import patch

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / 'android/app/src/main/python'))
import kaishi_plugins

source = (root / 'tests/fixtures/plugins/catalogue.py').read_bytes()
digest = hashlib.sha256(source).hexdigest()
assertions = 0

def check(value):
    global assertions
    assert value
    assertions += 1

with tempfile.TemporaryDirectory(prefix='kaishi-python-plugin-') as directory:
    path = Path(directory) / 'plugin.py'
    path.write_bytes(source)
    def run(operation, args):
        return json.loads(kaishi_plugins.call(str(path), digest, digest, '自有 ', operation, json.dumps(args)))
    check(run('home', [False])['list'][0]['vod_name'] == '自有 测试短片')
    check(run('category', ['films', '2', False, {}])['page'] == 2)
    check('opaque-episode-2' in run('detail', [['one']])['list'][0]['vod_play_url'])
    check(run('search', ['我的短片', False, '2'])['list'][0]['vod_name'] == '我的短片')
    play = run('play', ['测试线路', 'opaque-episode-1', []])
    check(play['url'].endswith('opaque-episode-1.mp4'))
    check(play['header']['Referer'] == 'https://media.example.test/')
    path.write_bytes(source + b'\n#changed\n')
    try:
        run('home', [False])
        raise AssertionError('Changed plugin executed')
    except ValueError as failure:
        check('hash changed' in str(failure))

    # Same source and ext URL, but approving changed ext bytes must re-run init.
    changing_source = b'''from base.spider import Spider as Base
class Spider(Base):
    def init(self, extension):
        self.title = self.fetch(extension).text
    def homeContent(self, filter=False):
        return {"list": [{"vod_id": "one", "vod_name": self.title}]}
'''
    path.write_bytes(changing_source)
    entry_digest = hashlib.sha256(changing_source).hexdigest()
    extension = 'https://rules.example.test/catalogue.json'
    def revision(candidate):
        return json.loads(kaishi_plugins.call(str(path), entry_digest, candidate,
                                             extension, 'home', '[false]'))['list'][0]['vod_name']
    with patch('requests.Session.get', return_value=SimpleNamespace(text='first')) as fetch:
        check(revision('approved-ext-v1') == 'first')
        fetch.return_value = SimpleNamespace(text='second')
        check(revision('approved-ext-v1') == 'first')
        check(revision('approved-ext-v2') == 'second')
        check(fetch.call_count == 2)
kaishi_plugins.clear()
print(f'{assertions} Python plugin execution assertions passed')
