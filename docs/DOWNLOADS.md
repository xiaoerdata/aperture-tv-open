# 下载与版本

**开视仅适配小米电视，Android 8+。** 当前为 **v20 / 0.2.0-alpha.17 Alpha测试版**，尚非稳定版。新版提供标准版和爸妈版，均使用独立包名、专用发行签名，关闭调试。

[本次发布](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.17) · [源码](https://github.com/xiaoerdata/aperture-tv-open) · [问题反馈](https://github.com/xiaoerdata/aperture-tv-open/issues/new/choose)

| 下载 | 内容 |
| --- | --- |
| [标准版APK](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.17/kaishi-v20-standard.apk) | 四个观看入口、折叠应用、NAS与家庭分区 |
| [爸妈版APK](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.17/kaishi-v20-parents.apk) | 看电视、看电视剧、看电影、看综艺与常用应用 |
| [源码包](https://github.com/xiaoerdata/aperture-tv-open/archive/refs/tags/v0.2.0-alpha.17.tar.gz) | 家庭服务、二次开发与本地构建 |
| [校验和](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.17/SHA256SUMS) | 两版APK和测试清单SHA-256 |
| [测试清单](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.17/testing-manifest.json) | 包名、版本、签名、源码与验收边界 |

## 从v18/v19升级

v20沿用v18/v19的两个包名与发行证书，可在原版上更新，不需卸载或重新配对。安装前保留数据；APK分别为52.60/52.63 MiB，默认共用ARM32/64，不需要选择CPU架构。

## 与v17并存

旧临时签名私钥已丢失，无法对v17进行同签名覆盖升级。v18起按维护者选择使用新包名与v17并存安装，**不要卸载v17**。旧APK、标签和旧数据保留；新版不能自动读取旧版私有设置或NAS密码。

| 版本 | v18新包名 | v17保留包名 |
| --- | --- | --- |
| 标准版 | `org.zhixiaoce.kaishi` | `org.zhixiaoce.aperture` |
| 爸妈版 | `org.zhixiaoce.kaishi.parents` | `org.zhixiaoce.aperture.parents` |

从v17首次安装新版时，从原系统应用列表打开，重新配对家庭服务、添加NAS与影视来源。从v18更新保留其已有配置。需要时可以继续打开旧版。新旧四个开视包均排除原桌面候选；不自动切换默认桌面，不自动搬运凭证。详见[并存安装与迁移](MIGRATION-V18.md)。

[保留的v17发行](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.14)可继续下载。本版签名证书SHA-256以本次测试清单为准，今后升级须保持新版包名和证书、递增versionCode。安装提示签名冲突时先核对来源，不自动卸载。

## 安装与校验

完整下载所需附件后，可用 `sha256sum -c SHA256SUMS`（Linux）或 `shasum -a 256 -c SHA256SUMS`（macOS）核对。只下载一个APK时，对照该文件对应行。Windows使用 `Get-FileHash .\kaishi-v20-standard.apk -Algorithm SHA256`。摘要相同只证明文件一致，不等于完成真机验收。

安装路径见[小米安装说明](../INSTALL.md)。默认作为普通应用，保留原桌面、小爱和小米账号。本机应用、内嵌影院、手输NAS不依赖电脑家庭服务；照片、留言、统一片库和WorkBuddy另需运行家庭服务。家庭服务默认HTTPS；选择私网IP的HTTP时，须在电视明确开启，CLI另加 `--allow-lan-http`。NAS凭证交接与Agent安装卸载仍要求可信HTTPS。

## 本次改动与测试范围

缩小标准版与爸妈版首页、影院、NAS和设置的控件，保留遥控焦点。家庭屏精简为相册、今天的安排、留言；工作台撤下独立指标、生活连接和场景编辑，已有数据保留。播放中5秒收起控制栏，方向键可唤回。22张逐页生图参考用于设计，不进入APK。见[页面改动](UI-COMPACT-S58.md)。

浏览器、Java协议、模拟器与小米真机证据分别记录。WorkBuddy客户端与小米真机由维护者复验，未测型号不标为兼容。本轮发行清单绑定实际APK与源码输入；旧阶段报告保留原始版本含义，不作为当前全部通过证明。

可[免费本地构建](ANDROID-LOCAL.md)；发行操作见[签名说明](ANDROID-RELEASE.md)。公开源码不含签名密钥、NAS凭证、私人数据、工具链或原始用户测试PDF。

v20发布后另在main追加匿名下载与源码回读记录，不修改冻结标签和附件。[v19历史回读](verification/public-alpha-v19.json)与[v18历史回读](verification/public-alpha-v18.json)保留。
