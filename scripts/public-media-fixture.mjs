// Optional development fixture. Downloaded public media stays in artifacts; never bundled.
import {createServer} from 'node:http';
import {readFile,writeFile,stat} from 'node:fs/promises';
import {createReadStream} from 'node:fs';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {mkdir} from 'node:fs/promises';
const run=promisify(execFile);let capturing=false;
const dir='artifacts/s66-public-media', requests=[];
const server=createServer(async(req,res)=>{
  const path=new URL(req.url,'http://fixture.invalid').pathname;
  requests.push({path,range:req.headers.range||'',at:Date.now()});
  if(path==='/capture'){
    const query=new URL(req.url,'http://fixture.invalid').searchParams,name=query.get('name'),pkg=query.get('package');
    if(!['section','detail','toolbar','options','series','variety','hls','channels','failure'].includes(name)||!['org.zhixiaoce.kaishi','org.zhixiaoce.kaishi.parents'].includes(pkg)||capturing){res.writeHead(400);res.end();return;}
    capturing=true;try{const edition=pkg.endsWith('.parents')?'parents':'standard',out=`artifacts/s68-host-captures/${edition}`;await mkdir(out,{recursive:true});
      const {stdout}=await run('.android-local/android-sdk/platform-tools/adb',['-s','emulator-5568','exec-out','screencap','-p'],{encoding:'buffer',maxBuffer:12*1024*1024,timeout:30000});
      if(!stdout.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])))throw Error('No PNG');await writeFile(`${out}/${name}.png`,stdout);res.end('captured');
    }catch(error){res.writeHead(503);res.end('capture failed');}finally{capturing=false;}return;
  }
  if(path==='/requests'){res.end(JSON.stringify(requests));return;}
  if(path==='/pack.json'){res.setHeader('Content-Type','application/json');res.end(JSON.stringify(pack));return;}
  if(path==='/channels.m3u'){res.end(`#EXTM3U\n#EXTINF:-1 tvg-logo="./poster.jpg" group-title="公开测试",BBB · MP4\nbbb.mp4\n#EXTINF:-1 tvg-logo="./poster.jpg" group-title="公开测试",BBB · HLS片段\nsample.m3u8\n`);return;}
  const file=path.slice(1), diskFile=file==='bbb.mp4'?'bbb-faststart.mp4':file;
  if(!/^(bbb\.mp4|poster\.jpg|sample\.m3u8|segment[0-2]\.ts)$/.test(file)){res.writeHead(404);res.end();return;}
  try{const size=(await stat(`${dir}/${diskFile}`)).size;res.setHeader('Content-Type',file.endsWith('.mp4')?'video/mp4':file.endsWith('.jpg')?'image/jpeg':file.endsWith('.ts')?'video/mp2t':'application/vnd.apple.mpegurl');res.setHeader('Accept-Ranges','bytes');
    const range=/^bytes=(\d+)-(\d*)$/.exec(req.headers.range||'');let start=0,end=size-1;
    if(range){start=Number(range[1]);end=Math.min(end,range[2]?Number(range[2]):end);if(start>end){res.writeHead(416);res.end();return;}res.statusCode=206;res.setHeader('Content-Range',`bytes ${start}-${end}/${size}`);}
    res.setHeader('Content-Length',end-start+1);createReadStream(`${dir}/${diskFile}`,{start,end}).pipe(res);
  }catch(e){res.writeHead(500);res.end();}
});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
const port=server.address().port,base=`http://10.0.2.2:${port}`;
const pack={format:'kaishi.resource-pack',version:1,id:'org.example.public.bbb',revision:1,title:'开放短片测试',resources:[{type:'collection',id:'films',title:'开放短片',section:'movies',items:[['mp4','Big Buck Bunny · MP4','bbb.mp4'],['hls','Big Buck Bunny · HLS片段','sample.m3u8']].map(([id,title,path])=>({id,title,metadata:{poster:base+'/poster.jpg',year:'2008',genre:'动画',summary:'Blender Foundation 开放短片。CC BY 3.0。测试配图为影片第60秒画面；HLS为30秒测试片段。'},episodes:[{title:'正片',url:base+'/'+path}]}))}]};
for(const section of ['series','variety'])pack.resources.push({...pack.resources[0],id:section,title:section==='series'?'电视剧分区测试':'综艺分区测试',section});
await writeFile(dir+'/pack.json',JSON.stringify(pack));
await writeFile(dir+'/server.json',JSON.stringify({port,base,publicMediaViaLocalMirror:true}));
console.log(JSON.stringify({port,base}));
process.on('SIGTERM',async()=>{await writeFile(dir+'/requests.json',JSON.stringify(requests,null,2));server.close(()=>process.exit(0));});
