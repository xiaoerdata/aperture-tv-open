"""Freeze reviewed v38 inputs against v37 using a private Git index; never pushes or deletes."""
from pathlib import Path,PurePosixPath
import hashlib,importlib.util,json,os,posixpath,subprocess
from urllib.parse import urlsplit,unquote
r=Path(__file__).resolve().parents[1];repo=r/'artifacts/public-v17-git';parent='4ff9a8785c9990a4b0d0c1947d3acb3069f73779';index=r/'artifacts/s95-candidate.index';out=r/'artifacts/s95-v38-delivery'
assert not index.exists() and not out.exists(),'Never overwrite an earlier frozen candidate'
env=dict(os.environ,GIT_INDEX_FILE=str(index))
def git(*args,data=None,environment=None):return subprocess.check_output(['git','-C',str(repo),*args],input=data,env=environment or env)
def h(data):return hashlib.sha256(data).hexdigest()
assert git('rev-parse','refs/heads/candidate-v37-r1').decode().strip()==parent
assert not git('show-ref','--heads').decode().count('refs/heads/candidate-v38-r2')
arm=json.loads((r/'artifacts/s95-v38-arm/manifest.json').read_text());x86=json.loads((r/'artifacts/s95-v38-x86/manifest.json').read_text());assert arm['inputs']==x86['inputs'] and arm['versionCode']==x86['versionCode']==38
assert arm['certificateSha256']==x86['certificateSha256']=='8176c1580418563f290e57d6f4b3f05ed6e1503748f52feca0a0c437da87ec33'
assert len(arm['inputs']['files'])==213 and arm['inputs']['sha256']=='679928fa86bf5347ec90a4ed9887b2352dddf4ed4d7be10747683e3717be0675'
unit=json.loads((r/'docs/verification/s95-unit.json').read_text());browser=json.loads((r/'docs/verification/s95-browser.json').read_text());compiled=json.loads((r/'docs/verification/s95-probe-compile.json').read_text());scope=json.loads((r/'docs/verification/s95-validation-scope.json').read_text())
assert unit['passed'] and browser['passed'] and compiled['buildPassed'] and compiled['javacExitCode']==0 and not compiled['executed'] and not compiled['networkPermission']
assert not scope['round2AndroidExecuted'] and scope['androidInputSha256']==arm['inputs']['sha256']
for source in unit['sources']+compiled['sources']:assert h((r/source['path']).read_bytes())==source['sha256'],source['path']
assert h((r/compiled['apk']).read_bytes())==compiled['sha256']
spec=importlib.util.spec_from_file_location('review',r/'scripts/review-public-source.py');review=importlib.util.module_from_spec(spec);spec.loader.exec_module(review)
spec=importlib.util.spec_from_file_location('prepare',r/'scripts/prepare-public-source.py');prep=importlib.util.module_from_spec(spec);spec.loader.exec_module(prep)
manifest=json.loads(git('show',parent+':SOURCE-MANIFEST.json'));files={f['path']:f for f in manifest['files']};changes={}
extra=['package.json','package-lock.json','docs/CANDIDATE-V38-R2.md','docs/process/ROUND2-V38.md','docs/process/JOURNAL.md','docs/process/README.md','media/DEVLOG-070.md','scripts/s95-unit-test.py','scripts/s95-gallery-browser-test.mjs','scripts/s95-build-r2-probe.py','scripts/s95-sign-candidate.py','scripts/s95-freeze-candidate.py','scripts/s95-final-delivery.py','tests/java/UploadConsentTest.java','tests/java/FrameEvidenceGateTest.java','docs/verification/s95-unit.json','docs/verification/s95-browser.json','docs/verification/s95-probe-compile.json','docs/verification/s95-validation-scope.json','docs/verification/s95-r1-cloud-review.json']
extra += [p.relative_to(r).as_posix() for folder in ['tests/android/recovery37','tests/android/evidence38'] for p in sorted((r/folder).glob('*.java'))]
for name in sorted(set([f['path'] for f in arm['inputs']['files']]+extra)):
 data=(r/name).read_bytes();assert not review.sensitive_path(name) and not review.scan(data),name
 if name in files and h(data)==files[name]['sha256']:continue
 changes[name]=data;files[name]={'path':name,'bytes':len(data),'sha256':h(data)}
