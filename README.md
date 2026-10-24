# 开窗 · Aperture TV

**本项目仅适配小米电视。** Android 8+ 是最低系统要求，具体机型和固件需要实测。开窗是运行在原电视系统上的家庭入口应用，不是小米官方ROM或官方认证产品。

智小策的首个开源项目，当前为 **Alpha 测试版**：保留原桌面、小爱同学与原系统账号，在电视上增加家庭照片、日程、留言、数据、NAS资源和可定制入口。

## 两个版本

- 标准版：直播、影视、相册、数据、消息、工具、家庭屏与原桌面；深色界面、4K背景和遥控焦点。
- 爸妈版：看电视、看电视剧、看电影，以及常用应用快捷入口。安装和来源配置留在家人设置。

![标准版首页（浏览器演示，非真机）](docs/images/s31-standard-1920.png)

![爸妈版首页（浏览器演示，非真机）](docs/images/s31-parents-1920.png)

## 安装与使用

按[小米电视安装说明](INSTALL.md)开始。[下载与版本](docs/DOWNLOADS.md)说明两版选择和校验方法；[Alpha测试下载](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.10)提供标准版、爸妈版和源码；也可[免费本地构建](docs/ANDROID-LOCAL.md)。不需要Root、刷固件或停用原桌面，默认作为普通应用启动。

设置里可以从文件选择器或已连接NAS选取独立APK，由原系统确认安装。SMB2/3与HTTPS WebDAV可读取家庭NAS。家庭信息、WorkBuddy和统一片库另使用自托管家庭服务。

官方Agent示例使用[WorkBuddy](docs/WORKBUDDY.md)：扫码配对后按授权更新内容；安装和卸载应用分别授权，电视开启短期窗口并保留系统确认。请求提交、等待确认与设备回执分开，不提供任意Shell、远程代码或静默安装。

## 开发

需要Node.js 22+、Python 3.10+和Pillow。按仓库现有说明设置本地环境变量，不提交私人凭证。

```bash
npm ci
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
PATH="$PWD/.venv/bin:$PATH" npm run start:checked
```

配置与家庭服务启动步骤见[部署](docs/DEPLOYMENT.md)，Android构建见[本地构建](docs/ANDROID-LOCAL.md)。系统边界见[小米兼容检查](docs/XIAOMI-COMPATIBILITY.md)，扩展见[开发说明](docs/DEVELOPMENT.md)。

工作流程与原始需求保留在[开发记录](docs/process/README.md)。历史文档中的私有下载链接统一指向[下载说明](docs/DOWNLOADS.md)，不表示历史内测包已公开。公开源码不含私人家庭数据、开发凭证、签名密钥、SDK运行目录或旧私有Git历史。

## 当前验证边界

已有源码、针对性测试和本地debug构建。WorkBuddy客户端与小米真机由维护者验收，未测型号不标为兼容；模拟器、浏览器和合成回执不能代替硬件测试。测试版APK使用debug签名，未完成小米机型验收；正式稳定版另行发布。

问题反馈见[Issues](https://github.com/xiaoerdata/aperture-tv-open/issues/new/choose)，二次开发欢迎[Pull Requests](https://github.com/xiaoerdata/aperture-tv-open/pulls)。

核心代码使用[MIT许可](LICENSE)。字体、图片、依赖与上游连接器分别遵守各自许可，详见[第三方声明](THIRD_PARTY_NOTICES.md)。
