# 从私有验证到公开发布

P35更新：用户选择通过开源方式测试，因此先以`xiaoerdata/aperture-tv-open`提供Alpha源码与debug测试附件，真机/WorkBuddy现场结果后续补齐。原研发库仍私有；以下正式稳定发行的签名、机型、外网与部署条件不作为公开Alpha源码的前置阻塞。

当前研发仓库：xiaoerdata/aperture-tv，保持Private；正式公开时只导入审阅后的无历史源码目录，公开仓库名称由维护者确认。GitHub的权限与可见性才是权威状态；package.json的private只用于阻止误发npm，不能替代GitHub私有设置。

本项目仅适配小米电视。WorkBuddy和真机现场验收按P34由项目维护者完成。

## 当前阶段的验收

| 要证明的事 | 应看到的证据 | 当前状态 |
| --- | --- | --- |
| 项目已入私有仓库 | GitHub visibility=private，目标私有分支/候选来源可回读，README可读取 | 当前以私有草稿分支为准，不将推送说成合并main |
| 新开发者能启动 | npm ci、Python/Pillow、npm run doctor、start:checked、浏览器主要任务 | v13公开准备包在新目录重新npm ci、新建Python venv/Pillow、doctor与start:checked通过；标准/爸妈入口及完整/轻量预览通过，见verification/v13-public-onboarding.json |
| 助手能可靠更新 | 一次授权、自动保存、MCP写入、TV回执、冲突与撤销 | MCP/HTTP与浏览器已验证；WorkBuddy客户端按P34由维护者测试 |
| 旧电视有可选路线 | 轻量页、旧内核选择、ES5解析、遥控器/配对/更新/撤销 | 模拟旧能力已验证，API26模拟器运行有记录；小米固件、遥控器与硬件由维护者现场测试 |
| 外部人能直接安装 | 签名APK、下载附件、校验和、明确型号实测 | 私有debug候选已构建与签名校验；正式发行签名与真机待验证 |
| 公开展示真实Star | 公开GitHub数据、源时间、空值与缓存提示 | 取数机制及展示预备完成；私有阶段不展示 |

S34公开前历史与依赖的实际发现见 [审阅说明](OPEN-SOURCE-REVIEW.md)。当前不能把源码包检查通过等同于整个Git历史可直接公开。

## 私有仓库内的自动验证

.github/workflows/verify.yml保留为手动可选的Node/业务验证；默认使用本地免费路径，不部署Hub、不自动公开仓库。actions以只读contents权限运行，不需要业务密钥。第一次运行与日志要在GitHub实际确认，不能把本地通过当成远程CI通过。

## 确认可以推广后

1. 完成至少一台明确型号旧小米电视的ANDROID矩阵，录制安装、退出、遥控、2小时稳定性、旧内核和家庭网络结果；WorkBuddy应用中走一次真实自然语言闭环。
2. 在受控的Android SDK环境生成可分发签名APK；把签名密钥放在专用私密签名环境，不提交仓库。发布前核对包名、版本、签名摘要与SHA-256，记录已测设备。
3. 对源码和历史做一次私有内容/凭证审阅，确认README、许可、第三方声明、问题反馈方式、安装与卸载路径。不得从上层individual-tools整体推送。
   原始账户运行证据已隔离在private-preview/process，源码包不包含该目录。既有Git历史曾包含P12原文；移动当前文件不等于历史脱敏，因此保留现有私有库，不将其直接切为Public；使用OPEN-SOURCE-PREPARATION中记录的无旧Git历史目录。不得把SOURCE-MANIFEST、扫描结果或删除当前文件当成旧历史已脱敏。
4. 经项目所有者最终确认仓库名称与发布内容后，以干净公开目录新建仓库首个提交，保留现有私有研发库及其历史；不得直接把当前私有库转Public。用匿名API核对公开仓库private=false、full_name与许可，再发布带校验和及稳定签名的Release和APK。当前没有执行此步骤。
5. 在Hub的src/platform/labs/catalog.mjs将开窗repository设为已经匿名核实的实际公开owner/name、status设为published，保留实际版本/下载与安装说明。私有阶段repository必须为null，不能把个人访问令牌送入公开页面。
6. 发布Hub后访问/labs和/labs/aperture-tv，核对匿名可读的GitHub链接、真实Star、取数时间、失败空态和缓存标记；GitHub目录服务约10分钟缓存，浏览器每60秒刷新状态，属于有时效说明的近实时数据，不承诺逐秒变化。
7. 再发布小红书过程篇，写入实际仓库/发行URL与时间，不提前填造下载量、Star或真机成功。

## 回退

开发版本随时可从Android快捷菜单退出，保留原桌面。源码缺陷通过后续提交修复；不重写已有远程历史。Hub上线或可见性变更另有发布记录，私有代码推送不自动触发这些动作。
