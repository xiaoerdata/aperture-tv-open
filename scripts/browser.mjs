import {pathToFileURL} from 'node:url';
export async function launchBrowser(){
  let library;
  if(process.env.PLAYWRIGHT_MODULE) library=await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href);
  else {try{library=await import('playwright-core');}catch{library=await import('../../zhixiaoce-hub/node_modules/playwright-core/index.mjs');}}
  return library.chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH || '/home/ubuntu/.cache/ms-playwright/chromium-1161/chrome-linux/chrome',args:['--no-sandbox']});
}
