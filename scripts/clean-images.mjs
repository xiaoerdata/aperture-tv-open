import {readFile,readdir,realpath,unlink,lstat} from 'node:fs/promises';
import {resolve,join} from 'node:path';
const directory=resolve(process.env.APERTURE_DATA || '.data'),imageDirectory=join(directory,'images');
if((await lstat(directory)).isSymbolicLink() || await realpath(directory)!==directory)throw new Error('Refusing symlink data directory');
if((await lstat(imageDirectory)).isSymbolicLink())throw new Error('Refusing symlink images directory');
const state=JSON.parse(await readFile(join(directory,'state.json'),'utf8'));
const referenced=new Set(state.board.photos.map(p=>p.asset)),unused=[];
for(const file of await readdir(imageDirectory,{withFileTypes:true})){if(file.isSymbolicLink())throw new Error('Refusing symlink image');if(file.isFile()&&/^[a-f0-9]{64}\.jpg$/.test(file.name)&&!referenced.has(file.name))unused.push(file.name);}
console.log(JSON.stringify({mode:process.argv.includes('--apply')?'delete':'preview',requirement:'Stop the server before applying cleanup',unreferencedFiles:unused},null,2));
if(process.argv.includes('--apply'))for(const filename of unused)await unlink(join(imageDirectory,filename));
