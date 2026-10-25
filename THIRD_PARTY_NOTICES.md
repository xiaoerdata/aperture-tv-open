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

## S48 · Additional landscape wallpapers

All three photographs are CC0 1.0, separately from the code license. Downloaded original JPEGs unchanged; UI cropping, shading and lower-screen blur are CSS layers. No local pixel edits or AI upscaling. Commons API per-file license metadata, revision, original dimensions and checksums are retained in `docs/verification/s48-wallpapers.json`.

- `public/launcher/namib-4k.jpg`: Keith Hardy keithhardy2001; 3872×2592, 3488606 bytes. Source: https://commons.wikimedia.org/wiki/File:Namib_Desert,_Namibia_(Unsplash).jpg (revision 1114346687). SHA-256 `24455afd8623857e7e2f6c847d4cab45390049aafd7387629f2717e49c2a55db`.
  Picker thumbnail: Commons-generated derivative downloaded unchanged from https://upload.wikimedia.org/wikipedia/commons/thumb/f/f8/Namib_Desert%2C_Namibia_%28Unsplash%29.jpg/500px-Namib_Desert%2C_Namibia_%28Unsplash%29.jpg; SHA-256 `c1edf76b2be47ef1453e6cfbbf5463cc9c212dffd23bb6ecb5c27c44b5025a63`.
- `public/launcher/fuji-4k.jpg`: J iamtheoldmanofthemountain; 4096×2731, 1510187 bytes. Source: https://commons.wikimedia.org/wiki/File:Mt._Fuji,_Fujinomiya,_Japan_(Unsplash).jpg (revision 1180488708). SHA-256 `af85a25491970757164623434ab1978f870d85bda60aab4d545d6037e9058f15`.
  Picker thumbnail: Commons-generated derivative downloaded unchanged from https://upload.wikimedia.org/wikipedia/commons/thumb/a/ae/Mt._Fuji%2C_Fujinomiya%2C_Japan_%28Unsplash%29.jpg/500px-Mt._Fuji%2C_Fujinomiya%2C_Japan_%28Unsplash%29.jpg; SHA-256 `6745460bd3015a6ac175560403ff081771582c90a1d2dddde83273eeb085e871`.
- `public/launcher/lofoten-4k.jpg`: Johannes Groll followhansi; 5059×3373, 9905230 bytes. Source: https://commons.wikimedia.org/wiki/File:Lofoten,_Norway_(Unsplash).jpg (revision 1290120845). SHA-256 `3bccf7c95c7e453f448da8f2d660737adc01eaad8a5f115767321537143136a5`.
  Picker thumbnail: Commons-generated derivative downloaded unchanged from https://upload.wikimedia.org/wikipedia/commons/thumb/8/86/Lofoten%2C_Norway_%28Unsplash%29.jpg/960px-Lofoten%2C_Norway_%28Unsplash%29.jpg; SHA-256 `2ffb65ad74904c300bbe3119c59dd91afc62e9e8d4004ea428ba63b4b366a91b`.

CC0: https://creativecommons.org/publicdomain/zero/1.0/ . Japan option is labelled Fujinomiya cloudscape; the photo does not show Mount Fuji’s summit. Research images with a visible person/pool were not selected or bundled.


## S49应用名称与生成素材

哔哩哔哩、腾讯视频、爱奇艺、优酷、芒果TV、咪咕、Netflix、Apple TV、YouTube、Disney+、Plex、Kodi及对应标志属于各自权利人。目录只用于识别本机应用/提供建议，不表示合作、官方认证或内容订阅授权。S49仅从已安装软件读取图标并使用文字占位；S52已移除文字占位，改用下列随包品牌素材。

暮色湖面、玻璃档案和玻璃燕子由内置imagegen工具按项目提示词生成，见[原图与提示词](design-assets/s49/README.md)。它们不是CC0摄影，不冒用现有四张风景摄影的署名。项目LICENSE适用于项目可授权部分，不作排他版权保证。

## S50 · 影视插件运行时

本项目的 CatVod ABI 兼容类、插件信任/下载/调用层、JS 适配脚本、Python `base.spider` 和原创测试插件为独立实现，沿用项目 MIT。协议研究读取过 `takagen99/Box` 的 AGPL-3.0 源码，但没有把其 loader、播放器或爬虫实现复制进本项目。`hjdhnx/dr_py`（仓库 LGPL-3.0）的源码只用于本机互操作验证，位于不导出的 `artifacts/drpy-reference`，没有随 APK 或源码包分发。出处、摘要和执行范围见 `docs/verification/s50-plugin-research.json`、`s50-drpy-execution.json`。

