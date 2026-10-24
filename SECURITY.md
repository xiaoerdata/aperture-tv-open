# 安全与隐私边界

这是开发alpha，不应作为匿名公共上传服务。请勿把真实口令、家庭照片、家人日程、电视序列号或会话Cookie提交到公开Issue。

家庭照片与数据受同源会话鉴权，服务器限制上传、解码并重编码图片。单家庭所有有效成员共享内容；暂不提供成员级私密笔记。服务器文件未做应用层加密。

不从网络安装任意场景脚本，不执行遥控shell，不禁用系统软件。原生WebView仅导航配置好的服务源，不暴露JavaScript桥。

安全问题请通过[GitHub私密漏洞报告](https://github.com/xiaoerdata/aperture-tv-open/security/advisories/new)提交，该仓库已启用Private vulnerability reporting。普通兼容性问题使用[Issues](https://github.com/xiaoerdata/aperture-tv-open/issues/new/choose)。不在公开Issue附上可利用细节、账号或家庭资料；当前没有承诺响应时限或漏洞奖金。

## v0.2 新增边界

生活MCP只运行部署者审核的固定只读调用，原始返回不直接共享，拒绝任意工具/参数/URL；凭证引用环境变量，不进入家庭板。第三方本地stdio代码拥有宿主进程权限，仍需部署者审阅源码与许可，本项目未提供其进程沙盒。

影院默认关闭，只有owner可以配置；本地文件需真实路径包含于配置根，符号链接跳过；Jellyfin权限限制到用户/媒体库，M3U来源需部署者白名单。不得导入执行TVBox spider、JAR或脚本。播放令牌不暴露给电视；代理检查重定向、分段来源和撤销，缓冲中的片段可能短暂继续播放。

自托管助手只有明确批准的scope，发现/调用/领域提交重新校验，凭证过期/撤销下一次请求拒绝。扫码挑战与领取secret分开；领取和调用绑定私有来源IP。详见WORKBUDDY与DEPLOYMENT的可信代理要求；IP不是硬件身份。二维码不应进入公开录像，原始代理访问日志应避免保留查询参数中的短期挑战。

数据文件、Agent凭证、媒体源配置和浏览器会话不得导出。源包排除私有Hub脚手架，不公开托管平台的Skill/schema。公开的自托管协议不能换取Hub付费Agent权限。
