"""New immutable v39 revision from d727; preserve every previous ref and authoritative artifact."""
from pathlib import Path,PurePosixPath
import hashlib,importlib.util,json,os,posixpath,subprocess
from urllib.parse import urlsplit,unquote
r=Path(__file__).resolve().parents[1];repo=r/'artifacts/public-v17-git';parent='d72791867192394e0f121cf80583ffab1f4ad0ea';ref='refs/heads/candidate-v39-r3-r1';index=r/'artifacts/s97-v39-r1/freeze.index';out=r/'artifacts/s97-v39-r1-delivery';assert not index.exists() and not out.exists();env=dict(os.environ,GIT_INDEX_FILE=str(index))
def git(*args,data=None):return subprocess.check_output(['git','-C',str(repo),*args],input=data,env=env)
def sha(data):return hashlib.sha256(data).hexdigest()
baseline=json.loads((r/'artifacts/s97-v39-r1/preserved-baseline.json').read_text())
for f in baseline['files']:assert sha((r/f['path']).read_bytes())==f['sha256']
for name,expected in baseline['refs'].items():assert git('rev-parse','refs/heads/'+name).decode().strip()==expected
assert git('rev-parse','refs/heads/candidate-v39-r3').decode().strip()==parent and ref not in git('show-ref','--heads').decode()
arm=json.loads((r/'artifacts/s97-v39-r1-arm/manifest.json').read_text());x86=json.loads((r/'artifacts/s97-v39-r1-x86/manifest.json').read_text());assert arm['inputs']==x86['inputs'] and arm['versionCode']==x86['versionCode']==39 and arm['version']==x86['version']=='0.2.0-alpha.36-r1'
assert len(arm['inputs']['files'])==217 and arm['inputs']['sha256']=='3128e0896c95a3c7bbfc23455d269755ae8db03d7ce5a1891603cf28998e94a1'
unit=json.loads((r/'docs/verification/s97-unit-final.json').read_text());compiled=json.loads((r/'docs/verification/s97-probe-compile-frozen.json').read_text());assert unit['passed'] and compiled['javacExitCode']==compiled['d8ExitCode']==0 and not compiled['executed'] and not compiled['networkPermission']
for f in unit['sources']+compiled['sources']:assert sha((r/f['path']).read_bytes())==f['sha256'],f['path']
inventory=json.loads((r/'docs/verification/s97-case-inventory-final.json').read_text())
for f in inventory['runnerCaseInventory']:assert sha((r/f['path']).read_bytes())==f['currentSha256']
assert (r/'android/app/src/main/AndroidManifest.xml').read_bytes()==git('show',parent+':android/app/src/main/AndroidManifest.xml')
spec=importlib.util.spec_from_file_location('review',r/'scripts/review-public-source.py');review=importlib.util.module_from_spec(spec);spec.loader.exec_module(review)
spec=importlib.util.spec_from_file_location('prepare',r/'scripts/prepare-public-source.py');prep=importlib.util.module_from_spec(spec);spec.loader.exec_module(prep)
source=json.loads(git('show',parent+':SOURCE-MANIFEST.json'));files={f['path']:f for f in source['files']};changes={}
names=[f['path'] for f in arm['inputs']['files']]+['package.json','package-lock.json','docs/CANDIDATE-V39-R3-R1.md','docs/process/ROUND3-V39-R1.md','docs/process/JOURNAL.md','media/DEVLOG-074.md','tests/java/GalleryUploadEpochTest.java','scripts/s97-unit-test.py','scripts/s97-case-inventory.py','scripts/s97-build-gallery-probe.py','scripts/s97-sign-candidate.py','scripts/s97-freeze-candidate.py','scripts/s97-final-delivery.py']
names += ['docs/verification/'+n for n in ['s97-unit-final.json','s97-probe-compile-frozen.json','s97-case-inventory-final.json','s97-parent-cloud-reported.json','s97-validation-scope.json']]
names += [p.relative_to(r).as_posix() for directory in ['tests/android/evidence38','tests/android/gallery39','tests/android/recovery37'] for p in sorted((r/directory).glob('*.java'))]
for name in sorted(set(names)):
 data=(r/name).read_bytes();assert not review.sensitive_path(name) and not review.scan(data),name
 if name in files and sha(data)==files[name]['sha256']:continue
 changes[name]=data;files[name]={'path':name,'bytes':len(data),'sha256':sha(data)}
