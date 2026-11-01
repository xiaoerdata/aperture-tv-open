"""Freeze private v39 from product v38 plus corrected R2 tests. No publication or cleanup."""
from pathlib import Path,PurePosixPath
import hashlib,importlib.util,json,os,posixpath,subprocess
from urllib.parse import urlsplit,unquote
r=Path(__file__).resolve().parents[1];repo=r/'artifacts/public-v17-git';parent='31a379a8f5c1eb9741fccc0039fc147523b5811d';product='0f09b9d218a0937f16c5d0418f0f546ff138174e';index=r/'artifacts/s96-v39/freeze.index';out=r/'artifacts/s96-v39-delivery';ref='refs/heads/candidate-v39-r3'
assert not index.exists() and not out.exists();env=dict(os.environ,GIT_INDEX_FILE=str(index))
def git(*args,data=None,environment=None):return subprocess.check_output(['git','-C',str(repo),*args],input=data,env=environment or env)
def h(data):return hashlib.sha256(data).hexdigest()
baseline=json.loads((r/'artifacts/s96-v39/preserved-baseline.json').read_text())
for item in baseline['files']:assert h((r/item['path']).read_bytes())==item['sha256'],item['path']
for name,sha in baseline['refs'].items():assert git('rev-parse','refs/heads/'+name).decode().strip()==sha
assert git('rev-parse','refs/heads/testfix-v38-r2-gesture-boundary').decode().strip()==parent and ref not in git('show-ref','--heads').decode()
arm=json.loads((r/'artifacts/s96-v39-arm/manifest.json').read_text());x86=json.loads((r/'artifacts/s96-v39-x86/manifest.json').read_text());assert arm['inputs']==x86['inputs'] and arm['versionCode']==x86['versionCode']==39
assert arm['certificateSha256']==x86['certificateSha256']=='8176c1580418563f290e57d6f4b3f05ed6e1503748f52feca0a0c437da87ec33'
assert len(arm['inputs']['files'])==216 and arm['inputs']['sha256']=='53c55cb4838dfeef8ecf09292becea729ae3f5b0604caa6fe43091f970d3a325'
unit=json.loads((r/'docs/verification/s96-unit-final.json').read_text());compiled=json.loads((r/'docs/verification/s96-probe-compile.json').read_text());scope=json.loads((r/'docs/verification/s96-validation-scope.json').read_text());assert unit['passed'] and compiled['javacExitCode']==compiled['d8ExitCode']==0 and not compiled['executed'] and not compiled['networkPermission'];assert not scope['androidExecutedLocally']
for item in unit['sources']+compiled['sources']:assert h((r/item['path']).read_bytes())==item['sha256'],item['path']
assert h((r/compiled['apk']).read_bytes())==compiled['sha256']
# The corrected remote gesture and exact opening-boundary injection remain untouched from 31a.
name='tests/android/evidence38/Round2Runner.java';old=git('show',parent+':'+name).decode();new=(r/name).read_text();assert new==old.replace('==38','==39').replace('actual v38 package','actual v39 package')
spec=importlib.util.spec_from_file_location('review',r/'scripts/review-public-source.py');review=importlib.util.module_from_spec(spec);spec.loader.exec_module(review)
spec=importlib.util.spec_from_file_location('prepare',r/'scripts/prepare-public-source.py');prep=importlib.util.module_from_spec(spec);spec.loader.exec_module(prep)
manifest=json.loads(git('show',parent+':SOURCE-MANIFEST.json'));files={f['path']:f for f in manifest['files']};changes={}
extra=['package.json','package-lock.json','docs/CANDIDATE-V39-R3.md','docs/process/ROUND3-V39.md','docs/process/JOURNAL.md','media/DEVLOG-073.md','scripts/s96-unit-test.py','scripts/s96-build-gallery-probe.py','scripts/s96-sign-candidate.py','scripts/s96-freeze-candidate.py','scripts/s96-final-delivery.py','tests/java/GalleryTraceTest.java','tests/android/recovery37/RecoveryGalleryProbe.java','tests/android/evidence38/R2GalleryProbe.java','tests/android/evidence38/Round2Runner.java','docs/verification/s96-unit-final.json','docs/verification/s96-probe-compile.json','docs/verification/s96-asset-audit.json','docs/verification/s96-parent-cloud-reported.json','docs/verification/s96-validation-scope.json']
extra += [p.relative_to(r).as_posix() for p in sorted((r/'tests/android/gallery39').glob('*.java'))]
for name in sorted(set([f['path'] for f in arm['inputs']['files']]+extra)):
 data=(r/name).read_bytes();assert not review.sensitive_path(name) and not review.scan(data),name
 if name in files and h(data)==files[name]['sha256']:continue
 changes[name]=data;files[name]={'path':name,'bytes':len(data),'sha256':h(data)}
