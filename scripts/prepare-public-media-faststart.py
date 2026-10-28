from pathlib import Path
import struct,hashlib,json,cv2
src=Path('artifacts/s66-public-media/bbb.mp4');data=src.read_bytes()
def boxes(buf,start,end):
 while start<end:
  size,kind=struct.unpack_from('>I4s',buf,start);header=8
  if size==1:size=struct.unpack_from('>Q',buf,start+8)[0];header=16
  if size==0:size=end-start
  assert size>=header and start+size<=end
  yield start,size,kind,header;start+=size
parts=list(boxes(data,0,len(data)));moov=next(b for b in parts if b[2]==b'moov');mdat=next(b for b in parts if b[2]==b'mdat');assert moov[0]>mdat[0] and moov[0]+moov[1]==len(data)
meta=bytearray(data[moov[0]:moov[0]+moov[1]]);changed=0

def patch(start,end):
 global changed
 for pos,size,kind,header in boxes(meta,start,end):
  if kind in [b'moov',b'trak',b'mdia',b'minf',b'stbl']:patch(pos+header,pos+size)
  elif kind in [b'stco',b'co64']:
   count=struct.unpack_from('>I',meta,pos+header+4)[0];unit=4 if kind==b'stco' else 8;fmt='>I' if unit==4 else '>Q'
   for i in range(count):
    at=pos+header+8+i*unit;offset=struct.unpack_from(fmt,meta,at)[0];assert mdat[0]<=offset<moov[0];struct.pack_into(fmt,meta,at,offset+len(meta));changed+=1
patch(0,len(meta));assert changed
new=data[:mdat[0]]+meta+data[mdat[0]:moov[0]];assert len(new)==len(data)
newparts=list(boxes(new,0,len(new)));newmdat=next(b for b in newparts if b[2]==b'mdat');assert data[mdat[0]:mdat[0]+mdat[1]]==new[newmdat[0]:newmdat[0]+newmdat[1]]
out=Path('artifacts/s66-public-media/bbb-faststart.mp4');out.write_bytes(new);c=cv2.VideoCapture(str(out));assert c.isOpened();c.set(cv2.CAP_PROP_POS_MSEC,60000);assert c.read()[0];c.release()
p=Path('docs/verification/s66-public-resource-provenance.json');r=json.loads(p.read_text());r['faststart']={'description':'Fixture-only container relocation: moov moved before mdat, stco offsets adjusted; encoded audio/video mdat bytes unchanged','mdatSha256':hashlib.sha256(data[mdat[0]:mdat[0]+mdat[1]]).hexdigest(),'chunkOffsetsAdjusted':changed,'bytes':len(new),'sha256':hashlib.sha256(new).hexdigest(),'opencvSeek60sPassed':True};p.write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n');print('Prepared faststart fixture; unchanged encoded samples')
