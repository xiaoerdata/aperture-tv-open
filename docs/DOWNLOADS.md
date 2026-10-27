# 下载与版本

**开视仅适配小米电视，Android 8+。** 当前为 **v22 / 0.2.0-alpha.19 Alpha测试版**，尚非稳定版。新版提供标准版和爸妈版，均使用独立包名、专用发行签名，关闭调试。

[本次发布](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.19) · [源码](https://github.com/xiaoerdata/aperture-tv-open) · [问题反馈](https://github.com/xiaoerdata/aperture-tv-open/issues/new/choose)

| 下载 | 内容 |
| --- | --- |
| [标准版APK](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.19/kaishi-v22-standard.apk) | 四个观看入口、折叠应用、NAS与家庭分区 |
| [爸妈版APK](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.19/kaishi-v22-parents.apk) | 看电视、看电视剧、看电影、看综艺与常用应用 |
| [源码包](https://github.com/xiaoerdata/aperture-tv-open/archive/refs/tags/v0.2.0-alpha.19.tar.gz) | 家庭服务、二次开发与本地构建 |
| [校验和](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.19/SHA256SUMS) | 两版APK和测试清单SHA-256 |
| [测试清单](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.19/testing-manifest.json) | 包名、版本、签名、源码与验收边界 |

## 从v18/v19/v20/v21升级

v22沿用v18/v19/v20/v21的两个包名与发行证书，可在原版上更新，不需卸载或重新配对。安装前保留数据；APK分别为52.63/52.66 MiB，默认共用ARM32/64，不需要选择CPU架构。

## 与v17并存

旧临时签名私钥已丢失，无法对v17进行同签名覆盖升级。v18起按维护者选择使用新包名与v17并存安装，**不要卸载v17**。旧APK、标签和旧数据保留；新版不能自动读取旧版私有设置或NAS密码。

| 版本 | v18新包名 | v17保留包名 |
| --- | --- | --- |
| 标准版 | `org.zhixiaoce.kaishi` | `org.zhixiaoce.aperture` |
| 爸妈版 | `org.zhixiaoce.kaishi.parents` | `org.zhixiaoce.aperture.parents` |

从v17首次安装新版时，从原系统应用列表打开，重新配对家庭服务、添加NAS与影视来源。从v18更新保留其已有配置。需要时可以继续打开旧版。新旧四个开视包均排除原桌面候选；不自动切换默认桌面，不自动搬运凭证。详见[并存安装与迁移](MIGRATION-V18.md)。

[保留的v17发行](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.14)可继续下载。本版签名证书SHA-256以本次测试清单为准，今后升级须保持新版包名和证书、递增versionCode。安装提示签名冲突时先核对来源，不自动卸载。

## 安装与校验

完整下载所需附件后，可用 `sha256sum -c SHA256SUMS`（Linux）或 `shasum -a 256 -c SHA256SUMS`（macOS）核对。只下载一个APK时，对照该文件对应行。Windows使用 `Get-FileHash .\kaishi-v22-standard.apk -Algorithm SHA256`。摘要相同只证明文件一致，不等于完成真机验收。

安装路径见[小米安装说明](../INSTALL.md)。默认作为普通应用，保留原桌面、小爱和小米账号。本机应用、内嵌影院、手输NAS不依赖电脑家庭服务；照片、留言、统一片库和WorkBuddy另需运行家庭服务。家庭服务默认HTTPS；选择私网IP的HTTP时，须在电视明确开启，CLI另加 `--allow-lan-http`。NAS凭证交接与Agent安装卸载仍要求可信HTTPS。

## 本次改动与测试范围

修复原生详情背板透明、快速方向键焦点被旧任务抢回、数字输入被闲置任务关闭、节目表滚动描边偏移；频道列表和播放浮层显示当前/下一节目。EPG增加有界重试与30秒失败缓存，保留独立的视频请求。详情按钮间距、海报圆角和批次提示一并调整。见[原生修复与复现](NATIVE-REGRESSION-S62.md)、[报告逐项状态](V20-FEEDBACK-S60.md)。

两版各40项完整原生Activity/MediaPlayer用例通过，均包含一次节目单503故障后的恢复；26项生产Java断言与8项真实HTTP/gzip断言通过。环境为API28 Android TV x86调试版、合成资源与本地H.264/AAC测试片，不能替代小米ARM真机。最终ARM两版release/lint、验签、ABI和随包资源检查另记在[发行软件清单](verification/s63-software-acceptance.json)。此前S60全套Node102通过、3项缺Samba跳过，属于历史结果，未宣称在本次最终源码上重跑全部Node用例。

报告原资源包尚缺，具体分组翻倍与灰海报未完成复现；真实TMDB账号、NAS、小爱/米家与WorkBuddy客户端仍待维护者验收。公开发行仍为Alpha，未测型号不标为兼容。旧阶段回执保持原来的版本含义。

可[免费本地构建](ANDROID-LOCAL.md)；发行操作见[签名说明](ANDROID-RELEASE.md)。公开源码不含签名密钥、NAS凭证、私人数据、工具链或原始用户测试PDF。

[v20发布与匿名回读](verification/public-alpha-v20.json)已完成：两版完整APK、清单与校验和一致；完整源码Git树与冻结清单逐项匹配。未下载完整GitHub源码归档。回执仅追加main，不修改冻结标签和附件。[v19历史回读](verification/public-alpha-v19.json)与[v18历史回读](verification/public-alpha-v18.json)保留。

[v21发布与匿名回读](verification/public-alpha-v21.json)保留历史结论；v22的发布与匿名回读另在发布后追加main，不修改冻结标签或APK。

[v22公开发布与匿名回读](verification/public-alpha-v22.json)已完成：标准/爸妈两版完整APK、测试清单与校验和的SHA-256一致；匿名Git标签与1405个文件的blob摘要逐项匹配冻结源码。API限流后使用无凭证、无Cookie的Git浅层树读取，没有下载完整GitHub源码归档或全部源码blob。7个旧标签、7次发行和28份旧附件未变。回执追加main，不修改发行标签和APK。
