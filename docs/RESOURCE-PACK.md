# 开视资源包 v1

公开 v20（0.2.0-alpha.17）已支持资源包 v1，以及片单 `metadata.poster/year/genre/area/director/actors/summary` 和频道 `logo`。本文同时描述随 v21（0.2.0-alpha.18）提供的 S60 扩展，不能直接交给 v20 APK；下载状态以发行页面为准。

一个 UTF-8 JSON 文件，建议以 `.kaishi.json` 结尾。用户或自己的 Agent 可以制作文件、发布到可直接读取的 HTTPS 地址，再一次导入。原有 TVBox、M3U/TXT 和 XML/JSON 导入入口继续使用。

- [JSON Schema](contracts/kaishi-resource-pack-v1.schema.json)
- [混合资源完整示例](../examples/resource-packs/family.kaishi.json)
- [TVBox 适配示例](../examples/resource-packs/tvbox.kaishi.json)
- [Agent 制作与导入指南](RESOURCE-PACK-AGENT.md)

示例的 `example.invalid` 地址均为占位地址，需替换后才能播放。

## 一个文件可以放什么

| `type` | 内容 | 打开方式 |
| --- | --- | --- |
| `live` | 一个直播清单 URL，或带稳定频道 ID 的多线路频道列表 | 看电视 |
| `catalog` | MacCMS JSON、MacCMS XML、TVBox type4 点播 API | 按 `sections` 与 `categoryIds` 进入电影、电视剧、综艺 |
| `collection` | 直接编排的节目及各集 HTTP(S) 播放地址 | 按 `section` 进入对应入口，无需另建目录 API |
| `tvbox` | 单个 TVBox 配置对象，兼容现有 sites/lives 与插件引用 | 复用现有 TVBox 引擎及插件确认流程 |
| `epg`（v21新增） | XMLTV XML 或 gzip 压缩节目单 | 直播侧栏的当前/下一节目与今天、明天节目表 |

第一版明确覆盖媒体资源。壁纸、相册、信息面板、NAS账号与应用安装不属于这个文件格式；未知类型会报错。后续新增能力须更新规范、解析器和对应授权，不能用扩展字段偷偷执行操作。

综艺在标准版和爸妈版均为一级入口。原生目录优先使用 `categoryIds.variety` 的分类编号；没有编号时识别“综艺、真人秀、脱口秀、晚会、variety”等名称。没有匹配分类时会说明原因，不把全部电影和电视剧塞进综艺。专门的单分类站点可声明 `sections:["variety"]`。

## 最小文件

```json
{
  "format": "kaishi.resource-pack",
  "version": 1,
  "id": "org.example.family",
  "revision": 1,
  "title": "家里的片单",
  "resources": [
    {
      "id": "weekend",
      "type": "collection",
      "title": "周末综艺",
      "section": "variety",
      "items": [
        {
          "id": "family-show",
          "title": "家庭舞台",
          "episodes": [
            {"title": "第1期", "url": "https://media.example.invalid/show-01.mp4"}
          ]
        }
      ]
    }
  ]
}
```

`format` 固定为 `kaishi.resource-pack`，`version` 固定为整数 1。`id` 是资源包稳定编号（最多96字符），`revision` 是1至2147483647的递增整数。`title` 最多120字符；可加 `description`（500字符）和 `author`（120字符）。完整字段以 Schema 为准，普通资源对象不接受未知字段。

资源和节目 ID 最多64字符，使用小写字母、数字、点、短横线与下划线，以字母或数字开头。资源 ID 在同一包中唯一；频道 ID 在同一直播资源中唯一；节目 ID 在同一清单中唯一。ID代表同一份内容，不把旧ID重新分配给另一站点或另一节目。

## 点播站点与分类

```json
{
  "id": "my-api",
  "type": "catalog",
  "title": "我的点播站",
  "protocol": "maccms-json",
  "url": "https://catalog.example.invalid/api.php/provide/vod/",
  "sections": ["movies", "series", "variety"],
  "categoryIds": {"movies": ["1"], "series": ["2"], "variety": ["3"]}
}
```

