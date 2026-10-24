# 下载与版本

正式中文名为“开视”。本次 **v16 / 0.2.0-alpha.13** 包含电视布局修正、内嵌直播与影视、返回页码和焦点保留；详见[画面适配](TV-VIEWPORT.md)与[内嵌直播/影视](EMBEDDED-CINEMA.md)。

**本项目仅适配小米电视。** Android 8+ 是最低要求，具体型号和固件需实测。

当前提供 **v16 / 0.2.0-alpha.13 Alpha测试版**，用于通过开源仓库收集测试结果。APK是已核对签名的debug构建，小米真机与WorkBuddy客户端仍由维护者测试；不是正式稳定版。默认作为普通应用安装，不替换小米桌面。

[查看本次测试发布](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.13) · [查看源码](https://github.com/xiaoerdata/aperture-tv-open) · [反馈问题](https://github.com/xiaoerdata/aperture-tv-open/issues/new/choose)

| 下载 | 文件 |
| --- | --- |
| [标准版APK](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.13/aperture-tv-v16-standard-debug.apk) | `aperture-tv-v16-standard-debug.apk` |
| [爸妈版APK](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.13/aperture-tv-v16-parents-debug.apk) | `aperture-tv-v16-parents-debug.apk` |
| [源码包](https://github.com/xiaoerdata/aperture-tv-open/archive/refs/tags/v0.2.0-alpha.13.tar.gz) | 电脑/NAS运行服务、二次开发或自行构建 |
| [校验和](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.13/SHA256SUMS) | 两版APK及测试清单的SHA-256 |
| [测试清单](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.13/testing-manifest.json) | 版本、包名、签名、来源与待测范围 |

历史文档中的“私有候选”“内测下载”统一指向本页。它们是开发阶段记录，历史版本不因此全部公开；以本页所列Alpha附件为准。也可按[免费本地构建](ANDROID-LOCAL.md)自行生成APK。

## 选择版本

| 版本 | 包名 | 首页 | 本地构建 |
| --- | --- | --- | --- |
| 标准版 | `org.zhixiaoce.aperture` | 直播、影视、相册、数据、信息、工具、家庭屏、原桌面 | `npm run android:apk -- --edition standard` |
| 爸妈版 | `org.zhixiaoce.aperture.parents` | 看电视、看电视剧、看电影、常用应用 | `npm run android:apk -- --edition parents` |

两个包可以同时安装，设置和授权各自独立。两版同时构建可用 `--edition both`。源码包用于电脑或 NAS 运行家庭服务、二次开发和构建 APK，不能直接安装到电视。

## 安装前核对

本地构建产物分别位于 `artifacts/android-local` 和 `artifacts/android-local-parents`。每个目录包含 APK、`manifest.json`、`SHA256SUMS`、`signature.txt` 与 `package.txt`。核对包名、版本、APK 摘要及签名；校验和要与同一可信来源的资料比较，不能只看文件名。

从测试发布下载APK和SHA256SUMS后，可分别核对所需文件。源码使用GitHub标签自动归档，解压后的逐文件摘要见SOURCE-MANIFEST.json，不将其压缩包混入APK校验和。本地构建的SHA256SUMS在各自产物目录。Linux完整下载附件后执行：

```bash
sha256sum -c SHA256SUMS
```

macOS 可执行 `shasum -a 256 -c SHA256SUMS`。Windows PowerShell 可用 `Get-FileHash .\aperture-tv-v16-standard-debug.apk -Algorithm SHA256`，与该目录的 `SHA256SUMS` 对照。摘要一致只证明文件一致，不证明来源可信、已完成硬件验收或能够覆盖升级。

已有同包名应用时，只有兼容签名及版本才可能覆盖升级。正式签名与私有 debug 签名可能不同；不要为解决签名错误自动卸载旧版，卸载会清除本地设置和授权。先核对来源并保留数据，再决定迁移方式。

安装后默认从原桌面应用列表打开，HOME 候选默认关闭。本机应用、内嵌来源播放与手输 NAS 可独立使用；家庭照片、留言、统一片库和 WorkBuddy 另需运行家庭服务。具体操作见[安装说明](../INSTALL.md)、[第一次使用](QUICKSTART.md)和[WorkBuddy 指引](WORKBUDDY.md)。

## 当前测试范围

两版APK沿用对应旧版本的包名与签名，版本号递增。软件回归包括76项Node测试、19个浏览器脚本和两版API26模拟器的实际媒体解码、来源加密与覆盖升级，见[本轮记录](verification/s47-software-acceptance.json)。发行字节与干净源码的对应关系另见Release的testing-manifest.json。浏览器、协议和模拟器证据分别保留；WorkBuddy 客户端与小米真机由维护者测试，未测型号不标为兼容。设备范围见[支持说明](SUPPORT.md)，发布准备见[开源准备](OPEN-SOURCE-PREPARATION.md)。

维护者生成正式包时使用[release构建与签名说明](ANDROID-RELEASE.md)；当前说明中的命令尚未完整执行，不能据此标为已发行。
