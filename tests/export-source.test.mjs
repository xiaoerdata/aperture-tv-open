import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,writeFile,readFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const script=fileURLToPath(new URL('../scripts/export-source.mjs',import.meta.url));
async function fixture(t){
 const root=await mkdtemp(join(tmpdir(),'aperture-export-fixture-'));
 t.after(()=>rm(root,{recursive:true,force:true}));
 for(const name of ['.github','shared','server','public','android','examples','docs','tests','scripts','media','artifacts'])await mkdir(join(root,name));
 for(const name of ['AGENTS.md','INSTALL.md','README.md','LICENSE','THIRD_PARTY_NOTICES.md','CONTRIBUTING.md','SECURITY.md','package-lock.json','requirements.txt','.gitignore'])await writeFile(join(root,name),'Synthetic export fixture\n');
 await writeFile(join(root,'package.json'),JSON.stringify({version:'0.0.0-fixture'}));
 return root;
}
const run=(root,args=[])=>spawnSync(process.execPath,[script,...args],{cwd:root,encoding:'utf8'});

test('default export cannot overwrite a previously frozen archive',async t=>{
 const root=await fixture(t),path=join(root,'artifacts/aperture-tv-v0.0.0-fixture-source.tar.gz'),original=Buffer.from('frozen archive sentinel');
 await writeFile(path,original);
 const result=run(root);
 assert.notEqual(result.status,0);assert.match(result.stderr,/already exists; preserved unchanged/);
 assert.deepEqual(await readFile(path),original);
});

test('a named snapshot is a readable source tar, and repeated export preserves its bytes',async t=>{
 const root=await fixture(t),output='artifacts/review-source.tar.gz';
 const result=run(root,['--output',output]);assert.equal(result.status,0,result.stderr);
 const bytes=await readFile(join(root,output));
 const listing=spawnSync('tar',['-tzf',join(root,output)],{encoding:'utf8'});assert.equal(listing.status,0);assert.match(listing.stdout,/aperture-tv\/SOURCE-MANIFEST.json/);
 const repeated=run(root,['--output',output]);assert.notEqual(repeated.status,0);assert.deepEqual(await readFile(join(root,output)),bytes);
 const outside=run(root,['--output','docs/not-an-artifact.tar.gz']);assert.notEqual(outside.status,0);
});
