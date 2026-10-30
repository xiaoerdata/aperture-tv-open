"""Inspect packaged release classes and photo policy. This is not a remote-input test."""
from pathlib import Path
import argparse,json,re,subprocess,tempfile,zipfile,hashlib
parser=argparse.ArgumentParser();parser.add_argument('--directory',type=Path,required=True);parser.add_argument('--dexdump',type=Path,required=True);args=parser.parse_args()
report=[]
for edition in ['standard','parents']:
 metadata=json.loads((args.directory/edition/'manifest.json').read_text());apk=args.directory/edition/metadata['apk'];assert hashlib.sha256(apk.read_bytes()).hexdigest()==metadata['sha256']
 names=['CinemaActivity','CinemaLivePanel','CinemaChoicePanel','CinemaEpisodeGrid','ConfirmPress','FeaturePolicy','MainActivity','NasActivity'];found={};drivers=[]
 with tempfile.TemporaryDirectory(prefix='kaishi-release-dex-') as tmp,zipfile.ZipFile(apk) as z:
  for name in z.namelist():
   if not re.fullmatch(r'classes[0-9]*\.dex',name):continue
   path=Path(tmp)/name;path.write_bytes(z.read(name))
   # DEX strings use modified UTF-8; replacement is limited to diagnostic text, not source/asset validation.
   dump=subprocess.check_output([str(args.dexdump),'-d',str(path)]).decode('utf-8',errors='replace')
   for section in re.split(r'(?m)^Class #',dump):
    desc=re.search(r"Class descriptor\s+: '([^']+)'",section)
    if not desc:continue
    descriptor=desc[1]
    if '/aperture/test/' in descriptor or 'RemoteRegressionRunner' in descriptor:drivers.append(descriptor)
    for required in names:
     if descriptor=='Lorg/zhixiaoce/aperture/'+required+';':found[required]=section
 assert set(found)==set(names),'Missing release class'
 assert not drivers,'Test drivers in product'
 method=re.search(r"name\s+: 'photos'(.*?)(?:Virtual methods|\n    #\d+)",found['FeaturePolicy'],re.S);assert method,'Missing photo policy method'
 assert re.search(r'const/4 v0, #int 0',method[1]) and re.search(r'return v0',method[1]),'Album enabled in shipping build'
 assert 'CinemaRelated' not in found['CinemaActivity'],'Unexpected related-film call'
 report.append({'edition':edition,'apkSha256':metadata['sha256'],'classesPresent':names,'releasePhotoMethodReturnsFalse':True,'testDriversAbsent':True,'detailRelatedCallsAbsent':True,'remoteInputExecuted':False})
result={'method':'actual release DEX disassembly; not runtime acceptance','editions':report}
(args.directory/'player-policy-check.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
