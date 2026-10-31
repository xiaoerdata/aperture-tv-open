// Usage: node scripts/message-feed.mjs path/to/family.messages.json
import {readFile} from 'node:fs/promises';import {messageFeed} from '../shared/message-feed.mjs';
try{const bytes=await readFile(process.argv[2]);if(bytes.length>131072)throw Error('留言文件超过128KiB');const data=messageFeed(JSON.parse(bytes.toString('utf8')));console.log('留言格式有效：'+data.messages.length+' 条；此检查不上传文件，也不证明电视已同步。');}catch(e){console.error(e.message);process.exitCode=1;}