readme=git('show',parent+':README.md').decode();assert readme.startswith('> 私有开发候选：v38');changes['README.md']=('> 私有开发候选：v39 / 0.2.0-alpha.36，未公开发布。先读 [Round3候选说明](docs/CANDIDATE-V39-R3.md)。\n\n'+readme.split('\n\n',1)[1]).encode()
process='docs/process/README.md';addition=(r/process).read_text().split('\nS96：',1)[1];changes[process]=(git('show',parent+':'+process).decode().rstrip()+'\n\nS96：'+addition).encode()
for name in ['README.md',process]:files[name]={'path':name,'bytes':len(changes[name]),'sha256':h(changes[name])}
for name,data in changes.items():
 if not name.endswith('.md'):continue
 for link in prep.markdown_links(data.decode()):
  url=urlsplit(link['target'])
  if url.scheme or url.netloc or not url.path or url.path.startswith('/'):continue
  target=posixpath.normpath(posixpath.join(str(PurePosixPath(name).parent),unquote(url.path)));assert target in files or any(x.startswith(target.rstrip('/')+'/') for x in files),(name,target)
expected={f['path']:f['sha256'] for f in arm['inputs']['files']};assert {n:f['sha256'] for n,f in files.items() if n.startswith(('android/','public/launcher/'))}==expected
manifest['version']='0.2.0-alpha.36';manifest['files']=[files[n] for n in sorted(files)];manifest['derivation']['publicParent']=parent;manifest['derivation']['postReleaseReceiptFor']=None
manifest['priorTestCorrection']=manifest.pop('testCorrection');manifest['candidate']={'round':3,'versionCode':39,'productBaseCommit':product,'testSourceCommit':parent,'publiclyReleased':False,'parentsPaused':True,'waitForCloudRetest':True,'galleryWebTimeoutRootCause':'unknown'}
changes['SOURCE-MANIFEST.json']=(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n').encode();git('read-tree',parent)
for name,data in sorted(changes.items()):
 oid=git('hash-object','-w','--stdin',data=data).decode().strip();git('update-index','--add','--cacheinfo','100644',oid,name)
tree=git('write-tree').decode().strip();commit=git('commit-tree',tree,'-p',parent,data=b'Private v39 round 3: local gallery stages and operable native compatibility album\n').decode().strip();git('update-ref',ref,commit,'0'*40)
out.mkdir();(out/'v38-tested-to-v39-r3.patch').write_bytes(git('diff','--binary',parent,commit));(out/'v38-product-to-v39-r3.patch').write_bytes(git('diff','--binary',product,commit));subprocess.run(['git','-C',str(repo),'bundle','create',str(out/'v39-r3.bundle'),ref,'^'+product],check=True)
(r/'artifacts/s96-v39/candidate-commit.txt').write_text(commit+'\n');(r/'artifacts/s96-v39/freeze.json').write_text(json.dumps({'commit':commit,'parent':parent,'productBaseCommit':product,'tree':tree,'changedFiles':list(changes),'androidInputSha256':arm['inputs']['sha256'],'publiclyPublished':False},indent=2)+'\n')
for folder in ['s96-v39-arm','s96-v39-x86']:
 p=r/'artifacts'/folder/'manifest.json';m=json.loads(p.read_text());m['candidateCommit']=commit;m['baseCommit']=parent;m['productBaseCommit']=product;m['testSourceCommit']=parent;p.write_text(json.dumps(m,indent=2)+'\n');(p.parent/'SHA256SUMS').write_text(''.join(h(f.read_bytes())+'  '+f.name+'\n' for f in [p.parent/m['apk'],p]))
print(json.dumps({'commit':commit,'parent':parent,'productBaseCommit':product,'tree':tree,'changed':len(changes),'publiclyPublished':False}))
