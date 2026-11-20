# S121 · 按文件大小管理资源容量

对应 P135。2026-11-20完成源码与定向验证，随后按P136纳入[V47](RELEASE-V47.md)双版发行；发布状态以发行页和回读证据为准。V46旧APK的行为没有变化。标准版与爸妈版共用本轮解析器。

## 当前开发规则

- 原始文件最多4 MiB（4,194,304字节），按UTF-8字节计算，边界值允许。
- 展开后的本机配置也最多4 MiB，先检查再预览/保存；加密存储保留相同预算。共享字段复制到多个站点后可能变大，不能只检查下载文件。
- 取消资源总项数、点播站点、直播来源、多仓入口、频道、片单节目和直接集数的导入数量上限。支持的条目完整解析，不因超过12个来源而跳过；保存后重新读取也不截断。
- TVBox（含资源包内适配器）、开视资源包、M3U和TXT统一采用上述规则。多仓入口用于选择一个配置，不自动把全部仓库拉取合并。
- 保留结构、字段长度、地址、ID唯一性、修订号校验。栏目枚举/分类映射、请求头、单频道8条备用线路、4个节目单来源、远程单页1000条/最多10000页等既有协议与运行保护继续保留；没有在本轮取消网络/解压/播放运行预算。

制作者运行 `npm run resources -- validate 文件.kaishi.json`。超过V46旧数量配额时，输出最低 `minimumVersionCode:47`。这是V47及以上安装要求，不能当作V46已支持，也不能把Schema通过当作电视最终导入成功；保留 `nativeImportValidationRequired:true`，由生产解析器检查TVBox内容和规范化大小。

## 验证

```bash
npm ci --ignore-scripts --no-audit --no-fund
APERTURE_NAS_TEST_RUNTIME="$PWD/.android-local/nas-tests" node --test tests/resource-capacity.test.mjs
APERTURE_NAS_TEST_RUNTIME="$PWD/.android-local/nas-tests" node --test --test-name-pattern='resource schema|Agent can verify' tests/resource-pack.test.mjs
python3 scripts/s121-resource-regression.py
```

Java使用既有本地JDK及缓存依赖，无Gradle构建或APK产物。容量用例使用测试专用org.json库；原有导入/HTTP回归另外使用缓存20160810版org.json。脚本在退出时清理临时类文件和资源，不留下数MiB的合成资源包。

- 14组完整容量/边界夹具：36站点＋36直播来源、240站点、144直播来源、193多仓入口、TVBox适配、6001频道（标准、TVBox、M3U、TXT）、6001节目、30001集；覆盖AES加密后重新读取与最后来源身份、直接选集不丢失。
- 4 MiB准确边界通过；多1字节明确拒绝；输入未超限但共享字段展开后超过4 MiB也明确拒绝。
- 既有1200部节目持久化/分页、远程第501页和远程单页超量保护继续通过。
- 原有45项资源包检查和51项解析/HTTP/加密检查通过；制作工具Schema/CLI及本地HTTP下载摘要/超量拒绝通过。
- 容量Java进程用 `-Xmx256m` 执行。这仅是合成协议用例的进程预算，不是电视内存峰值测试或最大资源量承诺。

首次执行因清理后的工作区没有ajv依赖而失败，按锁文件安装依赖后通过。没有更改依赖版本或为通过测试调整生产校验。本轮不涉及UI改版，没有新界面截图；没有小米真机遥控、原生渲染或互联网资源站验收。

[容量证据](verification/s121-resource-capacity.json) · [原有回归](verification/s121-resource-regression.json) · [日志](process/JOURNAL.md) · [传播编辑稿](../media/DEVLOG-094.md)
