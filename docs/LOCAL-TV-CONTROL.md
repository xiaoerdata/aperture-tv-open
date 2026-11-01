# 局域网控制 · v30 Alpha

仅操作开视自身的前台页面，不代替ADB或Android系统权限。默认关闭；不要求电脑家庭服务、公网地址或Hub账号。原有家庭服务MCP、NAS凭证交接和应用安装授权不变。本入口是电视本机的短期设备授权，不授予智小策Hub产品或Agent权益。

## 在电视开启

两版设置均有“局域网控制”，影视来源设置末尾也可进入。选择“开启配对”，电视生成本机HTTPS证书，显示六位码（2分钟）、端口和完整SHA-256指纹。电脑须与电视在同一私有IPv4网络；网络隔离、禁止mDNS、休眠可能使发现不可用，仍可按IP和端口连接。

六位码只允许发起请求；电视还会显示电脑地址和核对码，必须用遥控器确认。授权最长30分钟，内存保存，关闭连接、应用进程退出或到期立即失效。截图、状态与界面树也要求该授权，没有默认匿名读取。

## 以WorkBuddy为例

让自己的WorkBuddy在本机检出开视源码，使用Node.js 22+：

```bash
node scripts/tv-control.mjs discover
node scripts/tv-control.mjs pair --host 192.168.1.30 --port 12345 --fingerprint 电视显示的完整SHA256 --pin 电视显示的六位码
```

发现结果仅供定位，不能代替电视上的证书指纹核对。TLS客户端先核对证书，匹配后才发送令牌与内容。证书私钥留在电视AndroidKeyStore；连接令牌只保存在本机权限0600的会话文件，默认`~/.config/kaishi/tv-control.json`。可用`--session`指定该文件；不要提交或分享。

电视确认后返回开视首页或片单：

```bash
node scripts/tv-control.mjs status
node scripts/tv-control.mjs ui
node scripts/tv-control.mjs keys down,right,ok
node scripts/tv-control.mjs screenshot --output 新截图.png
node scripts/tv-control.mjs import --file family.kaishi.json
node scripts/tv-control.mjs close
```

截图输出路径必须是新文件。资源包含HTTP媒体时，在本次导入加`--allow-lan-http`，不影响其他模块TLS策略。片单/详情从原生统一导入服务保存；播放中拒绝替换，先返回片单。重复导入按资源包ID、revision及摘要处理；换来源或修改内容请增加revision。局域网JSON封装后仍有512KiB请求上限。

## 操作序列与语义树

`commands --file actions.json`接收数组，一次最多24项。示例：

```json
[
  {"type":"key","key":"down"},
  {"type":"wait","ms":300},
  {"type":"key","key":"ok"}
]
```

支持`up/down/left/right/ok/back/menu/play_pause`，不支持系统HOME、小爱、任意Shell或按键编号。`tap`用`x/y`表示电视原始像素；`text`只填写当前非密码输入框；等待每项最多1秒、总计最多6秒。请求超时可能已有部分操作执行，应先读`status/ui`确认，不自动重放序列。

`ui`给出开视原生节点、当前焦点、可用状态和坐标；玻璃首页由预置只读脚本报告可见按钮和标题，不读取输入值、Cookie、存储或任意网页。系统弹窗、其他应用和敏感页面不受控。截图只绘制开视自身界面，最大宽1280；SurfaceView视频区域不包含影片画面，不绕过FLAG_SECURE或DRM，不能用作系统录屏。坐标以`ui/status`的原始分辨率为准，勿直接使用缩小截图像素。

## 电视侧探测

```bash
node scripts/tv-control.mjs probe --url https://media.example.invalid/sample.mp4
```

使用电视的网络与原生解码器读取首帧，返回实际宽、高及`firstFrameDecoded`。接受媒体HTTP206；不靠高度猜测电影画幅或码率。单次请求最多等待18秒，不并发启动多个底层探测；原生调用未返回前拒绝新探测。首帧成功不代表直播、音轨、两小时播放或全部来源通过。

## 接口契约

mDNS服务`_kaishi-tv._tcp.local`只在限时窗口内广播设备标识、端口、版本及证书指纹；不广播账号、令牌、源地址或家庭数据。广播和直连均不是公网远程控制。

| HTTPS接口 | 方法 | 内容 |
| --- | --- | --- |
| `/pair/request` | POST | `{pin}`，返回待电视确认的短期ticket |
| `/pair/result` | POST | `{ticket}`，确认后返回30分钟令牌 |
| `/status` | GET | 当前开视页面、原始尺寸、授权剩余时间 |
| `/ui` | GET | 原生节点和预置玻璃首页语义 |
| `/screenshot` | GET | 自绘PNG，视频与受保护页面不捕获 |
| `/commands` | POST | `{commands:[...]}`，仅开视内部输入 |
| `/resources` | POST | `{pack:{...},allowHttp:false}`，统一资源包导入 |
| `/probe` | POST | `{url,allowHttp:false}`，电视网络/解码探测 |
| `/close` | POST | 撤销连接并停止监听 |

配对以外每次请求校验私网真实对端、证书连接与Bearer令牌。拒绝浏览器Origin、代理头、重绑定Host、超大或分块请求。没有文件系统、账号读取、安装卸载或清数据端点。应用安装/卸载继续通过原有电视发起的单次窗口与系统确认。

## 验证状态

开发检查、原生模拟器和小米真机分开记录。mDNS三秒发现、实体遥控、电视网卡限制及推包五秒的实机指标须由维护者同网段复测；没有用ADB转发或模拟器结果代替这些指标。当前为v30开发能力，公开版本以Release为准。
