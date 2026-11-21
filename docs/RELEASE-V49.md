# V49 · 原生首页照片屏

标准版`0.2.0-alpha.46`、爸妈版`0.2.0-alpha.46-parents`，versionCode 49。预发布测试版，正式适配范围仍为小米电视Android 8+。

针对V48海信激光电视“首页黑底、管理里照片正常”的反馈，标准版首页照片屏改用独立原生页面，绕开首页WebView全屏图片层。直接下翻进入，无需点确定；左右换图、上下翻屏、确定进管理和返回继续保留。旧内核首页的原生照片控件也移除透明动画和双图层，采用单张不透明ImageView。原生兼容页直接切图，保留轮播间隔；网页渐变/漂移/双图模板暂不在该兼容页执行。

照片目录和签名不变，**覆盖对应版本安装，不卸载、不清数据，无需重新上传照片**。爸妈版同步版本与共用控件，简化首页没有新增照片入口。不改电视分辨率或density，也不改小爱和系统HOME。

- [标准版APK](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.46/kaishi-v49-standard-arm.apk) · [SHA256SUMS](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.46/SHA256SUMS) · [测试清单](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.46/testing-manifest.json)
- [爸妈版APK](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.46/kaishi-v49-parents-arm.apk) · [SHA256SUMS-parents](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.46/SHA256SUMS-parents) · [测试清单](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.46/parents-testing-manifest.json)

保留V48直播、切线、年份评分排序、连续选集、播放缓冲与点播分段缓存、来源返回、配对码5分钟和设置长按加速。[实现与验证](NATIVE-HOME-ALBUM-S136.md)说明明确替身与浏览器测试范围；没有连接海信或小米真机，不能据此称黑屏已在用户设备验收。发布状态以[发行页](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.46)与完整下载校验为准。

[WorkBuddy配置](WORKBUDDY-QUICKSTART-V48.md) · [过程记录](process/JOURNAL.md) · [传播底稿](../media/DEVLOG-097.md)
