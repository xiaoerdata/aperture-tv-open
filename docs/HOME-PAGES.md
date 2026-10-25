# 首页与遥控翻屏 · S50

标准版首屏保留时间、日期和风景，同时提供三张直接观看的小卡片和折叠应用组。打开时焦点在“看电视”。无需先配置家庭服务，也能选择观看入口、安装应用或进入设置。来源未设置时，由对应播放器页面引导配置。

| 页面 | 内容 | 遥控操作 |
| --- | --- | --- |
| 标准首屏 | 看电视、看电视剧、看电影；折叠常用应用；配置后的家庭摘要 | 左右选观看入口；向下到应用组；确定或向下展开；组内向下到翻屏按钮，再向下进第二屏 |
| 片库与应用 | 直播、影视、四个常用应用、所有应用 | 观看入口向下到常用应用；再向下进第三屏 |
| 家庭 | 相册、数据、消息、工具、轮播与摘要、家庭屏、原桌面 | 顶部入口向上回第二屏；确定打开分区 |
| 爸妈首页 | 看电视、看电视剧、看电影；一行较小的常用应用 | 左右选择，上下连接核心入口、应用和设置 |

返回逐屏上返并保留上次焦点。应用组收起时，内部应用不能被点击或聚焦；从其他屏返回到曾展开的应用组时，焦点落在可见的组入口。从摘要打开分区后，如果摘要已被撤销，返回落到“看电视”，不会留在隐藏按钮上。菜单键随时打开设置。

首屏三张观看卡分别进入内嵌直播、电视剧和电影；第二屏保留片库管理和外部应用选择。NAS、影视来源与播放记录按各自模块处理，详见[内嵌影视](EMBEDDED-CINEMA.md)与 NAS 文档。小爱、搜索、音量按键继续交给原系统；浏览器按键检查不等于真机唤醒验收。

## 布局与素材

电视使用1920×1080设计画布和96/54像素安全边距，按实际逻辑窗口等比缩放。首屏观看卡为236×110设计像素，三卡总面积不到画面的6%；其余空间留给壁纸、时间和可选摘要。常用应用收起为一个入口，展开后才显示单独应用。爸妈主卡仍为236px高、46px标题，应用快捷入口108px高。大标识与“爸妈版”副标题同一基线，避免超出顶部安全区。

玻璃使用深灰底、透景、细亮边和柔和高光。支持的WebView对卡片做局部模糊，对下半部风景做渐变遮罩模糊；关闭透明效果后使用实底。应用折叠、展开和入场动效都有限次，并遵循减少动态偏好。旧WebView实际质感与GPU负载仍需小米电视验收。

内置四张至少3840×2160的CC0风景照片，以及一张1672×941的生成背景“暮色湖面”。生成背景不标作原生4K。默认卡雷扎湖，设置→壁纸可切换，两版独立保存；大图成功读取才保存，失败保留旧图。来源见[摄影素材清单](verification/s48-wallpapers.json)、[生成素材](../design-assets/s49/README.md)和[第三方声明](../THIRD_PARTY_NOTICES.md)。

竖屏预览使用文档滚动，能滚到下方应用和翻屏按钮；电视仍按整屏遥控翻页。

## 当前画面与验证

- [首屏未配置](images/s50-home-standard-empty.png)、[已配置摘要与应用](images/s50-home-standard-ready.png)、[应用展开](images/s50-home-standard-expanded.png)。
- [片库与应用](images/s50-home-standard-library.png)、[家庭](images/s50-home-standard-family.png)、[爸妈首页](images/s50-home-parents.png)。
- [实际页面动画](../media/captures/s50-fold-and-art.webm)。

截图和录屏来自实际HTML/CSS页面，应用安装状态、NAS连接和数据是合成示例，图标为文字预设。没有用整张生成UI图替代可操作页面。

| 命令 | 检查 | 报告 |
| --- | --- | --- |
| `node scripts/browser-home-entries-qa.mjs` | 三个直接入口、三屏遥控、折叠回焦、21组电视布局、竖屏底部可达 | [S50首页](verification/s50-home-entries.json) |
| `node scripts/browser-home-personal-qa.mjs` | 应用预设、摘要轮播、授权撤销、失焦暂停、分区回焦 | [S50个性化](verification/s50-home-personal.json) |
| `node scripts/browser-tv-controls-qa.mjs` | 两版720p/1080p/4K遥控路径、菜单、分区和片库网格 | [S50遥控](verification/s50-tv-controls.json) |
| `node scripts/browser-wallpaper-qa.mjs` | 五张壁纸、保存/取消/失败、非法ID、独立偏好与减少动态 | [S50壁纸](verification/s50-wallpaper-browser.json) |
| `node scripts/capture-home-motion.mjs` | 页面折叠/展开与生成插图的浏览器录屏 | [S50录屏](verification/s50-motion-capture.json) |

这些是源码与浏览器验证，不能代替小米硬件、原生图标、实际遥控、旧WebView性能或爸妈用户可读性验收。安装包版本与发行状态以[安装说明](../README.md)和Release记录为准。

S48/S49截图和报告保留为历史阶段证据，不被当前运行覆盖：[S48布局](verification/s48-tv-viewport.json)、[S48遥控](verification/s48-tv-controls.json)、[S49个性化](verification/s49-home-personal.json)。过程记录见[JOURNAL](process/JOURNAL.md)。


## S51 · 下一版综艺入口

v18源码中，标准版首屏和爸妈版均增加“看综艺”。爸妈版改为四个观看入口一行，常用应用保持较小的一行；标准版仍保留风景、摘要和折叠应用，四个观看入口沿原行展开。方向键依次选择电视、电视剧、电影、综艺，向下进入常用应用。新布局见[标准版](images/s51-home-standard-ready.png)与[爸妈版](images/s51-home-parents.png)，[遥控与尺寸检查](verification/s51-home-entries.json)。图片为浏览器画面和合成状态，未更新公开APK。

[开视资源包](RESOURCE-PACK.md)可为综艺指定站点分类编号或直接编排节目、期数和播放地址，继续复用收藏与观看记录。


## S52 · 原厂应用图标

当前源码移除应用文字色块，12项预设使用[原厂素材](../design-assets/app-logos/README.md)，已安装应用优先显示本机图标。标准版首页展开组、片库页快捷入口、折叠堆叠、爸妈快捷入口和添加应用页共用图标与回退链。历史S50/S51截图中的文字占位保留为历史记录，不再代表当前源码。

[标准版](images/s52-home-standard-expanded.png) · [爸妈版](images/s52-home-parents.png) · [应用选择页](images/s52-logos-standard-presets-top.png) · [图标验证](verification/s52-app-logos.json) · [布局与遥控](verification/s52-home-entries.json)。均为浏览器与合成安装状态，没有构建新APK。
