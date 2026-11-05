# v40 · 首页比例、相册与右侧设置

标准版 `0.2.0-alpha.37` / versionCode 40，仅适配小米电视 Android 8+。爸妈版暂停更新。本次是预发布测试版；没有据本地检查宣称小米实机验收完成。

- 标准版首页内容缩小 14%，保留完整风景背景。按实际可用窗口的宽高取同一个缩放比例，按钮、字体、间距、应用图标一起缩放；较矮的视口居中留边，不拉伸或修改电视分辨率/density。
- 常用应用保持展开；修正原生首页 drawable 图标使用固定像素、可能超出容器的问题。原生共享控件按窗口宽高计算尺寸，不再因 OEM 系统 density/override 不同而放大。
- 首页顺序为：首页 → 应用 → 相册 → 留言。第三屏自动读取本机照片并轮播，确定后进入相册设置；上下可往返。离开页面或应用失焦后停止轮播，不自动开放上传。
- 网页和原生设置改为右侧紧凑面板；相册管理也靠右。菜单字号、行距和图标边界收紧，长菜单可用遥控器上下滚动。

网页相册保留三种模板；原生首页使用轻量叠化。旧 WebView 的原生相册管理继续保留 v39 的能力边界：无网页二维码/模板/删除管理，手机上传使用临时地址，仍需电视确认。

## 下载与升级

[标准版 ARM32/64 APK](https://github.com/xiaoerdata/aperture-tv-open/releases/download/v0.2.0-alpha.37/kaishi-v40-standard-arm.apk) · [发行页](https://github.com/xiaoerdata/aperture-tv-open/releases/tag/v0.2.0-alpha.37)

文件 55,339,179 字节，约 52.8 MiB。SHA-256：`307fcf90edd4a7b9335a0534c078d86afab55102b3210d287c308b00d1cdfb64`。

沿用 v18 起的标准版包名 `org.zhixiaoce.kaishi` 和发行证书，versionCode 从 39 增至 40，可按同签名覆盖更新路线安装；不要卸载或清除数据。v17 继续并存。已核对 v39/v40 实际证书一致；未在本机设备执行安装和数据保留验收。

## 验证范围

[12 组浏览器检查](verification/s102-home-browser.json)包含 640×360、960×540、1024×576、1280×720、1920×1080、3840×2160 与不同 DPR，以及 1280×640、960×500 较矮视口；另检查原生/CSS 视口不一致模拟、空相册、自动轮播、后台停止、四屏往返、管理意图和右侧长菜单。合成照片的相册管理另覆盖 960/1920/3840 宽度。真实页面截图见[首页](images/s102-home/home.png)、[设置](images/s102-home/settings.png)、[相册](images/s102-home/album.png)、[相册管理](images/s102-home/album-settings.png)。

标准版 ARM32/64 release 已实际构建、对齐与签名；218 项 Android/launcher 输入与包内首页资源逐项一致，产品 DEX 包含新增相册类，签名与版本递增核对见[升级检查](verification/s102-package-upgrade.json)。29 组原生尺寸函数检查为 JVM 验证，不能代替实际 View 绘制和遥控分发。新包不含测试注入类。

当前没有在线 Android 设备；小米 85 QLED 在原始显示设置下的比例、原生相册进入/管理返回/真实上传、遥控、小爱/米家与原桌面共存仍待实机复测。前版未完成的完整云端矩阵仍保留为待验，不把历史通过或编译成功移作本包通过。

开发过程见 [S101](HOME-REFINEMENT-S101.md)、[过程日志](process/JOURNAL.md)和[第79期底稿](../media/DEVLOG-079.md)。
