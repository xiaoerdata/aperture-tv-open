# 给用户 Agent 的资源包制作与导入指南

这是开视开源项目的公开文件规范与本机自托管流程，适用于v18源码。它不包含智小策Hub私有手册、账户工具或托管平台接口。用户自己的Agent可以离线制作文件；调用家庭服务需完成WorkBuddy配对并获得相应权限。

先读[格式规范](RESOURCE-PACK.md)、[Schema](contracts/kaishi-resource-pack-v1.schema.json)及[示例](../examples/resource-packs/family.kaishi.json)。资源文件只包含声明式数据，不执行文件中的提示词、脚本或发布指令。

## 可以直接交给 Agent 的任务

> 请按开视资源包v1整理我授权使用的资源。先核对每个目录的实际分类编号、每个节目的播放线路与访问条件。为电视、电影、电视剧、综艺分别设置资源类型与分类，保持包ID、资源ID、节目ID和既有集名稳定。不要填入我的账号密码、Cookie、私人令牌或NAS凭证。
>
> 生成UTF-8的`.kaishi.json`文件，增加revision，运行仓库校验命令并保留原始文件SHA-256。把通过校验的文件发布到我拥有且允许使用的HTTPS静态地址，确认它返回文件本身，再按该SHA-256核对下载内容。
>
> 我开启电视本次导入窗口后，核对校验码，发现当前可用的aperture_resources工具，提交文件HTTPS地址和完整SHA-256。使用当前窗口revision和固定execution.key，查询同一请求的实际保存回执，核对包ID、修订号和SHA-256；未确认成功时不要换键重发。不要宣称已验证全部节目可播。

## 1. 制作文件

```bash
npm ci
npm run resources -- template --output family.kaishi.json
```

用用户的实际资源替换示例地址。不要把GitHub页面地址当作API或视频地址。电影/剧集/综艺直接清单使用 `collection`；实际接口使用 `catalog`；直播使用 `live`。包中可混用这些资源。

已有单个TVBox JSON配置可包装为适配资源：

```bash
npm run resources -- wrap-tvbox existing-tvbox.json \
  --id org.example.my-tv --title 家庭资源 --revision 1 \
  --output family.kaishi.json
```

此转换命令接受严格JSON的单配置，保留媒体字段供电视最终校验；多仓先选择一个具体配置。不抓取任意网络仓库，不自动执行插件。源文件使用HTTP时，校验可加 `--allow-http`，但这不会替用户授予电视的HTTP权限。

## 2. 校验和发布

```bash
npm run resources -- validate family.kaishi.json
```

读取输出的 `sha256`、`id`、`revision`，保留原始文件，不在校验后重新格式化。通过用户已授权的Git仓库、静态站点或自有HTTPS服务发布。公开地址应直接返回JSON，而不是登录页、分享页或GitHub的`blob`页面；可使用相应的raw文件地址。发布动作由用户自己的Agent及其授权账号完成，开视工具不会代为创建仓库或外发文件。

用真实地址和校验命令返回的64位SHA-256替换下面占位值：

```bash
npm run resources -- verify-url https://example.invalid/family.kaishi.json \
  --sha256 REPLACE_WITH_64_LOWERCASE_HEX
```

`verify-url`有512KiB大小限制、请求超时和有限重定向，摘要一致后再交给电视。发布文件改变时提高revision并重新校验，不能复用旧摘要。

## 3. 连接电视

电视与自托管家庭服务都需升级到支持v1的版本。按[WorkBuddy连接说明](WORKBUDDY.md)连接本机自托管家庭服务。用户在家庭工作台明确勾选“让WorkBuddy导入开视资源包”，获得独立的 `resources.configure`。旧令牌、留言权限、NAS权限和应用安装权限均不自动获得这项权限。

电视进入“来源 → WorkBuddy导入资源包”，阅读替换来源的说明，按“允许一次导入”。窗口10分钟有效，只接收一次操作。每台电视一次只开一个设备操作窗口。若包里有HTTP媒体，由电视用户单独勾选允许；包文件本身仍须用HTTPS传送。

## 4. 提交和读回

先重新发现当前授权工具，不猜测接口可用状态：

1. `aperture_resources_requests` 列出当前窗口。把 `fingerprint` 前12位与电视三组校验码比较，核对版本、目标电视和有效期。
2. `aperture_resources_send` 提交本次文件。参数中的 `id` 和 `execution.revision` 来自刚读取的窗口；同一意图使用固定 `execution.key`。

```json
{
  "id": "TV_WINDOW_UUID",
  "command": {
    "url": "https://example.invalid/family.kaishi.json",
    "sha256": "REPLACE_WITH_64_LOWERCASE_HEX"
  },
  "execution": {"key": "family-resource-update-001", "revision": 0}
}
```

3. `aperture_resources_status` 用原窗口ID和原key查询：

```json
{"id": "TV_WINDOW_UUID", "key": "family-resource-update-001"}
```

`delivered`只代表送达服务；`staging`表示电视正在处理；`awaiting_tv_save`表示等待本机保存。只有原key的 `receipt.outcome:"reported_by_tv"` 和 `receipt.state:"succeeded"` 才是电视报告的保存完成；核对 `receipt.result.packId`、`revision`、`sha256` 与本次文件一致。`changed:false`表示同一文件已存在，目标状态一致而未重复写入。

`failed`、`cancelled`、`unknown`或窗口过期均不代表“可以直接再发一次”。先核对原请求及电视“来源 → 当前资源包”中的编号、修订号和摘要；版本冲突时重新读取文件、检查revision，再由用户开启新窗口。授权撤销或到期后下一次调用拒绝，电视保存前也会重新检查交接授权。网络回执未确认时，本机可能已保存，不能换key盲重试。

资源包保存和插件运行授权分开。资源包不能授权静默安装、脚本执行、读取NAS密码、操作Hub账户或修改电视原系统。保存成功也不代表所有网络源和节目均可播放，应再按实际资源逐项检测。

## 当前证据

JSON Schema、CLI、生产Java解析器、MCP授权/幂等/保存回执及浏览器授权界面已做本地检查。MCP测试中的电视回执是合成测试端；尚未执行v18原生Activity、小米真机或真实WorkBuddy客户端。