`protocol` 可选 `maccms-json`、`maccms-xml`、`tvbox-type4`。分类编号是上游真实返回的文本编号，不能凭不同站点常用编号猜测；先读取该站分类再填写。`categoryIds` 的每个键都需在 `sections` 声明。

`collection` 的 `section` 只能是 `movies`、`series` 或 `variety`。节目有稳定 `id`、`title`，可选 `remark`。每集包含唯一 `title`、直接媒体 `url` 和可选 `headers`。结构化集名可以包含 `#`、`$`，无需使用 TVBox 播放列表的分隔符拼接。

## 片单封面与详情

v20 可直接在节目内填写 `metadata`。此前说明漏列了这些字段，并非只能填写 `id/title/remark/episodes`：

```json
{
  "id": "holiday",
  "title": "假日短片",
  "metadata": {
    "poster": "https://images.example.invalid/holiday.jpg",
    "year": "2026",
    "genre": "家庭短片",
    "area": "中国",
    "director": "实际署名",
    "actors": "实际署名",
    "summary": "由资源作者填写的实际简介。"
  },
  "episodes": [{"title": "正片", "url": "https://media.example.invalid/holiday.mp4"}]
}
```

v21 新增：`metadata.backdrop`、`duration`、`rating`、`ratingSource`；也接受节目顶层 `cover`（等同 `metadata.poster`）、`plot`（等同 `metadata.summary`），以及 `year/area/genre/actors/director/rating/ratingSource/duration/backdrop`。同时填写嵌套字段与对应顶层字段时必须相同，否则拒绝导入。每集可选 `cover` 为数据预留，当前选集仍以文字为主，不承诺逐集缩略图。

`rating` 是来源提供的文本，不能编造评分或匹配百分比；有出处时填写 `ratingSource`。海报应使用有权使用、可直接读取的图片地址。没有图片时采用片名占位；不把无图节目伪装成已匹配海报。详情的“同一片单 / 同一来源”来自实际内容，不保证固定推荐数量。

可在任一观看入口 → 来源 → 封面与资料，选择是否联网补全。默认关闭；开启后会发送片名和年份，电影还需用户自己的 TMDB Read Access Token。真实账号、网络可达性与匹配结果需另测，不能保证海报命中率。

## 直播与请求头

`live` 必须选择 `url` 或 `channels` 之一。`url` 指向直播清单；`channels` 每项含 `id`、`title`、可选 `group`、1至8个 `urls` 和可选 `headers`。资源级请求头作为默认值，频道或剧集级值覆盖同名项。

原生资源的公开请求头限定为 `Accept`、`User-Agent`、`Referer`、`Origin`、`Content-Type`，最多8项，每值最多1024字符。禁止换行和身份凭证请求头。资源包里不放账号密码、Cookie、账户令牌或 NAS 凭证；带私人签名参数的地址也不适合公开发布。

地址必须是绝对 HTTP(S) URL，不能含网址账号或 `#fragment`。默认仅接受 HTTPS。HTTP媒体需用户在本次导入中明确允许，文件不能自行打开此权限。Agent交接的资源包文件地址始终要求HTTPS。

## 节目单与频道号（v21新增）

在同一资源包中增加以下资源，并在频道上设置 `epgId`、`number`：

```json
{"id":"guide","type":"epg","title":"我的节目单","url":"https://guide.example.invalid/tv.xml.gz"}
```

频道 `epgId` 匹配 XMLTV 的 channel id；未指定时才按频道名尝试匹配。`number` 为 1–9999，同一直播资源内不重复；未指定时分配不占用明确编号的顺序号。M3U 支持 `tvg-id`、`tvg-chno` 和 `tvg-logo`。编号范围为当前所选直播来源，不跨不同来源猜测频道身份。

每包最多4个节目单；不能只导入节目单而没有可播放资源。单次传输不超过4MiB，解压不超过8MiB；拒绝 DTD、实体、过深嵌套与超量节目。节目时间支持 `yyyyMMddHHmmss +0800`，缺省时区为中国时区。没有有效节目单时显示空态，视频请求独立进行。应用不内置互联网频道或节目单地址。

