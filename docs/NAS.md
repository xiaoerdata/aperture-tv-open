# 家里的 NAS，电视直接读

P24–P26 将 NAS 原生协议接入列为核心能力。Android 原生模块已实现 SMB 2/3 与 HTTPS WebDAV；当前源码版本 `0.2.0-alpha.7 / versionCode 10`，实际安装包以 [私有下载索引](DOWNLOADS.md) 为准。v4 不含本模块。

## 适配范围与证据

| 目标 | 当前接入路线 | 一手材料与边界 | 实机状态 |
| --- | --- | --- | --- |
| 最新 Xiaomi 智能存储 | 优先核验 SMB 2/3 | [官方客户端](https://app.mi.com/details?id=com.xiaomi.station)确认产品名称及配套关系，但公开介绍没有列清 SMB 版本、签名及 WebDAV 能力。暂不预设 WebDAV 支持。P26明确不包含小米路由器硬盘/USB共享 | 待测试 |
| 海康存储 | 优先 SMB 2/3 | [R1 产品页](https://www.hiksemitech.com/cn/homepage/products/nas/r1.html)列出 SMB / AFP / DLNA；不由此推断所有型号支持 WebDAV 或相同 SMB 配置 | 待测试 |
| 绿联 UGOS | SMB 2/3；HTTPS WebDAV | [SMB 官方指引](https://support.ugnas.com/detail/article/zh-CN/78)、[WebDAV 官方指引](https://support.ugnas.com/detail/article/en-US/82)；控制面板→文件服务，版本/端口以自家设备为准 | 待测试 |
| 群晖 DSM | SMB 2/3；WebDAV Server 的 HTTPS 端点 | [DSM 规格](https://www.synology.com/en-af/dsm/7.2/software_spec/dsm)；不能把 DSM Web 登录页当作 WebDAV 地址 | 待测试 |
| TrueNAS Community Edition | SMB 2/3 | [SMB 文档](https://www.truenas.com/docs/scale/scaletutorials/shares/smb/)、[上游 Samba 代码](https://github.com/truenas/samba)。用于开放实现互通验证 | 待测试 |

资料核查：2026-10-24。群晖作为常见商业 NAS 补充，TrueNAS 作为开放实现补充；没有可信的统一用户数量统计，**不宣称其中一个同时是用户最多、最开放的品牌**。小米商城部分检索结果打开后跳到显示器商品，未当作协议规格证据；此前旧路由器文档不再用于小米新品适配。社区评测不能替代新品官方协议说明和实测。

## 在电视上使用

1. 标准版进入「影视仓→家里的 NAS · 直连」或「家庭工具→家里的 NAS」；相册区有「浏览 NAS 照片」。爸妈版在家人设置添加和绑定NAS目录，日常从「看电影」「看电视剧」直达，保留三个大入口和四个小快捷。旧 WebView 的原生回退菜单也有 NAS 入口。
2. 在 NAS 上准备一个家庭共享文件夹和专用只读账号，让电视与 NAS 网络互通。选择品牌，填写连接名称、共享地址、账号、密码；SMB 的域通常留空。
3. SMB 地址形如 `smb://192.168.1.20/Family/相册`；必须包含共享名。WebDAV 使用服务实际提供的 HTTPS 目录地址；不接受账号密码内嵌 URL、不跟随跳转、不关闭证书校验。小米品牌目前只接受 SMB 路线。
4. 「测试目录并保存」成功后进入目录。可按全部/相册/视频筛选，方向键、确认键、返回键操作。最多保存8个连接，可分别保存照片、电影、电视剧文件夹。
5. 照片在本机读取/缩放，支持 JPEG/PNG/WebP，源文件不超过12MB、解码尺寸不超过5000万像素。视频通过 Android `MediaDataSource` 随位置读取，支持播放/暂停、前后30秒；具体容器、音轨、字幕、4K/HDR等仍由电视解码能力决定。本轮没有转码或字幕库。
6. 在已打开的目录选择「家人设置 · 将当前目录用于首页入口」，确认用于看电影、看电视剧或NAS相册。爸妈版从对应大入口直接打开，标准版在「影视仓→我的家庭片库」选择NAS电影/剧集或家庭服务完整片库，相册分区直达已选NAS相册。观看模式只在选定目录内浏览，不向上跨到其他目录。
7. 「重新连接/检测」重新认证并读取当前目录；这只能证明目录可读，不能代表整库影片都能播放。NAS 休眠、网络变化、改密码后，可重新连接。忘记连接移除它的观看绑定，不改NAS文件，也不会将绑定转给列表中另一台NAS。可在「首页观看入口 · 查看/恢复家庭服务」逐项恢复默认；未绑定的电影/剧集继续使用家庭服务。

v8也可在连接列表选择「用手机 / 助手配置NAS」，无需在电视输入账号密码。此交接需要已配对的可信HTTPS家庭服务：手机管理员或明确授权nas.configure的本机助手交付密文，电视测试目录后在本机保存。原生直接浏览仍不需要电脑服务。[完整步骤与边界](NAS-SETUP.md)。

返回影片/照片或上级目录时保留上次页码和焦点；目录历史最多64项。每个目录最多200项，界面每页20项，不递归扫描整台 NAS。超出时明确提示拆成较小片库，而不是静默漏掉后续文件。路径禁止穿越、备用数据流和不明确的编码；SMB 跳过可识别的 reparse 项，不启用 DFS。最终的目录/链接/用户权限仍需 NAS ACL 约束，不能把客户端路径检查当服务器权限沙箱。

## 凭证与原系统

- 地址、账号、密码和域在电视本机 Android Keystore 的 AES-GCM 加密记录中保存；`allowBackup=false`，NAS Activity 不对外导出，并禁用系统截图/最近任务预览以保护真实内容。硬件密钥保护能力随电视而异。
- NAS明文不进入电视WebView、家庭服务持久化数据、Agent参数、URL或日志。本机手输和v8加密交接共用原生字段校验；手机页填写时明文短暂存在该手机内存，发送前加密、不写浏览器存储。Agent使用本地私有文件加密助手，只交付密文；本轮仅首次接入，不是持续远程改配置通道。
- SMB 强制签名、认证账号、SMB 2.0.2/2.1/3.0/3.0.2/3.1.1，不降级到 SMB1/来宾。签名不等于链路加密。WebDAV 仅 HTTPS；自签证书需正规信任链，不提供忽略证书开关。
- 小米原生账号与小爱继续由原系统管理；NAS 账号不等于小米电视账号，不尝试复用系统账号令牌。
- 断开/失焦会暂停影片，不自动恢复；停止Activity会释放连接，再回来重新核验同一连接标识，恢复目录而不自动恢复播放。准备影片过程中发生失焦也会取消初次自动播放；Surface重建复用已有播放器，不另建重复实例。旧异步结果不会写回新界面。网络关闭可能等待正在执行的读取退出；TCP连接5秒、SMB单项8秒、WebDAV单次15秒；DNS及Android媒体释放行为受系统影响，不宣称严格总时限。
- v7原生NAS播放器另处理独立音频焦点：小爱等只占用音频时也暂停，准备中断取消自动开始，焦点拒绝保持安静；焦点归还不续播，需主动按播放。暂停/播完/错误/退出释放焦点，旧回调不控制新影片。[实现与真机边界](XIAOMI-VOICE.md#nas影片与音频焦点源码v7)。这不控制外部影视应用或网页播放器。

## 与家庭服务、爸妈版、Agent 的关系

本轮直连模块完全在 Android 内运行，不要求电脑挂载或额外网关。已有网页影院仍可使用服务器共享目录/Jellyfin/M3U和后台自动源检测，两条路线标记清楚。

**已补齐（源码v6）：**用固定连接ID与相对目录绑定电影、剧集、相册。绑定保存在原有加密记录中，不靠列表索引，不合并凭证；爸妈版日常直达，标准版明确选择NAS或家庭服务。已有v5记录增加ID，原加密格式和JSON数组保持兼容，迁移/改绑/忘记均只改本机记录。

**尚未完成：**自动合并NAS与服务器的海报/搜索/播放进度、自动海报刮削、首页NAS照片轮播、局域网自动发现、NAS健康周期巡检、上传/备份、原厂相册/影视数据库私有API及跨设备同步。本轮实现的是明确选择来源和目录，不宣称已有整合数据库。

`NasReader.list/open/readAt` 是人机共用能力的原生边界，UI与传输分层。v8新增受nas.configure保护的aperture_nas_requests/send/status，只交接当前电视请求的密文，不读取已存NAS。未来设备能力`nas.connections`（脱敏已存连接）、`nas.list`（连接ID与相对目录）、`nas.open`（经授权只读资源）仍**未开放**。本轮复用原配对/身份/scope/撤销，使用独立请求版本和限时幂等回执；不增设匿名本地HTTP端口、不把NAS密码发给WorkBuddy，Hub商业Agent权益也不由此获得。

## 验证与复现

`npm run check:native` 只解析依赖并运行两版 javac/API检查，不产APK。`npm run test:nas` 同时验证生产NasLibrary/NasCipher/NasNavigation：稳定ID迁移、绑定替换/删除/恢复、越界/重复记录、GCM篡改/错误密钥、目录历史和准备播放的失焦代次。额外运行协议测试使用生产Java类：路径/目录边界、证书固定的本地HTTPS WebDAV、隔离Samba的SMB2.1与SMB3.1.1签名连接。WebDAV检查Depth1、Range/ETag、拒绝跳转/错误MIME/超大响应/关闭后读取；SMB检查中文目录、随机偏移、错误密码及关闭。

JDK17/SDK35/Gradle8.9与依赖通过既有隔离运行时使用；测试额外需 Python 环境 `impacket==0.12.0`（只生成合成密码和旧模拟服务）以及解包的 Samba。设置 `APERTURE_NAS_TEST_PYTHON`、`APERTURE_NAS_SAMBA_ROOT` 可覆盖本机 `/tmp/aperture-nas-runtime` 默认位置。Samba只绑定回环高端口，所有配置/合成账号/文件位于临时目录，独立进程组，结束删除；没有安装系统服务、没有修改 `/etc/samba`。缺少Samba时对应两项明确skip，不计为通过。JVM模型测试另用SHA256固定的org.json 20240303测试包代替Android框架API，不加入APK依赖；Android Keystore和Activity本身仍未在设备中执行。

初始Impacket服务的签名响应被客户端拒绝，保留为失败；随后换用Samba签名正向测试。另发现并修复SMBJ不能复制普通空集合的实际API问题；Samba测试账号初始LCT=0导致密码过期，改成合成测试时钟。测试服务的进程组清理亦已隔离，避免停止服务影响测试进程。没有因此放宽生产签名策略。

结果见[协议验收记录](verification/nas-protocol.json)。**JVM协议通过不等于Android Keystore、MediaPlayer、SMBJ在小米电视运行或上述五类NAS实机通过。** 后续每款记录型号/固件、协议、中文路径、照片、影片拖动、休眠重连、账号撤销/改密和小爱浮层，不上传真实凭证或家庭内容。


v6入口绑定证据：[模型与生命周期](verification/nas-library.json)、[来源标签与遥控浏览器](verification/nas-routing-browser.json)。SMB2.1/3.1.1真实隔离服务另覆盖了“绑定相册子目录→解析来源→只读该子目录”，仍不是Android Intent派发或真机验证。

v7播放控制证据：[音频焦点](verification/playback-focus.json)。`node --test tests/playback-session.test.mjs`实际运行生产意图控制器的10组JVM场景；没有运行Android音频服务/解码器，没有品牌实机结论。
