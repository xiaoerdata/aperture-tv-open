# 来源与权利说明

- 家庭屏、三个场景、海边 SVG 插画、品牌拱窗符号、视频排版和镜头脚本为本项目新写内容；没有复制电视 Launcher、艺术家作品、影视片段或私人照片。
- `public/ui/mobile-controls.css` 是智小策现有移动 UI v2 的共享控件快照，保留原注释。本项目拟公开时与自有代码统一按 MIT 授权；后续平台改动应通过更新快照同步，不在产品主题覆盖尺寸。
- Pillow：运行时图像解码与重新编码，锁定版本 11.1.0；实际包元数据和完整许可标为 MIT-CMU（此前 HPND 说明已纠正）。包含各组件声明的完整 LICENSE 保留在 `docs/third-party/Pillow-11.1.0-LICENSE.txt`。由安装依赖单独获取，不把第三方库重新标为我们的 MIT 代码。
- Playwright：仅开发验证与录制，Apache-2.0；Chromium 及其第三方许可随浏览器分发。
- ffmpeg：仅素材编码，许可随具体构建而定。当前使用临时安装的 imageio-ffmpeg 二进制，包含 H.264 编码；不将该二进制打入源码包，不声称拥有编码器代码。
- 普通控件与正文使用系统字体；电视首页标题、日期和时钟使用以下本地OFL字体。没有嵌入商业字体；其他字形按本机 Noto CJK / Arial 等回退。
- 参考文档、参考产品截图仅用于内部设计研究，不作为我们公开作品。见 `docs/DESIGN-RESEARCH.md`。
- “智小策”“小米”“Android”等名称仅作项目归属或兼容性说明，不暗示小米/Google官方合作；本项目未取得任何官方认证。
- 用户上传素材归用户所有。公开分享场景包不自动分享上传图片；将照片用于自媒体应另行获得相关人员许可。

- @modelcontextprotocol/sdk 1.32.1：MIT，运行时依赖，按 package-lock.json 安装；其传递依赖保留各自许可。
- hls.js 1.7.3：Apache-2.0（见已安装包 LICENSE），仅从本机依赖目录提供播放器模块，不引用远程CDN脚本。
- 生活来源目录只有原创说明和上游链接，不随包分发第三方连接器。小米HA自定义许可与mijia-api说明差异见 LIFE-CONNECTIONS.md。

## qrcode 1.5.4

MIT，作者与完整许可见安装依赖中的 qrcode/license。用于在本机生成授权二维码；不向第三方二维码服务发送链接。依赖锁定于 package-lock.json，保留依赖自带许可。

## 电视首页字体

- 时钟：Newsreader派生子集（字体内部名Lab Reader），Copyright 2020 The Newsreader Project Authors，SIL OFL 1.1。复用智小策已有子集，未修改字体，随包提供`public/launcher/fonts/Newsreader-OFL.txt`；上游https://github.com/productiontype/Newsreader。
- 中文标题与日期：Source Han Serif CN Regular的本项目子集，改名Aperture Song以遵守Reserved Font Name。实际字体版权字段为 © 2017–2024 Adobe，Reserved Font Name 为 Source，SIL OFL 1.1。下列上游许可文本快照的版权头保留 2017–2022，原文未改写；实际字体与源文件的版权/名称/许可字段及摘要见 `docs/verification/s34-fonts.json`。许可证由上游https://github.com/adobe-fonts/source-han-serif/blob/master/LICENSE.txt读取并原文保存在`public/launcher/fonts/SourceHanSerif-OFL.txt`。子集化脚本`scripts/subset-lobby-font.py`、字符范围和原文件/产物摘要`public/launcher/fonts/aperture-song.json`可复现。用户自定义文本不在子集时使用系统字体回退。
- 字体随离线客厅资源打包，不请求外部字体CDN。字体继续适用OFL，不随项目代码重新标为MIT；许可不是设计者对本产品的背书。


## Android NAS 原生客户端

SMBJ0.14.0、ASN.1 ONE0.6.0、OkHttp4.12.0、Okio3.6.0、Kotlin1.9.10与JetBrains annotations13.0适用Apache-2.0；SLF4J2.0.9、MBassador1.3.0适用MIT，Bouncy Castle1.79保留MIT式上游许可。完整文本、署名和本轮12个实际JAR摘要保存在android/third-party并作为APK资源打包；不得统一改标为项目MIT。没有嵌入第三方品牌SDK或复制其私有登录机制。Samba和Impacket仅作为隔离本地测试运行时，不打包分发。


## Default 4K photograph: Carezza

- File: `public/launcher/carezza-4k.jpg`, 3840×2160 JPEG, 2471360 bytes.
- Author: Cem Sagisman (cems77). Title: Lake near the mountains in Carezza.
- Source and license evidence: https://commons.wikimedia.org/wiki/File:Lake_near_the_mountains_in_Carezza_(Unsplash).jpg (revision 1090880360). Published on Unsplash before 5 June 2017 under CC0 1.0.
- License: https://creativecommons.org/publicdomain/zero/1.0/ . The photograph is CC0, separately from the project code license.
- Downloaded Commons 3840px derivative unchanged; source photograph is 5760×3240. No local resizing, sharpening, recoloring or AI upscaling. UI shading is a separate CSS layer.
- SHA-256: `071a29a0ac25ea897f7369a989cac71c7c55ec9ef27257c971cfdb10165c37b2`.