原生播放时，控制栏隐藏后按确定打开频道侧栏，左右切分组、上下预览、确定换台、返回关闭；5秒无操作收起。数字输入后按确定或等待约1.2秒跳台。“节目单”打开同窗口的今天/明天时间表，不启动第二路视频。当前是本地实现，遥控器焦点、时序和机型性能仍待原生运行复测。

## TVBox兼容

继续直接导入原有 TVBox 文件，或使用 `type:"tvbox"` 把单个配置放在 `config` 中。相对地址可通过 `baseUrl` 指定原配置的绝对URL。可额外声明 `sections` 和 `categoryIds`。

资源包不自动展开多仓：先从原多仓选出一个具体配置，再包装。适配器接受现有引擎支持的0/1/3/4型站点与直播。未接入的站点类型、非空网页解析器链等会使该资源包导入失败，并说明原因；完整兼容范围见[插件说明](PLUGIN-ENGINE.md)。TVBox原生字段不等于新增开视资源能力，适配器只取现有媒体引擎能使用的内容。

导入只保存插件引用。JAR、JavaScript、Python仍经过原有代码摘要与信任确认；资源包中的字段不能授予插件权限。

## 更新、记录与原子导入

同一资源包保持 `id`，更新时增加 `revision`。同一编号与同一修订号、相同原始文件摘要及HTTP选择再次导入，作为无变化处理；同一修订号内容不同会冲突；较旧修订号会被拒绝。本机HTTP选择变化会单独保存，无需改变文件修订号。修改排版也改变原始文件摘要，发布新文件时同样增加修订号。

原生站点/清单的来源身份由包ID与资源ID组成，频道、节目使用稳定ID。更新媒体地址或节目显示名称时可保留收藏与进度。续播还使用线路名和集名；直接清单使用固定线路“播放”，因此已发布的集名需保持稳定，新增集数直接追加。TVBox适配器沿用旧引擎的来源身份，不强行合并不同来源的历史。

一次导入替换本机直播与影视来源，观看记录、收藏与NAS连接保留。先完整解析、校验及预览，再加密保存一个配置记录；任何资源不支持、重复ID、超限或版本冲突都不会保存半份配置。保存时再次检查当前资源包修订号，避免预览后的并发覆盖。

每文件最多512KiB，最多24个资源；展开后最多12个点播来源、12个直播来源、500个内置频道、200个直接节目、500集。规范化存储后也需在512KiB以内。远端直播清单继续执行原有频道数量限制。

## 校验与导入

在仓库运行 `npm ci`，然后：

```bash
npm run resources -- validate examples/resource-packs/family.kaishi.json
npm run resources -- template --output my-family.kaishi.json
npm run resources -- validate my-family.kaishi.json
```

校验输出包ID、修订号、资源数量及原始文件SHA-256。命令行校验覆盖 Schema、ID、分类、地址与汇总限制；TVBox适配内容与规范化大小仍由电视的同一生产解析器做最终校验，输出中保留 `nativeImportValidationRequired:true`。

电视进入任一观看入口 → 来源 → 导入本机文件，或填写文件的HTTPS直链。预览会显示包名、修订号、数量和SHA-256，确认后保存。“来源 → 当前资源包”可核对本机已保存的编号、修订号和摘要。Agent自动交接见[制作与导入指南](RESOURCE-PACK-AGENT.md)。

## 验证边界

v20 已发布，不修改其既有标签和附件。S60修复进入v21候选，发布状态以下载索引及发行回读为准；[修复对照](V20-FEEDBACK-S60.md)区分生产解析器、HTTP、浏览器和未完成的原生/小米验收。协议测试使用合成资源；用户报告中的实际资源包尚未取得，分组重复与真实海报命中问题仍需原文件复现。WorkBuddy客户端、真实账号和小米系统生态仍按既定分工由用户复测。
