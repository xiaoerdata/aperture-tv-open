# V48 · 播放、相册与返回修复

标准版 **0.2.0-alpha.45**、爸妈版 **0.2.0-alpha.45-parents**，均为 **versionCode48**。仅适配小米电视Android 8+，仍为预发布测试版。发布状态以[发行页](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.45)与公开完整下载回读为准。

- 直播侧栏左右键支持同来源分组与不同直播来源，浏览不换台，确定后才播放；忽略长按重复，旧请求不覆盖新组。
- “换线路”进入画面一级控制条，与频道并列，不再藏在更多。当前线路明确标注，单线路有直接反馈。
- 详情与播放中选集按向下自动追加后续集数，不用下一组按钮；保留长标题、旧卡片与焦点，上翻可补前面的集数。
- 每次影片追加后，对全部已加载内容按**年份降序 → 同年评分降序**重排。豆瓣模式同样年份优先，当前影片的可视位置保留。
- 修复播放中综艺有完整同名集数仍提示无对应集数：完整名优先，再规范化，再唯一集号；保留播放进度，不误选上下期或重复标题。

- 网络播放接入Media3前向缓冲、重试和最多128 MiB的点播分段缓存；按可用空间与内存收紧，直播不落盘。系统解码器仍作格式/解码兼容回退。NAS播放链路保持原实现。
- 读取来源时返回会取消请求并回内容页，过期结果不会反复弹出输入框。
- 首页相册首张图直接显示，前台每5秒检查上传/删除变化；读取失败保留已有画面。取消已提交图片的手动回收，减少原生绘制线程访问失效位图的风险。网页相册同样增加首帧/加载失败保护。
- 屏保设置返回键直接关闭子页面回开视，退出前停止网页生命周期回调；任务根异常恢复时重建开视首页，不回到系统原桌面。

- 局域网控制配对码有效期延长为5分钟，电视提示与未配对监听窗口同步；批准后的30分钟会话保持。

- 设置中长按向下逐级加速，短按一项、持续长按两项/四项，松开恢复；不加速确认键。

保留V47资源容量规则（原文件与展开配置各4 MiB，无资源站点导入总数配额）、照片修复、天气、NAS及原系统返回。没有改首页布局、全局分辨率或density。

| 版本 | 安装包 | 校验 |
| --- | --- | --- |
| 标准版 | [kaishi-v48-standard-arm.apk](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.45/kaishi-v48-standard-arm.apk) | [SHA256SUMS](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.45/SHA256SUMS) · [测试清单](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.45/testing-manifest.json) |
| 爸妈版 | [kaishi-v48-parents-arm.apk](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.45/kaishi-v48-parents-arm.apk) | [SHA256SUMS-parents](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.45/SHA256SUMS-parents) · [测试清单](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.45/parents-testing-manifest.json) |

沿用对应V47正式包名与发行签名，ARM32/64通用。**覆盖对应版本安装，不要先卸载或清除数据。** 两版数据各自保留，不能跨版读取私有照片或NAS密码；V47发行附件继续保留供回退。

专项生产逻辑/明确JVM替身、现有进度/存储回归、原生API编译通过；双版实际构建、签名和源码指纹检查以本版测试清单为准。没有连接小米真机，没有真实互联网资源播放验收，也没有实测播放卡顿改善幅度；海信激光电视反馈纳入照片兼容处理，但没有该设备验收，不扩大品牌适配承诺；浏览器示意不当作Android绘制证据。详见[实现与验证](LIVE-REMOTE-S123.md)、[过程记录](process/JOURNAL.md)、[传播底稿](../media/DEVLOG-096.md)。
