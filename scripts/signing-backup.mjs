// Never exported: key bytes stay in private runtime and .android-local, not receipts or logs.
import {mkdir,lstat,readFile,writeFile,copyFile,chmod} from 'node:fs/promises';
import {constants} from 'node:fs';
import {join} from 'node:path';
async function bytes(path){try{const stat=await lstat(path);if(!stat.isFile()||stat.isSymbolicLink())throw new Error('Signing key must be a regular private file');return await readFile(path);}catch(e){if(e.code==='ENOENT')return null;throw e;}}
export async function preserveDebugKey({runtimeDirectory,backupDirectory,existingSignedCandidate=false}){
 const key=join(runtimeDirectory,'android-user','debug.keystore'),backup=join(backupDirectory,'debug.keystore');
 const original=await bytes(key),saved=await bytes(backup);
 if(!original&&!saved){if(existingSignedCandidate)throw new Error('Original signing key is missing. Restore its private backup; refusing to generate a different key for an existing signed candidate.');return {present:false,restored:false};}
 if(original&&saved&&!original.equals(saved))throw new Error('Runtime signing key differs from persistent backup; both preserved.');
 await mkdir(backupDirectory,{recursive:true,mode:0o700});await chmod(backupDirectory,0o700);
 if(!saved){await writeFile(backup,original,{flag:'wx',mode:0o600});await chmod(backup,0o600);}
 if(!original){await mkdir(join(runtimeDirectory,'android-user'),{recursive:true,mode:0o700});await copyFile(backup,key,constants.COPYFILE_EXCL);await chmod(key,0o600);}
 return {present:true,restored:!original};
}
