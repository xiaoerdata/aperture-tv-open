import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));
const verify=spawn(process.execPath,['scripts/doctor.mjs'],{cwd:root,stdio:'inherit'});
verify.on('exit',code=>{if(code!==0){process.exitCode=code||1;return;}const server=spawn(process.execPath,['server/main.mjs'],{cwd:root,stdio:'inherit',env:process.env});for(const signal of ['SIGINT','SIGTERM'])process.once(signal,()=>server.kill(signal));server.on('exit',code=>process.exitCode=code||0);});
