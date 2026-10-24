import {createHash, randomUUID} from 'node:crypto';
import {fail, normalizeEntry, normalizeSettings, text} from '../shared/model.mjs';
export const digest = value => createHash('sha256').update(value).digest('hex');
export function createBoardService(store) {
  return {
    read: () => store.read().board,
    async write(actor, operation, input, execution) {
      if (!input || typeof input !== 'object' || Array.isArray(input)) fail('操作内容必须是对象');
      if (!['owner','editor','controller'].includes(actor.role)) fail('电视只有展示权限，请用手机编辑',403,'forbidden');
      if (!execution || !/^[a-zA-Z0-9_-]{8,100}$/.test(execution.key || '') || !Number.isSafeInteger(execution.revision)) fail('缺少幂等键或版本');
      const requestHash = digest(JSON.stringify({operation,input}));
      return store.transact(state => {
        const active = state.sessions[actor.id];
        if (!active || active.expiresAt <= Date.now() || active.role !== actor.role) fail('会话已失效',401,'unauthorized');
        if(actor.role==='controller'){
          const required={'entry.add':'content.write','scene.update':'scene.write'}[operation];
          if(!required||!active.scopes?.includes(required))fail('此能力未授权',403,'scope_denied');
        }
        const key = `${actor.id}:${execution.key}`;
        if (state.receipts[key]) {
          if (state.receipts[key].hash !== requestHash) fail('幂等键已用于不同操作',409,'idempotency_conflict');
          return {...state.receipts[key].result, replayed:true};
        }
        const board = state.board;
        if (board.revision !== execution.revision) fail('家人刚刚更新了内容，请刷新后再提交；草稿已保留',409,'revision_conflict');
        if (operation === 'entry.add') {
          if (board.entries.length >= 120) fail('最多保留120条内容，请先整理旧内容',413,'quota');
          board.entries.push({...normalizeEntry(input), id:randomUUID(), createdAt:new Date().toISOString()});
        } else if (operation === 'entries.import') {
          if (!Array.isArray(input.entries) || input.entries.length < 1 || input.entries.length > 20) fail('每次导入1至20条内容');
          if (board.entries.length + input.entries.length > 120) fail('导入后将超过120条内容上限',413,'quota');
          const entries = input.entries.map(normalizeEntry);
          board.entries.push(...entries.map(entry=>({...entry,id:randomUUID(),createdAt:new Date().toISOString()})));
        } else if (operation === 'scene.update') {
          if(typeof input.motion!=='boolean')fail('动效开关无效');
          board.settings={...normalizeSettings({...board.settings,scene:input.scene,motion:input.motion}),headline:text(input.headline,40,'场景标题',true)};
        } else if (operation === 'cinema.settings') {
          if(actor.role!=='owner')fail('只有管理员可配置家庭影院',403);
          board.cinema={enabled:input.enabled,sourceIds:input.sourceIds,sourceKinds:input.sourceKinds||{}};
        } else if (operation === 'cinema.playlist') {
          if(actor.role!=='owner')fail('只有管理员可配置播放清单',403);
          state.mediaChannels=input.channels;
        } else if (operation === 'life.publish') {
          if(actor.role!=='owner')fail('只有管理员可分享生活数据',403,'forbidden');
          if(!Array.isArray(input.signals)||!input.signals.length||input.signals.length>6)fail('每次分享1至6项生活数据');
          const ids=new Set(input.signals.map(s=>s.id));
          const keep=(board.life||[]).filter(s=>!ids.has(s.id));
          if(keep.length+input.signals.length>6)fail('家庭屏最多展示6项生活数据，请先移除旧项目',413,'quota');
          board.life=[...keep,...input.signals];
        } else if (operation === 'life.remove') {
          if(actor.role!=='owner')fail('只有管理员可移除生活数据',403,'forbidden');
          if(!(board.life||[]).some(s=>s.id===input.id))fail('生活数据不存在',404,'not_found');
          board.life=board.life.filter(s=>s.id!==input.id);
        } else if (operation === 'entry.remove') {
          if (!board.entries.some(e=>e.id===input.id)) fail('内容不存在',404,'not_found');
          board.entries = board.entries.filter(e=>e.id!==input.id);
        } else if (operation === 'entry.toggle') {
          const entry = board.entries.find(e=>e.id === input.id && e.type === 'event');
          if (!entry) fail('日程不存在',404,'not_found');
          if (typeof input.done !== 'boolean') fail('完成状态无效');
          entry.done = input.done;
        } else if (operation === 'settings.update') board.settings = normalizeSettings(input);
        else if (operation === 'photo.add') {
          if (board.photos.length >= 30) fail('最多保留30张照片',413,'quota');
          board.photos.push({id:randomUUID(), asset:input.asset, title:text(input.title,48,'照片标题'), caption:text(input.caption || '',100,'照片说明',true), createdAt:new Date().toISOString()});
        } else if (operation === 'photo.remove') {
          if (!board.photos.some(p=>p.id===input.id)) fail('照片不存在',404,'not_found');
          board.photos = board.photos.filter(p=>p.id!==input.id);
        } else fail('未知操作',404,'not_found');
        board.revision++; board.updatedAt = new Date().toISOString();
        const result = {revision:board.revision, operation, outcome:'applied'};
        state.receipts[key] = {hash:requestHash,result};
        state.audit||=[];state.audit.push({at:new Date().toISOString(),actor:actor.role,operation,revision:board.revision});state.audit=state.audit.slice(-200);
        for (const old of Object.keys(state.receipts).slice(0,-500)) delete state.receipts[old];
        return result;
      });
    }
  };
}
