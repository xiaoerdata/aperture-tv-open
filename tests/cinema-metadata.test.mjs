import test from 'node:test';import assert from 'node:assert/strict';
import {normalizeName,automaticMatch,providerArtwork,createMetadata} from '../server/cinema/metadata.mjs';
test('metadata names remove release tags, retain explicit year, and prefer latest without a confirmation step',()=>{
 assert.deepEqual(normalizeName('流浪地球.2019.2160p.WEB-DL.mkv'),{title:'流浪地球',year:'2019'});
 assert.deepEqual(normalizeName('The.Last.of.Us.S01E02.1080p.mkv'),{title:'The Last of Us',year:''});
 const values=[{key:'old',title:'三体',year:'2015'},{key:'new',title:'三体',year:'2023'}];
 assert.equal(automaticMatch(normalizeName('三体'),values).key,'new');assert.equal(automaticMatch(normalizeName('三体','2015'),values).key,'old');
 assert.equal(automaticMatch(normalizeName('山海之间'),[{key:'a',title:'山海之间的故事',year:'2020'},{key:'b',title:'山海之间的旅程',year:'2025'}]).key,'b');
 assert.equal(automaticMatch(normalizeName('三体'),[{key:'x',title:'Friends',year:'2025'}]),null);
 assert.equal(automaticMatch(normalizeName(''),values),null);
});
test('provider poster URLs reject credentials, lookalike origins, arbitrary hosts and local paths',()=>{
 assert.equal(providerArtwork('https://image.tmdb.org/t/p/w500/test.jpg'),'https://image.tmdb.org/t/p/w500/test.jpg');
 for(const v of ['http://image.tmdb.org/t/p/w500/test.jpg','https://image.tmdb.org.evil/t/p/w500/test.jpg','https://u:p@static.tvmaze.com/uploads/test.jpg','https://127.0.0.1/private','file:///etc/passwd','https://static.tvmaze.com:444/uploads/test.jpg'])assert.equal(providerArtwork(v),null,v);
});
test('metadata opt-in, provider credentials, source-free queries and bounded cached results',async()=>{
 let requests=[];const fetcher=async(url,options)=>{requests.push({url,options});return new Response(JSON.stringify({results:[{id:1,title:'山海之间',release_date:'2020-01-01',poster_path:'/one.jpg',overview:'<p>旧版</p>'},{id:2,title:'山海之间',release_date:'2025-01-01',poster_path:'/two.jpg',overview:'<p>新版</p>'}]}),{headers:{'Content-Type':'application/json'}});};
 const off=createMetadata({enabled:false,token:'synthetic-only',fetcher});assert.equal((await off.search('山海之间','','movie')).state,'disabled');assert.equal(requests.length,0);
 const on=createMetadata({enabled:true,token:'synthetic-only',fetcher});const result=await on.search('山海之间','','movie');assert.equal(result.match.key,'tmdb:movie:2');assert.equal(result.match.summary,'新版');assert.equal(requests.length,1);assert.ok(!requests[0].url.includes('synthetic-only'));assert.equal(requests[0].options.headers.Authorization,'Bearer synthetic-only');await on.search('山海之间','','movie');assert.equal(requests.length,1);assert.equal(requests[0].options.redirect,'error');
 const noToken=createMetadata({enabled:true,token:'',fetcher});assert.equal((await noToken.search('山海之间','','movie')).state,'needs_tmdb');assert.equal(requests.length,1);off.close();on.close();noToken.close();
});
test('provider errors do not break playback/catalogue; oversized responses are discarded',async()=>{
 const broken=createMetadata({enabled:true,token:'synthetic-only',fetcher:async()=>{throw Error('offline');}});assert.equal((await broken.search('流浪地球','','movie')).state,'unavailable');broken.close();
 const huge=createMetadata({enabled:true,token:'synthetic-only',fetcher:async()=>new Response('x'.repeat(524289))});assert.equal((await huge.search('流浪地球','','movie')).state,'unavailable');huge.close();
});

test('resource-pack metadata and channel logos follow the same URL policy as media addresses',async()=>{const {checkPack}=await import('../scripts/resource-pack.mjs');const {readFile}=await import('node:fs/promises');const pack=JSON.parse(await readFile('examples/resource-packs/family.kaishi.json','utf8'));pack.resources[1].items[0].metadata={poster:'https://media.example/poster.jpg',year:'2025',summary:'家庭简介'};pack.resources[0].channels[0].logo='https://media.example/logo.png';assert.ok(checkPack(Buffer.from(JSON.stringify(pack))));for(const field of ['poster','logo'])for(const bad of ['https://name:pass@media.example/x.jpg','http://media.example/x.jpg','javascript:alert(1)']){const next=structuredClone(pack);if(field==='poster')next.resources[1].items[0].metadata.poster=bad;else next.resources[0].channels[0].logo=bad;assert.throws(()=>checkPack(Buffer.from(JSON.stringify(next))));}});
