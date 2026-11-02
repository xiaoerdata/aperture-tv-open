"""Freeze test-only default DPAD navigation correction and a safe, complete source overlay."""
from pathlib import Path
import hashlib,importlib.util,json,os,subprocess,zipfile
r=Path(__file__).resolve().parents[1];out=r/'artifacts/s98-focus';repo=r/'artifacts/public-v17-git';parent='72afdfe44695aa2bd398315af29a762761610d86';ref='refs/heads/testfix-v39-r1-default-focus';index=out/'test-freeze.index';assert not index.exists();env=dict(os.environ,GIT_INDEX_FILE=str(index))
def git(*args,data=None,environment=None):return subprocess.check_output(['git','-C',str(repo),*args],input=data,env=environment or env)
def sha(data):return hashlib.sha256(data).hexdigest()
def item(path):
 p=r/path;return {'path':path,'bytes':p.stat().st_size,'sha256':sha(p.read_bytes())}
base=json.loads((out/'preserved-baseline.json').read_text())
def preserved():
 assert git('rev-parse','refs/heads/candidate-v39-r3-r1').decode().strip()==parent
 for entry in base['files']:
  data=(r/entry['path']).read_bytes();assert len(data)==entry['bytes'] and sha(data)==entry['sha256'],entry['path']
preserved();assert ref not in git('show-ref','--heads').decode()
compiled=json.loads((r/'docs/verification/s98-focus-compile.json').read_text());assert compiled['javacExitCode']==compiled['d8ExitCode']==0 and not compiled['executed'] and not compiled['networkPermission'];assert sha((r/compiled['apk']).read_bytes())==compiled['sha256']
for entry in compiled['sources']:assert sha((r/entry['path']).read_bytes())==entry['sha256'],entry['path']
callerPaths=['tests/android/recovery37/RecoveryRunner.java','tests/android/evidence38/Round2Runner.java','tests/android/gallery39/Round3Runner.java','tests/android/gallery39/Round3PhotosRunner.java']
for name in callerPaths:
 old=git('show',parent+':'+name).decode();new=(r/name).read_text();assert new==old.replace('.put("inputLayer","app-internal-window-dispatch")','.put("inputLayer",ProbeKeys.INPUT_LAYER)'),name
keys=(r/'tests/android/evidence38/ProbeKeys.java').read_text();assert 'sendKeySync' not in keys and 'sendKeyDownUpSync' not in keys and '16908313' not in keys and '16908314' not in keys and '.getButton(' not in keys
product=json.loads((r/'artifacts/s97-v39-r1-delivery/REVISION-MANIFEST.json').read_text());assert product['candidateCommit']==parent and product['androidInputSha256']==base['androidInputSha256'] and product['androidAndLauncherFiles']==217
spec=importlib.util.spec_from_file_location('review',r/'scripts/review-public-source.py');review=importlib.util.module_from_spec(spec);spec.loader.exec_module(review)
source=json.loads(git('show',parent+':SOURCE-MANIFEST.json'));files={entry['path']:entry for entry in source['files']};changes={}
extras=['tests/android/evidence38/ProbeKeys.java','tests/android/evidence38/FocusNavigationProbe.java','tests/android/evidence38/FocusNavigationRunner.java','tests/android/evidence38/AOSP-FOCUS-NOTICE.md','tests/android/evidence38/AOSP-FOCUS-LICENSE.txt','scripts/s98-build-focus-probe.py','scripts/s98-freeze-test.py','docs/verification/s98-focus-compile.json','docs/verification/s98-focus-source-scope.json','docs/verification/s98-focus-preservation.json','docs/process/V39-R1-FOCUS-TEST-FIX.md','docs/process/JOURNAL.md','media/DEVLOG-075.md']+callerPaths
for name in extras:
 data=(r/name).read_bytes();assert not review.sensitive_path(name) and not review.scan(data),name;changes[name]=data;files[name]={'path':name,'bytes':len(data),'sha256':sha(data)}
source['files']=[files[name] for name in sorted(files)];source['testCorrection']={'productCandidateCommit':parent,'productChanged':False,'versionChanged':False,'description':'Shared unhandled DPAD default focus navigation in captured test window','inputLayer':'app-internal-window-dispatch+default-focus-navigation-simulation','actualSystemRemoteChainAccepted':False,'formalCloudRerunPending':True}
assert {n:f['sha256'] for n,f in files.items() if n.startswith(('android/','public/launcher/'))}=={f['path']:f['sha256'] for f in json.loads((r/'artifacts/s97-v39-r1-arm/manifest.json').read_text())['inputs']['files']}
changes['SOURCE-MANIFEST.json']=(json.dumps(source,ensure_ascii=False,indent=2)+'\n').encode();git('read-tree',parent)
for name,data in changes.items():
 oid=git('hash-object','-w','--stdin',data=data).decode().strip();git('update-index','--add','--cacheinfo','100644',oid,name)
