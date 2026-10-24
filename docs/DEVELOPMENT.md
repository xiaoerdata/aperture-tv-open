# 二次开发指南

## 边界与依赖

入口 `server/main.mjs` 只读取配置/口令并启动，`server/app.mjs` 装配可替换依赖。领域状态在 `shared/model.mjs`，业务写入在 `server/service.mjs`；HTTP只负责协议。电视和手机使用同一真实家庭数据，不各自维护业务副本。测试创建独立临时目录，不连接生产Hub数据库。

Node22运行源文件，无前端构建。`server/image.py` 需要Python/Pillow。公开源码包含操作系统无关的业务代码；浏览器截图脚本可通过 `PLAYWRIGHT_MODULE` 和 `CHROMIUM_PATH` 指定本机工具，默认路径是当前开发环境的便利回退，不是运行产品所必需。

## 增加一个场景

1. 在 `public/scenes/` 新建模块，例如 `my-garden.js`，导出 `drawGarden(ctx,width,height,seconds,board)`。
2. 在 `public/scenes/engine.js` 的 `sceneRegistry` 注册中文名称、标题、说明、艺术来源标签和绘制函数。
3. 同时扩展 `shared/model.mjs` 的 `SCENES` 白名单及电视/手机选择入口；未知名字必须被服务器拒绝，避免只在前端校验。
4. `draw` 是纯绘制函数，不能读Cookie、发请求、读私有存储或改DOM；只使用传入的家庭只读快照。引擎负责尺寸、暂停、可见性与帧率。
5. 在480×600、1280×720、1920×1080下查看内容，验证方向键、减少动态、空数据和长文本。动画作品和真实观测要分别标注。
6. 上传的场景偏好包只选择已安装模块。用户JSON不得含JS、HTML、URL或可执行表达式。未来第三方插件须有独立沙盒、签名、权限和审核机制；当前不能从互联网热加载JS。

`examples/custom-scene.js` 是独立绘制示例，不默认安装。它不是网页或脚本上传入口。

## 扩展数据

新增内容类型先在 `normalizeEntry` 定义字段、长度、日期、取值、错误，再在共享服务注册操作，最后同步电视与手机。优先使用有清楚来源/日期的手工数据；接天气、日历或其他互联网服务时需要独立适配器，明确时区、更新频率、缓存和失败状态。

不要让用户提交任意URL由服务器直接获取。需要远程图片或外部数据时，先完成域名白名单、重定向校验、私网地址阻断、超时、文件限制和版权说明。首版没有任意URL抓取。

## 上传结构化内容文件

工作台「日程与数据」底部提供JSON内容包导入，文件最多64KB、1至20条，格式见 `examples/family-content.json`。先校验全部行并预览，再使用 `entries.import` 原子提交；任何一条无效都不会部分写入。只有日程、留言、数据可以导入，照片走独立解码入口。时间和来源按文件原值保存，导入前核对，不把示例日期改写成实时数据。

## 已实现HTTP接口

这些是自托管家庭界面的同源接口，不是官方托管版的付费Agent入口。

| 路径 | 用途 / 权限 |
|---|---|
| `GET /api/demo` | 公共合成示例，无私人内容 |
| `POST /api/login` | 本实例管理员口令换HttpOnly会话 |
| `POST /api/pair/start` | 申请viewer或editor，返回短码及申请端secret |
| `POST /api/pair/approve` | owner批准短码 |
| `POST /api/pair/poll` | 短码+secret领取一次性会话 |
| `GET /api/session` | 当前设备角色和到期时间 |
| `GET /api/board` | 所有有效家庭设备可读 |
| `POST /api/write` | owner/editor；operation/input/execution |
| `POST /api/photos` | owner/editor；图片base64、标题、说明、execution |
| `GET /media/<hash>.jpg` | 需有效会话且该图片仍被当前家庭引用 |
| `GET /api/devices` | owner查看活跃设备 |
| `POST /api/devices/revoke` | owner撤销设备；成员只能撤销自身 |
| `POST /api/logout` | 撤销当前会话并清除Cookie |

业务写入形状：

```json
{
  "operation": "entry.add",
  "input": {
    "type": "note", "title": "今晚一起吃饭", "body": "六点半见。", "author": "家人", "pinned": true
  },
  "execution": {"key": "a-new-unique-request-key", "revision": 0}
}
```

先读取当前revision，再发送请求；重试同一逻辑操作使用同一key、同一input和原revision。成功返回outcome与新revision；重复返回replayed，不增加记录。409时保留草稿，显式读取新版本后生成新的逻辑请求。只读电视不能调用写入，即使前端按钮被伪造。

HTTP适配器禁止直接 `photo.add`，只能经过图像处理入口。领域服务依赖的照片asset必须是可信处理器结果；未来增加其他协议不能绕过这个前置条件。

## 验证与素材再生成

```bash
npm test
# 安装本机开发工具后，指向实际Chromium路径
npm install --no-save playwright-core
CHROMIUM_PATH=/path/to/chrome npm run qa

# 先另开终端 npm start；需要本机 ffmpeg 支持 libx264
CHROMIUM_PATH=/path/to/chrome FFMPEG_PATH=/path/to/ffmpeg npm run media
CHROMIUM_PATH=/path/to/chrome FFMPEG_PATH=/path/to/ffmpeg node scripts/capture-interaction.mjs
```

