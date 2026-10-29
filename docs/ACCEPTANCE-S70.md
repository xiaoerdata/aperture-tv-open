# v26 / alpha.23 软件验证

开视仅适配小米电视，Android 8+。本轮没有安装到小米真机；v24的MiTV_MTSU0报告不能自动代表v26通过。

| 验证 | 标准版 | 爸妈版 |
| --- | --- | --- |
| 浏览与应用墙 | 13项通过 | 13项通过 |
| 点播进度 | 5项通过 | 5项通过 |
| 直播与节目单 | 8项通过 | 8项通过 |

这是每版三次分段运行，入口检查有重复，不是同一次全量26项。使用API28 x86只读模拟器、合成目录/合成XMLTV及本机HLS样流。每版已安装debug包完整回读与对应冻结候选一致；每版146份核心UI、目录及播放器编译类输入与签名ARM发行版一致。5份Debug准入/入口类存在预期分支差异，没有将整个debug APK说成ARM执行。

首页浏览器7组任务通过，含四档TV尺寸、合成应用/NAS摘要、方向键、折叠偏好和手机说明回退。10组领域测试分批通过；路径失效、并行工具解析导致的EPG总超时均保留，EPG独立重跑16断言及真实30秒失败缓存恢复通过。

最终源码输入、APK摘要、逐段结果与限制见[机器清单](verification/s70-software-acceptance.json)。[修复范围](V24-FEEDBACK-S70.md)说明分类、筛选与EPG配置边界。两版release构建/lint、专用签名、品牌像素、39份首页资源、ARM32/64及新类进入DEX/测试驱动排除已核验。

## 实际画面

- [标准片单](images/s70-native-standard-ui-grid.png)、[标准应用墙](images/s70-native-standard-ui-apps.png)。
- [爸妈片单](images/s70-native-parents-ui-grid.png)、[爸妈应用墙](images/s70-native-parents-ui-apps.png)、[设置末项](images/s70-native-parents-ui-settings.png)。
- [标准版直播采集](images/s70-native-standard-live-channels.png)。短暂频道层可能在采集时自动收回，列表末项完整可达由独立原生矩形断言验证，不把这张画面说成抽屉视觉验收。

标准版设置采集超时返回HTTP503，保留captureErrors；按钮完整可见的原生矩形断言已通过。缺失的本轮设置图不使用旧候选替代。

图片为实际Android模拟器画面，非小米实拍。影片配图和样流采用Big Buck Bunny，© 2008 Blender Foundation / www.bigbuckbunny.org，CC BY 3.0；HLS为保留的Mux测试分片，未内置到产品。

## 保留的未通过记录

设置末项原44px仅见37px，修复滚动留白后通过；应用墙原两项被权重撑成两半屏，已改为固定物理列宽并补实际尺寸断言。首次IME驱动在OnShow监听安装前调用提交，改为等待独立UI任务；返回详情驱动原只认“播放”，已兼容真实“继续”按钮。一个组合长按运行未通过且未取得状态诊断，之后把选择与起播拆开验证、两版均通过；不改写旧失败结果。

本轮62MiB MP4在软件CPU的一次90秒测试未起播，日志持续缓冲；该结果仍为失败，后续交互用约1MiB HLS样流完成，没有修改产品准备超时去迁就测试。原始资源包没有提供，因此不声称覆盖其所有源站或61项海报比例。

v26首次标准版系统注入换台失败，诊断为选中1但播放索引0、确认持有索引0且频道层已关闭，尚未确定根因。后续连续频道键通过UI线程的`Activity.dispatchKeyEvent`验证真实处理器，长按仍等待真实5.3秒；这不代表完整系统遥控传输链路通过。原失败见[注入记录](verification/s70-native-standard-live-v26-injection-attempt1.json)。

小米原桌面、小爱、米家、真实NAS与WorkBuddy仍由维护者复验，真机重点复测换台。源码与APK发布记录另追加，不能用本清单冒充真机验收。


[v26 Alpha已发布及匿名回读](verification/public-alpha-v26.json)。标准版直播图仅保留视频画面；[爸妈版直播采集](images/s70-native-parents-live-channels.png)显示合成当前节目。两张都没有捕获展开的频道层，不据此宣称频道抽屉视觉验收通过。发行源码标签保持冻结，本文与回执为发布后追加。
