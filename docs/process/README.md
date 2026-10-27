# 开视：从一句想法到家庭屏

用户要求开发与记录同步进行。本目录是过程底稿，media 是可编辑传播稿，两者互相引用。

- [阶段提示词原文](PROMPTS-001.md)：本次上下文可见的产品需求，按顺序编号。
- [开发日志与证据](JOURNAL.md)：每个阶段为什么这样做、做到哪里、怎么验证。
- [窗光设计语言](../DESIGN-LANGUAGE.md)：参考、取舍和实现位置。
- [深色玻璃与大时钟](../DARK-GLASS-DESIGN.md)：S15/S16当前电视首页设计、文案、字体与截图复现。
- [当前验收边界](../ACCEPTANCE.md)：开发测试、真实客户端、真机分开。
- [下一阶段系统框架](../SYSTEM-FRAMEWORK-ROADMAP.md)：P36–P38、架构参考、权限分层与 Open-MiTV 候选命名。
- [开视 v14 电视画面适配](../TV-VIEWPORT.md)：P39正式中文名、安装布局反馈、低逻辑分辨率修正与两版预览。
- [v15内嵌直播与影视](../EMBEDDED-CINEMA.md)：P40、TVBox兼容子集、原生目录/播放器、来源配置、协议与APK证据。
- [S48留白与遥控翻屏](../HOME-PAGES.md)：P42、三屏结构、世界风景、两版实际浏览器截图与复现。
- [家庭中枢篇分镜](../../media/HOME-HUB-STORY.md)：可用于小红书剪辑。

本次是补录与新增记录并行。更早轮次的逐字提示、原始工具时间和未保留的探索画面不可从摘要还原；没有把事后补录伪装成完整实时记录。自本规范起，新阶段持续追加原文、过程和证据。

对外发布前使用源码导出清单；家庭数据、密码、连接令牌、配对 QR 和私有 Hub 手册不进入公开素材。这里的产品提示词不包含开发环境系统提示词。

账户运行证据P12的完整原文保存在私有仓库private-preview/process，公开过程卷只保留索引。源包导出明确排除整个private-preview；历史提交不会因此自动脱敏，仓库转公开前仍需单独审阅。

