# 生活连接：来源、用法与二次开发

开发核查：2026-10-23 至 24。当前是 **14 个来源目录 + 6 个生活用法 + 通用只读 MCP 消费框架**，不是14个真实账号已连接。个人数据的流程是：服务端受控读取 → 管理员手机预览 → 默认不选中 → 选择分享 → 家庭屏快照。没有自动控制家电。

## 已选来源

| 来源 | 适合的家庭用途 | 许可与接入判断 |
|---|---|---|
| [HA 官方 MCP](https://www.home-assistant.io/integrations/mcp_server/) | 已暴露的家庭实体 | HA Core Apache-2.0；须按实际 API 工具映射 |
| [社区 ha-mcp](https://github.com/homeassistant-ai/ha-mcp) | 家居状态、家庭日历、能耗 | MIT；仅启用审阅过的只读工具 |
| [小米 HA 集成](https://github.com/XiaoMi/ha_xiaomi_home) | 米家 → 用户自己的 HA | 非 MCP；小米自定义非商业许可，不随本项目内置/分发 |
| [mijia-api](https://github.com/Do1e/mijia-api) | 米家直接读取候选 | GPL-3.0，README 另有非商业限制；暂缓默认推荐 |
| [和风 MCP](https://github.com/fengyucn/hefeng-qweather-mcp) | 天气、降雨、空气、预警 | 已取得小写 license 文件，MIT；数据服务另有配额/条款 |
| [高德 MCP](https://github.com/sugarforever/amap-mcp-server) | 通勤、周边、目的地 | MIT；需高德 Key |
| [12306 MCP](https://github.com/Joooook/12306-mcp) | 车次与余票查询 | MIT；社区项目，不自动订票支付 |
| [滴答清单](https://github.com/Martinqi826/dida-mcp) | 中国版当天家庭任务 | MIT；需应用及 OAuth |
| [飞书官方 MCP](https://github.com/larksuite/lark-openapi-mcp) | 授权的家庭日历 | MIT；国内域名 open.feishu.cn，限定账号与日历范围 |
| [Memos](https://github.com/mambucodev/mcp-memos) | 家庭标签、日记摘要 | 桥接 MIT；自托管，先核对实例版本 |
| [思源](https://github.com/Fromsko/siyuan-mcp-server) | 选定笔记、阅读记录 | 桥接 ISC；避免通用 SQL/API 工具 |
| [Immich](https://github.com/mattmaas/immich-mcp) | 相册标题、往年回忆元信息 | 桥接 MIT；本版只映射文本/数值，图片仍走上传 |
| [Mealie](https://github.com/timo-reymann/mealie-mcp-server) | 今日菜单、已有菜谱 | 桥接 MIT；需自己维护食谱 |
| [Grocy](https://github.com/attmous/grocy-mcp) | 库存、临期食物、家务 | 桥接 MIT；库存不等于自动冰箱识别 |

一手 README/许可证链接与 blob 标识见 integration-evidence.json。可境内部署不等于已经从大陆网络测试；GitHub、容器、模型的初次获取是独立条件。第三方库及数据服务各自的许可、价格、API 权限不被本项目 MIT 覆盖。

## 无需任何厂商账号的可复现演示

```bash
npm ci
npm run demo:life
```

打开终端显示的本地地址。使用明确标注的演示口令登录，进入「生活连接」，读取示例家庭，勾选一项分享，再配对另一浏览器的电视页。该脚本在独立临时目录启动服务，数据来自本地真实 MCP 协议 fixture；停止后清理。不是米家或天气账号验收。

## 连接一个真实来源

1. 在电脑/NAS 单独安装可信 MCP 上游，固定其版本，完成上游账号授权。家庭服务不执行网页给出的安装命令。
2. 在私有目录保存 aperture-connections/v1 配置，使用绝对可执行文件路径（stdio）或可信固定地址（Streamable HTTP）。凭证使用环境变量引用，不放浏览器。
3. 审阅实际 tools/list 和工具实现，选取精确工具名与固定参数。将 readOnlyReviewed 设为 true 代表部署者已经审阅；上游 readOnlyHint 不是独立安全保证。
4. 配置 query.tiles 的 valuePath 和可选 observedAtPath。路径为点分隔对象字段/数组索引，例如 data.state、tasks.0.title。仅标量可映射，不执行表达式。
5. 用私有配置启动：APERTURE_CONNECTIONS=/private/connections.json npm start。工作台自动显示已配置的固定查询。
6. 在手机检查值、来源、时间，再勾选分享。字段缺失或格式错误会单独报告；无有效字段时拒绝发布。

配置结构示例（所有路径为示意，请与本机实际工具核对）：

```json
{
  "format": "aperture-connections/v1",
  "sources": [{
    "id": "my-home",
    "catalogId": "ha-community",
    "label": "客厅传感器",
    "transport": "http",
    "url": "https://your-ha-mcp.example/mcp",
    "bearerEnv": "APERTURE_HA_MCP_TOKEN",
    "readOnlyReviewed": true,
    "readTools": ["ha_get_state"],
    "queries": [{
      "id": "temperature",
      "label": "读取客厅温度",
      "tool": "ha_get_state",
      "arguments": {"entity_id": "sensor.living_room_temperature"},
      "tiles": [{
        "id": "temperature",
        "title": "客厅温度",
        "valuePath": "data.state",
        "unit": "°C",
        "category": "home",
        "ttlSeconds": 900
      }]
    }]
  }]
}
```

上述示例是字段映射模板，未用真实 HA 验证。HA 官方 MCP 与社区 ha-mcp 的路径、鉴权、工具和返回封装可能不同，不能混用。没有 observedAtPath 时，界面会写“读取时间；源时间未知”，不会将服务器抓取时间伪装成传感器观测时间。可以用 scripts/inspect-life-source.mjs 查看实际结果的字段类型，不打印字段值。

stdio 配置将 transport 改为 stdio，使用 command、args、可选 cwd 和 envFrom，例如 envFrom: {"MEMOS_API_TOKEN":"APERTURE_MEMOS_TOKEN"}。SDK 只继承基础系统环境及显式映射；stdio 上游仍是可信本机程序，部署者必须审阅并使用最小权限账户。本版本无 OAuth 授权浏览器、旧 SSE transport、资源读取、图片结果解析或上游通知订阅。

## 限制与隐私

最多24来源，每来源10固定查询、20工具白名单，每次6字段；最多2并发读取、12次/分钟；结果64KB，传输缓冲256KB；预览16份、5分钟有效、只属于发起的管理员会话。每个工具请求10秒，整个读取12秒。分享最多6项，TTL 60秒至24小时。

完整上游结果只在请求内存中存在；原始字段不发送电视。分享通过现有领域服务的 revision + execution.key 写入，持久化回执最多500项。预览过期后不能用同一预览继续发起操作，结果不明时先读取家庭屏确认，不能换键盲试。

接口与适配层：server/life/config.mjs、mcp.mjs、service.mjs。目录与用法：public/life/catalog.js。UI：public/life/studio.js，TV：public/life/tv.js。上游工具说明、图片、HTML 和脚本都作为不可信数据处理。

微信运动、小米运动健康等未获得可核实的通用官方 MCP 路径，因此没有伪造“已支持”；个人健康指标先通过手动/JSON 选择性录入。后续自动刷新需增加持续共享授权、调度、撤销和数据保留策略，不能直接把本版手动预览改成整库抓取。

