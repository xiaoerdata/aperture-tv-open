import {randomBytes, randomInt, timingSafeEqual} from 'node:crypto';
import {digest} from './service.mjs';
import {fail} from '../shared/model.mjs';
import {scopes as automationScopes} from './local-agent/contract.mjs';
export function createAuth(store, ownerKey) {
  const ownerHash = Buffer.from(digest(ownerKey));
  const session = (role, label) => {
    const token = randomBytes(32).toString('base64url');
    return {token, id:digest(token), record:{role,label,expiresAt:Date.now()+30*86400_000}};
  };
  return {
    read(req) {
      const token = /(?:^|;\s*)aperture=([\w-]+)/.exec(req.headers.cookie || '')?.[1];
      const id = digest(token || ''); const s = store.read().sessions[id];
      if (!s || s.role==='controller' || s.expiresAt <= Date.now()) fail('请先连接家庭屏',401,'unauthorized');
      return {id,...s};
    },
    readController(req,peer){const token=/^Bearer ([\w-]+)$/.exec(req.headers.authorization||'')?.[1],id=digest(token||''),s=store.read().sessions[id];if(!s||s.role!=='controller'||s.expiresAt<=Date.now())fail('助手连接无效',401,'unauthorized');if(!peer||s.peerHash!==digest(peer))fail('网络来源已变化，请重新配对',403,'network_changed');return {id,...s};},
    async login(key) {
      if (typeof key !== 'string' || key.length > 200 || !timingSafeEqual(ownerHash, Buffer.from(digest(key)))) fail('管理口令不正确',401,'unauthorized');
      const s = session('owner','家庭管理员');
      await store.transact(state => { state.sessions[s.id] = s.record; this.prune(state); }); return s.token;
    },
    prune(state) {
      for (const [id,s] of Object.entries(state.sessions)) if(s.expiresAt <= Date.now()) delete state.sessions[id];
      if(Object.keys(state.sessions).length > 80) fail('连接设备过多，请先撤销旧设备',429,'quota');
      for (const [id,p] of Object.entries(state.pairs)) if(p.expiresAt <= Date.now()) delete state.pairs[id];
    },
    async start(role,peer) {
      if (!['viewer','editor','controller'].includes(role)) fail('连接角色无效');
      return store.transact(state => {
        this.prune(state);
        if(Object.keys(state.pairs).length >= 50) fail('连接请求较多，请稍后再试',429,'rate_limited');
        let code; do { code = String(randomInt(100000,1000000)); } while (state.pairs[code]);
        const secret = randomBytes(32).toString('base64url'); const expiresAt = Date.now()+5*60_000;
        const challenge=role==='controller'?randomBytes(16).toString('base64url'):undefined;
        if(role==='controller'&&!peer)fail('缺少网络验证',403);
        state.pairs[code] = {role, secret:digest(secret), expiresAt, approved:false,...(challenge?{challenge,peerHash:digest(peer)}:{})};
        return {code,secret,expiresAt,...(challenge?{challenge}:{})};
      });
    },
    async approve(actor,code,grant) {
      if (typeof code !== 'string' || !/^\d{6}$/.test(code)) fail('连接码无效');
      if(actor.role !== 'owner') fail('只有管理员可以批准连接',403,'forbidden');
      return store.transact(state => {
        if(!state.sessions[actor.id] || state.sessions[actor.id].expiresAt<=Date.now() || state.sessions[actor.id].role!=='owner') fail('管理员会话已失效',401,'unauthorized');
        const p=state.pairs[code];
        if(!p || p.expiresAt <= Date.now()) fail('连接码不存在或已过期',404,'not_found');
        if(p.approved)fail('连接码已批准，不能更改授权',409,'already_approved');
        if(p.role==='controller'){
          if(p.challenge!==grant?.challenge)fail('请使用完整的扫码连接请求',403,'challenge_mismatch');
          if(!grant||!Array.isArray(grant.scopes)||!grant.scopes.includes('home.read')||grant.scopes.some(s=>!automationScopes.includes(s))||![1,24,168].includes(grant.hours))fail('请在助手连接入口明确选择能力和有效期');
          p.grant={scopes:[...new Set(grant.scopes)],expiresAt:Date.now()+grant.hours*3600000,peerHash:p.peerHash};
        }else if(grant)fail('此连接码不是助手连接');
        p.approved=true; return {role:p.role,expiresAt:p.expiresAt};
      });
    },
    async poll(code,secret,controller=false,peer) {
      if (typeof code !== 'string' || !/^\d{6}$/.test(code)) fail('连接码无效');
      return store.transact(state => {
        const p=state.pairs[code];
        if(!p || p.expiresAt <= Date.now() || typeof secret !== 'string' || p.secret !== digest(secret)) fail('连接请求已过期，请重新连接',410,'pair_expired');
        if((p.role==='controller')!==controller)fail('连接入口不匹配',403,'forbidden');
        if(controller&&(!peer||p.peerHash!==digest(peer)))fail('网络来源已变化，请重新申请',403,'network_changed');
        if(!p.approved) return {status:'pending'};
        const s=session(p.role,p.role==='controller'?'本机 WorkBuddy 助手':p.role === 'viewer'?'家庭电视':'家人手机');
        if(p.role==='controller'){if(!p.grant)fail('缺少助手授权',403);s.record={...s.record,...p.grant,kind:'self-hosted-local'};}
        state.sessions[s.id]=s.record; delete state.pairs[code]; this.prune(state);
        return {status:'connected',token:s.token,role:s.record.role};
      });
    },
    async revoke(actor,id) {
      if(actor.role !== 'owner' && actor.id !== id) fail('无权移除设备',403,'forbidden');
      await store.transact(state => { if(!state.sessions[actor.id] || state.sessions[actor.id].expiresAt<=Date.now()) fail('会话已失效',401,'unauthorized'); delete state.sessions[id]; });
    }
  };
}
