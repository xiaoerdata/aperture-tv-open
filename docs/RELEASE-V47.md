# V47 · 资源容量更新

标准版 **0.2.0-alpha.44**、爸妈版 **0.2.0-alpha.44-parents**，均为 **versionCode 47**。仅适配小米电视 Android 8+，仍为预发布测试版。发布状态以[发行页](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.44)及公开回读为准。

原来只保留前12个影视站点/直播来源，本版取消这项限制，也取消资源总项数、多仓入口、频道、直接片单节目与集数的导入总数配额。原始UTF-8文件和展开后的配置分别最多 **4 MiB**；支持的来源完整导入，保存后重新读取不会因数量截断。TVBox、开视资源包、M3U/TXT共用规则。节目单、远程单页、插件执行等运行预算继续保留，详见[容量说明](RESOURCE-CAPACITY-S121.md)。

两版保留V46照片修复、天气、影视排序、NAS及观看进度/线路记忆，不更改首页布局、电视density或分辨率。

| 版本 | 安装包 | 校验 |
| --- | --- | --- |
| 标准版 | [kaishi-v47-standard-arm.apk](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.44/kaishi-v47-standard-arm.apk) | [SHA256SUMS](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.44/SHA256SUMS) · [测试清单](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.44/testing-manifest.json) |
| 爸妈版 | [kaishi-v47-parents-arm.apk](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.44/kaishi-v47-parents-arm.apk) | [SHA256SUMS-parents](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.44/SHA256SUMS-parents) · [测试清单](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.44/parents-testing-manifest.json) |

均为ARM32/64通用包，沿用对应V46的正式包名与发行证书。覆盖对应版本安装，**不要先卸载或清除数据**。标准版与爸妈版各自保留原数据，不能跨版读取私有照片或NAS密码。

容量验证包含14组本地合成夹具：36/240影视站点、144直播来源、193多仓、6001频道/节目、30001集，执行生产解析与AES加密保存再恢复；准确4 MiB边界、多1字节及规范化展开超限均有检查。45项既有资源包检查、51项解析/HTTP/加密检查、Schema/CLI与本地HTTP下载摘要验证通过。双版构建、签名、冻结源码和附件检查以本版测试清单为准。

本轮没有新UI截图；沿用V46画面。没有小米真机或真实互联网资源站容量/播放验收；协议夹具和安装包发布不能代替真机体验。参见[开发过程](process/JOURNAL.md)、[第95期底稿](../media/DEVLOG-095.md)。
