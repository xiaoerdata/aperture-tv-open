# 影视插件

开视可以在电视内运行用户配置的 CatVod JAR、JavaScript 和 Python 插件。插件负责获取分类、搜索结果、剧集和播放地址；开视负责选集、播放、续播、收藏与追剧记录。项目不附带影视供应商、付费内容账号、直播订阅或第三方资源仓库。

只面向小米电视，Android 8 及以上。下面的协议验证不代表全部小米型号、全部 WebView 版本和全部网络来源均已验收。

## 配置与使用

1. 进入「看电影 / 看电视剧 → 来源」，导入自己的 TVBox 配置 URL。配置可同时包含直播、XML/JSON 站点与插件。
2. 首次打开插件站点时，开视下载代码并计算 SHA-256，显示来源、类型和摘要。下载和保存配置不会执行插件；选择「信任并运行」后才会初始化。
3. 用遥控器进入分类、搜索、选集。插件返回可播放地址后，使用内嵌播放器。观看记录保存片目与集数标识，续播时重新获取地址，不长期保留临时播放链接。
4. 来源设置中的「检查插件更新」清除本次运行实例，下次打开重新下载。代码、已声明 JS 依赖或配置变化，会产生新摘要并重新询问。
5. 「撤销插件授权」撤销本机所有插件的后续执行许可。清空观看记录、删除来源和撤销插件许可是不同操作。

以下只演示字段，不是可用资源地址：

```json
{
  "spider": "https://example.test/plugins/fixture.jar",
  "sites": [
    {"key":"own-jar","name":"自己的 JAR","type":3,"api":"csp_Fixture","ext":"自有 "},
    {"key":"own-js","name":"自己的 JS","type":3,"api":"https://example.test/plugins/catalogue.js","ext":"自有 "},
    {"key":"own-py","name":"自己的 Python","type":3,"api":"https://example.test/plugins/catalogue.py","ext":"自有 "},
    {"key":"own-t4","name":"自己的 T4","type":4,"api":"https://example.test/api","ext":"自己的扩展参数"}
  ]
}
```

JAR 可以用站点的 `jar` 覆盖全局 `spider`，支持 `地址;md5;32位摘要` 的现有约定。MD5 只用于检查来源声明是否匹配；本机信任标识使用 SHA-256。相对 URL 按配置地址解析。HTTP 需在导入时明确允许，默认使用 HTTPS。

## 实际执行方式

| 类型 | 执行方式 | 已接入调用 |
| --- | --- | --- |
| `type:3`、`csp_*` | Android `DexClassLoader` 加载包含 `classes.dex` 的 JAR | `init`、`homeContent`、`homeVideoContent`、`categoryContent`、`detailContent`、`searchContent`、`playerContent` |
| `type:3`、`.js/.mjs` | 本机 WebView 的 ES module / Promise 引擎 | `init`、`home`、`homeVod`、`category`、`detail`、`search`、`play`；支持 `export default` 对象/函数和 `__jsEvalReturn` |
| `type:3`、`.py` | APK 内 Chaquopy / CPython 3.10 | `Spider` 类的同名 CatVod Python 调用；返回 Python 字典或 JSON 字符串 |
| `type:4` | 服务端 HTTP API | 目录、详情、搜索，以及 `play` / `flag` / `extend` 获取最终播放 JSON |

JAR 的 `com.github.catvod.crawler.Spider` 与 `SpiderDebug` 是本项目独立实现的兼容调用层。插件自带的内部库、原生库和特定 TVBox 分支私有 API，并不会自动成为开视的一部分。缺少依赖会显示错误，而不是默默展示空页面。

JavaScript 支持同步 `req/request/http`、会话内 `local`、Base64、MD5、`joinUrl` 和 HTML 选择辅助函数。静态声明的相对模块会一起下载、计入摘要；最多 24 个模块、8 层依赖。`assets://js/lib/名称.js` 按主脚本同目录的配套模块解析，也计入确认。应提供源码模块和完整依赖，不要把其他 TVBox APK 自带资源当成已安装库。

已在桌面 Chromium 中，使用真实上游 `hjdhnx/dr_py` 的 `drpy2.js`、配套模块和本项目原创规则，通过生产 JS 适配层执行分类、详情、搜索和播放调用。这是桌面互操作验证，不作为 Android WebView 上实际运行 drpy 的验收。上游测试源仅存在开发环境的忽略目录，未打包进开视；接入实际规则时仍需用户自行提供并确认来源。

