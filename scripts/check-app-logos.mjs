// Audit the packaged brand files against the recorded original bytes and exact Android allowlist.
import assert from 'node:assert/strict';
import {readFile,readdir,mkdir,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const manifest=JSON.parse(await readFile('design-assets/app-logos/manifest.json','utf8'));
const presets=JSON.parse(await readFile('public/launcher/app-presets.json','utf8'));
const allowlist=await readFile('android/app/src/main/java/org/zhixiaoce/aperture/LobbyAssets.java','utf8');
assert.equal(presets.length,12);assert.equal(manifest.assets.length,12);
assert.equal(new Set(manifest.assets.map(a=>a.id)).size,12);
const assets=[];
for(const p of presets){
 assert.equal(p.mark,undefined);assert.equal(p.color,undefined);assert.match(p.icon,/^app-icons\/[a-z]+\.(png|jpg)$/);
 const source=manifest.assets.find(a=>a.id===p.id);assert.ok(source);assert.equal(source.file,'public/launcher/'+p.icon);
 assert.equal(new URL(source.sourceUrl).protocol,'https:');assert.equal(new URL(source.sourcePage).protocol,'https:');assert.ok(source.width>=144&&source.height>=144);
 const bytes=await readFile(source.file);assert.equal(bytes.length,source.bytes);assert.equal(createHash('sha256').update(bytes).digest('hex'),source.sha256);
 assert.ok(allowlist.includes('"'+p.icon+'"'),'Android exact whitelist: '+p.icon);
 assert.equal(source.transformation,'none; original bytes');assets.push({id:p.id,sha256:source.sha256,bytes:bytes.length});
}
const generic='app-icons/application.svg';assert.ok(allowlist.includes('"'+generic+'"'));
assert.deepEqual((await readdir('public/launcher/app-icons')).sort(),[...presets.map(p=>p.icon.split('/')[1]),'application.svg'].sort());
await mkdir('docs/verification',{recursive:true});
await writeFile('docs/verification/s52-logo-assets.json',JSON.stringify({assets,bundledBytes:assets.reduce((n,a)=>n+a.bytes,0),originalBytesVerified:true,exactAndroidWhitelist:true,projectMITAppliesToBrandAssets:false},null,2)+'\n');
console.log('12 original brand assets: hashes, sizes, sources and exact Android allowlist verified.');
