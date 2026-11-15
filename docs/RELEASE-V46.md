# V46 · 照片屏兼容修复

标准版 **0.2.0-alpha.43**、爸妈版 **0.2.0-alpha.43-parents**，均为 **versionCode 46**。仅适配小米电视 Android 8+，仍为预发布测试版。发布状态以[发行页](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.43)为准。

- 修复旧WebView忽略inset时照片舞台可能零尺寸、上传后照片屏空白的问题；已有照片时明确隐藏空态。
- 原生相册提供“管理照片”、删除确认及8/12/20秒轮播间隔。默认保留，旧弹窗或后台/关闭后的迟到回调不执行写入。
- 沿用正式照片库、上传协议和20MiB限制；保留V45天气、影视排序、观看进度/线路记忆、NAS及应用入口。爸妈版继续简化首页，共享底层修复，不新增标准版的复杂面板。

## 下载与升级

| 版本 | APK | 校验与证据 |
| --- | --- | --- |
| 标准版 | [kaishi-v46-standard-arm.apk](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.43/kaishi-v46-standard-arm.apk) | [SHA256SUMS](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.43/SHA256SUMS) · [测试清单](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.43/testing-manifest.json) |
| 爸妈版 | [kaishi-v46-parents-arm.apk](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.43/kaishi-v46-parents-arm.apk) | [SHA256SUMS-parents](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.43/SHA256SUMS-parents) · [测试清单](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.43/parents-testing-manifest.json) |

标准版55,380,284字节，SHA-256：`2a514c2cf1402608f0acbf038ab290f93afeecd72787ce9b3053755fe84f1286`。爸妈版55,408,956字节，SHA-256：`661a48d1b4dfcd5f6ac2714130903c34896e64770f331a0c20f1e75da1a86032`。

两版均为ARM32/64通用包，包名与原发行证书分别沿用，可覆盖对应V45，勿先卸载或清数据。标准版与爸妈版是独立应用，不能跨版自动读取照片/NAS密码。如果此前装过独立“照片测试”应用，V46不会读取那个测试应用的独立照片库；已有正式开视的照片仍使用原私有目录。v17迁移继续见[并存说明](MIGRATION-V18.md)。

## 验证范围

本轮复核S118补丁、补充原生迟到回调保护，实际执行六组前后兼容实验、18项菜单回调、原生编译/29项尺寸、标准与爸妈版首页回归，再做双版release构建、签名、资源/源码指纹和公开附件核验。兼容实验使用现代浏览器忽略inset的夹具，原生管理检查使用JVM界面/存储替身；没有小米真机、实际解码或跨手机验收结论。

详见[照片修复](PHOTO-FIX-S119.md)、[开发过程](process/JOURNAL.md)、[第93期底稿](../media/DEVLOG-093.md)。发布完成并核对匿名完整下载后才清理过时本地包和构建副本，签名与源码保留。