old_readme=git('show',parent+':README.md').decode();assert old_readme.startswith('> 本地开发候选：v37');readme=old_readme.split('\n\n',1)[1]
changes['README.md']=('> 私有开发候选：v38 / 0.2.0-alpha.35，未公开发布。先读 [Round2候选说明](docs/CANDIDATE-V38-R2.md)。\n\n'+readme).encode();files['README.md']={'path':'README.md','bytes':len(changes['README.md']),'sha256':h(changes['README.md'])}
# The local process index contains historical private-preview links. Retain the reviewed parent
# index and append only this round's entry; do not reintroduce stale private destinations.
process='docs/process/README.md';addition=(r/process).read_text().split('\nS95：',1)[1]
changes[process]=(git('show',parent+':'+process).decode().rstrip()+'\n\nS95：'+addition).encode();files[process]={'path':process,'bytes':len(changes[process]),'sha256':h(changes[process])}
for name,data in changes.items():
 if not name.endswith('.md'):continue
 for link in prep.markdown_links(data.decode()):
  url=urlsplit(link['target'])
  if url.scheme or url.netloc or not url.path or url.path.startswith('/'):continue
  target=posixpath.normpath(posixpath.join(str(PurePosixPath(name).parent),unquote(url.path)));assert target in files or any(x.startswith(target.rstrip('/')+'/') for x in files),(name,target)
expected={f['path']:f['sha256'] for f in arm['inputs']['files']};assert {n:f['sha256'] for n,f in files.items() if n.startswith(('android/','public/launcher/'))}==expected
manifest['version']='0.2.0-alpha.35';manifest['files']=[files[n] for n in sorted(files)];manifest['derivation']['publicParent']=parent;manifest['derivation']['postReleaseReceiptFor']=None
manifest['candidate']={'round':2,'versionCode':38,'publiclyReleased':False,'parentsPaused':True,'waitForCloudRetest':True}
changes['SOURCE-MANIFEST.json']=(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n').encode();git('read-tree',parent)
for name,data in sorted(changes.items()):
 oid=git('hash-object','-w','--stdin',data=data).decode().strip();git('update-index','--add','--cacheinfo','100644',oid,name)
tree=git('write-tree').decode().strip();commit=git('commit-tree',tree,'-p',parent,data=b'Private v38 round 2 candidate: upload confirmation, remote diagnostics and frame evidence\n').decode().strip();git('update-ref','refs/heads/candidate-v38-r2',commit,'0'*40)
out.mkdir();(out/'v37-to-v38-r2.patch').write_bytes(git('diff','--binary',parent,commit));subprocess.run(['git','-C',str(repo),'bundle','create',str(out/'v38-r2.bundle'),'refs/heads/candidate-v38-r2','^'+parent],check=True)
(r/'artifacts/s95-candidate-commit.txt').write_text(commit+'\n');(r/'artifacts/s95-freeze.json').write_text(json.dumps({'commit':commit,'parent':parent,'tree':tree,'changedFiles':list(changes),'androidInputSha256':arm['inputs']['sha256'],'publiclyPublished':False},indent=2)+'\n')
for folder in ['s95-v38-arm','s95-v38-x86']:
 p=r/'artifacts'/folder/'manifest.json';m=json.loads(p.read_text());m['candidateCommit']=commit;m['baseCommit']=parent;p.write_text(json.dumps(m,indent=2)+'\n');(p.parent/'SHA256SUMS').write_text(''.join(h(f.read_bytes())+'  '+f.name+'\n' for f in [p.parent/m['apk'],p]))
print(json.dumps({'commit':commit,'parent':parent,'tree':tree,'changed':len(changes),'publiclyPublished':False}))
