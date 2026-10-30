# v27 · 星河屏保与天气

版本：v27 / `0.2.0-alpha.24`；仅适配小米电视，Android 8+，继续为Alpha。标准版和爸妈版沿用既有包名与专用签名，覆盖更新v18—v26时保留配置；v17继续并存，不自动卸载或搬运凭证。

本版增加星河、流光和静态屏保，显示时间、日期与天气；首页提供公网出口IP估算城市、手选城市、24小时和未来7天预报。使用方法、天气提供方、网络和固件限制见[功能说明](AMBIENT-WEATHER-S71.md)。免费Open-Meteo API限非商业用途，数据CC BY 4.0署名与API访问条件分别遵守。

两版已公开发布，四个附件已经匿名完整下载并逐项核对SHA-256；源码清单匿名逐字节回读，完整Git树通过鉴权API核对全部文件摘要（公共API当时限流）。没有声称完整下载GitHub自动源码归档。正式下载见[v27 Alpha](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.24)。

## 验证范围

- S71通过13组实际浏览器任务，包含两版、四档TV尺寸、天气切换、城市持久化、方向键唤醒、独立页返回、闲置计时、离线和动画启停；截图为合成天气。
- 5组Node/JVM测试通过，含15项生产Java传输断言；两版Android35 API定向编译通过。开发机实查三个提供方接口连通。
- 本轮两版release构建及Lint通过；原发行证书核对、包名/版本/minSdk/targetSdk、ARM32/64、品牌像素和45份首页资源逐字节检查通过。新增屏保/天气原生类进入DEX，测试驱动未打入包。二进制清单确认HOME候选默认关闭、Dream服务由系统权限保护、独立页面未导出，未增加麦克风或系统写权限。完整输入与APK绑定见[软件发行清单](verification/s72-software-acceptance.json)。
- 标准版约52.67MiB，爸妈版约52.69MiB。完整Android输入摘要为`ab7846b5920b52db8b965cb43c9f8e9f97fd59098baf6e680194800f1fa8d029`。
- 首次Gradle进程退出143，未算通过；增量重跑119项任务后明确BUILD SUCCESSFUL，保留首次日志。

系统Dream服务是否开放、实际电视动效流畅度、待机和小爱唤醒均未真机验收。既有影视原生回归保留于[S70](ACCEPTANCE-S70.md)，不改称本轮新增屏保运行通过。WorkBuddy和小米真机继续由维护者复验。

下载入口：[版本索引](DOWNLOADS.md)。开发提示词、失败与证据见[过程记录](process/README.md)，自媒体底稿为[第53期](../media/DEVLOG-053.md)，未上传。

[发布与独立回读](verification/public-alpha-v27.json)。冻结标签提交`df4df3192765fa2904dcde083857ae92da477b68`；发行回执后加至main，不移动标签或改附件。
