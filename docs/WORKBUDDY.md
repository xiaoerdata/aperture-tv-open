# WorkBuddy 官方使用指引

本项目仅适配小米电视。官方安装与Agent示例统一以WorkBuddy为例，通用MCP协议仍保留扩展性。

日常内容由自己的助手通过受限 MCP 调整，手机负责初次授权与撤销，也保留手工编辑，双方共用同一家庭领域服务。用户口述的 WorkBody 按支持 MCP 的个人助手实现；已核查 [腾讯 WorkBuddy 官方 MCP 指引](https://www.workbuddy.ai/docs/zh/workbuddy/From-Beginner-to-Expert-Guide/Function-Description/MCP-Guide)，采用通用 stdio 桥接。尚未在 WorkBuddy 应用内现场验收，不假装已安装或自动修改用户配置。

## 当前能完成的闭环

“我到家了，把屏幕换成安静的窗景，写上‘欢迎回家’，提醒我晚饭后散步。”

管理员先批准授权。助手读取当前 revision → 设置已安装的窗景/动效/40字内标题 → 添加家庭留言、日程或指标 → 查询原幂等键回执。电视通过认证 SSE 接收版本通知，拉取最新 board；5秒轮询作补偿。电视页面渲染后提交带 revision 的回执。

applied = 服务端已提交；displayAcknowledgements = 过去60秒内已配对电视浏览器报告的渲染状态。两者都不证明电视物理屏幕、遥控硬件、音响或真人已经看到。没有在线电视回执时，助手必须明确“已保存，等待电视连接”，不能声称已显示。现场局域网延迟没有测量保证。

## 配对步骤

1. 家庭服务部署者显式设置 APERTURE_LOCAL_AUTOMATION=1。默认关闭，不开公网匿名 MCP。
2. 在运行 WorkBuddy 的电脑安装项目 npm 依赖，然后运行：
```bash
node scripts/workbuddy-pair.mjs --origin https://your-home.example --output /private/aperture-connection.json
```
本机演示可用 http://127.0.0.1:4318；局域网默认使用可信 HTTPS，HTTP内测使用文末的显式开发选项。不要关闭 TLS 验证。
3. 电脑显示终端二维码与完整授权链接；可追加 `--qr /private/pair.png` 保存二维码。手机扫码后自动带入请求，未登录时先验证家庭管理员，再继续原申请。选择授权能力与1小时/24小时/7天有效期，确认一次。默认只有读取；想让助手修改屏幕，勾选场景和内容权限。无需手工填写连接码，也不把管理员口令交给助手。
4. 电脑程序自动领取并保存600权限的本地凭证文件；已存在文件拒绝覆盖。不要发给模型、截图或上传仓库。将程序作为 stdio MCP 接入客户端：
```json
{
  "mcpServers": {
    "aperture-home": {
      "command": "/absolute/path/to/node",
      "args": ["/absolute/path/to/aperture-tv/scripts/workbuddy-mcp.mjs", "--config", "/private/aperture-connection.json"]
    }
  }
}
```
WorkBuddy 官方指引区分用户级 ~/.workbuddy/mcp.json 和项目级配置，按客户端实际版本操作。上面只有文件路径，不包含令牌。
如果WorkBuddy运行在Windows，请使用Windows本机的Node、项目和凭证路径，例如：

```json
{
  "mcpServers": {
    "aperture-home": {
      "command": "C:/Program Files/nodejs/node.exe",
      "args": ["C:/aperture-tv/scripts/workbuddy-mcp.mjs", "--config", "C:/Users/your-name/AppData/Local/Aperture/connection.json"]
    }
  }
}
```

以上是路径示例，需换成自己的实际位置；不要把WSL的`/home/...`路径直接交给Windows的Node。可以在Windows中安装项目Node依赖并运行配对/MCP脚本，家庭服务另在Linux或WSL运行；两者使用同一可达的可信HTTPS地址。配对文件放在仅自己可访问的目录，Windows权限由目录ACL决定，不能把POSIX的600模式当作Windows权限已核验。本示例尚未在Windows WorkBuddy客户端验收，由维护者按P34测试。

5. 让客户端重新发现工具。发现与每次调用都重新校验服务端会话、有效期及 scope。工具说明只在认证后返回；不公开 Hub 私有能力资料。
6. 结束授权时在该自托管家庭的设备列表移除“本机 WorkBuddy 助手”。随后调用立即失效。过期后重新配对，不静默续期。

## 编排契约与范围

home.read 可读取已共享家庭内容与自己的操作回执；scene.write 可切换已安装场景、动效与标题；content.write 可添加合法 note/event/metric。UI与机器共用 service.write、输入校验、120条配额、版本冲突、持久化幂等回执和有界审计。

源码v8增加可选nas.configure，须在新配对时明确勾选；既有令牌不自动扩大。aperture_nas_requests/send/status交接电视主动发起的加密NAS配置，复用原生NasDraft/NasReader/本机加密记录；不读取已存密码/文件，不把明文凭证交给MCP。新工具用电视请求revision、限时交接回执，先查aperture_nas_status，不混用board回执。见[手机与Agent接入NAS](NAS-SETUP.md)。

不允许执行任意 JS/shell、安装插件、改变目录、登录网盘、开启影院、读取未分享上游字段、控制门锁/设备、支付/外发或删除原视频。更复杂的可编程场景通过受信任源码扩展 sceneRegistry；本版运行时是结构化编排，不是远程代码执行平台。

遇到超时/断线：先查询原 execution.key 回执，再读取家庭内容确认。回执 unknown 只表示当前500条回执窗口内没有记录，不证明从未执行；结合内容确认后再决定。revision_conflict 需重新读取，不能盲目覆盖。轮询领取凭证若结果不明，需要管理员检查并撤销孤立授权后重新配对。

## 与智小策托管 Agent 的边界

这是用户明确要求的开源自托管、本机助手配对，不连接平台业务数据库、不代表付费权益，也不能换取 Hub token。官方托管版 /agent 和 /api/agent/* 继续503；平台个人授权仍统一由 Hub /dashboard#agent-access 管理。平台后续接入必须重新校验独立 Agent 权益、产品权益、scope、撤销/退款/到期，不得直接启用这个家庭会话作为平台后门。

自托管凭证逻辑、公开源代码说明与私有 Hub Skill/schema 分开。integrations/hub-scaffold 不进公开源码导出。当前交付的是兼容协议测试与独立浏览器端到端验证，WorkBuddy应用、真实局域网证书和小米电视仍待现场验收。


## 局域网来源验证与撤销

请求必须来自回环、RFC1918 IPv4 或 IPv6 ULA；IPv4-mapped 地址规范化。不接受公有来源、未经配置的 Forwarded / X-Forwarded-For。令牌同时绑定配对申请时的确切来源IP；领取和每次调用都校验。DHCP换地址、切网、代理出口变化后需重新配对；建议固定家庭设备租约。这是来源限制，不是硬件证明，也不证明同一 Wi-Fi：NAT或同机其他进程可以共享IP，仍必须持有独立令牌。

二维码只含随机挑战和请求编号，不含领取secret或长期token。二维码持有人无法仅靠扫描领取凭证；需已登录owner批准，且电脑持有独立secret并从同一来源领取。已批准请求不允许重新更改范围。

代理部署须设置 APERTURE_TRUSTED_PROXIES 为直连代理的确切IP，代理覆盖单一 X-Forwarded-For，丢弃 Forwarded，不信任客户端自带声明；只允许代理访问后端端口。见 DEPLOYMENT 的配置。公网代理不转发来源时，服务端无法区分真实客户端与代理本身，不能靠回环地址声称“验证了家庭网络”。本地机器入口应在边缘限制私网/VPN可达，不做公网匿名暴露。

有效期内日常场景、标题、留言、日程与指标更新无需再扫码。初次添加MCP客户端配置、到期续连、撤销与网络变化仍需要用户操作；没有静默长期续期。当前照片上传、上游生活快照授权与媒体库管理仍是人工/部署者流程，尚未全部开放给Agent。


## 安装、卸载电视应用（源码v13）

这是安装已授权应用的交接能力，不是远程静默安装。你在电视上开启一个10分钟、一次操作的窗口，WorkBuddy提交确定目标；电视再展示安装包信息或卸载包名，最后由小米原系统确认。日常家庭内容编排不需要开启这个窗口。

1. 按上面的配对流程，在手机家庭工作台分别勾选“让WorkBuddy发起电视应用安装”与“让WorkBuddy发起非系统应用卸载”。两项默认不勾选，已有令牌不会自动获得权限；需要时新建授权。对应`apps.install`、`apps.uninstall`，不会因为能改留言就自动允许卸载。
2. 安装用APK先放在电视已经连接的NAS共享目录中。让WorkBuddy在你指定的本地文件上计算SHA-256并读取包名；没有可信文件或包名时先准备资料，不猜校验值，也不把NAS账号密码发到聊天。支持独立`.apk`，不支持`.apks`/`.xapk`拆分安装包，单文件不超过200MB。
3. 电视设置 → 安装应用 → WorkBuddy安装应用，或WorkBuddy卸载应用。页面显示校验码及本次范围。安装只提供已保存NAS的连接ID和名称，不上传保存的密码；卸载只提供本次可启动的非系统应用，不含两版开视与原系统组件。离开页面、超时或服务重启后，不得盲目重建相同操作。
4. 让WorkBuddy发现并调用`aperture_install_requests`或`aperture_uninstall_requests`，核对电视校验码、版本和目标。`fingerprint`完整值的前12位应与电视显示一致。
5. 安装调用`aperture_install_send`，`command`包含电视提供的`connectionId`、相对`path`、确定的`sha256`与`packageName`。卸载调用`aperture_uninstall_send`，只提交本次允许列表中的`packageName`。都使用该窗口的`id`、当前`revision`及固定`execution.key`；重试相同意图使用同一个key，不能换键绕开冲突。
6. 电视读取APK到自己的缓存，核对包名与SHA-256；资料不一致就停止。首次安装需在原系统允许开视作为安装来源，然后返回复核。卸载可能清除该应用数据，原NAS里的APK文件不会被删除。不能因为安装失败就自动卸载旧应用来“修复”。
7. 用`aperture_install_status`或`aperture_uninstall_status`查询同一`id`和原`key`。等待系统确认不等于完成；只有`succeeded / reported_by_tv`表示原生应用已经得到系统成功回执并读取了相应安装版本或卸载结果。`unknown`、回执超时或过期都不代表“没执行”，需要在电视系统应用管理核对后再决定。

给WorkBuddy的示例：

> 我授权你在客厅小米电视安装我NAS里Apps/player.apk。先读取电视当前安装窗口，和我确认校验码；用我指定文件的包名和SHA-256提交。系统确认我来按，最后查询原请求回执，不能把发送成功说成安装完成。

> 我授权你卸载电视上我选定的播放器。只使用电视本次允许列表中的包名，提交一次；我在电视确认后，查询这次操作结果。不要删除NAS原文件，也不要处理小米系统应用。

服务端与原生代码已经提供这些能力；协议测试使用真实stdio MCP桥接和合成电视回执，不能当作WorkBuddy客户端、小米固件、真实NAS或原生安装器已验收。现场验收按[安装说明](../INSTALL.md)交接给项目维护者。系统安装器已打开后再撤销Agent授权，无法撤回原系统已经接受的操作；此时回执不明会标为unknown，不谎报撤销成功。


## 下一版：Agent制作与导入资源包

v18源码新增独立`resources.configure`授权，家庭工作台需明确勾选“让WorkBuddy导入开视资源包”。电视“来源 → WorkBuddy导入资源包”开启一次导入窗口，Agent提交HTTPS文件直链和原始SHA-256，保存前复核授权，最终通过原key查询包ID、修订号和摘要。文件制作、发布校验和工具参数见[资源包Agent指南](RESOURCE-PACK-AGENT.md)。v17安装包尚无这项入口，当前证据为本地源码、API编译和合成电视协议测试。

## S53 · 局域网 HTTP 内测配对

HTTPS仍是默认。内测使用私有IP时，电脑、电视、手机、配对命令全部使用同一个`PUBLIC_ORIGIN`，无需修改Host或自建桥接：

```bash
HOST=0.0.0.0 PUBLIC_ORIGIN=http://192.168.1.50:4318 APERTURE_LOCAL_AUTOMATION=1 npm start
node scripts/workbuddy-pair.mjs --origin http://192.168.1.50:4318 --allow-lan-http --output /private/aperture-connection.json
```

地址须换成电脑实际的私有IP。HTTP不加密，只在受信任家庭网络测试；`--allow-lan-http`仅允许私有IP字面量，不接受公网IP或HTTP域名。此选择随凭证文件保存，后续MCP桥接无需重复传选项。服务端仍精确校验Host/Origin、来源网络、令牌、有效期和每次调用的scope，不增加任意来源白名单，不关闭TLS验证。

`aperture_add`按type分别声明完整必填字段：note为title/body/author；event为title/date/time/person；metric为title/value/target/unit/source/date。工具schema与人类界面共用的领域要求一致，缺失时一次列出字段名。先读revision并保留固定幂等键的规则不变。
