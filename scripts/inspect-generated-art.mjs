// Read-only provenance manifest for the selected original and runtime images.
import {readFile,writeFile,stat} from 'node:fs/promises';import {createHash} from 'node:crypto';
const originals=[['dusk-lake','dusk'],['memory-archive','archive'],['glass-swallow','swallow']];
const selected=[];
for(const [name,prompt] of originals){const path='design-assets/s49/originals/'+name+'.png',data=await readFile(path);selected.push({path,width:data.readUInt32BE(16),height:data.readUInt32BE(20),alpha:data[25]===6,bytes:data.length,sha256:createHash('sha256').update(data).digest('hex'),prompt:'design-assets/s49/prompts/'+prompt+'.txt'});}
const runtime=[];
for(const name of ['dusk-lake-v1','dusk-lake-thumb-v1','memory-archive-v1','glass-swallow-v1']){const path='public/launcher/art/'+name+'.webp',data=await readFile(path);runtime.push({path,bytes:(await stat(path)).size,sha256:createHash('sha256').update(data).digest('hex')});}
const report={tool:'built-in imagegen',modelVersionReported:false,generatedLandscapeIsFictional:true,native4kGenerated:false,originals:selected,runtime,totalRuntimeBytes:runtime.reduce((n,r)=>n+r.bytes,0),processing:'ImageMagick format encoding and downscaling only; no enlargement, retouching, cropping or alpha removal',apkBuilt:false};
await writeFile('design-assets/s49/manifest.json',JSON.stringify(report,null,2)+'\n');console.log({totalRuntimeBytes:report.totalRuntimeBytes,originals:selected.map(({path,width,height,alpha})=>({path,width,height,alpha}))});
