# 公开前审阅 · S34

当前项目仍在私有验证阶段。这里区分源码快照、完整仓库历史和安装包；任一项检查通过，都不能代替真实电视验收或所有者的公开决定。

## 本轮发现与修正

1. 字体署名：实际 Aperture Song 字体与用于生成它的原字体均保留 Adobe 2017–2024 版权字段，源文件摘要与子集清单一致。原上游 OFL 文本快照的头部仍写 2017–2022；没有改写上游文本，THIRD_PARTY_NOTICES 现明确说明两者。字体的派生名称与 OFL 声明仍在实际 WOFF2 中。证据见 [字体检查](verification/s34-fonts.json)。
2. Pillow 11.1.0：安装包元数据和完整 LICENSE 均标为 MIT-CMU，已纠正文档中的 HPND。保留 [完整许可与组件声明](third-party/Pillow-11.1.0-LICENSE.txt)，未把第三方代码改标为项目 MIT。
3. Node 锁文件：126 项依赖都有许可声明和完整性摘要，声明分布为 MIT 108、ISC 14、BSD-3-Clause 2、Apache-2.0 1、BSD-2-Clause 1。下载地址中 94 项指向 npmmirror、32 项指向 npmjs；指定 npm registry 参数不代表覆盖锁文件中所有 resolved 地址。原干净安装记录已补充这个区别。这里核对的是声明和锁定完整性，不代替逐项授权审阅。详见 [依赖清单](verification/s34-license-inventory.json)。

## 源码快照与 Git 历史

冻结 v10 源码包排除了 private-preview、家庭运行数据和本机签名路径。新工具仅以规则检查有限类型的凭证格式、私有路径与已知私有 CI 文本，不将匹配值写到报告或终端。

历史中存在私有 CI 原文的旧版本，当前私有目录也保留原始过程。把文件移出当前公开源码清单，不会删除旧提交或其他分支里的内容。脚本中用于防止这些原文进入 Markdown 的守卫字符串也会命中规则，但它是代码中的通用错误片段，应与原始账户记录分别判读。详细路径、对象和提交映射只保存在私有验证目录，不随源码包导出。

因此目前不能直接把整个既有仓库改为 Public。先完成远程分支/标签、历史内容与媒体审阅，再由所有者确认公开的具体历史或发行快照；不自动删除分支、重写历史或更改仓库可见性。源码导出包本身也仍需审阅图片、过程内容与权利来源。

## 复现

在独立 Aperture 仓库根目录执行：

```bash
python3 tests/public-review.test.py
python3 scripts/review-public-source.py --history --output artifacts/history-review.json
python3 scripts/review-public-source.py --archive artifacts/aperture-tv-v0.2.0-alpha.7-source.tar.gz --output artifacts/archive-review.json
```

工具只读取本地可达 Git 对象或指定源码包，不联网、不验证凭证是否有效、不扫描宿主机、不构建或发布。--history 包括所有本地 refs；不能代替远程 refs 的实时清单或 GitHub 的不可达对象/缓存检查。二进制和超过 2MiB 的内容不做文本规则扫描，始终标为需人工审阅。报告 publicationApproved 固定为 false，不能据此自动发布。

三组隔离夹具验证了：当前文件已删除但旧历史/其他分支仍被检查；输出不含命中内容，文件名中的令牌格式也被遮蔽；二进制不冒称检查通过；有链接或越界路径的归档被拒绝。夹具只有合成字符串，没有真实凭证。

本轮没有重新构建 APK，v10 双版与冻结源码包保持原始摘要。小米安装/旧 WebView/小爱、NAS 硬件、WorkBuddy 客户端和家人验收、正式签名、公开发行及 Hub 真实 Stars 仍未完成。
