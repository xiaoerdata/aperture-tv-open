"""Freeze reviewed v48 changes over the verified public baseline; no Git ref writes."""
from pathlib import Path,PurePosixPath
import hashlib,importlib.util,json,posixpath,subprocess
from urllib.parse import urlsplit,unquote

r=Path(__file__).resolve().parents[1];out=r/'artifacts/s124-release';base=json.loads((out/'baseline-source-manifest.json').read_text());baseline=json.loads((out/'baseline.json').read_text());m=json.loads((r/'artifacts/v48-standard-candidate/manifest.json').read_text());inputs=m['inputs']
assert m['versionCode']==48 and json.loads((r/'docs/verification/s124-release-candidate.json').read_text())['passed']
assert subprocess.check_output(['gh','api','repos/xiaoerdata/aperture-tv-open/git/ref/heads/main','--jq','.object.sha'],text=True).strip()==baseline['parent']
tree=json.loads((out/'baseline-tree.json').read_text());assert not tree['truncated'];objects={v['path']:v for v in tree['tree'] if v['type']=='blob'}
def sha(data):return hashlib.sha256(data).hexdigest()
def oid(data):return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
assert oid((out/'baseline-source-manifest.json').read_bytes())==objects['SOURCE-MANIFEST.json']['sha']
files={x['path']:x for x in base['files']};data={}
spec=importlib.util.spec_from_file_location('review',r/'scripts/review-public-source.py');review=importlib.util.module_from_spec(spec);spec.loader.exec_module(review)
spec=importlib.util.spec_from_file_location('prepare',r/'scripts/prepare-public-source.py');prep=importlib.util.module_from_spec(spec);spec.loader.exec_module(prep)
extra=['README.md','INSTALL.md','docs/release/README-public.md','docs/DOWNLOADS.md','package.json','package-lock.json','docs/LIVE-REMOTE-S123.md','docs/RELEASE-V48.md','scripts/s123-live-test.py','scripts/s123-live-preview.mjs','scripts/s124-build-candidates.py','scripts/s124-prepare-source.py','scripts/s124-upload-source.py','scripts/s125-episodes-test.py','scripts/s125-episode-sort-preview.mjs','scripts/s126-sort-test.py','scripts/s127-line-match-test.py','scripts/s114-sort-rail-test.py','tests/java/LivePanelTest.java','tests/java/LivePanelBeforeTest.java','tests/java/EpisodeAppendTest.java','tests/java/YearScoreAppendTest.java','tests/java/PlaybackLineMatchTest.java','tests/java/PlaybackPromotionTest.java','tests/java/HomeUpdatesTest.java','tests/java/CinemaDatesTest.java','tests/fixtures/live-v47/CinemaLivePanel.java','media/DEVLOG-096.md']
extra+=['docs/verification/'+n for n in ['s123-live-controls.json','s123-native-compile.json','s123-live-preview.json','s124-release-candidate.json','s125-episode-append.json','s125-episode-sort-preview.json','s126-year-score-sort.json','s127-line-match.json']]
extra+=['docs/WORKBUDDY.md','docs/WORKBUDDY-QUICKSTART-V48.md','scripts/s135-settings-test.py','scripts/s135-settings-browser.mjs','tests/java/SettingsScrollTest.java','docs/LOCAL-TV-CONTROL.md','scripts/s133-pair-test.py','tests/java/PairWindowTest.java','THIRD_PARTY_NOTICES.md','scripts/s129-native-compile.py','scripts/s129-stream-test.py','scripts/s130-source-return-test.py','scripts/s131-album-test.py','scripts/s131-album-browser.mjs','scripts/s109-weather-return-test.py','scripts/s124-release-gate.py','tests/java/CinemaStreamTest.java','tests/java/HomeAlbumTest.java','tests/java/AmbientReturnTest.java']
extra+=['docs/verification/'+n for n in ['s129-native-compile.json','s129-stream.json','s130-source-return.json','s131-album-native.json','s131-album-browser.json','s132-screensaver-return.json','s133-pair-window.json','s134-workbuddy-guide.json','s135-settings-scroll.json']]
for folder in ['s123-live-controls','s125-episodes-sort','s131-album']:extra+=[p.relative_to(r).as_posix() for p in (r/'docs/images'/folder).glob('*.png')]
for entry in inputs['files']:assert sha((r/entry['path']).read_bytes())==entry['sha256']
for name in sorted(set([v['path'] for v in inputs['files']]+extra)):
 blob=(r/name).read_bytes()
 if name=='INSTALL.md':blob=blob.replace(b'(private-preview/README.md)',b'(docs/DOWNLOADS.md)')
 if name in files and sha(blob)==files[name]['sha256']:continue
 data[name]=blob
