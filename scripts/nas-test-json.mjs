// Test-only JVM implementation of Android's org.json API; never an APK dependency.
import {mkdir,readFile,rename,rm} from 'node:fs/promises';
import {join} from 'node:path';import {createHash} from 'node:crypto';import {execFile} from 'node:child_process';import {promisify} from 'node:util';
export async function nasTestJson(){
 const directory=join(process.env.APERTURE_NAS_TEST_RUNTIME||'/tmp/aperture-nas-runtime','test-libs'),file=join(directory,'json-20240303.jar'),expected='3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed';
 await mkdir(directory,{recursive:true});let data;try{data=await readFile(file);}catch(error){if(error.code!=='ENOENT')throw error;const temporary=file+'.'+process.pid;try{await promisify(execFile)('curl',['-fLsS','--noproxy','','--max-time','30','https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar','-o',temporary]);data=await readFile(temporary);if(createHash('sha256').update(data).digest('hex')!==expected)throw new Error('Test JSON artifact hash mismatch');await rename(temporary,file);}finally{await rm(temporary,{force:true});}}
 if(createHash('sha256').update(data).digest('hex')!==expected)throw new Error('Test JSON artifact hash mismatch');return file;
}
