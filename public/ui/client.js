export const $=selector=>document.querySelector(selector);
export function el(tag,className,text){const node=document.createElement(tag);if(className)node.className=className;if(text!==undefined)node.textContent=text;return node;}
export async function api(path,body,options={}){
  const {signal:externalSignal,...init}=options;
  const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),12_000),cancel=()=>controller.abort();
  if(externalSignal?.aborted)cancel();else externalSignal?.addEventListener('abort',cancel,{once:true});
  try{
    const res=await fetch(path,{method:body===undefined?'GET':'POST',headers:body===undefined?{}:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:controller.signal,credentials:'same-origin',...init});
    const value=await res.json();if(!res.ok){const error=new Error(value.message || '请求失败');error.status=res.status;error.code=value.code;throw error;}return value;
  }catch(e){if(e.name==='AbortError')throw new Error('连接超时；内容未确认，请保留草稿后重试');throw e;}finally{clearTimeout(timeout);externalSignal?.removeEventListener('abort',cancel);}
}
export function uid(){return globalThis.crypto?.randomUUID?.() || 'op_'+Date.now().toString(36)+'_'+Array.from(crypto.getRandomValues(new Uint8Array(12)),n=>n.toString(16).padStart(2,'0')).join('');}
export function photoUrl(photo){return photo.url || '/media/'+photo.asset;}
export function showToast(message){const node=$('#toast');node.textContent=message;node.hidden=false;clearTimeout(showToast.timer);showToast.timer=setTimeout(()=>node.hidden=true,4500);}
