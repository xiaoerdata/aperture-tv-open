"""Sign one local standard candidate with the existing identity. Never publishes or generates keys."""
from pathlib import Path
import argparse,hashlib,json,os,re,subprocess,zipfile
from android_abis import verify_apk_abis
p=argparse.ArgumentParser();p.add_argument('--input',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--inputs',type=Path,required=True);p.add_argument('--abis',required=True);p.add_argument('--code',type=int,required=True);p.add_argument('--role',choices=['arm-candidate','x86-cloud-test','x86-baseline'],required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[1];out=a.output.resolve();assert r/'artifacts' in out.parents and not out.exists();env=os.environ;sdk=Path(env['ANDROID_HOME']);bt=sdk/'build-tools/35.0.0'
for k in ['APERTURE_RELEASE_KEYSTORE','APERTURE_RELEASE_STORE_PASSWORD_FILE','APERTURE_RELEASE_KEY_PASSWORD_FILE']:
 f=Path(env[k]).resolve();assert r not in f.parents and f.is_file() and f.stat().st_mode&0o077==0
inputs=json.loads(a.inputs.read_text());verify_apk_abis(a.input,[x for x in a.abis.split(',')]);out.mkdir();aligned=out/'aligned.apk';apk=out/('kaishi-v'+str(a.code)+'-standard-'+('arm' if a.role=='arm-candidate' else a.role)+'.apk')
def run(*args):return subprocess.check_output([str(s) for s in args],stderr=subprocess.STDOUT,text=True)
run(bt/'zipalign','-P','16','4',a.input,aligned)
run(bt/'apksigner','sign','--ks',env['APERTURE_RELEASE_KEYSTORE'],'--ks-key-alias',env['APERTURE_RELEASE_ALIAS'],'--ks-pass','file:'+env['APERTURE_RELEASE_STORE_PASSWORD_FILE'],'--key-pass','file:'+env['APERTURE_RELEASE_KEY_PASSWORD_FILE'],'--debuggable-apk-permitted','false','--v4-signing-enabled','false','--alignment-preserved','true','--in',aligned,'--out',apk)
signature=run(bt/'apksigner','verify','--min-sdk-version','26','--verbose','--print-certs',apk);cert=re.search(r'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+)',signature)[1].lower();assert cert==env['APERTURE_RELEASE_CERT_SHA256']
package=run(bt/'aapt','dump','badging',apk);fields=dict(re.findall(r"(\w+)='([^']*)'",package.splitlines()[0]));assert fields['name']=='org.zhixiaoce.kaishi' and int(fields['versionCode'])==a.code and 'application-debuggable' not in package
with zipfile.ZipFile(apk) as archive:
 for f in inputs['files']:
  if f['path'].startswith('public/launcher/'):assert hashlib.sha256(archive.read('assets/'+f['path'][16:])).hexdigest()==f['sha256'],f['path']
 for n in archive.namelist():
  if re.fullmatch('classes[0-9]*.dex',n):
   data=archive.read(n);assert not any(s in data for s in [b'ControlledWebView',b'RecoveryHomeProbe',b'RecoveryRunner',b'EvidenceCapture',b'FrameEvidenceGate',b'Round2Runner',b'R2GalleryProbe',b'R2FrameProbe',b'FakeSession',b'R3NativeGalleryProbe',b'R3WebGalleryProbe',b'Round3Runner',b'Round3PhotosRunner']), 'Fault injection leaked into candidate'
metadata={'version':fields['versionName'],'versionCode':a.code,'applicationId':fields['name'],'edition':'standard','role':a.role,'apk':apk.name,'bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'certificateSha256':cert,'abis':a.abis.split(','),'debuggable':False,'inputs':inputs,'candidateCommit':None,'published':False,'xiaomiTested':False,'actualCloudSigillReproduced':False,'packagedAssetsMatchInputs':True,'faultInjectionNotInProduct':True}
(out/'manifest.json').write_text(json.dumps(metadata,indent=2)+'\n');(out/'signature.txt').write_text(signature);(out/'package.txt').write_text(package);(out/'SHA256SUMS').write_text(metadata['sha256']+'  '+apk.name+'\n');print(json.dumps({k:metadata[k] for k in ['role','versionCode','sha256','abis','published']}))