随 APK 的运行时依赖保留原许可证：Chaquopy 16.1.0 为 MIT；CPython 3.10.15 保留 Python/PSF 许可及其附带声明；jsoup 1.18.3 为 MIT；Gson 2.11.0 为 Apache-2.0。Python 包包含 requests、Beautiful Soup、lxml、pyquery、PyCryptodome、jsonpath、cachetools 及固定的传递依赖。其中 certifi 的证书源适用 MPL-2.0，lxml 及其 libxml2/libxslt、PyCryptodome 等保留各自 BSD/MIT 式或公共领域声明，不能统一改标 MIT。

完整许可、实际版本、原始来源与许可 SHA-256 放在 `android/third-party/plugins/`，随离线资源进入 APK；其中 `dependencies.json` 记录 Python target 带入的 OpenSSL、libffi、bzip2、liblzma 和 SQLite。未修改的 certifi 证书源可从对应版本 PyPI 源分发包取得：https://pypi.org/project/certifi/2026.10.23/#files 。Python target 构建配方与依赖源出处：https://github.com/chaquo/chaquopy/blob/16.1.0/target/python/build.sh 。Java 构建依赖 error_prone_annotations 2.27.0 沿用 Apache-2.0（与 Gson 同许可文本），上游 https://github.com/google/error-prone/tree/v2.27.0 。

用户另行配置的 JAR、JS、Python、规则、媒体与账号不属于开视发行物，继续适用其原许可证、版权和服务条款。提供兼容入口不表示获得第三方内容授权或官方合作认证。


## 资源文件校验工具

Ajv 8.17.1 与 ajv-formats 3.0.1 均为MIT许可，作为开发与资源包制作工具的直接依赖，版本锁定于package-lock.json。安装依赖保留各自完整LICENSE；没有将其打入Android APK，不能把依赖许可改标为自有代码。JSON Schema及本项目资源包解析、制作脚本为独立实现。


## S52 · 应用品牌图标

随包的12项品牌图片来自应用官网、官方CDN、官方源码仓库，或经过发布者名称核对的官方App Store页面。文件保持下载原字节，没有重绘、改色或生成仿制商标。完整出处、下载时间、尺寸和SHA-256见[素材清单](design-assets/app-logos/manifest.json)，选用说明见[品牌素材说明](design-assets/app-logos/README.md)。

这些品牌图片与商标属于各自权利人，**不适用本项目MIT许可证**，也不因其可公开下载而转为公共领域。仅用于识别应用和展示安装建议，不表示合作、认可、官方分发身份或内容授权。再分发及其他使用应遵循对应权利人的品牌政策；本项目不授予第三方商标权。

`public/launcher/app-icons/application.svg`为项目原创的通用应用图形，适用项目MIT。电视读取到的已安装应用图标只在本机使用；目录里的服务品牌图片不能证明装有对应的电视版应用。


## S53 · 开视新标识

应用图标使用内置生图工具按项目提示词生成，原图、提示词和派生尺寸在[素材记录](design-assets/s53/README.md)。应用图标不含生成文字，TV横幅中的“开视/爸妈版”为Noto Sans CJK排版栅格，不新增字体文件。可授权部分沿用项目MIT；不声明第三方认证或排他商标权。

## S56 · 影视资料与署名

TVmaze资料采用其API说明的CC BY-SA条件，保留来源与归属；TMDB资料/图片遵循其API条款，非本项目MIT。项目不自带共享凭证，不宣称TMDB认可或认证。署名区使用[官方批准Logo](https://www.themoviedb.org/about/logos-attribution)原SVG及按比例栅格化PNG，来源与摘要见[素材清单](design-assets/s56/manifest.json)，未生成或重绘商标。声明：This product uses the TMDB API but is not endorsed or certified by TMDB.

两张生图仅用于设计探索，未打入APK；实际测试图像复用项目既有风景素材及其原有出处。没有增加授权电影或预置影视来源。元数据解析、UI与缓存为独立实现，没有复制Box、Kodi或Jellyfin的代码。
