import {spawn} from 'node:child_process';
import {mkdir, readdir, writeFile} from 'node:fs/promises';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {digest} from './service.mjs';
import {fail} from '../shared/model.mjs';
export function createImages(directory) {
  let running=0;
  return {
    directory,
    async save(data) {
      if(typeof data !== 'string' || data.length > 4_000_000 || !/^[A-Za-z0-9+/]+={0,2}$/.test(data)) fail('图片需为3MB以内的JPEG、PNG或WebP');
      if(running>=2) fail('正在处理照片，请稍后重试',429,'busy');
      running++;
      let output;
      try {
        output = await new Promise((resolve,reject) => {
          const child=spawn('python3',[fileURLToPath(new URL('./image.py',import.meta.url))],{stdio:['pipe','pipe','ignore']});
          const chunks=[];let size=0;const timer=setTimeout(()=>child.kill(),10_000);
          child.stdout.on('data',b=>{size+=b.length;if(size>8_000_000)child.kill();else chunks.push(b);});
          child.stdin.on('error',()=>{});
          child.on('error',e=>{clearTimeout(timer);reject(e);});
          child.on('close',code=>{clearTimeout(timer);code===0?resolve(Buffer.concat(chunks)):reject(new Error('decode'));});
          child.stdin.end(Buffer.from(data,'base64'));
        });
      } catch { fail('图片无法解码、尺寸过大，或图像处理器不可用',422,'invalid_image'); }
      finally {running--;}
      await mkdir(directory,{recursive:true,mode:0o700});
      const asset=digest(output)+'.jpg';
      const files=await readdir(directory);
      if(files.length>=128 && !files.includes(asset)) fail('图片存储已满，请管理员整理',413,'quota');
      await writeFile(join(directory,asset),output,{mode:0o600}); return asset;
    }
  };
}
