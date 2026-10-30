# 下载与版本

**开视仅适配小米电视，Android 8+。** 当前为 **v28 / 0.2.0-alpha.25 Alpha测试版**，尚非稳定版。新版提供标准版和爸妈版，均使用独立包名、专用发行签名，关闭调试。

[本次发布](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.25) · [源码](https://github.com/xiaoerdata/aperture-tv-open) · [问题反馈](https://github.com/xiaoerdata/aperture-tv-open/issues/new/choose)

| 下载 | 内容 |
| --- | --- |
| [标准版APK](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.25/kaishi-v28-standard.apk) | 四个观看入口、折叠应用、NAS与家庭分区 |
| [爸妈版APK](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.25/kaishi-v28-parents.apk) | 看电视、看电视剧、看电影、看综艺与常用应用 |
| [源码包](https://github.com/xiaoerdata/aperture-tv-open/archive/refs/tags/v0.2.0-alpha.25.tar.gz) | 家庭服务、二次开发与本地构建 |
| [校验和](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.25/SHA256SUMS) | 两版APK和测试清单SHA-256 |
| [测试清单](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.25/testing-manifest.json) | 包名、版本、签名、源码与验收边界 |

## 从v18—v27升级

v28沿用v18—v27的两个包名与发行证书，可在原版上更新，不需卸载或重新配对。安装前保留数据；两版安装包大小以本次测试清单为准，默认共用ARM32/64，不需要选择CPU架构。

## 与v17并存

旧临时签名私钥已丢失，无法对v17进行同签名覆盖升级。v18起按维护者选择使用新包名与v17并存安装，**不要卸载v17**。旧APK、标签和旧数据保留；新版不能自动读取旧版私有设置或NAS密码。

| 版本 | v18新包名 | v17保留包名 |
| --- | --- | --- |
| 标准版 | `org.zhixiaoce.kaishi` | `org.zhixiaoce.aperture` |
| 爸妈版 | `org.zhixiaoce.kaishi.parents` | `org.zhixiaoce.aperture.parents` |

从v17首次安装新版时，从原系统应用列表打开，重新配对家庭服务、添加NAS与影视来源。从v18更新保留其已有配置。需要时可以继续打开旧版。新旧四个开视包均排除原桌面候选；不自动切换默认桌面，不自动搬运凭证。详见[并存安装与迁移](MIGRATION-V18.md)。

[保留的v17发行](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.14)可继续下载。本版签名证书SHA-256以本次测试清单为准，今后升级须保持新版包名和证书、递增versionCode。安装提示签名冲突时先核对来源，不自动卸载。

## 安装与校验

完整下载所需附件后，可用 `sha256sum -c SHA256SUMS`（Linux）或 `shasum -a 256 -c SHA256SUMS`（macOS）核对。只下载一个APK时，对照该文件对应行。Windows使用 `Get-FileHash .\kaishi-v28-standard.apk -Algorithm SHA256`。摘要相同只证明文件一致，不等于完成真机验收。

安装路径见[小米安装说明](../INSTALL.md)。默认作为普通应用，保留原桌面、小爱和小米账号。本机应用、内嵌影院、手输NAS不依赖电脑家庭服务；日程、留言、统一片库和WorkBuddy另需运行家庭服务；相册暂从发行版关闭，开发代码与已保存数据保留。家庭服务默认HTTPS；选择私网IP的HTTP时，须在电视明确开启，CLI另加 `--allow-lan-http`。NAS凭证交接与Agent安装卸载仍要求可信HTTPS。

## 本次改动与测试范围

发行版暂时关闭首页和NAS相册；默认家庭分区保留安排与留言。照片代码、目录配置和旧数据保留，不删除家庭资料。

播放画面上按确认打开频道或选集，点播左右控制进度；直播上下换台。频道列表浏览时不自动消失。选集改为两列完整标题，按内容换行增高，多线路和每20集分组在原页切换；播放中的选集用宽侧栏并定位当前集。详情移除相似影片。

[改动说明](V27-FEEDBACK-S74.md) · [本轮打包验证](RELEASE-S75.md)。5组Node/JVM测试、浏览器检查与两版编译通过。本轮系统输入回归因模拟器测试驱动安装超时未执行，遥控换台、选集、进度与具体电视解码仍需维护者复测，不能把编译通过当作真机验收。小爱、米家、原桌面和WorkBuddy仍需现场测试。

屏保、天气与网络使用条件见[屏保与天气](AMBIENT-WEATHER-S71.md)。此前v27保留在[历史发行](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.24)。
