# V51 · 播放返回

标准版`0.2.0-alpha.48`、爸妈版`0.2.0-alpha.48-parents`，versionCode 51。预发布测试版，仅声明适配小米电视Android 8+。

播放返回先关闭已打开的面板或工具条，再回影片详情；继续返回回到原片单。详情恢复不再重新读取来源，保留本次已打开的影片、线路和选集；原片单保留来源、页码、筛选及影片位置。收藏、历史直接播放也先回影片概览。重复或过期返回键不再继续退出页面。

保留V50默认左右快退快进、原生首页照片屏及已有相册、NAS、来源配置。**对应版本直接覆盖安装，不卸载、不清数据。** 两版分别保持原包名和发行签名，ARM32/64通用。

- [标准版APK](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.48/kaishi-v51-standard-arm.apk) · [校验](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.48/SHA256SUMS) · [测试清单](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.48/testing-manifest.json)
- [爸妈版APK](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.48/kaishi-v51-parents-arm.apk) · [校验](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.48/SHA256SUMS-parents) · [测试清单](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.48/parents-testing-manifest.json)

[实现与验证](PLAYBACK-BACK-S140.md) · [播放左右键](PLAYER-KEYS-S138.md) · [WorkBuddy](WORKBUDDY-QUICKSTART-V48.md) · [开发日志](process/JOURNAL.md) · [素材底稿](../media/DEVLOG-099.md)

本地生产代码提取、JVM协作者、API编译与两版APK验证分别记录；没有实体电视验收，来源断网检查也不是Android真实网络或解码测试。正式发布、公开下载和源码一致性以发行页及回读记录为准。