Python 提供本项目的 `base.spider.Spider` 与 HTTP、HTML、缓存辅助函数；APK 包含 requests、Beautiful Soup、lxml、pyquery、PyCryptodome、jsonpath 与 cachetools。已公开v18包含ARMv7、ARM64、x86、x86_64四种ABI；下一版源码默认保留ARMv7与ARM64，x86/x86_64通过显式构建参数用于模拟器开发，Python依赖不删减，见[体积说明](APK-SIZE.md)。其他第三方 Python 包和厂商私有 Java 绑定，需要插件作者另行适配，不在电视端静默安装。

## 明确的边界

- 这不是对所有 TVBox 分支、全部网络仓库或每个插件的兼容承诺。引擎能执行代码，仍需插件匹配调用约定及运行依赖。
- 播放结果目前要求 `parse:0`、`jx:0` 的 HTTP(S) 媒体地址。`parse/jx` 网页解析、网页嗅探、DRM 登录流程和解析器链没有接入，不会伪装成已经播放成功。
- 不执行 `//DRPY`、`//bb` 等 QuickJS 预编译字节码；应使用 `.js` 源码。某些旧 WebView 不支持 ES module，需要先升级系统提供的 WebView；JAR/Python 不依赖 JS module 支持。
- 插件的 `proxyLocal` / `proxy` 本地转发服务、TVBox `jsapi` 自定义 Java 桥、动态远程 `import()` 依赖发现，尚未接入。依赖这些能力的来源会失败；不能把“支持 JavaScript”解释成包含所有宿主扩展。
- JS `local` 当前只保留本次运行的数据。JS 网络桥支持 GET、POST、HEAD 与有界响应，提供的请求参数集合不等同于全部 TVBox 请求选项。
- 追剧更新为用户在前台触发检查，不是后台实时推送。插件提供的集数、标题和线路变化会影响更新判断。

## 信任与运行范围

第三方插件是可执行代码，尤其 JAR 和 Python 与开视共享 Android UID，能够利用应用已有权限访问网络和应用数据。这不是隔离沙箱，也不能仅因为下载地址在局域网就认为可信。开视不把 NAS、家庭服务或 Hub 凭证作为插件参数，但同 UID 代码仍可能自行读取应用数据。

信任记录绑定代码、配置及声明依赖的摘要，不是永久信任某个 URL。JAR 最大 16 MB，单脚本最大 2 MB，响应最大 512 KB。代码文件写入内部缓存并设为只读，执行前再次核验入口文件摘要；JS 模块请求只供应已下载的模块。运行结果仍经过目录、字段、URL 和请求头校验。

JS 和原生插件调用均有 20 秒等待上限。首次运行 Python 前，开视会另用最多 60 秒启动 APK 自带的 CPython，并预载固定的 `kaishi_plugins`、`base.spider` 和 `requests` 模块；这一步不读取或执行用户 `.py`，不创建用户 Spider，也不访问插件指定的 URL。用户脚本加载、`Spider.init` 和本次业务方法仍共用原来的 20 秒。

这份内置准备在应用进程内共享，只启动一次。返回、切页、按 HOME 离开或撤销授权会停止当前调用的等待；不会取消其他调用共用的内置准备。准备完成后，以及用户代码进入执行队列时，都会重新检查本次页面是否有效、插件是否仍被允许执行。内置准备失败和超时有独立提示，只阻止 Python，不影响 JAR、JS 或普通 HTTP 目录；需退出应用进程后重试，不会并发重复启动 CPython。

退出页面会丢弃旧结果，关闭模块和撤销许可会请求取消。Java/Python 插件若忽略中断，不能保证已开始运行的代码被终止；同样不能强行终止卡住的 CPython 内置初始化线程，但超时后不会继续执行用户插件。遇到不响应的插件，应退出应用并移除来源。应用不声称具备操作系统级的恶意代码隔离。

JS 引擎在初始化前读取电视实际选用的 WebView 版本。宿主需要 Chromium / WebView 61 或以上的 ES 模块能力；Android 8 并不保证 WebView 达到此版本。旧版或无法识别的 provider 会立即显示具体要求，不等待 20 秒超时。此限制只影响 JS 插件，JAR、Python 和普通 HTTP 目录仍可使用。可检查小米系统或官方 WebView 更新；不自动替换系统组件。插件自身如果使用较新的 JavaScript 语法，可能需要更高版本，61 只是宿主最低要求，不表示所有 drpy 规则均兼容。

