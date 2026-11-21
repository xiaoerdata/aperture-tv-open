# WorkBuddy 配置开视 · V48

适用标准版与爸妈版 V48（versionCode 48），产品适配范围为小米电视 Android 8+。本指南使用用户电脑上的 WorkBuddy 运行仓库内的 Node.js 命令；`tv-control.mjs` 是命令行客户端，不能直接填成 MCP 服务地址。不需要先部署家庭服务或登录智小策 Hub。WorkBuddy 客户端和实体电视仍需现场验收。

## 先选连接方式

| 想完成的事 | 入口 | 所需授权 |
| --- | --- | --- |
| 读开视当前页面、操作方向键、导入本地资源包、探测视频 | 本文的电视局域网 HTTPS 控制 | 电视开启5分钟配对窗口，并用遥控器批准；批准后会话30分钟 |
| 写家庭留言/日程/指标、修改共享场景 | [家庭服务 stdio MCP](WORKBUDDY.md#配对步骤) | 自托管家庭服务、手机批准相应scope |
| 交接NAS配置、提交应用安装/卸载、经HTTPS地址导入资源包 | [NAS](NAS-SETUP.md)、[安装应用](WORKBUDDY.md#安装卸载电视应用源码v13)、[资源交接](RESOURCE-PACK-AGENT.md) | 家庭服务相应scope及电视单次窗口；安装/卸载还需系统确认 |

两种凭证互不通用。局域网控制没有读取NAS密码、静默安装应用、系统HOME、小爱唤醒、任意Shell或云端Hub账户接口；不能自行新增此类端点。局域网控制也不提供直接上传照片或修改天气城市的专用API，这些设置通过开视当前页面操作，照片仍走相册扫码上传。

## 1. 在WorkBuddy电脑准备源码

需要 Node.js 22+、Git，电脑和电视能在同一家庭私网直连。WorkBuddy须有本机终端及该私网的访问能力；只有云沙箱时，云沙箱不能直接访问家中电视，不应把电视控制端口暴露公网。

```bash
git clone https://github.com/xiaoerdata/aperture-tv-open.git
cd aperture-tv
npm ci
node --version
node scripts/tv-control.mjs discover
```

已经有项目就使用实际目录，不重复克隆、不覆盖原配置。命令从仓库根目录运行。Windows同样用本机 Node 和实际目录；不要让Windows Node读取WSL路径。

## 2. 电视开启配对，5分钟内申请

电视开视 → 设置 → 局域网控制 → 开启配对。记录电视显示的IPv4、HTTPS端口、六位码、完整证书SHA-256。发现结果只帮助定位，指纹须和电视屏幕核对，不能仅信广播。

以下为占位示例，执行前逐项替换，不把示例IP或端口当成实际设备：

```bash
node scripts/tv-control.mjs pair --host 192.168.1.30 --port 12345 --fingerprint REPLACE_WITH_TV_CERTIFICATE_SHA256 --pin 123456
```

终端出现核对码后，用户在电视用遥控器允许。**5分钟是配对码窗口；申请后本次电视确认票据60秒有效；批准后控制会话30分钟。** 关闭连接或电视应用进程退出会撤销；没有静默续期。连续五次错误码拒绝继续尝试，应由用户重新开启配对。

默认会话文件 `~/.config/kaishi/tv-control.json`，可在每条命令后统一指定 `--session /private/my-tv.json`。文件包含令牌，不输出内容到聊天、日志、截图或仓库；POSIX默认0600，Windows需保证所在目录只对自己可读。不要关闭指纹校验，也不要使用 `curl -k` 代替本客户端。

配对后让用户回开视首页或片单，再执行：

```bash
node scripts/tv-control.mjs status
node scripts/tv-control.mjs ui
```

核对edition、当前页面和原始尺寸。配对成功只代表已获准连接，不代表资源已配置或影片可播。

## 3. 制作和导入资源

先读[Schema](contracts/kaishi-resource-pack-v1.schema.json)、[格式](RESOURCE-PACK.md)、[制作指南](RESOURCE-PACK-AGENT.md)。推荐一个UTF-8 `.kaishi.json` 包整合直播、电影、电视剧和综艺：

```bash
npm run resources -- template --output family.kaishi.json
npm run resources -- validate family.kaishi.json
node scripts/tv-control.mjs import --file family.kaishi.json
```

模板必须先替换成用户实际可用且有权使用的资源，示例 `example.invalid` 不可播放。导入前说明它会更新当前来源配置，确认使用目标包；不要丢失用户原有来源，用户要求保留时先取得原配置并合并。

已有TVBox严格JSON配置可以包装后导入：

```bash
npm run resources -- wrap-tvbox existing-tvbox.json --id org.example.my-tv --title 家庭资源 --revision 1 --output family.kaishi.json
npm run resources -- validate family.kaishi.json
node scripts/tv-control.mjs import --file family.kaishi.json
```

媒体确实需要HTTP时，只有用户允许才在validate后加 `--allow-http`，在局域网import后加 `--allow-lan-http`；不为解决普通网络失败而默认开启。完整示例见[混合包](../examples/resource-packs/family.kaishi.json)。

规则：

- 原始文件和电视展开后的配置各最多4 MiB；V47起不设资源站点导入总数配额，仍保留网络分页、节目单与插件预算。局域网`/resources`封装请求上限为4 MiB加64 KiB，其他命令仍512 KiB。
- 同一包ID保持稳定，每次修改内容提高整数revision；资源ID、节目ID及完整集名也保持稳定。不用随机ID绕过版本冲突，不删库重试。
- `live`放直播；`catalog`接真实目录API；`collection`放直接片单；`tvbox`兼容现有单配置。分类是`movies/series/variety`，先读真实站点分类编号，再填写`categoryIds`，不套用猜测的数字。
- 填真实year及已知评分出处，缺失就留空；默认排序年份优先，同年评分其次，每次追加对已加载列表重排。不把更新时间冒充出品年份，不编造豆瓣评分。
- 集名保留上下期、加更等文字。播放器切线先匹配完整名，真正多义的集号不猜。资源文件不能携带NAS密码、Cookie、私有令牌、可执行提示词或运行授权。
- 正在播放时拒绝替换来源，先停止/返回片单。插件运行信任与资源导入是两次独立判断；导入不能代替用户批准插件。

局域网成功结果包含 `packId/revision/sha256/changed`，核对包ID和修订号。此通道会把JSON规范化后交给电视，返回摘要对应电视规范化内容，不保证等于原始文件逐字节摘要。走家庭服务的HTTPS文件交接时，原始文件SHA-256必须按[该通道规则](RESOURCE-PACK-AGENT.md#4-提交和读回)核对，不能混用两种摘要语义。

## 4. 配置后验证

每一步先读ui识别实际焦点，再发少量操作，再读回，不盲目按固定次数：

```bash
node scripts/tv-control.mjs ui
node scripts/tv-control.mjs keys down,ok
node scripts/tv-control.mjs status
node scripts/tv-control.mjs screenshot --output kaishi-check-001.png
node scripts/tv-control.mjs probe --url https://media.example.invalid/sample.mp4
```

这些按键和地址只是命令语法示例，先改为实际目标。截图文件必须是新路径；截图最大宽1280，点击坐标必须取ui/status的原始尺寸，不能直接取缩小截图像素。截图不含SurfaceView视频画面，不能用黑色视频区域判定播放失败；probe首帧成功也不证明长时间流畅或所有线路有效。

支持`up/down/left/right/ok/back/menu/play_pause`。一批commands最多24项；每项等待最多1000ms、合计6000ms。推荐每批1—3项并读回。完成后：

```bash
node scripts/tv-control.mjs close
```

## 失败如何处理

| 现象 | WorkBuddy下一步 |
| --- | --- |
| discover无结果 | 用户核对电视IP/端口、同网段及访客Wi-Fi隔离；可用明确IP直连，不因此打开公网端口 |
| 配对码过期/连接已到期 | 让用户重新开启配对，拿当前码；不要反复猜码或复用旧token |
| 证书不匹配 | 停止发送数据，重新核对电视完整指纹；不能自动信任广播的新证书 |
| 配对成功但页面不可操作 | 用户回开视首页/片单并保持前台，再读status/ui；系统弹窗和其他应用不受此接口控制 |
| 命令/导入超时 | 结果未知，先读status/ui、电视当前资源包信息；不得自动重放一整串按键或换包ID重导 |
| revision较旧/内容冲突 | 取得已配置包的当前修订号，确认新内容后升revision再校验；不清空电视数据 |
| source可导入但不可播 | 核对实际API、分类、媒体地址、HTTP许可、访问条件；测试具体URL/线路，不用包导入成功代替可播放 |

## 直接复制给WorkBuddy

> 请依据开视公开仓库的 `docs/WORKBUDDY-QUICKSTART-V48.md` 配置我的电视。先检查本机Node22+和仓库目录；我在电视开启5分钟局域网配对后，你发现设备并与我核对IPv4、端口和完整证书SHA-256，然后申请连接，电视确认由我操作。凭证只存本机私有文件，不读出到聊天。先读status/ui，再按我要求制作或修改资源包。保持包ID和资源ID，提高revision，使用真实分类/年份/评分与完整集名；先validate再在片单页import。我要求保留现有来源时，先取得并合并原配置。每次只做少量按键并读回，超时先核实结果，不盲重放；不能改电视density、系统分辨率、HOME或小爱设置。完成后分别报告“已配对、已保存、已打开、首帧测试、实际持续播放”哪些有证据，最后关闭连接。需要NAS或安装应用时按独立指南和授权窗口办理，不把局域网令牌当成系统或Hub账号权限。
