// Curated source review, 2026-10-23. Inclusion is not account/hardware acceptance.
export const catalog = [
  {id:'ha',name:'Home Assistant · 家庭状态',group:'home',kind:'MCP',license:'Apache-2.0（HA Core）',url:'https://www.home-assistant.io/integrations/mcp_server/',summary:'把温湿度、空气质量、灯光状态汇成一张家庭状态页。',needs:'运行中的 Home Assistant、已暴露的实体、只读工具配置。',region:'可在家庭局域网运行；具体设备可能依赖厂商云。',status:'可配置',caution:'官方 MCP 与社区 ha-mcp 的工具不同；按实际 tools/list 配置，不能互换名称。'},
  {id:'ha-community',name:'ha-mcp · 家居与日历',group:'home',kind:'社区 MCP',license:'MIT',url:'https://github.com/homeassistant-ai/ha-mcp',summary:'读取设备状态、家庭日历和能耗记录。',needs:'Home Assistant + 独立 ha-mcp 服务；建议开启上游只读模式。',region:'可自托管；安装包与更新需另行获取。',status:'可配置',caution:'上游也有系统管理、控制工具。仅允许审阅过的读取调用。'},
  {id:'xiaomi-ha',name:'小米官方 HA 集成',group:'home',kind:'设备桥接 · 非 MCP',license:'小米自定义非商业许可',url:'https://github.com/XiaoMi/ha_xiaomi_home',summary:'由用户自己的 Home Assistant 接入米家，再选取家庭状态。',needs:'米家账号、兼容设备、Home Assistant；本地模式有网关要求。',region:'支持米家体系；不是所有设备都能完全离线。',status:'许可受限',caution:'源码可见不等于通用开源许可；不内置、复制或随开窗分发，商业用途需另行确认。'},
  {id:'mijia',name:'mijia-api · 米家直连候选',group:'home',kind:'社区 MCP',license:'GPL-3.0 / README 另有限制',url:'https://github.com/Do1e/mijia-api',summary:'提供米家设备信息与属性读取，也包含控制能力。',needs:'本地 Python 服务、米家登录、逐设备核查。',region:'依赖米家账号及相关接口。',status:'暂缓推荐',caution:'LICENSE 与 README 的非商业说明存在差异，未解决前不作为默认接入方案。'},
  {id:'qweather',name:'和风天气 · 出门前看一眼',group:'outside',kind:'社区 MCP',license:'MIT',url:'https://github.com/fengyucn/hefeng-qweather-mcp',summary:'城市天气、降雨、空气质量、预警。',needs:'和风开发者账号、API 凭证、城市；部分数据有配额或费用。',region:'国内气象服务；实际网络与账号权限待验证。',status:'可配置',caution:'天气数据另受服务条款约束。没有更新时显示过期，不当作实时预警。'},
  {id:'amap',name:'高德 · 周边与出行',group:'outside',kind:'社区 MCP',license:'MIT',url:'https://github.com/sugarforever/amap-mcp-server',summary:'把路线时长、目的地和周边地点整理成出行便签。',needs:'高德 Web 服务 Key、已安装的 MCP；地址由用户配置。',region:'面向国内地图服务；额度和使用条款由高德决定。',status:'可配置',caution:'默认不上屏家庭详细地址。通勤估时不是到达保证。'},
  {id:'rail',name:'12306 · 周末出发',group:'outside',kind:'社区 MCP',license:'MIT',url:'https://github.com/Joooook/12306-mcp',summary:'查询车次、余票和中转，展示候选出发时间。',needs:'本地 MCP 服务、出发地、目的地、出行日期。',region:'查询国内铁路信息；服务可用性与频率受上游影响。',status:'可配置',caution:'社区查询工具，不是12306官方合作；不订票、不支付，不把余票当作已预订。'},
  {id:'dida',name:'滴答清单 · 今天的小事',group:'today',kind:'社区 MCP',license:'MIT',url:'https://github.com/Martinqi826/dida-mcp',summary:'筛选今天到期任务，把家务与共同安排放到电视。',needs:'中国版滴答清单账号、开发者应用与 OAuth 授权。',region:'面向 dida365.com 中国版；不是国际版 TickTick。',status:'可配置',caution:'仅分享家庭清单，工作任务与私人清单留在手机。'},
  {id:'feishu',name:'飞书 · 家庭日程',group:'today',kind:'官方 MCP',license:'MIT',url:'https://github.com/larksuite/lark-openapi-mcp',summary:'读取授权日历，展示“今天几点回来”。',needs:'飞书应用、日历权限、相应用户授权；需要应用配置经验。',region:'使用 open.feishu.cn 国内环境。',status:'可配置',caution:'只授权家庭日历；会议主题、同事信息和消息不自动公开。'},
  {id:'memos',name:'Memos · 日常碎片',group:'memory',kind:'社区 MCP',license:'MIT',url:'https://github.com/mambucodev/mcp-memos',summary:'从家庭标签里选一句日记、提醒或孩子的趣事。',needs:'自托管 Memos 与访问令牌；核对实例版本。',region:'可部署在家庭 NAS 或境内服务器。',status:'可配置',caution:'选择明确的家庭条目；不将私人日记整库共享。'},
  {id:'siyuan',name:'思源笔记 · 今日阅读',group:'memory',kind:'社区 MCP',license:'ISC',url:'https://github.com/Fromsko/siyuan-mcp-server',summary:'把选定笔记的一句话或学习计划带回客厅。',needs:'正在运行的思源笔记、API Token、受限读取工具。',region:'可在局域网自托管；无需把笔记上传海外服务。',status:'可配置',caution:'避免通用 SQL 和任意 API 工具；精确选择可读取的块。'},
  {id:'immich',name:'Immich · 家庭回忆',group:'memory',kind:'社区 MCP',license:'MIT（桥接服务）',url:'https://github.com/mattmaas/immich-mcp',summary:'发现往年今日、家庭相册标题和照片数量。',needs:'自托管 Immich、API Key 与限定相册。',region:'可在 NAS 运行；模型和容器需预先下载。',status:'可配置',caution:'本版只映射文字/数值。照片二进制接入待做，照片可用现有上传入口。'},
  {id:'mealie',name:'Mealie · 今晚吃什么',group:'food',kind:'社区 MCP',license:'MIT（桥接服务）',url:'https://github.com/timo-reymann/mealie-mcp-server',summary:'读取已保存的菜谱与用餐计划，显示今晚菜单。',needs:'自托管 Mealie、API Token、自己的菜谱。',region:'本地菜谱可自托管；外部食谱抓取依赖来源网站。',status:'可配置',caution:'推荐来自已有菜谱；过敏信息须由家庭成员确认。'},
  {id:'grocy',name:'Grocy · 冰箱与家务',group:'food',kind:'社区 MCP',license:'MIT（桥接服务）',url:'https://github.com/attmous/grocy-mcp',summary:'读取库存、临期食材和待办家务，减少重复采购。',needs:'自托管 Grocy、API Key；先维护库存记录。',region:'可在家庭服务器运行；不是自动识别冰箱内容。',status:'可配置',caution:'展示记录日期；库存取决于家庭录入的完整程度。'}
];
export const groups=[['all','全部'],['home','智能家居'],['today','当天安排'],['outside','出门与旅行'],['food','饮食与家务'],['memory','记录与回忆']];
export const recipes=[
 {id:'morning',time:'07:30',title:'出门前的三十秒',text:'今天带不带伞、第一件事是什么、去哪里。把准备工作收在一处。',sources:['qweather','dida','amap'],fields:['天气与降雨','首个家庭待办','粗略路线时长'],privacy:'只显示城市与目的地简称，隐藏住址和私人任务。'},
 {id:'home',time:'18:30',title:'家里，刚刚好',text:'客厅冷不冷、空气怎么样、今晚吃什么。回家先看一眼自己的家。',sources:['ha-community','mealie'],fields:['客厅温度','空气质量','今晚菜单'],privacy:'读取已选传感器，不根据读数自动操作门锁、电器。'},
 {id:'evening',time:'21:00',title:'把今天留在窗边',text:'一件完成的小事、一句想记住的话，让电视也参与家庭的晚安仪式。',sources:['dida','memos','siyuan'],fields:['家庭任务摘要','今日一句','阅读记录'],privacy:'个人记录先预览，勾选后才对家庭屏公开。'},
 {id:'weekend',time:'周末',title:'下一站，一起去',text:'看看合适的车次、目的地天气，再翻翻上次出游的相册。',sources:['rail','qweather','immich'],fields:['候选车次','目的地天气','往年回忆标题'],privacy:'不上屏身份证、订单号或精确行程轨迹。'},
 {id:'kitchen',time:'晚饭前',title:'先吃冰箱里的',text:'查看库存里快到期的食材，结合已有菜谱，少浪费一份食物。',sources:['grocy','mealie'],fields:['临期食材','库存记录时间','已选菜谱'],privacy:'数据来自人工库存记录，不代表冰箱自动识别结果。'},
 {id:'balance',time:'今天',title:'自己的节奏',text:'散步、阅读、饮水，记录你愿意分享的小目标，不把生活变成排行榜。',sources:[],fields:['手动步数/散步记录','阅读进度','个人选择的目标'],privacy:'健康数据默认不公开。本版没有已验证的微信运动/小米运动官方 MCP，先用手动或 JSON 导入。'}
];
export function sampleSignals(now=new Date()) {const fetchedAt=now.toISOString(),expiresAt=new Date(+now+900000).toISOString();return [
 {id:'demo-life:temperature',sourceId:'demo-life',title:'客厅温度',value:'24.6',unit:'°C',source:'示例传感器',category:'home',fetchedAt,observedAt:null,expiresAt},
 {id:'demo-life:weather',sourceId:'demo-life',title:'出门提醒',value:'傍晚有雨',unit:'',source:'示例天气',category:'outside',fetchedAt,observedAt:null,expiresAt},
 {id:'demo-life:dinner',sourceId:'demo-life',title:'今晚菜单',value:'番茄牛腩',unit:'',source:'示例菜谱',category:'food',fetchedAt,observedAt:null,expiresAt}
];}
