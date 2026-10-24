import {spawnSync} from 'node:child_process';
import {access,readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const checks=[];
checks.push({name:'Node.js 22+',ok:Number(process.versions.node.split('.')[0])>=22,detail:process.version});
for(const name of ['@modelcontextprotocol/sdk','hls.js','qrcode']){try{await access(fileURLToPath(new URL('../node_modules/'+name+'/package.json',import.meta.url)));checks.push({name,ok:true});}catch{checks.push({name,ok:false,detail:'在项目目录执行 npm ci'});}}
let pythonFound=false;
for(const command of ['python3']){const result=spawnSync(command,['-c','import sys; from PIL import Image; print(sys.version.split()[0]); print(Image.__version__)'],{encoding:'utf8',timeout:10000});if(result.status===0){checks.push({name:'Python 3.10+ / Pillow',ok:Number(result.stdout.split('.')[0])>=3&&Number(result.stdout.split('.')[1])>=10,detail:command+' '+result.stdout.trim().replace(/\n/g,' / ')});pythonFound=true;break;}}
if(!pythonFound)checks.push({name:'Python / Pillow',ok:false,detail:'python3 -m venv .venv，然后 .venv/bin/pip install -r requirements.txt，并把 .venv/bin 放在 PATH 前'});
const data=process.env.APERTURE_DATA||'.data';try{await access(data);checks.push({name:'家庭数据目录',ok:true,detail:'已存在；会保留原内容，不显示口令'});}catch{checks.push({name:'家庭数据目录',ok:true,detail:'首次启动时创建私有目录'});}
const summary={ready:checks.every(c=>c.ok),checks,exposure:'默认仅本机访问；跨设备需配置正确 PUBLIC_ORIGIN、监听与HTTPS。',television:'完整模式WebView89+；轻量模式面向旧内核，尚待真机验证。'};
if(process.argv.includes('--json'))console.log(JSON.stringify(summary,null,2));else{console.log('开窗 · 启动前检查');for(const c of checks)console.log((c.ok?'✓ ':'× ')+c.name+(c.detail?' — '+c.detail:''));console.log(summary.exposure);console.log(summary.television);}
process.exitCode=summary.ready?0:1;
