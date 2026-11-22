// Run only on the user's already paired TV; local evidence may contain private film titles/photos.
import {readFile,writeFile,mkdir} from 'node:fs/promises';import {resolve,join} from 'node:path';import {homedir} from 'node:os';import {request} from './tv-control.mjs';
const args=process.argv.slice(2),option=n=>{const at=args.indexOf('--'+n);return at<0?undefined:args[at+1]},output=option('output');
if(!output)throw Error('指定新的本地证据目录 --output PATH；仅发送一次返回，不自动重试按键');
const config=JSON.parse(await readFile(resolve(option('session')||process.env.KAISHI_TV_SESSION||join(homedir(),'.config/kaishi/tv-control.json')),'utf8'));if(config.expiresAt<=Date.now())throw Error('配对已过期，请重新配对');
const before=await request(config,'/status');if(before.screen!=='CinemaActivity'||!before.playing)throw Error('请先打开影视播放画面，关闭选集/线路菜单和工具条后再执行');
const directory=resolve(output);await mkdir(directory,{mode:0o700});
async function save(name,value){await writeFile(join(directory,name),typeof value==='string'||Buffer.isBuffer(value)?value:JSON.stringify(value,null,2)+'\n',{flag:'wx',mode:0o600})}
await save('before-status.json',before);await save('before-ui.json',await request(config,'/ui'));if(args.includes('--screenshots'))await save('before.png',await request(config,'/screenshot'));
let command;
try{command=await request(config,'/commands',{method:'POST',body:{commands:[{type:'key',key:'back'}]}})}catch(error){await save('result.json',{state:'unknown',message:'按键结果未知，未自动重试；先查看电视及当前status/ui'});throw error}
await new Promise(r=>setTimeout(r,400));const after=await request(config,'/status');await save('after-status.json',after);await save('after-ui.json',await request(config,'/ui'));if(args.includes('--screenshots'))await save('after.png',await request(config,'/screenshot'));
const result={commandsExecuted:command.executed,stayedInCinema:after.screen==='CinemaActivity',playingAfter:after.playing,physicalScreenAccepted:false,note:'状态/截图仅作为本机回读，详情页是否正确须核对UI；图片可能含私人资料，不自动上传'};await save('result.json',result);console.log(JSON.stringify({saved:directory,stayedInCinema:result.stayedInCinema,playingAfter:result.playingAfter}));
