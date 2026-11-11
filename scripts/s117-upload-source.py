"""Upload reviewed local-file blobs; no branch/tag/release mutation.
The connector accepts strings, not local binary paths; gh streams local payloads.
"""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor,as_completed
import base64,hashlib,json,os,subprocess,tempfile,time

r=Path(__file__).resolve().parents[1];out=r/'artifacts/s117-parents';review=json.loads((out/'source-overlay-review.json').read_text());state=out/'uploaded-source-blobs.json';saved=json.loads(state.read_text()) if state.exists() else {};env=dict(os.environ,GODEBUG='http2client=0')
def upload(item):
 name=item['path'];data=(out/'source-overlay'/name).read_bytes();assert hashlib.sha256(data).hexdigest()==item['sha256'];expected=item['gitBlob']
 if saved.get(name)==expected:return name,expected
 with tempfile.NamedTemporaryFile(mode='w',suffix='.json',dir=out) as body:
  json.dump({'encoding':'base64','content':base64.b64encode(data).decode()},body);body.flush()
  for attempt in range(3):
   response=subprocess.run(['gh','api','-X','POST','repos/xiaoerdata/aperture-tv-open/git/blobs','--input',body.name],env=env,capture_output=True,text=True,timeout=120)
   if response.returncode==0:break
   if attempt==2:raise RuntimeError('Source blob upload failed: '+name)
   time.sleep(2)
  actual=json.loads(response.stdout)['sha'];assert actual==expected
 return name,actual
with ThreadPoolExecutor(max_workers=3) as pool:
 futures=[pool.submit(upload,item) for item in review['changedFiles']]
 for index,future in enumerate(as_completed(futures),1):
  name,sha=future.result();saved[name]=sha;state.write_text(json.dumps(saved,indent=2)+'\n')
  if index%12==0 or index==len(futures):print(f'Reviewed source blobs {index}/{len(futures)}',flush=True)
elements=[{'path':item['path'],'mode':item['mode'],'type':'blob','sha':saved[item['path']]} for item in review['changedFiles']]
(out/'tree-elements.json').write_text(json.dumps(elements)+'\n');print('Source objects ready; main and release unchanged.',flush=True)