for name,prior,marker in [('docs/process/PROMPTS-001.md','baseline-prompts.md','## P137 '),('docs/process/JOURNAL.md','baseline-journal.md','## S123 ')]:
 text=(r/name).read_text();data[name]=((out/prior).read_text().rstrip()+'\n\n'+text[text.index(marker):]).encode()
data['docs/process/README.md']=((out/'baseline-process-index.md').read_text().rstrip()+'\n\nS123—S135：P137—P149直播侧栏跨来源换组、一级换线路、向下连续选集、年份优先评分其次的全已加载排序，以及同名综艺播放中切线、实际播放缓冲与有界缓存、来源读取返回、首页照片和屏保退出修复、配对码5分钟、WorkBuddy配置指引及设置长按加速；统一进入V48双版。见[实现与验证](../LIVE-REMOTE-S123.md)、[V48说明](../RELEASE-V48.md)及[第96期底稿](../../media/DEVLOG-096.md)。浏览器画面为布局示意，不是Android实拍；小米真机待测。\n').encode()
for name,blob in data.items():
 assert not review.sensitive_path(name) and not review.scan(blob),name
 files[name]={'path':name,'bytes':len(blob),'sha256':sha(blob)}
for name,blob in data.items():
 if not name.endswith('.md'):continue
 for link in prep.markdown_links(blob.decode()):
  url=urlsplit(link['target'])
  if url.scheme or url.netloc or not url.path or url.path.startswith('/'):continue
  target=posixpath.normpath(posixpath.join(str(PurePosixPath(name).parent),unquote(url.path)))
  assert target in files or any(n.startswith(target.rstrip('/')+'/') for n in files),(name,target)
assert {n:x['sha256'] for n,x in files.items() if n.startswith(('android/','public/launcher/'))}=={v['path']:v['sha256'] for v in inputs['files']}
base['version']=m['version'];base['files']=[files[n] for n in sorted(files)];base['derivation']['publicParent']=baseline['parent'];base['derivation']['postReleaseReceiptFor']=None
base['release']={'tag':'v0.2.0-alpha.45','versionCode':48,'edition':'standard','editions':['standard','parents'],'androidInputSha256':inputs['sha256'],'prerelease':True,'fullCloudRegressionAccepted':False,'xiaomiAccepted':False,'latestDocumentation':'docs/RELEASE-V48.md'}
data['SOURCE-MANIFEST.json']=(json.dumps(base,ensure_ascii=False,indent=2)+'\n').encode()
staging=out/'source-overlay';staging.mkdir(exist_ok=False)
for name,blob in data.items():p=staging/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(blob)
summary={'parent':baseline['parent'],'baseTree':baseline['baseTree'],'androidInputSha256':inputs['sha256'],'androidFileCount':len(inputs['files']),'totalFrozenFiles':len(files),'changedFiles':[{'path':name,'bytes':len(blob),'sha256':sha(blob),'gitBlob':oid(blob),'mode':objects.get(name,{}).get('mode','100644')} for name,blob in sorted(data.items())],'textPatternsReviewed':True,'changedMarkdownTargetsVerified':True,'newScreenshotsAreBrowserIllustrations':True,'xiaomiScreenshots':False,'privateWorkingTreeUntouched':True}
(out/'source-overlay-review.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n');print(json.dumps({'changedFiles':len(data),'androidFiles':len(inputs['files']),'bytes':sum(map(len,data.values()))}))
