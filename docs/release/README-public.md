# 开视 · Aperture TV

**本项目仅适配小米电视。** Android 8+ 是最低系统要求，具体机型和固件需要实测。开视是运行在原电视系统上的家庭入口应用，不是小米官方ROM或官方认证产品。

智小策的首个开源项目，当前为 **Alpha 测试版**：保留原桌面、小爱同学与原系统账号，在电视上增加家庭照片、日程、留言、数据、NAS资源和可定制入口。

当前版本 **v26 / 0.2.0-alpha.23**：分类留在左侧，影片在右侧五列浏览；合并加载更多计数，直播全屏并修复确认键竞争；首页放大应用图标，新增本机应用墙。见[回归说明与限制](../V24-FEEDBACK-S70.md)。

**v26可更新v18—v25，保留配置；与v17并存，保留旧数据。** 从v17首次使用新包名需要重新配对、配置NAS；请先读[迁移说明](../MIGRATION-V18.md)。默认作为普通应用安装，HOME候选关闭。

## 两个版本

- 标准版：首屏看电视、看电视剧、看电影、看综艺、常用应用；向下进入片库与家庭分区。深色玻璃、4K摄影背景和遥控焦点。
- 爸妈版：看电视、看电视剧、看电影、看综艺，以及常用应用快捷入口。安装和来源配置留在家人设置。

![标准版首页（浏览器演示，非真机）](../images/s70-home.png)

![爸妈版首页（浏览器演示，非真机）](../images/s70-home-parents.png)

## 安装与使用

按[小米电视安装说明](../../INSTALL.md)开始。[下载与版本](../DOWNLOADS.md)说明两版选择和校验方法；[Alpha测试下载](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.23)提供标准版、爸妈版和源码；也可[免费本地构建](../ANDROID-LOCAL.md)。不需要Root、刷固件或停用原桌面，默认作为普通应用启动。

直播与影视支持M3U/TXT、XML/JSON、多仓与type4播放解析；type3支持经用户确认的JAR/Dex、JavaScript/drpy和Python插件。插件兼容有边界，见[内嵌影院](../EMBEDDED-CINEMA.md)与[插件说明](../PLUGIN-ENGINE.md)；没有预置影视来源。

“我的”集中展示NAS和影视观看记录、收藏与追剧。首页摘要按已有配置显示，无数据时保持空白。[智小策Hub](../HUB-DISPLAY.md)预留电影、体重、旅行、账本的授权面板接口；本版尚不能扫码登录Hub。

设置里可以从文件选择器或已连接NAS选取独立APK，由原系统确认安装。SMB2/3与HTTPS WebDAV可读取家庭NAS。家庭信息、WorkBuddy和统一片库另使用自托管家庭服务。

官方Agent示例使用[WorkBuddy](../WORKBUDDY.md)：扫码配对后按授权更新内容；安装和卸载应用分别授权，电视开启短期窗口并保留系统确认。请求提交、等待确认与设备回执分开，不提供任意Shell、远程代码或静默安装。

## 开发

需要Node.js 22+、Python 3.10+和Pillow。按仓库现有说明设置本地环境变量，不提交私人凭证。

```bash
npm ci
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
PATH="$PWD/.venv/bin:$PATH" npm run start:checked
```

配置与家庭服务启动步骤见[部署](../DEPLOYMENT.md)，Android构建见[本地构建](../ANDROID-LOCAL.md)。系统边界见[小米兼容检查](../XIAOMI-COMPATIBILITY.md)，扩展见[开发说明](../DEVELOPMENT.md)。

工作流程与原始需求保留在[开发记录](../process/README.md)。历史文档中的私有下载链接统一指向[下载说明](../DOWNLOADS.md)，不表示历史内测包已公开。公开源码不含私人家庭数据、开发凭证、签名密钥、SDK运行目录或旧私有Git历史。

## 当前验证边界

本轮使用合成目录、合成XMLTV、保留的Mux HLS样流与Blender影片配图进行回归；媒体不内置到安装包。主机领域测试、首页浏览器任务与Android TV API28模拟器分开记录，结果见[本轮验收清单](../ACCEPTANCE-S70.md)。筛选仅作用于已加载内容，搜索限定当前来源。62MiB MP4在本轮软件CPU环境的一次测试未能在90秒内起播，失败记录保留，没有改写为通过。

历次v22、v23、v24的验证仍保留在各自记录中，不混称本轮全量测试。用户报告的原始资源包未提供，本轮不推断其全部源站、片单海报比例或重复来源根因；新版小米真机、具体输入法、小爱/米家、NAS硬件及WorkBuddy仍由维护者复验。产品继续为Alpha。

问题反馈见[Issues](https://github.com/xiaoerdata/aperture-tv-open/issues/new/choose)，二次开发欢迎[Pull Requests](https://github.com/xiaoerdata/aperture-tv-open/pulls)。

核心代码使用[MIT许可](../../LICENSE)。字体、图片、依赖与上游连接器分别遵守各自许可，详见[第三方声明](../../THIRD_PARTY_NOTICES.md)。

![影片片单（API28 x86模拟器、合成目录，非小米真机）](../images/s70-native-standard-ui-grid.png)

![影片详情（API28 x86模拟器、Blender影片配图，非小米真机）](../images/s70-native-standard-ui-section.png)
