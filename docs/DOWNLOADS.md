# 下载与版本

**仅适配小米电视，Android 8+。** 当前标准版为 **v41 / 0.2.0-alpha.38**，预发布测试版。应用图标居中，首页等比缩放，原生相册二维码与手机留言编辑补齐，天气增加气温曲线和七日温区。12组浏览器适配检查与标准版构建签名通过；小米真机待复测，爸妈版暂停更新。

[标准版 APK](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.38/kaishi-v41-standard-arm.apk) · [发行页面](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.38) · [校验和](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.38/SHA256SUMS) · [测试清单](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.38/testing-manifest.json)

[历史爸妈版 v32](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.29/kaishi-v32-parents.apk)

## 从v18—v40升级

v41 沿用 v18—v40 的标准版包名与发行证书，可在原标准版上更新，不需卸载或重新配对。安装前保留数据；本版安装包大小以测试清单为准，默认支持 ARM32/64，不需要选择CPU架构。

## 与v17并存

旧临时签名私钥已丢失，无法对v17进行同签名覆盖升级。v18起按维护者选择使用新包名与v17并存安装，**不要卸载v17**。旧APK、标签和旧数据保留；新版不能自动读取旧版私有设置或NAS密码。

| 版本 | v18新包名 | v17保留包名 |
| --- | --- | --- |
| 标准版 | `org.zhixiaoce.kaishi` | `org.zhixiaoce.aperture` |
| 爸妈版 | `org.zhixiaoce.kaishi.parents` | `org.zhixiaoce.aperture.parents` |

从v17首次安装新版时，从原系统应用列表打开，重新配对家庭服务、添加NAS与影视来源。从v18更新保留其已有配置。需要时可以继续打开旧版。新旧四个开视包均排除原桌面候选；不自动切换默认桌面，不自动搬运凭证。详见[并存安装与迁移](MIGRATION-V18.md)。

[保留的v17发行](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.14)可继续下载。本版签名证书SHA-256以本次测试清单为准，今后升级须保持新版包名和证书、递增versionCode。安装提示签名冲突时先核对来源，不自动卸载。

## 安装与校验

完整下载所需附件后，可用 `sha256sum -c SHA256SUMS`（Linux）或 `shasum -a 256 -c SHA256SUMS`（macOS）核对。只下载一个APK时，对照该文件对应行。Windows使用 `Get-FileHash .\kaishi-v41-standard-arm.apk -Algorithm SHA256`。摘要相同只证明文件一致，不等于完成真机验收。

安装路径见[小米安装说明](../INSTALL.md)。默认作为普通应用，保留原桌面、小爱和小米账号。本机应用、内嵌影院、手输NAS不依赖电脑家庭服务；日程、统一片库和WorkBuddy另需运行家庭服务；本机相册上传和扫码留言不需要电脑服务；旧家庭服务相册仍关闭，新增独立[本机相册](LOCAL-GALLERY.md)。家庭服务默认HTTPS；选择私网IP的HTTP时，须在电视明确开启，CLI另加 `--allow-lan-http`。NAS凭证交接与Agent安装卸载仍要求可信HTTPS。

## 本次改动与测试范围

已安装 v40 或更早标准版时，用 v41 同签名覆盖更新；versionCode 为 41，不需卸载或清数据。

本版改动与真实验收边界见 [v41 发行说明](RELEASE-V41.md)。APK 与公开来源一致性见本版测试清单。仅发布标准版，沿用包名和签名，可覆盖升级保留配置。

浏览器在 appassets 地址复现旧版初始化中断；单独更换屏保脚本后通过。Android28 / WebView66 隔离验证页面缩放与确认动作，省略大图和字体并绕过产品>=89内核门槛，不代表小米真机或完整应用验收。默认桌面、开机和 HOME 分发必须由小米固件提供支持。

屏保、天气与网络条件见[屏保与天气](AMBIENT-WEATHER-S71.md)。[保留的v29](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.26)可继续下载。


**资源数量说明：** v31 单包最多 2000 个直接节目、10000 集、2000 个频道和 4 MiB；远程目录最多 10000 页、每页 1000 条、单次响应 4 MiB，超过单页限制时提示错误。部分 TVBox 插件执行结果仍有 512 KiB 限制。
