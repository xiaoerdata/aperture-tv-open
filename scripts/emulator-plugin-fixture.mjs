// Original provider-free plugin fixtures, bound to loopback for the dedicated Android emulator.
import {createServer} from 'node:http';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import {spawnSync} from 'node:child_process';
const directory=resolve('artifacts/s50-plugin-runtime'),jar=resolve('artifacts/plugin-fixtures/fixture.jar'),revisionJar=join(directory,'fixture-revision.jar'),port=4399;
await mkdir(directory,{recursive:true});
const makeRevision=spawnSync('python3',['-c','import sys,zipfile\nwith zipfile.ZipFile(sys.argv[1]) as src, zipfile.ZipFile(sys.argv[2],"w") as dst:\n for item in src.infolist(): dst.writestr(item,src.read(item.filename))\n dst.writestr("fixture-revision.txt","S50 original synthetic bundle revision")',jar,revisionJar],{encoding:'utf8'});
if(makeRevision.status!==0)throw new Error(makeRevision.stderr||'Generate fixture first with node scripts/prepare-plugin-fixture.mjs');
let revision=0;const requests=[];
const server=createServer(async(req,res)=>{
 const address=new URL(req.url,'http://127.0.0.1:'+port);requests.push({path:address.pathname,method:req.method,revision});
 try{
  if(address.pathname==='/control/revision'){revision=address.searchParams.get('value')==='1'?1:0;res.writeHead(200,{'Content-Type':'text/plain'});res.end('ok');return;}
  if(address.pathname==='/profile.json'){
   const origin='http://10.0.2.2:'+port;
   const sites=[{key:'fixture-jar',name:'自有 JAR 测试',type:3,api:'csp_Fixture',ext:'S50 '},{key:'fixture-js',name:'自有 JS 测试',type:3,api:origin+'/catalogue.js',ext:'S50 '},{key:'fixture-python',name:'自有 Python 测试',type:3,api:origin+'/catalogue.py',ext:'S50 '}];
   res.writeHead(200,{'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify({spider:origin+'/fixture.jar',sites}));return;
  }
  if(address.pathname==='/fixture.jar'){res.writeHead(200,{'Content-Type':'application/java-archive'});res.end(await readFile(revision?revisionJar:jar));return;}
  if(!['/catalogue.js','/records.js','/catalogue.py'].includes(address.pathname)){res.writeHead(404);res.end();return;}
  let code=await readFile(resolve('tests/fixtures/plugins','.'+address.pathname),'utf8');
  // JS changes only an imported module to prove companion bytes are included in trust.
  if(revision&&address.pathname==='/records.js')code+='\n// S50 reviewed companion revision\n';
  if(revision&&address.pathname==='/catalogue.py')code+='\n# S50 reviewed Python revision\n';
  res.writeHead(200,{'Content-Type':address.pathname.endsWith('.js')?'text/javascript; charset=utf-8':'text/plain; charset=utf-8','Cache-Control':'no-store'});res.end(code);
 }catch(error){res.writeHead(500);res.end('Fixture unavailable');}
});
await new Promise(done=>server.listen(port,'127.0.0.1',done));console.log('S50 plugin fixture ready at loopback '+port);
async function stop(){await writeFile(join(directory,'requests.json'),JSON.stringify(requests,null,2)+'\n');server.closeAllConnections();await new Promise(done=>server.close(done));process.exit(0);}
process.once('SIGINT',stop);process.once('SIGTERM',stop);
