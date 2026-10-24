# 开窗：从一句想法到家庭屏

用户要求开发与记录同步进行。本目录是过程底稿，media 是可编辑传播稿，两者互相引用。

- [阶段提示词原文](PROMPTS-001.md)：本次上下文可见的产品需求，按顺序编号。
- [开发日志与证据](JOURNAL.md)：每个阶段为什么这样做、做到哪里、怎么验证。
- [窗光设计语言](../DESIGN-LANGUAGE.md)：参考、取舍和实现位置。
- [深色玻璃与大时钟](../DARK-GLASS-DESIGN.md)：S15/S16当前电视首页设计、文案、字体与截图复现。
- [当前验收边界](../ACCEPTANCE.md)：开发测试、真实客户端、真机分开。
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
