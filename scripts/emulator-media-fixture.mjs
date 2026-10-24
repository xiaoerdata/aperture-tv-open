// Loopback-only synthetic media/API for the dedicated Android TV test emulator.
import {createServer} from 'node:http';
import {mkdir,readFile,writeFile,stat} from 'node:fs/promises';
import {createReadStream} from 'node:fs';
import {spawnSync} from 'node:child_process';
import {join,basename} from 'node:path';
const directory='artifacts/s47-runtime/fixture',port=4398;
await mkdir(join(directory,'hls'),{recursive:true});
const ffmpeg=process.env.FFMPEG_PATH||'ffmpeg';
for(const args of [
 ['-f','lavfi','-i','testsrc2=size=320x180:rate=12','-f','lavfi','-i','sine=frequency=400:sample_rate=48000','-t','120','-c:v','libx264','-profile:v','baseline','-level','3.0','-preset','ultrafast','-pix_fmt','yuv420p','-g','48','-c:a','aac','-b:a','32k','-ac','1','-movflags','+faststart',join(directory,'video.mp4')],
 ['-i',join(directory,'video.mp4'),'-c','copy','-hls_time','4','-hls_list_size','0','-hls_segment_filename',join(directory,'hls','segment%d.ts'),join(directory,'hls','index.m3u8')]
]){
 const r=spawnSync(ffmpeg,['-hide_banner','-loglevel','error','-y','-threads','2',...args],{encoding:'utf8'});if(r.status!==0)throw new Error(r.stderr||r.error?.message);
}
const origin='http://10.0.2.2:'+port,requests=[];let offline=false;
const profile={sites:[{name:'家庭JSON站',type:1,api:origin+'/api'}],lives:[{name:'直播清单',url:origin+'/channels.m3u'},{name:'HTML来源',channels:[{name:'坏样本',urls:[origin+'/html']}]}]};
const videos=[{vod_id:'movie-1',vod_name:'周末短片',vod_remarks:'测试影片',vod_play_from:'家庭线路',vod_play_url:Array.from({length:14},(_,i)=>'第'+(i+1)+'集$'+origin+'/video.mp4').join('#')},{vod_id:'broken',vod_name:'失效影片',vod_play_from:'错误线路',vod_play_url:'播放$'+origin+'/missing.mp4'}];
const m3u='#EXTM3U\n#EXTINF:-1 group-title="客厅",台一\n'+origin+'/video.mp4\n#EXTINF:-1 group-title="客厅",台二\n'+origin+'/hls/index.m3u8\n';
function send(res,code,type,data){res.writeHead(code,{'Content-Type':type,'Cache-Control':'no-store'});res.end(data);}
const server=createServer(async(req,res)=>{
 const url=new URL(req.url,'http://127.0.0.1:'+port);requests.push({time:Date.now(),method:req.method,path:url.pathname,query:Object.fromEntries(url.searchParams),range:req.headers.range||null});
 try{
  if(url.pathname==='/control/offline'){offline=url.searchParams.get('value')==='1';return send(res,200,'text/plain','ok');}
  if(url.pathname==='/requests')return send(res,200,'application/json',JSON.stringify(requests));
  if(url.pathname==='/profile.json')return send(res,200,'application/json',JSON.stringify(profile));
  if(url.pathname==='/channels.m3u')return send(res,200,'audio/x-mpegurl',m3u);
  if(url.pathname==='/html')return send(res,200,'text/html','<html>Not a media stream</html>');
  if(url.pathname==='/api'){
   if(offline)return send(res,503,'application/json','{}');
   let list=videos;const id=url.searchParams.get('ids'),word=url.searchParams.get('wd');if(id)list=videos.filter(v=>v.vod_id===id);if(word)list=list.filter(v=>v.vod_name.includes(word));
   return send(res,200,'application/json',JSON.stringify({class:[{type_id:'movie',type_name:'家庭电影'},{type_id:'series',type_name:'家庭剧集'}],page:1,pagecount:1,list}));
  }
  let file,type;
  if(url.pathname==='/video.mp4'){file=join(directory,'video.mp4');type='video/mp4';}
  else if(/^\/hls\/(index\.m3u8|segment\d+\.ts)$/.test(url.pathname)){file=join(directory,'hls',basename(url.pathname));type=file.endsWith('.m3u8')?'application/vnd.apple.mpegurl':'video/mp2t';}
  else return send(res,404,'text/plain','missing');
  const {size}=await stat(file);res.setHeader('Content-Type',type);res.setHeader('Accept-Ranges','bytes');
  let start=0,end=size-1;if(req.headers.range){const match=/^bytes=(\d+)-(\d*)$/.exec(req.headers.range);if(!match||Number(match[1])>=size){res.writeHead(416,{'Content-Range':'bytes */'+size});return res.end();}start=Number(match[1]);end=match[2]?Math.min(size-1,Number(match[2])):size-1;if(end<start)return send(res,416,'text/plain','range');res.statusCode=206;res.setHeader('Content-Range',`bytes ${start}-${end}/${size}`);}
  res.setHeader('Content-Length',end-start+1);if(req.method==='HEAD')return res.end();const stream=createReadStream(file,{start,end});res.on('close',()=>stream.destroy());stream.pipe(res);
 }catch{if(!res.headersSent)res.writeHead(500);res.end();}
});
await new Promise(r=>server.listen(port,'127.0.0.1',r));
console.log('Synthetic emulator media fixture ready on loopback '+port);
async function stop(){await writeFile(join(directory,'requests.json'),JSON.stringify(requests,null,2)+'\n');server.closeAllConnections();await new Promise(r=>server.close(r));process.exit(0);}
process.once('SIGTERM',stop);process.once('SIGINT',stop);
