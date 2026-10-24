# 首次私有验证交付

日期：2026-10-24（Asia/Shanghai）。

已通过GitHub已连接账号核对：仓库xiaoerdata/aperture-tv，ID1431321453，visibility=private，默认分支main。用户创建的初始化提交cadc70ec37c65359c69dc3b2cddcf238ac495e37保留。

核心实现提交：49c797a83ad98e955966a9bd2f65c4e85bddd39c。已执行SSH push，随后git ls-remote核对远程main等于该提交，并通过已鉴权GitHub连接器回读README。后续记录性提交可能改变main，代码提交可作为本次验证基线。

| 证据 | 实际结果 | 边界 |
| --- | --- | --- |
| npm test | 31项通过 | 当前环境的领域、HTTP、MCP与ES5解析 |
| 原家庭浏览器流程 | 15组通过 | APERTURE_QA_PORT=4344；当前Chromium |
| 生活/影院/助手浏览器 | 6/7/3组通过 | 合成来源、VP9媒体、通用MCP客户端 |
| 轻量屏浏览器 | 4组通过 | Chrome64 UA及关闭现代网络API；不是真旧内核 |
| 新目录安装 | npm ci、doctor、start:checked及3个体验路由通过 | 从导出包解压；当前Linux/Python环境 |
| Hub公开Star预备 | 18项Node验证、4组隔离页面fixture通过 | 私有状态不显示链接/数字；未部署Hub |
| GitHub回读 | private、远程提交和README确认 | 不代表CI、发布或真机验收 |

原始JSON报告在本地artifacts中，截图在docs/images，生成脚本随仓库保存；不会把真实数据目录或连接凭证提交Git。

干净安装报告的源码包SHA256为32bc0032db68a216729419d587415cc30fbe1159ef80f9aff2f72a8db302d7e8；它对应安装检查时的快照，之后增加过程记录、改进展示截图并再次导出，新的包应使用自己的校验值。不要将不同包的哈希混用。

GitHub Actions验证文件已推送；当前连接器的工作流查询仅支持PR触发记录，本机gh认证已过期，本轮未核实push工作流的最终运行结果。不能把空status列表当作CI通过。

下一阶段仍需完成签名APK、WorkBuddy应用、手机摄像头扫码、真实家庭HTTPS/局域网、至少一台明确型号旧电视的安装/遥控/长期运行验证，再由所有者确认公开、Release下载和推广。当前没有将仓库改Public、发布APK、部署Hub或上传小红书。

## 后续更新（2026-10-24，S11–S12）

上文为首次交付快照。当前已免费本地构建debug APK并验证签名，私有候选在PR #1分支的private-preview目录；正式发行与真机仍待验收。GitHub托管作业因账户账单限制未执行任何步骤，托管工作流现为手动可选，非构建前提。

S12新增`npm run start:lan`，实际CLI、当前主机私有网卡和浏览器配对/留言/退出验证通过；不等于实际家庭网络验收。仓库回读仍为private，PR继续draft。公开仓库、正式Release、真实Star展示和推广尚未执行。
