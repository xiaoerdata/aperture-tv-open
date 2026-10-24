import {fail} from '../../shared/model.mjs';
export async function appOperationsHttp({path,req,actor,body,service}) {
 if(req.method!=='POST')fail('接口不存在',404);
 const input=await body();
 if(path==='/api/app-operations/start')return service.start(actor,input);
 if(path==='/api/app-operations/poll')return service.poll(actor,input);
 if(path==='/api/app-operations/stage')return service.stage(actor,input);
 if(path==='/api/app-operations/finish')return service.finish(actor,input);
 fail('接口不存在',404);
}
