# 影视内页 · v19

仅适配小米电视，最低Android 8。目录、详情、选集、直播频道、播放器、来源设置、NAS文件列表和弹窗采用同一套深灰材质、暖白焦点与大字号。原生使用轻量渐变和描边，不依赖Android 12的实时模糊接口；不增加GPU模糊库。播放器进入画面后收起控制，按方向键重新显示。小爱语音键交给原系统处理。

## 封面和资料

1. 优先保留来源数据：TVBox/MacCMS的`vod_pic`、年份、类型、地区、导演、演员和简介；XML的`pic/year/type/area/director/actor/des`；M3U的`tvg-logo`；Jellyfin提供的封面与资料。
2. 开视资源包v1的节目可附加`metadata`，频道可附加`logo`。这是兼容扩展，原v1文件继续可用。更新文件应递增revision；旧开视版本会拒绝新字段，需要升级v19。Schema与CLI同步，不能把Token放进资源包。
3. 可选联网补全：先去除文件名中的分辨率、编码、季集和发行标签，保留片名及年份，再按电影或剧集查询。精确名称优先；资源明确带年份时优先该年，否则使用合理近似结果中最新的一部。**不弹匹配确认，不阻断播放。** 完全无关的结果保留空封面。需要时在详情选择“更正资料”。
4. 资料缺失、网络失败、封面损坏、过大或不支持格式时，仍可浏览、播放、收藏和追剧。来源的播放链接与观看标识不因联网匹配而改变。元数据不是播放源，也不提供影视内容。

原生入口：影视 → 来源 → 封面与资料。开启“联网补全封面与简介”；电影填写自己的TMDB **API Read Access Token**，剧集在未配置TMDB时使用TVmaze。只保存到本应用的加密本机设置。默认未开启联网，避免在用户不知情时发送片名；开启后原生NAS的影片文件名也用于匹配，但不发送文件路径、账号或密码。手动更正保留最多32项。

家庭服务可在私有运行环境中设置`APERTURE_METADATA_ONLINE=1`，需要电影资料时同时设置`APERTURE_TMDB_READ_TOKEN`。不用把值写入源码、资源包、截图或Agent提示词。浏览器只访问带身份校验的`/api/cinema/metadata/:id`和`/api/cinema/art/:id`，看不到媒体库Token或TMDB凭证；Jellyfin图片由已配置的限定媒体库代理。停用影院、撤销连接或移除媒体库后，下次资料/图片请求也会拒绝。

TMDB凭证自行申请，项目不内置共享Key，也未对用户账号做真实TMDB验收。部分外部资料服务在中国大陆可能不可达；仍以来源自带资料和原文件播放为基线，不能承诺所有中文片名联网命中。TVmaze主要覆盖电视剧，不能当成全量中文电影库。名字误匹配只影响显示资料，用户可更正为来源资料。

## 文件示例

节目清单中的单个item：

```json
{
  "id": "family-film",
  "title": "家庭影片",
  "metadata": {
    "poster": "https://media.example/poster.jpg",
    "year": "2025",
    "genre": "纪录片",
    "summary": "一段旅程。"
  },
  "episodes": [{"title": "播放", "url": "https://media.example/film.mp4"}]
}
```

资源地址仅为文档示例，不是可用影视源。完整文件结构与Agent制作路径见[资源规范](RESOURCE-PACK.md)和[WorkBuddy资源指引](RESOURCE-PACK-AGENT.md)。

## 图片与资源预算

原生最多2个下载线程，队列32，单图4MiB、解码采样到约900×1200，内存图片缓存12MiB、私有磁盘缓存32MiB。目录离开或Activity销毁时撤销旧请求/回调。图片不携带来源或NAS凭证，不跟随重定向；使用HTTPS，只有明确允许HTTP的来源可读取HTTP封面。家庭服务图片并发最多8，缓存32MiB；只允许已配置Jellyfin或资料服务明确的图片域，不提供任意URL代理。缓存有界，不等于APK变大。

原生资料查询为单线程、间隔至少900ms、缓存96项/24小时；家庭服务128项/24小时。用户主动更正保存在本机加密设置；家庭服务浏览器的更正仅在当前页面保存，不声称跨设备同步。

## 参考与归属

- [TVBox/Box](https://github.com/takagen99/Box)：参考来源、目录、线路、选集与历史的分层，不复制其UI或代码；现有TVBox插件兼容范围另见[插件引擎](PLUGIN-ENGINE.md)。
- [Jellyfin metadata](https://jellyfin.org/docs/general/server/metadata/)：优先媒体库既有资料，避免只依赖单一互联网服务；其文档明确说明部分服务在中国大陆可能不可达。
- [Kodi NFO](https://kodi.wiki/view/NFO_files)：参考本地元数据优先原则；**本版没有新增NFO sidecar扫描**。
- [TVmaze API](https://www.tvmaze.com/api)：结果和图片按CC BY-SA注明来源。详情保留归属链接，不把其数据改称项目原创。
- [TMDB authentication](https://developer.themoviedb.org/docs/authentication-application)、[attribution](https://developer.themoviedb.org/docs/faq)：采用用户自己的Read Access Token；设置/网页署名区使用官方Logo及要求的声明。

[生图参考、提示词与实际取舍](../design-assets/s56/README.md) · [浏览器任务流](verification/s56-cinema-browser.json)。截图为本地真实HTTP服务上的合成电影与资料，不是小米真机截图；原生API编译和JVM协议测试不能代替电视遥控实测。