S18补充：[爸妈版统一片库与检测](../FAMILY-CINEMA.md#爸妈版与自动检测)、[米家授权与快照](../XIAOMI-HOME.md)、[本期分镜](../../media/DEVLOG-010.md)。新截图与报告含明确合成数据/浏览器边界；已有v3安装包不含这轮变化。

S19补充：[常用应用小快捷分镜](../../media/DEVLOG-011.md)，当前爸妈版图见docs/images/parents-shortcuts-1920.png；三大观看入口和较小的常用app一级快捷同时保留。

S20补充：片库内直接配对的真实浏览器流程见verification/cinema-pairing.json（相对docs目录），分镜见media/DEVLOG-012.md；新版候选是否已生成请以private-preview索引和manifest核对，不能从网页画面推断。


S21/S22补充：[原生首页数据](../NATIVE-HOUSEHOLD.md)、[NAS协议与品牌范围](../NAS.md)、[第十三期分镜](../../media/DEVLOG-013.md)；P24/P25/P26保留原文，小米范围按P26收窄至最新Xiaomi智能存储。


S24/S25补充：NAS电影/剧集入口绑定、加密模型和焦点中断修正见NAS.md、verification/nas-library.json与nas-routing-browser.json；第十四期分镜见media/DEVLOG-014.md。新增提示词仅在收到用户原文时追加，本次为既有目标续作，没有补造新提示。


S26补充：[NAS播放与小爱音频焦点](../XIAOMI-VOICE.md#nas影片与音频焦点源码v7)、[播放验证](../verification/playback-focus.json)、[讲解动画记录](../verification/playback-focus-media.json)、[第十五期分镜](../../media/DEVLOG-015.md)。这是生产控制器JVM与合成事件讲解，未作实机结论。

S27补充：[v7两版APK证据](../verification/v7-candidates.json)、[源码包证据](../verification/v7-source.json)、[私有下载](../DOWNLOADS.md)。免费本地一次构建；目录/签名/来源验证通过，Android与小米硬件仍待验收。

S28补充：[手机/Agent交接NAS](../NAS-SETUP.md)、[专项报告](../verification/nas-setup.json)、[第十六期](../../media/DEVLOG-016.md)。原生读取仍可独立使用；交接需要可信HTTPS家庭服务。手机画面为浏览器/合成/JVM，不是电视验收。

S29补充：[v8候选与打包清单](../verification/v8-candidates.json)、[冻结源码包](../verification/v8-source.json)、[实际手机授权检查](../verification/nas-setup-grant.json)。本机一次免费构建，包名/签名与v7一致；仍待设备验收。

S30补充：[本机首页路由与恢复](../HOME-MODES.md)、[Java策略](../verification/lobby-mode.json)、[设置浏览器检查](../verification/home-mode-browser.json)、[两版界面素材](../../media/DEVLOG-017.md)。源码alpha.6先于APK；P27要求的图片为当前浏览器页面，不是电视/NAS实拍。

S31补充：[电视遥控布局](../TV-REMOTE.md)、[尺寸与遥控检查](../verification/tv-controls.json)、[真实4K背景来源](../verification/background-4k.json)、[第十八期素材](../../media/DEVLOG-018.md)。P28/P29原文已保留；v9为调整前本地候选，不作为当前新界面下载。

S32/S33补充：[v10候选](../verification/v10-candidates.json)、[私有远程回读](../verification/v10-remote.json)、[冻结源码干净启动](../verification/v10-clean-install.json)、[使用指引浏览器检查](../verification/s33-install-guide.json)、[第十九期](../../media/DEVLOG-019.md)。当前指南文案晚于v10冻结源码包，安装包来源不变；设备验收仍待完成。

S34补充：[公开前审阅](../OPEN-SOURCE-REVIEW.md)、[字体来源](../verification/s34-fonts.json)、[依赖声明清单](../verification/s34-license-inventory.json)、[第二十期分镜](../../media/DEVLOG-020.md)。工具规则检查不等于公开许可或全面安全验收；详细历史映射留在不导出的私有目录。

S35/S36补充：[Android TV模拟器](../ANDROID-EMULATOR.md)、[小米共存检查](../XIAOMI-COMPATIBILITY.md)、[第二十一期](../../media/DEVLOG-021.md)、[第二十二期](../../media/DEVLOG-022.md)。模拟器运行与小米固件、语音、米家硬件验收分开记录。

S37补充：[安装与升级](../APP-INSTALLATION.md)、[安装入口浏览器检查](../verification/installation-browser.json)、[第二十三期](../../media/DEVLOG-023.md)。系统确认由原安装器处理，爸妈版安装管理留给家人设置。

S38/S39补充：[小米电视安装说明](../../INSTALL.md)、[WorkBuddy应用管理](../WORKBUDDY.md)、[仅小米电视的范围](../SUPPORT.md)、[开源准备](../OPEN-SOURCE-PREPARATION.md)、[第二十四期](../../media/DEVLOG-024.md)与[第二十五期](../../media/DEVLOG-025.md)。WorkBuddy客户端与真机测试由维护者完成；开发证据不冒称现场验收。

S40补充：[v13公开包新目录启动](../verification/v13-public-onboarding.json)、[一致的公开发布步骤](../PRIVATE-RELEASE.md)、[第二十六期](../../media/DEVLOG-026.md)。Linux依赖安装与浏览器检查通过；没有替代维护者的WorkBuddy/小米真机验收，已有冻结包不覆盖。

S40冻结补充：[r2公开源码交接收据](../verification/public-source-v13-r2.json)。运行字节与已直接测试的r1相同；r2未重复安装，APK仍v13。

S41：[下载与版本](../DOWNLOADS.md)、[旧公开包的链接缺失清单](../verification/s41-link-baseline.json)、[第二十七期底稿](../../media/DEVLOG-027.md)。开发包、源码和正式发行包分别说明；不把历史内测链接当公共下载。

S41结果：[r3源码交接与链接检查](../verification/public-source-v13-r3.json)。73份Markdown/167个本地文件链接，无缺失；外部网址和标题锚点不在本次检查范围。

S42：[release交接说明](../ANDROID-RELEASE.md)、[现有双版APK与工具参数检查](../verification/s42-release-preparation.json)、[第二十八期底稿](../../media/DEVLOG-028.md)。命令帮助核对不等于完成正式构建或签名。

S43：[P35开源测试原文](PROMPTS-001.md#p35--通过开源方式测试)、[Alpha下载说明](../DOWNLOADS.md)、[第二十九期底稿](../../media/DEVLOG-029.md)。按用户最新顺序，先公开测试，真机结果后补。

S43公开结果：[公开仓库](https://github.com/xiaoerdata/aperture-tv-open)、[Alpha测试发布](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.10)、[匿名下载核对](../verification/public-alpha-v13.json)。APK/清单下载已验证，真机与WorkBuddy结果待补。

S44：[系统框架研究与路线](../SYSTEM-FRAMEWORK-ROADMAP.md)、[定向文档检查](../verification/s44-framework-docs.json)、[第三十期底稿](../../media/DEVLOG-030.md)。Open-MiTV仍为候选；本轮没有改名、构建或发布。

S46：[内嵌影院](../EMBEDDED-CINEMA.md)、[生产解析/HTTP检查](../verification/s46-embedded-cinema.json)、[v15包核对](../verification/s46-v15-candidates.json)、[第三十二期底稿](../../media/DEVLOG-032.md)。新原生UI和解码尚未设备运行，TVBox仅兼容所列子集，公开版本仍为v13。

S47：[发布前验证矩阵](../verification/s47-acceptance-plan.json)、[浏览器汇总](../verification/s47-browser-summary.json)、[模拟器H264诊断](../verification/s47-emulator-h264-diagnosis.json)、[第三十三期底稿](../../media/DEVLOG-033.md)。保留失败原因与验证边界；发布结果见[匿名下载与源码回读](../verification/public-alpha-v16.json)。

S48：[三屏与风景](../HOME-PAGES.md)、[第三十四期](../../media/DEVLOG-034.md)。

S49：[首页个性化](../HOME-PERSONALIZATION.md)、[Hub预留](../HUB-DISPLAY.md)、[素材与提示词](../../design-assets/s49/README.md)、[第三十五期](../../media/DEVLOG-035.md)。本地开发，未更新公开APK。

S50：首页紧凑观看入口、应用折叠、三种插件执行与NAS统一记录，见[阶段记录](JOURNAL.md#s50--首页观看入口插件执行与nas统一记录)和[第三十六期底稿](../../media/DEVLOG-036.md)。


S50验证收尾：[最终软件汇总](../verification/s50-final-software-acceptance.json)、[API28环境失败与恢复](../verification/s50-api28-development-issues.json)、[两版插件运行记录](../PLUGIN-ENGINE.md#r6-android-实际运行记录)。最终公开源码安装回执位于Release测试清单，历史r3安装记录不作为本次发行证据；发布回读在完成后另补。


S50发布结果：[v17 Alpha下载](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.14)、[匿名附件与完整源码归档回读](../verification/public-alpha-v17.json)。两版软件与最终源码安装通过，旧alpha.13和私有研发仓库保留；小米真机/WorkBuddy仍由维护者验收。此补记晚于发行标签，仅更新main文档。


S51：[综艺与资源包v1](../RESOURCE-PACK.md)、[Agent制作发布导入](../RESOURCE-PACK-AGENT.md)、[本地验证汇总](../verification/s51-summary.json)、[问题与修正](../verification/s51-development-issues.json)、[第三十七期](../../media/DEVLOG-037.md)。v18为本地源码，未打包或发布。

S52：P49原厂应用图标，[出处](../../design-assets/app-logos/README.md)、[验证](../verification/s52-app-logos.json)、[底稿](../../media/DEVLOG-038.md)。

S53：P50—P55，[真机问题处理](../V17-FEEDBACK.md)、[软件检查](../verification/s53-software-acceptance.json)、[模拟器失败记录](../verification/s53-native-environment.json)、[并存迁移](../MIGRATION-V18.md)、[新标识与原图](../../design-assets/s53/README.md)、[第三十九期](../../media/DEVLOG-039.md)。本版供小米真机复测，未把原生测试驱动构建称为UI验收通过。

S53发布结果：[v18 Alpha](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.15)、[匿名下载与逐文件回读](../verification/public-alpha-v18.json)。两版新包名并存；v17标签和旧附件均保持原样。此补记晚于发行标签。

S54：P56指定第二套Logo，[两版标识与提示词](../../design-assets/s54/README.md)、[本地浏览器检查](../verification/s54-brand.json)、[第四十期底稿](../../media/DEVLOG-040.md)。仅本地修改，没有更新公开APK。

S55：P57安装包瘦身，[方案与边界](../APK-SIZE.md)、[旧APK体积分解](../verification/s55-apk-size-baseline.json)、[配置与验包检查](../verification/s55-abi-policy.json)、[第四十一期底稿](../../media/DEVLOG-041.md)。约35%为旧包分析估算，没有生成或发布新的安装包。

S56：P58—P59，[影视内页与资料](../TV-INTERIORS.md)、[生图与提示词](../../design-assets/s56/README.md)、[实际浏览器检查](../verification/s56-cinema-browser.json)、[第四十二期](../../media/DEVLOG-042.md)。v19候选包含S54/S55，发布状态以后续回执为准。

S56发布结果：[v19 Alpha](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.16)、[匿名附件和完整源码回读](../verification/public-alpha-v19.json)。两版约52.6MiB，可覆盖更新v18；此回执后加在main，不改发行标签。


S57：P60清理重复测试产物，[清理清单与保留核对](../verification/s57-cleanup.json)、[第四十三期底稿](../../media/DEVLOG-043.md)。删除开发机临时产物，保留源码、关键截图、发行包和签名；原始小型回执集中在私有artifacts/s57-cleanup-evidence。仅本地清理，未发布。

S58（本地实现完成）：P61逐页生图与紧凑UI，[页面/尺寸/验证清单](../UI-COMPACT-S58.md)、[参考图及完整提示词](../../design-assets/s58/README.md)、[第44期编辑稿](../../media/DEVLOG-044.md)。家庭页减为相册、安排和留言，旧数据保留；本地源码迭代，未构建或发布。22张独立生图及提示词已保存；[完成核对](../verification/s58-completion.json)记录交付证据。原生新布局无本轮运行截图，真机验收按既定分工另行执行。

S59（已发布Alpha）：P62授权v20 / alpha.17发布，[发行软件验证](../verification/s59-software-acceptance.json)、[第45期编辑稿](../../media/DEVLOG-045.md)。小米真机与WorkBuddy仍待用户验收。

[v20公开发布与匿名回读](../verification/public-alpha-v20.json)：两版APK完整SHA-256、源码Git树及旧版本不可变性已核对。

S60：P63用户v20报告，[逐项修复与未完成项](../V20-FEEDBACK-S60.md)、[软件检查与源码摘要](../verification/s60-summary.json)、[第46期编辑稿](../../media/DEVLOG-046.md)、[生图参考与提示词](../../design-assets/s60/README.md)。浏览器、协议和API编译通过；原生运行、实际资源包复现与小米生态验收未完成。S61开始准备v21 / alpha.18测试发行，最终状态以公开回读为准。

S61（v21已发布Alpha）：[发行与匿名完整附件回读](../verification/public-alpha-v21.json)、[软件与签名检查](../verification/s61-software-acceptance.json)。公开冻结提交7654e917c6433ba9fb9f441aef36644a741ed822；原生新交互与实际报告资源包仍待复测。
