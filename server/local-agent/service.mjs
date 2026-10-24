import {fail} from '../../shared/model.mjs';
import {tools,instructions} from './contract.mjs';
export function createLocalAgent({store,service,nasSetup,appOperations,enabled=false}){
  function authorize(actor,scope){if(!enabled)fail('本机自动化入口未开启',503,'local_automation_disabled');const active=store.read().sessions[actor.id];if(!active||active.role!=='controller'||active.expiresAt<=Date.now())fail('助手连接已过期或撤销',401,'unauthorized');if(scope&&!active.scopes?.includes(scope))fail('此能力未授权',403,'scope_denied');return active;}
  const acknowledgements=()=>Object.entries(store.read().displays||{}).filter(([id,d])=>store.read().sessions[id]?.role==='viewer'&&store.read().sessions[id]?.expiresAt>Date.now()&&Date.now()-Date.parse(d.at)<60000).map(([,d])=>d);
  return {
    discovery(actor){const active=authorize(actor);return {tools:tools.filter(t=>active.scopes.includes(t.scope)).map(({scope,...tool})=>({...tool,annotations:{readOnlyHint:scope==='home.read'||/(?:_requests|_status)$/.test(tool.name),destructiveHint:tool.name==='aperture_uninstall_send'}})),instructions,expiresAt:active.expiresAt};},
    async call(actor,name,args){
      const tool=tools.find(t=>t.name===name);if(!tool)fail('不存在此能力',404,'tool_not_found');authorize(actor,tool.scope);
      if(!args||typeof args!=='object'||Array.isArray(args))fail('参数需为对象');
      const appTool=/^aperture_(install|uninstall)_(requests|send|status)$/.exec(name);
      if(appTool){const [,kind,operation]=appTool;if(operation==='requests')return {requests:appOperations.list(actor,kind)};if(operation==='send')return appOperations.send(actor,kind,args);return appOperations.inspect(actor,kind,args.id,args.key);}
      if(name==='aperture_nas_requests')return {requests:nasSetup.list(actor)};
      if(name==='aperture_nas_status')return nasSetup.inspect(actor,args.id,args.key);
      if(name==='aperture_nas_send')return nasSetup.send(actor,args);
      if(name==='aperture_home'){const board=service.read();return {version:board.version,revision:board.revision,settings:board.settings,entries:board.entries,life:board.life||[],photoCount:board.photos.length,displayAcknowledgements:acknowledgements()};}
      if(name==='aperture_receipt'){if(!/^[a-zA-Z0-9_-]{8,100}$/.test(args.key||''))fail('幂等键无效');const receipt=store.read().receipts[actor.id+':'+args.key];return {outcome:receipt?'applied':'unknown',result:receipt?.result||null,displayAcknowledgements:acknowledgements()};}
      if(name==='aperture_scene')return service.write(actor,'scene.update',{scene:args.scene,motion:args.motion,headline:args.headline},args.execution);
      if(name==='aperture_add')return service.write(actor,'entry.add',args.entry,args.execution);
    }
  };
}
