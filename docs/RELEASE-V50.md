# V50 · 播放左右键

标准版`0.2.0-alpha.47`、爸妈版`0.2.0-alpha.47-parents`，versionCode 50。预发布测试版，产品仍仅声明适配小米电视Android 8+。

点播与NAS播放默认左键快退、右键快进，松开直接跳转，不用额外确认。修复初始焦点落到按钮时误变成按钮切换，以及NAS先唤出工具条才操作的问题。工具条自动显示不改变默认左右键；只有用户主动按上下/菜单进入操作控件后才导航按钮，返回退出控件恢复进度操作。选集、线路菜单和直播侧栏保留各自方向键导航。

保留V49原生首页照片屏：直接下翻进入，沿用已有照片，左右换图、上下翻屏及确定管理；兼容页直接切图，暂不执行网页渐变/漂移/双图模板。相册、NAS、资源配置目录和发行签名保持，**覆盖对应版本安装，不卸载、不清数据，不需重新上传照片**。

- [标准版APK](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.47/kaishi-v50-standard-arm.apk) · [校验](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.47/SHA256SUMS) · [测试清单](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.47/testing-manifest.json)
- [爸妈版APK](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.47/kaishi-v50-parents-arm.apk) · [校验](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.47/SHA256SUMS-parents) · [测试清单](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.47/parents-testing-manifest.json)

[播放器实现与验证](PLAYER-KEYS-S138.md) · [照片显示路径与海信复测](NATIVE-HOME-ALBUM-S136.md) · [WorkBuddy](WORKBUDDY-QUICKSTART-V48.md) · [开发日志](process/JOURNAL.md) · [素材底稿](../media/DEVLOG-098.md)

本地逻辑、显式UI/Media替身、直播与播放意图回归及原生API编译分开记录。没有连接实体电视，不声称小米/海信遥控、解码和照片显示已通过设备验收；正式发布及完整下载校验以发行页和回读记录为准。
