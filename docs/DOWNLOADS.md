# 下载与版本

**开视仅适配小米电视，Android 8+。** 当前为 **v29 / 0.2.0-alpha.26 Alpha测试版**，尚非稳定版。新版提供标准版和爸妈版，均使用独立包名、专用发行签名，关闭调试。

[本次发布](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.26) · [源码](https://github.com/xiaoerdata/aperture-tv-open) · [问题反馈](https://github.com/xiaoerdata/aperture-tv-open/issues/new/choose)

| 下载 | 内容 |
| --- | --- |
| [标准版APK](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.26/kaishi-v29-standard.apk) | 四个观看入口、折叠应用、NAS与家庭分区 |
| [爸妈版APK](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.26/kaishi-v29-parents.apk) | 看电视、看电视剧、看电影、看综艺与常用应用 |
| [源码包](https://github.com/xiaoerdata/aperture-tv-open/archive/refs/tags/v0.2.0-alpha.26.tar.gz) | 家庭服务、二次开发与本地构建 |
| [校验和](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.26/SHA256SUMS) | 两版APK和测试清单SHA-256 |
| [测试清单](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.26/testing-manifest.json) | 包名、版本、签名、源码与验收边界 |

## 从v18—v28升级

v29沿用v18—v28的两个包名与发行证书，可在原版上更新，不需卸载或重新配对。安装前保留数据；两版安装包大小以本次测试清单为准，默认共用ARM32/64，不需要选择CPU架构。

## 与v17并存

旧临时签名私钥已丢失，无法对v17进行同签名覆盖升级。v18起按维护者选择使用新包名与v17并存安装，**不要卸载v17**。旧APK、标签和旧数据保留；新版不能自动读取旧版私有设置或NAS密码。

| 版本 | v18新包名 | v17保留包名 |
| --- | --- | --- |
| 标准版 | `org.zhixiaoce.kaishi` | `org.zhixiaoce.aperture` |
| 爸妈版 | `org.zhixiaoce.kaishi.parents` | `org.zhixiaoce.aperture.parents` |

从v17首次安装新版时，从原系统应用列表打开，重新配对家庭服务、添加NAS与影视来源。从v18更新保留其已有配置。需要时可以继续打开旧版。新旧四个开视包均排除原桌面候选；不自动切换默认桌面，不自动搬运凭证。详见[并存安装与迁移](MIGRATION-V18.md)。

[保留的v17发行](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.14)可继续下载。本版签名证书SHA-256以本次测试清单为准，今后升级须保持新版包名和证书、递增versionCode。安装提示签名冲突时先核对来源，不自动卸载。

## 安装与校验

完整下载所需附件后，可用 `sha256sum -c SHA256SUMS`（Linux）或 `shasum -a 256 -c SHA256SUMS`（macOS）核对。只下载一个APK时，对照该文件对应行。Windows使用 `Get-FileHash .\kaishi-v29-standard.apk -Algorithm SHA256`。摘要相同只证明文件一致，不等于完成真机验收。

安装路径见[小米安装说明](../INSTALL.md)。默认作为普通应用，保留原桌面、小爱和小米账号。本机应用、内嵌影院、手输NAS不依赖电脑家庭服务；日程、留言、统一片库和WorkBuddy另需运行家庭服务；相册暂从发行版关闭，开发代码与已保存数据保留。家庭服务默认HTTPS；选择私网IP的HTTP时，须在电视明确开启，CLI另加 `--allow-lan-http`。NAS凭证交接与Agent安装卸载仍要求可信HTTPS。

## 本次改动与测试范围

播放线路过滤分享页和HTML/JSON，解码失败依次尝试同一集的其他线路；手动换线保留进度。片单固定五列，侧栏始终可见。长集名完整换行、等高排列；确认打开频道/选集，点播左右调进度，直播上下换台。首页返回原桌面后移除开视任务，不改默认HOME。相册继续从发行版关闭，代码与旧数据保留。

新增[只读留言源](MESSAGE-SOURCE.md)，可由WorkBuddy更新已配置的HTTPS文件；电视前台刷新、缓存和到点播报。家庭服务留言与独立HTTPS留言源是两种接入方式，后者不要求在电脑启动家庭服务。

两版各27条Android系统输入断言、4组首页与13组天气/屏保浏览器任务通过，最终签名包与源码摘要一致。模拟器不是小米真机；小爱/米家、原小米桌面、OEM屏保、中文TTS、WorkBuddy真实上传、外部点播成功率与两小时耐久仍需维护者测试。见[发行说明](RELEASE-S77.md)、[软件回执](verification/s76-summary.json)及[反馈处理](V28-FEEDBACK-S76.md)。

屏保、天气与网络条件见[屏保与天气](AMBIENT-WEATHER-S71.md)。[保留的v28](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.25)可继续下载。
