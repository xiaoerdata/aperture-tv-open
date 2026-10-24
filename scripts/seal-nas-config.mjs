// Local-only helper: plaintext file never becomes an MCP argument. Paths only on the command line.
import {readFile,writeFile,lstat} from 'node:fs/promises';
import {sealNas} from '../public/nas-setup/seal.mjs';
async function main(){
const args=process.argv.slice(2),option=name=>{const at=args.indexOf(name);if(at<0||!args[at+1])throw new Error('Use --request public-request.json --input private-nas.json --output sealed-delivery.json');return args[at+1];};
const requestFile=option('--request'),inputFile=option('--input'),output=option('--output');
const info=await lstat(inputFile);
if(!info.isFile()||info.size>8192||process.platform!=='win32'&&(info.mode&0o077))throw new Error('NAS input must be a private regular file (chmod 600), at most 8KB');
const requestInfo=await lstat(requestFile);if(!requestInfo.isFile()||requestInfo.size>8192)throw new Error('Invalid public request file');
const request=JSON.parse(await readFile(requestFile,'utf8'));
if(!/^[a-f0-9-]{36}$/.test(request.id||''))throw new Error('Invalid TV request');
let draft=JSON.parse(await readFile(inputFile,'utf8'));
const envelope=await sealNas(request,draft);draft.password='';draft.user='';draft=null;
await writeFile(output,JSON.stringify({id:request.id,envelope,execution:{key:crypto.randomUUID(),revision:request.revision}},null,2)+'\n',{flag:'wx',mode:0o600});
console.log('Sealed delivery saved to private output file. No plaintext was sent or printed; the input file remains under your control.');
}
try{await main();}catch{console.error('未生成交接文件。请检查参数、私有文件权限、JSON格式和当前电视请求；不会打印输入内容。');process.exitCode=1;}