## 开发与复现

- `scripts/prepare-plugin-fixture.mjs`：编译本项目原创 `csp_Fixture`，生成同时含 `.class` 与 `classes.dex` 的测试 JAR；不构建产品 APK。
- `scripts/check-plugin-execution.mjs`：用生产 ABI、Python runner、JS 页面与适配脚本实际执行三个自有插件的五项业务调用，并在 JVM 执行共享准备的并发等待、取消隔离、授权重检与失败/超时验证。该协调测试不启动 APK 内 CPython。
- `scripts/fetch-drpy-reference.mjs` 按测试清单校验下载；`scripts/check-drpy-reference.mjs` 使用 `artifacts/drpy-reference` 中独立准备的上游源码和原创规则运行互操作检查。所用文件的 URL 与 SHA-256 在 `docs/verification/s50-drpy-execution.json`。
- `scripts/emulator-plugin-fixture.mjs`：供 Android 仪器验收使用的本机 HTTP 夹具。模拟器结果与 Chromium/JVM 结果分开记录，不替代小米真机测试。

Android 构建固定 Chaquopy 16.1.0 与 Python 3.10（当前 target 3.10.15），直接与传递 Python 依赖都固定版本。需要缓存下载时可设置 `KAISHI_PLUGIN_WHEELHOUSE` 指向已经检查过的本机 wheel 目录；默认仍从官方索引解析固定版本。Chaquopy 的 pip 使用 `--isolated`，因此不能用 `PIP_FIND_LINKS` 代替这一显式构建参数。

### r6 Android 实际运行记录

`versionCode 17` 的标准版与爸妈版分别运行了本项目原创插件。每个运行时的 15 项检查包含未授权拒绝、分类/详情/搜索/播放地址调用、中文与分页参数、请求头、模块变更后重新批准，以及撤销后的下一次调用拒绝。

| 实际环境 | 标准版 | 爸妈版 |
| --- | --- | --- |
| API 26 / x86，JAR | [15 项通过](verification/s50-plugin-standard-jar-r6.json) | [15 项通过](verification/s50-plugin-parents-jar-r6.json) |
| API 26 / x86，APK 内 CPython | [15 项通过](verification/s50-plugin-standard-python-r6.json) | [15 项通过](verification/s50-plugin-parents-python-r6.json) |
| API 28 / x86，官方 WebView 66.0.3359.158，JavaScript | [15 项通过](verification/s50-plugin-standard-javascript-r6.json) | [15 项通过](verification/s50-plugin-parents-javascript-r6.json) |

两版 JavaScript 回执同时记录了实际安装 APK 的 SHA-256，均与对应 r6 候选一致。测试没有预温 Python 或 JavaScript，也没有改变产品调用超时。标准版与爸妈版 JS 首次 `home` 分别用时 18,906 与 11,875 毫秒；这是无硬件加速开发模拟器的观测，不代表电视速度或流畅性。API 26 的 WebView 58 另验证了 [明确拒绝 JS 的分支](verification/s50-plugin-standard-old-webview-r6.json)，其中 `executed=false`，不能算作 JS 执行通过。

这些插件回执验证原生引擎与返回 JSON，均保留 `mediaPlayed=false`；不以播放地址返回成功代替媒体解码。它们也不代表全部 drpy 规则、第三方仓库、小米型号或 NAS 品牌已通过。模拟器启动、密钥路径修正与首次系统编译过程见 [API 28 环境记录](verification/s50-api28-development-issues.json)，测试报告元数据失败及修复见 [回执收集记录](verification/s50-plugin-report-metadata-issue.json)。小米真机、WorkBuddy 与实际来源仍需对应环境验收。

## 来源和许可

调用约定研究：`takagen99/Box`（AGPL-3.0）。没有复制其 JAR/JS/Python loader、播放器或爬虫实现。drpy 互操作参考：`hjdhnx/dr_py`（仓库许可证 LGPL-3.0）；未作为随包源分发。出处与逐文件摘要见 `docs/verification/s50-plugin-research.json`。

本项目自写引擎与原创测试夹具沿用项目 MIT；依赖保留各自许可，不能统一改标 MIT。见根目录 `THIRD_PARTY_NOTICES.md`。用户自行配置的插件、规则和媒体另受其来源许可与服务约定约束。
