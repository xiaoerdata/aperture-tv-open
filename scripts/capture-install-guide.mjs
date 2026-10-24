// Current onboarding copy, real browser frames; no household credentials or pairing code.
import {mkdtemp,rm,writeFile,mkdir} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createApp} from '../server/app.mjs';
import {launchBrowser} from './browser.mjs';
const prefix=process.argv[2]||'s33-install-guide';
if(!/^[a-z0-9-]+$/.test(prefix))throw new Error('Use a simple screenshot prefix');
const directory=await mkdtemp(join(tmpdir(),'aperture-guide-')),origin='http://127.0.0.1:4394';
const app=await createApp({directory,ownerKey:'synthetic-guide-owner-123456789',origin});await new Promise(r=>app.server.listen(4394,'127.0.0.1',r));const browser=await launchBrowser(),errors=[],frames=[];
try{
 const page=await browser.newPage({viewport:{width:1440,height:1000}});page.on('pageerror',e=>errors.push(e.message));await page.goto(origin+'/guide');
 await mkdir('docs/images',{recursive:true});
 await mkdir('docs/verification',{recursive:true});
 for(const width of [320,390,430,760,761,1440]){await page.setViewportSize({width,height:1000});await page.locator('#family details').first().evaluate(el=>el.open=true);if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw new Error('guide overflows '+width);}
 for(const [width,height,label]of [[1440,1000,'desktop'],[390,844,'phone']]){
  await page.setViewportSize({width,height});
  const path='docs/images/'+prefix+'-'+label+'.png';await page.screenshot({path,fullPage:true,animations:'disabled'});frames.push({width,height,path});
  await page.locator('#start').screenshot({path:'docs/images/'+prefix+'-routes-'+label+'.png',animations:'disabled'});
 }
 // Follow the two actual preview links from the guide, then return to inspect anchors.
 const routes=[];
 for(const selector of ['.guide-hero a[href="/home?demo=1"]','.guide-hero a[href*="edition=parents"]']){
  await page.goto(origin+'/guide');await page.locator(selector).click();await page.locator('#lobby').waitFor();routes.push(new URL(page.url()).pathname+new URL(page.url()).search);
 }
 await page.goto(origin+'/guide');
 const missingAnchors=await page.locator('a[href^="#"]').evaluateAll(links=>links.map(a=>a.getAttribute('href')).filter(href=>href.length>1&&!document.getElementById(href.slice(1))));
 if(missingAnchors.length)throw new Error('Missing guide sections: '+missingAnchors.join(', '));
 if(errors.length)throw new Error(errors.join('\n'));await writeFile('docs/verification/'+prefix+'.json',JSON.stringify({frames,errors,previewRoutesFollowed:routes,missingAnchors,widths:[320,390,430,760,761,1440],expandedDeploymentSteps:true,realBrowser:true,realTV:false,credentialsShown:false},null,2)+'\n');console.log({frames,routes,errors});
}finally{await browser.close();await new Promise(r=>app.server.close(r));await rm(directory,{recursive:true,force:true});}