readme=git('show',parent+':README.md').decode();assert readme.startswith('> 私有开发候选：v39');changes['README.md']=('> 私有开发候选：v39修订 / 0.2.0-alpha.36-r1，未公开发布。先读 [结束边界与测试修订](docs/CANDIDATE-V39-R3-R1.md)。\n\n'+readme.split('\n\n',1)[1]).encode()
process='docs/process/README.md';changes[process]=(git('show',parent+':'+process).decode().rstrip()+'\n\nS97：'+(r/process).read_text().split('\nS97：',1)[1]).encode()
for name in ['README.md',process]:files[name]={'path':name,'bytes':len(changes[name]),'sha256':sha(changes[name])}
for name,data in changes.items():
 if not name.endswith('.md'):continue
 for link in prep.markdown_links(data.decode()):
  url=urlsplit(link['target'])
  if url.scheme or url.netloc or not url.path or url.path.startswith('/'):continue
  target=posixpath.normpath(posixpath.join(str(PurePosixPath(name).parent),unquote(url.path)));assert target in files or any(n.startswith(target.rstrip('/')+'/') for n in files),(name,target)
assert {n:f['sha256'] for n,f in files.items() if n.startswith(('android/','public/launcher/'))}=={f['path']:f['sha256'] for f in arm['inputs']['files']}
source['version']='0.2.0-alpha.36-r1';source['files']=[files[n] for n in sorted(files)];source['derivation']['publicParent']=parent;source['candidate']={'round':3,'revision':1,'versionCode':39,'productBaseCommit':parent,'testBaselineCommit':parent,'retainedGestureSourceCommit':'31a379a8f5c1eb9741fccc0039fc147523b5811d','publiclyReleased':False,'parentsPaused':True,'waitForCloudRetest':True,'formalParentR2FailureBranch':'unknown'}
changes['SOURCE-MANIFEST.json']=(json.dumps(source,ensure_ascii=False,indent=2)+'\n').encode();git('read-tree',parent)
for name,data in sorted(changes.items()):
 oid=git('hash-object','-w','--stdin',data=data).decode().strip();git('update-index','--add','--cacheinfo','100644',oid,name)
tree=git('write-tree').decode().strip();commit=git('commit-tree',tree,'-p',parent,data=b'Private v39 R3 revision: immediate upload revocation and scoped test lifecycle/input gates\n').decode().strip();git('update-ref',ref,commit,'0'*40)
out.mkdir();(out/'d727-to-v39-r3-r1.patch').write_bytes(git('diff','--binary',parent,commit));subprocess.run(['git','-C',str(repo),'bundle','create',str(out/'v39-r3-r1.bundle'),ref,'^'+parent],check=True)
(r/'artifacts/s97-v39-r1/candidate-commit.txt').write_text(commit+'\n');(r/'artifacts/s97-v39-r1/freeze.json').write_text(json.dumps({'commit':commit,'parent':parent,'tree':tree,'changedFiles':list(changes),'androidInputSha256':arm['inputs']['sha256'],'published':False},indent=2)+'\n')
for folder in ['s97-v39-r1-arm','s97-v39-r1-x86']:
 p=r/'artifacts'/folder/'manifest.json';m=json.loads(p.read_text());m['candidateCommit']=commit;m['baseCommit']=parent;m['revision']=1;m['testSourceCommit']=commit;m['testBaselineCommit']=parent;p.write_text(json.dumps(m,indent=2)+'\n');(p.parent/'SHA256SUMS').write_text(''.join(sha(f.read_bytes())+'  '+f.name+'\n' for f in [p.parent/m['apk'],p]))
print(json.dumps({'commit':commit,'parent':parent,'tree':tree,'changedFiles':len(changes),'published':False}))
