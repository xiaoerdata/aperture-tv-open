import {join} from 'node:path';
import {createStore} from './store.mjs';
import {createBoardService} from './service.mjs';
import {createAuth} from './auth.mjs';
import {createImages} from './images.mjs';
import {createHttp} from './http.mjs';
import {createLifeService} from './life/service.mjs';
import {createCinemaService} from './cinema/service.mjs';
import {createLocalAgent} from './local-agent/service.mjs';
import {createAppOperations} from './app-operations/service.mjs';
import {createNasSetup} from './nas-setup/service.mjs';
import {createMessages} from './messages/service.mjs';
import {createWeather} from './weather.mjs';
export async function createApp({directory,ownerKey,origin,sources=[],mediaConfig,localAutomation=false,trustedProxies=[],messageSource,weatherFetcher}) {
  if(!ownerKey || ownerKey.length<24)throw new Error('Owner key must be at least 24 characters');
  const store=await createStore(directory),service=createBoardService(store),auth=createAuth(store,ownerKey),images=createImages(join(directory,'images'));
  const life=createLifeService({store,service,sources});
  const cinema=createCinemaService({store,service,config:mediaConfig});
  const nasSetup=createNasSetup({store});
  const appOperations=createAppOperations({store});
  const localAgent=createLocalAgent({store,service,nasSetup,appOperations,enabled:localAutomation});
  const messages=createMessages({source:messageSource});
  const weather=createWeather({fetcher:weatherFetcher});
  const server=createHttp({service,store,auth,images,origin,life,cinema,nasSetup,appOperations,localAgent,localAutomation,trustedProxies,weather,messages});server.once('close',()=>{cinema.close();nasSetup.close();appOperations.close();weather.close();messages.close();});
  return {server,store,service,auth,life,cinema,localAgent,nasSetup,appOperations};
}
