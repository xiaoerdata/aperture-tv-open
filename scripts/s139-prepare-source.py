"""Freeze reviewed v50 changes over the verified public baseline; no Git ref writes."""
from pathlib import Path,PurePosixPath
import hashlib,importlib.util,json,posixpath,subprocess
from urllib.parse import urlsplit,unquote

r=Path(__file__).resolve().parents[1];out=r/'artifacts/s139-release';base=json.loads((out/'baseline-source-manifest.json').read_text());baseline=json.loads((out/'baseline.json').read_text());m=json.loads((r/'artifacts/v50-standard-candidate/manifest.json').read_text());inputs=m['inputs']
assert m['versionCode']==50 and json.loads((r/'docs/verification/s139-release-candidate.json').read_text())['passed']
assert subprocess.check_output(['gh','api','repos/xiaoerdata/aperture-tv-open/git/ref/heads/main','--jq','.object.sha'],text=True).strip()==baseline['parent']
tree=json.loads((out/'baseline-tree.json').read_text());assert not tree['truncated'];objects={v['path']:v for v in tree['tree'] if v['type']=='blob'}
def sha(data):return hashlib.sha256(data).hexdigest()
def oid(data):return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
assert oid((out/'baseline-source-manifest.json').read_bytes())==objects['SOURCE-MANIFEST.json']['sha']
files={x['path']:x for x in base['files']};data={}
spec=importlib.util.spec_from_file_location('review',r/'scripts/review-public-source.py');review=importlib.util.module_from_spec(spec);spec.loader.exec_module(review)
spec=importlib.util.spec_from_file_location('prepare',r/'scripts/prepare-public-source.py');prep=importlib.util.module_from_spec(spec);spec.loader.exec_module(prep)
extra=['README.md','INSTALL.md','docs/release/README-public.md','docs/DOWNLOADS.md','package.json','package-lock.json','docs/PLAYER-KEYS-S138.md','docs/RELEASE-V50.md','scripts/s138-player-keys-test.py','tests/java/PlayerSeekKeysTest.java','scripts/s139-release-gate.py','scripts/s139-build-candidates.py','scripts/s139-prepare-source.py','scripts/s139-upload-source.py','media/DEVLOG-098.md']
extra+=['docs/verification/'+n for n in ['s123-live-controls.json','s138-player-keys.json','s138-native-compile.json','s139-release-candidate.json']]
for entry in inputs['files']:assert sha((r/entry['path']).read_bytes())==entry['sha256']
for name in sorted(set([v['path'] for v in inputs['files']]+extra)):
 blob=(r/name).read_bytes()
 if name=='INSTALL.md':blob=blob.replace(b'(private-preview/README.md)',b'(docs/DOWNLOADS.md)')
 if name in files and sha(blob)==files[name]['sha256']:continue
 data[name]=blob
for name,prior,marker in [('docs/process/PROMPTS-001.md','baseline-prompts.md','## P151 '),('docs/process/JOURNAL.md','baseline-journal.md','## S138 ')]:
 text=(r/name).read_text();data[name]=((out/prior).read_text().rstrip()+'\n\n'+text[text.index(marker):]).encode()
data['docs/process/README.md']=((out/'baseline-process-index.md').read_text().rstrip()+'\n\nS138—S139：P151点播/NAS播放默认左右快退快进，初始按钮焦点不改变默认操作，选集/线路/直播组保留导航。见[实现验证](../PLAYER-KEYS-S138.md)、[V50说明](../RELEASE-V50.md)及[第98期底稿](../../media/DEVLOG-098.md)。原生图片页继承V49，真机与JVM替身分开记录。\n').encode()
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
base['release']={'tag':'v0.2.0-alpha.47','versionCode':50,'edition':'standard','editions':['standard','parents'],'androidInputSha256':inputs['sha256'],'prerelease':True,'fullCloudRegressionAccepted':False,'xiaomiAccepted':False,'latestDocumentation':'docs/RELEASE-V50.md'}
data['SOURCE-MANIFEST.json']=(json.dumps(base,ensure_ascii=False,indent=2)+'\n').encode()
staging=out/'source-overlay';staging.mkdir(exist_ok=False)
for name,blob in data.items():p=staging/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(blob)
summary={'parent':baseline['parent'],'baseTree':baseline['baseTree'],'androidInputSha256':inputs['sha256'],'androidFileCount':len(inputs['files']),'totalFrozenFiles':len(files),'changedFiles':[{'path':name,'bytes':len(blob),'sha256':sha(blob),'gitBlob':oid(blob),'mode':objects.get(name,{}).get('mode','100644')} for name,blob in sorted(data.items())],'textPatternsReviewed':True,'changedMarkdownTargetsVerified':True,'newScreenshotsAreBrowserIllustrations':True,'xiaomiScreenshots':False,'privateWorkingTreeUntouched':True}
(out/'source-overlay-review.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n');print(json.dumps({'changedFiles':len(data),'androidFiles':len(inputs['files']),'bytes':sum(map(len,data.values()))}))
