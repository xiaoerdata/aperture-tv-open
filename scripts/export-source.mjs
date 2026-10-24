import {mkdtemp,mkdir,readdir,lstat,readFile,writeFile,cp,rm,link} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve,join,relative,extname,dirname,sep} from 'node:path';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {sourceProvenance,androidSource} from './android-source.mjs';
const root=process.cwd(),args=process.argv.slice(2);
if(args.length && (args.length!==2||args[0]!=='--output'))throw new Error('Usage: npm run export:source -- [--output artifacts/name-source.tar.gz]');
const version=JSON.parse(await readFile(join(root,'package.json'),'utf8')).version;
if(!/^[0-9][A-Za-z0-9.+-]*$/.test(version))throw new Error('Invalid source version');
const artifacts=resolve(root,'artifacts'),output=resolve(root,args[1]||'artifacts/aperture-tv-v'+version+'-source.tar.gz');
if(!output.startsWith(artifacts+sep)||!output.endsWith('.tar.gz'))throw new Error('Source output must be a .tar.gz file inside artifacts');
try{await lstat(output);throw new Error('Source archive already exists; preserved unchanged. Choose a new --output path.');}catch(error){if(error.code!=='ENOENT')throw error;}
const temp=await mkdtemp(join(tmpdir(),'aperture-export-')),target=join(temp,'aperture-tv');
let publicationTemp;
const roots=['AGENTS.md','INSTALL.md','README.md','LICENSE','THIRD_PARTY_NOTICES.md','CONTRIBUTING.md','SECURITY.md','package.json','package-lock.json','requirements.txt','.gitignore','.github','shared','server','public','android','examples','docs','tests','scripts','media'];
const skip=new Set(['.data','.env','.venv','node_modules','artifacts','integrations','output','build','.gradle','local.properties','__pycache__']);
const manifest=[];
async function copy(path){const stat=await lstat(path);if(stat.isSymbolicLink())throw new Error('Symlink rejected');const rel=relative(root,path);if(stat.isDirectory()){for(const child of await readdir(path))if(!skip.has(child))await copy(join(path,child));return;}if(!stat.isFile()||['.keystore','.jks','.pyc'].includes(extname(path)))throw new Error('Unexpected export file');const dest=join(target,rel);await mkdir(resolve(dest,'..'),{recursive:true});await cp(path,dest);const data=await readFile(path);manifest.push({path:rel,bytes:data.length,sha256:createHash('sha256').update(data).digest('hex')});}
try{
  for(const name of roots)await copy(join(root,name));
  const provenance=await sourceProvenance(root,await androidSource(root));
  await writeFile(join(target,'SOURCE-MANIFEST.json'),JSON.stringify({format:'aperture-source/v1',version,source:{commit:provenance.sourceCommit,workingTreeDirty:provenance.workingTreeDirty},files:manifest,excludes:['family data','credentials','private Hub integration','downloaded SDK/JDK/test runtimes','generated media','private preview binaries and raw account evidence']},null,2));
  await mkdir(dirname(output),{recursive:true});
  publicationTemp=await mkdtemp(join(dirname(output),'.aperture-source-'));
  const archive=join(publicationTemp,'source.tar.gz');
  const result=spawnSync('tar',['-czf',archive,'-C',temp,'aperture-tv'],{encoding:'utf8'});if(result.status!==0)throw new Error('Source tar creation failed');
  // Publish the completed file atomically; even a concurrent export cannot replace an older archive.
  await link(archive,output);
  console.log(JSON.stringify({output,files:manifest.length,sha256:createHash('sha256').update(await readFile(output)).digest('hex')},null,2));
}finally{await rm(temp,{recursive:true,force:true});if(publicationTemp)await rm(publicationTemp,{recursive:true,force:true});}
