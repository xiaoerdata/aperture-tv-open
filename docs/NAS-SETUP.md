# 帮电视连上 NAS：手机填写，电视验证

源码v8 / alpha.5新增首次配置交接，实际APK以[私有候选](DOWNLOADS.md)为准。原生SMB/WebDAV读取仍可独立使用；**本流程需要已配对的可信HTTPS家庭服务**，可以由家中电脑/NAS托管。局域网HTTP不用于交接，开发测试仅允许本机回环。尚未在Android/小米电视和品牌NAS验证。

## 家人怎么用

1. 家庭服务与电视先完成原有只读配对。进入「家里的NAS → 用手机 / 助手配置NAS」。两版设置独立。
2. 保持电视接收页打开。手机扫描本次二维码，验证家庭管理员身份，并核对两端显示的12位校验码。完整SHA256指纹也随扫码链接核对；二维码不包含领取secret、NAS密码或私钥。
3. 在手机填写品牌、目录和专用只读账号。首页用途默认为「只保存连接」；明确选择电影/剧集/相册时会替换对应的本机NAS来源，不移动文件。小米只面向最新独立Xiaomi智能存储，当前只接受SMB，不预设WebDAV。
4. 点击「加密交给电视，测试后保存」。手机将资料加密，电视原生解密、校验字段和目录，并重新检查交接授权，成功后将连接与用途一次保存到本机加密记录。
5. 手机先显示「等待电视」，只有电视报告保存才显示完成。返回开窗日常观看入口。目录可读不证明所有影片能解码；请继续按[NAS指引](NAS.md)测试照片、影片及休眠恢复。

密码会在手机表单和电视原生进程内短暂出现；代码不将其写入浏览器存储、电视WebView、Agent参数或家庭服务持久化文件。浏览器/操作系统密码管理器和终端内存不由本项目保证擦除。请只使用自己信任的家庭服务和设备。

离开/暂停电视接收Activity会立即禁止本次本机保存，并尽力发送取消；进行中的NAS只读操作可能等待超时后才结束。每次请求最多10分钟，家庭服务仅保留有界内存记录，重启/过期后需重新发起。交接不是持续远程配置通道，已保存的连接不会因为原交接者后来撤权而自动删除；请从电视「忘记本机连接」移除。

## 给用户自己的 Agent

沿用[WorkBuddy连接](WORKBUDDY.md)的本机网络、独立令牌与每次权限验证；新增`nas.configure`须在本次配对时明确勾选，旧令牌不扩大权限。既有home.read/scene.write/content.write不授予NAS配置。

| 能力 | 输入/结果 | 范围 |
| --- | --- | --- |
| `aperture_nas_requests` | `{requests:[…]}`，正在等待的电视请求、公钥、指纹、请求revision | 不列出已保存NAS或密码；先核对用户指定电视和校验码 |
| `aperture_nas_send` | 请求id、预先加密的envelope、execution.key/revision | 只交付密文；`awaiting_tv`不是保存成功 |
| `aperture_nas_status` | 请求id，可带原execution.key | 同一调用者的交接回执；`saved_on_tv`仅表示电视报告已测试并保存 |

不要把明文NAS密码粘贴到聊天或MCP参数。用户在本机准备权限为600的私有JSON文件，通过本地助手读取并加密；Agent只需要公开请求和加密文件。模板见`examples/nas-setup.synthetic.json`，不能把模板占位值当真实账号。

```sh
node scripts/seal-nas-config.mjs --request /private/tv-request.json --input /private/nas.json --output /private/nas-sealed.json
```

参数只有路径。`tv-request.json`来自当前已授权请求；`nas.json`由用户在本机填写并设为600。输出用600权限新建、拒绝覆盖，保留同一份密文和execution.key，再交给`aperture_nas_send`。脚本不发网络请求、不打印明文、不替用户删除输入文件。不需要也不应让Agent先读取密码到模型上下文；用户/本地凭证工具负责准备私有输入。