tree=git('write-tree').decode().strip();paths=git('diff','--cached','--name-only',parent).decode().splitlines();assert set(paths)==set(changes) and not any(p.startswith(('android/','public/launcher/')) or p in ['package.json','package-lock.json'] for p in paths)
commit=git('commit-tree',tree,'-p',parent,data=b'Test-only v39-r1 correction: scoped default DPAD focus navigation and safety fixtures\n').decode().strip();git('update-ref',ref,commit,'0'*40)
patch=out/'v39-r1-default-focus.patch';patch.write_bytes(git('diff','--binary',parent,commit));bundle=out/'v39-r1-default-focus.bundle';subprocess.run(['git','-C',str(repo),'bundle','create',str(bundle),ref,'^'+parent],check=True)
check_index=out/'patch-check.index';assert not check_index.exists();check_env=dict(os.environ,GIT_INDEX_FILE=str(check_index));git('read-tree',parent,environment=check_env);git('apply','--cached','--check','--binary',str(patch),environment=check_env);git('apply','--cached','--binary',str(patch),environment=check_env);assert git('write-tree',environment=check_env).decode().strip()==tree
with (out/'bundle-verify.log').open('wb') as log:subprocess.run(['git','-C',str(repo),'bundle','verify',str(bundle)],stdout=log,stderr=subprocess.STDOUT,check=True)
# Complete test source overlay, without product code/assets, APKs, data, credentials or hidden paths.
overlayPaths=sorted({f['path'] for f in compiled['sources']}|{p for p in extras if p.startswith(('tests/','scripts/','docs/verification/','docs/process/V39-'))})
overlayEntries={name:git('show',commit+':'+name) for name in overlayPaths}
overlayManifest={'productCandidateCommit':parent,'testCorrectionCommit':commit,'androidInputSha256':base['androidInputSha256'],'inputLayer':source['testCorrection']['inputLayer'],'containsProductCodeOrCredentials':False,'compiledTestSources':compiled['sources'],'files':[{'path':name,'bytes':len(data),'sha256':sha(data)} for name,data in sorted(overlayEntries.items())]}
overlayEntries['TEST-OVERLAY-MANIFEST.json']=(json.dumps(overlayManifest,indent=2)+'\n').encode();overlayEntries['evidence/aosp-focus-excerpts.txt']=(out/'aosp-focus-excerpts.txt').read_bytes();overlay=out/'v39-r1-default-focus-test-overlay.zip'
with zipfile.ZipFile(overlay,'w',compression=zipfile.ZIP_DEFLATED) as archive:
 for name,data in sorted(overlayEntries.items()):
  assert not Path(name).is_absolute() and '..' not in Path(name).parts and not name.startswith(('android/','public/','.git/','.android-local/')) and not review.scan(data),name
  info=zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;archive.writestr(info,data)
with zipfile.ZipFile(overlay) as archive:
 for name,data in overlayEntries.items():assert archive.read(name)==data
preserved()
manifest={'format':'kaishi-test-correction/v1','productCandidateCommit':parent,'testCorrectionCommit':commit,'parentCommit':parent,'testTree':tree,'localRef':ref,'productRefUnchanged':True,'productChanged':False,'productRebuilt':False,'versionChanged':False,'androidInputSha256':base['androidInputSha256'],'androidAndLauncherFiles':217,'changedFiles':[{'path':name,'bytes':len(data),'sha256':sha(data)} for name,data in sorted(changes.items())],'patch':item(str(patch.relative_to(r))),'bundle':item(str(bundle.relative_to(r))),'completePatchReconstructsTree':True,'safeTestOverlay':item(str(overlay.relative_to(r))),'overlayMemberCount':len(overlayEntries),'fullCompiledTestSources':compiled['sources'],'probe':item(compiled['apk']),'probePackage':compiled['package'],'probeVersionCode':compiled['versionCode'],'probeVersionName':compiled['versionName'],'probeNetworkPermission':False,'compileReceipt':item('docs/verification/s98-focus-compile.json'),'compileLog':item('artifacts/s98-focus/compile.log'),'sourceEvidence':item('docs/verification/s98-focus-source-scope.json'),'aospExcerpts':item('artifacts/s98-focus/aosp-focus-excerpts.txt'),'javacExitCode':0,'d8ExitCode':0,'localDeviceExecution':False,'formalFrozenCloudRerun':False,'inputLayer':source['testCorrection']['inputLayer'],'completeViewRootImeOrSystemRemoteChainAccepted':False,'existingRunnerAssertionsAndLifecycleGatesUnchanged':True,'parentFormalFailuresPreserved':{'r1Passed':51,'r2PassedBeforeFocusFailure':5,'r3PassedBeforeFocusFailure':4,'photosExecuted':False,'lateExitFailureResolved':False},'preservedOriginalFiles':base['files'],'realUploadListenerStarted':False,'photoFixtureCreated':False,'permissionsOrDefaultHomeChanged':False,'credentialsOrServiceChanges':False,'cleanupPerformed':False,'publiclyPublished':False,'v40Started':False,'stopAndWaitForCloud':True}
dest=out/'TEST-CORRECTION-MANIFEST.json';dest.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n');(out/'SHA256SUMS').write_text(''.join(sha(p.read_bytes())+'  '+os.path.relpath(p,out)+'\n' for p in [dest,patch,bundle,overlay,r/compiled['apk'],r/'docs/verification/s98-focus-compile.json']))
print(json.dumps({'productCandidateCommit':parent,'testCorrectionCommit':commit,'changedFiles':paths,'probeSha256':compiled['sha256'],'overlaySha256':sha(overlay.read_bytes()),'javacExitCode':0,'d8ExitCode':0,'productPreserved':True,'deviceExecuted':False,'manifest':str(dest.relative_to(r))},indent=2))
