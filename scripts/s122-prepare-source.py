"""Freeze reviewed v47 changes over the verified public baseline; no Git ref writes."""
from pathlib import Path,PurePosixPath
import hashlib,importlib.util,json,posixpath,subprocess
from urllib.parse import urlsplit,unquote

r=Path(__file__).resolve().parents[1];out=r/'artifacts/s122-release';base=json.loads((out/'baseline-source-manifest.json').read_text());baseline=json.loads((out/'baseline.json').read_text());m=json.loads((r/'artifacts/v47-standard-candidate/manifest.json').read_text());inputs=m['inputs']
assert m['versionCode']==47 and json.loads((r/'docs/verification/s122-release-candidate.json').read_text())['passed']
assert subprocess.check_output(['gh','api','repos/xiaoerdata/aperture-tv-open/git/ref/heads/main','--jq','.object.sha'],text=True).strip()==baseline['parent']
tree=json.loads((out/'baseline-tree.json').read_text());assert not tree['truncated'];objects={v['path']:v for v in tree['tree'] if v['type']=='blob'}
def sha(data):return hashlib.sha256(data).hexdigest()
def oid(data):return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
assert oid((out/'baseline-source-manifest.json').read_bytes())==objects['SOURCE-MANIFEST.json']['sha']
files={x['path']:x for x in base['files']};data={}
spec=importlib.util.spec_from_file_location('review',r/'scripts/review-public-source.py');review=importlib.util.module_from_spec(spec);spec.loader.exec_module(review)
spec=importlib.util.spec_from_file_location('prepare',r/'scripts/prepare-public-source.py');prep=importlib.util.module_from_spec(spec);spec.loader.exec_module(prep)
extra=['README.md','INSTALL.md','docs/release/README-public.md','docs/DOWNLOADS.md','package.json','package-lock.json','docs/RESOURCE-PACK.md','docs/RESOURCE-PACK-AGENT.md','docs/RESOURCE-CAPACITY-S121.md','docs/RELEASE-V47.md','docs/contracts/kaishi-resource-pack-v1.schema.json','scripts/resource-pack.mjs','scripts/s121-resource-regression.py','scripts/s122-build-candidates.py','scripts/s122-prepare-source.py','scripts/s122-upload-source.py','tests/resource-capacity.test.mjs','tests/java/ResourceCapacityTest.java','tests/java/ResourcePackTest.java','tests/java/EmbeddedCinemaTest.java','media/DEVLOG-094.md','media/DEVLOG-095.md','docs/verification/s121-resource-capacity.json','docs/verification/s121-resource-regression.json','docs/verification/s122-release-candidate.json']
for entry in inputs['files']:assert sha((r/entry['path']).read_bytes())==entry['sha256']
for name in sorted(set([v['path'] for v in inputs['files']]+extra)):
 blob=(r/name).read_bytes()
 if name=='INSTALL.md':blob=blob.replace(b'(private-preview/README.md)',b'(docs/DOWNLOADS.md)')
 if name in files and sha(blob)==files[name]['sha256']:continue
 data[name]=blob
for name,prior,marker in [('docs/process/PROMPTS-001.md','baseline-prompts.md','## P135 '),('docs/process/JOURNAL.md','baseline-journal.md','## S121 ')]:
 text=(r/name).read_text();data[name]=((out/prior).read_text().rstrip()+'\n\n'+text[text.index(marker):]).encode()
data['docs/process/README.md']=((out/'baseline-process-index.md').read_text().rstrip()+'\n\nS121—S122：P135—P136资源容量调整与V47双版发行。见[容量规则](../RESOURCE-CAPACITY-S121.md)、[V47说明](../RELEASE-V47.md)、[第94期](../../media/DEVLOG-094.md)和[第95期底稿](../../media/DEVLOG-095.md)。按4 MiB文件及展开预算管理来源，取消导入总数配额；真机仍待复测。\n').encode()
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
base['release']={'tag':'v0.2.0-alpha.44','versionCode':47,'edition':'standard','editions':['standard','parents'],'androidInputSha256':inputs['sha256'],'prerelease':True,'fullCloudRegressionAccepted':False,'xiaomiAccepted':False,'latestDocumentation':'docs/RELEASE-V47.md'}
data['SOURCE-MANIFEST.json']=(json.dumps(base,ensure_ascii=False,indent=2)+'\n').encode()
staging=out/'source-overlay';staging.mkdir(exist_ok=False)
for name,blob in data.items():p=staging/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(blob)
summary={'parent':baseline['parent'],'baseTree':baseline['baseTree'],'androidInputSha256':inputs['sha256'],'androidFileCount':len(inputs['files']),'totalFrozenFiles':len(files),'changedFiles':[{'path':name,'bytes':len(blob),'sha256':sha(blob),'gitBlob':oid(blob),'mode':objects.get(name,{}).get('mode','100644')} for name,blob in sorted(data.items())],'textPatternsReviewed':True,'changedMarkdownTargetsVerified':True,'newScreenshots':False,'v46ScreenshotsRetained':True,'privateWorkingTreeUntouched':True}
(out/'source-overlay-review.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n');print(json.dumps({'changedFiles':len(data),'androidFiles':len(inputs['files']),'bytes':sum(map(len,data.values()))}))
