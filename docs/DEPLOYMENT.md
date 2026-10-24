# 自托管与互联网访问

当前服务供一个家庭使用，默认回环监听，不会自动部署到生产或互联网。

## 本机

Node.js 22+ 和 Python/Pillow；先 npm ci，再按 README 启动。`.data` 包含私人内容、会话、管理口令和图片，应只有服务用户可读。不要运行第二个进程指向同一数据目录。服务使用原子文件替换和进程内串行队列，未实现跨进程锁，也不是高可用数据库。

## 局域网开发

首次试用可以执行`npm run start:lan`，选择本机私有IPv4网卡，获得电视地址和手机工作台二维码；依赖未齐时先提示修复。它只在选中网卡监听，不会覆盖已有HOST/PUBLIC_ORIGIN、不保存部署配置、不自动开启助手。详细操作见[第一次使用](QUICKSTART.md)。

使用主机真实局域网地址，例如 `192.168.1.50`，手机和电视在同一网络：

```bash
HOST=0.0.0.0 PUBLIC_ORIGIN=http://192.168.1.50:4318 npm start
```

仅供受信任家庭网络开发。HTTP不是加密传输；正式使用配置HTTPS。Android debug 包允许局域网HTTP，release包只允许HTTPS。`PUBLIC_ORIGIN` 必须是实际访问源，Host 和 Origin 都精确校验；不能一边配置127.0.0.1一边通过其他域名访问。

## 公网/远程家人

把服务部署到你控制的主机，用真实域名和 HTTPS 反向代理。例如 Caddy：

```text
screen.example.com {
    request_body { max_size 5MB }
    reverse_proxy 127.0.0.1:4318
}
```

启动服务：

```bash
PUBLIC_ORIGIN=https://screen.example.com HOST=127.0.0.1 PORT=4318 npm start
```

反向代理保留原始Host；不要把未经限制的请求头当管理员身份。配置域名DNS和TLS后，手机工作台与电视使用同一HTTPS源。浏览器会话使用HttpOnly、SameSite=Strict和HTTPS环境下的Secure cookie。

为域名配置请求体上限、连接速率、WAF/CDN等边缘策略。应用自身有基本登录/连接/写入限流，但不宣称能抵御带宽型DDoS。当前应用按直连socket IP限流，代理后的请求共享限额；正式大规模托管须实现可信代理与多租户限额。本版不适合开放匿名注册或公共上传市场。

## 备份与删除

停止服务后备份整个 `.data`，以保留家庭内容、图片、会话与幂等回执的一致性。恢复测试在独立目录和端口执行，不把真实数据放入源码包。`state.json` 和照片未做应用层加密，需由主机磁盘加密、文件权限和备份保护负责。

删除内容会立即从家庭板及受保护读取入口移除。为避免并发错误，磁盘上的已移除图片不自动清除；内部最多128个JPEG文件。需要彻底删除时先停止服务，使用 `npm run clean:images` 列出未引用图片，再使用 `npm run clean:images -- --apply` 删除。工具拒绝目录链接且只匹配内容哈希命名的JPEG。运行前保留所需备份。

## 运行边界

- 电视使用认证 SSE 接收版本通知，5秒轮询作补偿；前台轮询在后台停止。SSE不传家庭正文，随后读取仍需有效会话。
- 服务中断时电视保留本次已加载内容，明确显示断连和最后同步时刻。重新启动且离线时不保证照片可见。
- 会话失效或设备撤销在下一次请求被拒绝，电视清除当前显示内容。人类/电视会话默认30天；本机助手按批准的1小时/24小时/7天，到期需重新连接。
- 幂等回执保留最近500次成功写入。超出窗口后旧请求的原revision仍不会静默覆盖新状态；不要长期复用旧请求键。
- 当前源代码没有支付、短信、第三方图片存储、远程代码下载或自动系统更新。

## 可选家庭能力

- `APERTURE_CONNECTIONS=/private/connections.json`：部署者审核过的 MCP 只读查询映射，见 LIFE-CONNECTIONS。无需修改主入口添加厂商副本。
- `APERTURE_MEDIA_CONFIG=/private/media.json`：本地目录、Jellyfin及M3U来源白名单，见 FAMILY-CINEMA。影院仍需owner在工作台主动开启。
- `APERTURE_LOCAL_AUTOMATION=1`：开启自托管助手，见 WORKBUDDY。默认关闭；不会启用官方托管 /agent。

## 本机助手经过反向代理时

仅服务人类界面的上述公网示例不能直接当作助手私网验证配置。助手端必须使用可信HTTPS；确保后端端口只允许代理连接，并在入口拒绝公网来源访问 /api/local-pair/* 和 /api/local-agent/*。

以单层 Nginx、后端直连地址127.0.0.1为例，在这两类location中复用：

```nginx
allow 127.0.0.1;
allow 10.0.0.0/8;
allow 172.16.0.0/12;
allow 192.168.0.0/16;
allow ::1;
allow fc00::/7;
deny all;
proxy_set_header Host $host;
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header Forwarded "";
proxy_pass http://127.0.0.1:4318;
```

服务进程设置 `APERTURE_TRUSTED_PROXIES=127.0.0.1`；若实际socket为其他地址需填写准确值。反代必须覆盖单一客户端IP，不能使用追加链，也不能透传客户端自称的地址。该片段需放入现有TLS server下的匹配location，不是完整可直接部署的配置；多层CDN/代理链未支持，不能照搬本片段。

如果公网代理隐藏源地址并不传任何头，后端看到的回环来源无法证明家庭局域网。因此必须同时落实入口限制与可信源转发。IP绑定不等于设备身份；NAT共享、DHCP更换、VPN变化都要结合令牌策略。当前未在真实家庭网络与TLS代理上现场验收。

反代SSE需关闭响应缓冲，并允许持续连接。应用仍按socket来源限流；不要将可信来源解析误称为已完成分布式限流。