QA用独立4329端口与临时数据，不读取 `.data/owner-key`，不写入真实家庭。正式迭代先检查端口是否空闲。素材脚本只访问明确的演示路由，使用原创程序插画和合成家庭数据。

## 后续Hub接入

本项目使用平台脚手架起步，生成物移入私有 `integrations/hub-scaffold/` 以免将生产依赖带入开源核心。该目录不是已集成模块，公开源包全部排除。未来用Hub统一账号/产品权限、capability、Agent运行时来装配同一业务服务；不能为MCP复制一套家庭数据。

实现之前补齐会话主体到Hub主体的适配、用户/家庭隔离、多租户存储、机器scope、实时撤销和付费权益验证。私有Skill和工具schema必须先登录并验证产品Agent权益，不能通过公开资源或仓库附件分发。当前禁用入口不会因为文件存在而放行调用。

## v0.2 模块与协议

- server/life：SDK stdio/Streamable HTTP、固定只读查询、受控字段映射与快照；public/life为选择共享和过期展示。
- server/cinema：默认关闭的媒体库、文件范围读取、Jellyfin、M3U与HLS代理；public/cinema为原生video/hls.js播放。
- server/local-agent：能力契约、scope、网络策略与客户端；scripts/workbuddy-pair及workbuddy-mcp提供扫码配对和stdio桥接。
- server/nas-setup：有界限时密文交接，复用原会话与独立nas.configure；HTTP与MCP调用同一服务。public/nas-setup负责手机输入/本地加密，原生NasSetupCrypto/Client/Import负责接收、校验与目录测试，NasVault一次本机保存。不是匿名电视服务或已存NAS浏览接口。
- public/ui/material：窗光材质令牌、减少透明与回退；docs/DESIGN-LANGUAGE记录设计取舍。
- public/guide：产品指引及可暂停的CSS生活场景。

新增接口：`/api/life/*`、`/api/cinema/*`、`/api/local-pair/*`、`/api/local-agent/*`、`/api/events`、`/api/display/ack`。具体权限与限制分别见 LIFE-CONNECTIONS、FAMILY-CINEMA、WORKBUDDY。原 `/api/agent/*` 继续关闭。HTTP和MCP写入复用领域服务，不将请求入口复制成新业务模型。

依赖由package-lock锁定：MCP SDK、hls.js、qrcode；运行npm ci，不执行前端构建。新增验证：npm run qa:life、qa:cinema、qa:assistant；影院测试另需ffmpeg支持VP9编码。浏览器工具不属于运行依赖。

`npm run media:hub` 在独立4334端口、临时目录和合成账号里生成真实浏览器/MCP演示；不读取实际 .data。截图按12fps编码为24秒，无音轨，不用于测量延迟。

每个阶段还需遵循项目AGENTS中的记录约定；docs/process为原始产品提示词与阶段日志，media为传播编辑稿。不要将事后摘要当成完整逐字历史。

电视只读配对组件见public/ui/tv-pairing.js，当前用于家庭影院的首次连接与撤销重连，复用既有pair接口。secret只在页面内存，关闭/离开取消请求，过期需显式重试。共享api()将调用方AbortSignal与内部12秒超时合并，不用调用方signal替换请求超时。复现node scripts/browser-cinema-pairing-qa.mjs；源码的版本号与私有下载manifest须一致。


## 从公开准备包验证首次启动

维护者可以从项目研发目录执行：

```bash
node scripts/verify-source-install.mjs artifacts/aperture-tv-public-v13.tar.gz --output artifacts/new-onboarding-report.json
```

工具在临时目录解压并逐项核对源码清单，重新npm ci、新建Python虚拟环境、按requirements安装Pillow，再运行doctor和start:checked。随机选择空闲回环端口，必须同时观察自己服务的启动标记及一致的版本后才开始浏览器检查，不能拿其他占用端口的服务当成功。默认清除继承的APERTURE配置，使用独立空家庭，检查默认鉴权和两版入口；结束后停止本次服务并移除临时目录。

需要本机已配置Playwright/Chromium，见上面的开发工具配置；它们是检查工具，不属于用户启动服务的依赖。报告记录复用浏览器工具，不冒充全新浏览器安装。每次选择新的JSON输出名，已有报告不会覆盖。当前v13公开归档的Linux检查见verification/v13-public-onboarding.json；不是WorkBuddy客户端或小米真机验收，也不重新构建APK。


## 公开源码的文档链接

公开准备会将缺失的私有下载链接改为`docs/DOWNLOADS.md`，保留历史正文；转换清单写入SOURCE-MANIFEST。公开README模板中的相对链接会按根目录重新计算，因此模板和生成的README均能打开本地文档。归档生成前检查所有Markdown的本地文件链接，缺少文件就停止，不产出新的公开目录。

可对解压目录单独只读核对：

```bash
python3 scripts/prepare-public-source.py --check-links artifacts/aperture-tv-public-v13-r3
```

检查覆盖本仓库使用的Markdown行内链接、图片和引用定义，跳过代码示例。它核对本地文件存在，不核对标题锚点、家庭服务路由或外部网址，也不是完整CommonMark校验。外部发行下载与真实设备仍需分别验收。