结果不明先查原id/key的`aperture_nas_status`，不重新生成密文换键重发。过期/服务重启返回setup_expired，旧回执不存在，不能由此断言没有本机保存；先在电视检查，避免重复添加。回执与本地保存不是分布式事务：可能已经保存而回执丢失，电视会显示「已保存；回执未送达」。程序在目录测试后再次请求授权，但不能承诺授权响应与本机提交之间不存在竞态，也不宣称能远程撤回已保存的NAS凭证。

这是开源自托管能力，不连接Hub生产数据库、不授予托管Agent付费权益。托管版`/agent`与`/api/agent/*`继续禁用；不公开Hub私有手册/工具契约。

## 实现和边界

接收端`NasSetupCrypto`为每次请求在原生内存生成RSA2048临时密钥，手机/本地加密器用RSA-OAEP SHA256/MGF1-SHA256封装AES256密钥，AES-GCM以请求id作为附加认证数据。私钥不上传或持久化；释放引用不等于擦除所有JVM/系统内存。显式选择两个OAEP摘要，避免Android默认值差异。[Android加密说明](https://developer.android.com/privacy-and-security/cryptography#oaep-mgf1-message-digest)、[WebCrypto加密与安全上下文](https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto/encrypt)。

家庭服务验证电视会话、领取secret摘要与设备归属；发送端必须是管理员或当前拥有nas.configure的本机Agent。每次读取/提交/领取重新检查会话与权限，目录测试后再检查一次配置者授权。64个请求上限、10分钟TTL、单次8KB加密内容；接收器限制固定接口/JSON大小/二维码尺寸、不跟随跳转、不绕过TLS证书。读取有超时预算，DNS/系统行为不是硬总期限。

加密保护中转密文和落盘边界，**不防恶意家庭服务向手机下发窃取输入的JavaScript**。手机代码来源、TLS、家中设备本身和管理员身份仍须可信。家庭服务/Agent没有取得已存NAS读取、浏览、删除、遥控播放或重置密钥能力；没有匿名电视HTTP端口，也没有从网页启动任意Intent。

`NasSetupImport`复用NasReader和NasDraft；本地手输同样走NasDraft。未知字段、导入ID/home、非固定用途、URL内凭证和小米WebDAV均拒绝。通过目录测试后，NasLibrary与NasVault一次写入连接/用途；不分两次写造成半完成。

## 证据与复现

- `node --test tests/nas-setup.test.mjs tests/nas-setup-http.test.mjs`：生产Java/WebCrypto互通、篡改/错电视/释放密钥、权限/撤销/配额、真实Java HTTP客户端、本地加密CLI、真实stdio MCP交接和回执、真实SMB3.1.1目录→模型/GCM保存。NAS连接失败、取消、目录测试后撤权均拒绝保存。JVM保存使用测试密钥，不是Android Keystore。
- `node scripts/browser-nas-setup-qa.mjs`：真实手机页/服务/加密/JVM解密，320/390/430/760/761/1280px、输入保持、域展开、校验码不匹配、提交响应丢失后查询且不重发。浏览器用合成saved回执检查展示，不是Android保存证据。
- `npm run check:native`：双版API编译。Android Activity、CookieManager、Keystore、实际扫码、老电视内存/焦点、HTTPS部署与五类NAS硬件仍需实体设备验证。
- `node scripts/browser-nas-grant-qa.mjs`：实际390px手机授权页验证NAS默认不勾选；明确选择后只发现读取+NAS工具，未附带场景/内容写权限。此脚本是S29打包后的复现补充，不在5f5891c冻结源码包内；该源码包仍包含S28原授权实现。

本轮记录和传播素材见[JOURNAL S28](process/JOURNAL.md)、[第十六期](../media/DEVLOG-016.md)。截图和动画均标明合成/浏览器/JVM，不混作实机。
