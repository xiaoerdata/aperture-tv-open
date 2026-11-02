# v39 / R3 修订候选

`versionCode 39` / `0.2.0-alpha.36-r1`，仍为v39，未公开发布。父提交为 `d72791867192394e0f121cf80583ffab1f4ad0ea`；旧候选和v38及测试修正引用不移动。现有包名、证书不变，同版本Code的修订可按原签名覆盖安装；安装包的versionName和SHA用于区分，不能仅凭39认包。

## 已知事实与未定因

[协调方报告](verification/s97-parent-cloud-reported.json)区分三层：旧候选的真实原生相册冷暖/取消/返回通过；R1受控51通过；R2正式用例在21项后失败，原 `last.closed && !uploaded` 未拆分，失败支仍未知。独立退出诊断没有复现，不能当正式通过。原R3全局Back UP跨出应用报注入权限异常，只有前9项原生检查通过，网页与照片路径没有运行。

原ZIP未到本机、未核其hash；报告值和原件事实分开记录。此次修补已确定的结束窗口及测试缺口，不宣称已重现/定因正式失败，也不把它说成v39独有问题。

## 产品边界

`finish()`与`finishAndRemoveTask()`先永久结束本Activity的上传代次，撤销确认、关闭当前session和页面，再交给系统结束。外部直接调用finish也不再等onPause；重复结束、destroy继续调用不会重开或重复关闭同一work。所有资格检查同时包含closed、isFinishing/isDestroyed、resumed、ready、当前owner和不可复活的permit。

新 `GalleryUploadEpoch` 把网页/原生owner、显式确认和一次工作绑定。pause、关闭、owner替换和结束都撤销；resume只恢复界面，不恢复旧permit。排队工作只能claim一次；若已在撤销前进入start，它作为在途操作立即关闭，其结果仍必须通过UI闸门。没有持有代次锁等待阻塞start。真实GalleryUpload还在地址解析/绑定与请求有效性检查中验证同一授权条件，close先发布volatile关闭状态，再处理已有socket。10分钟、私网/来源/令牌校验不放宽。

结果提交到网页或原生UI、失败状态、照片刷新均检查原work身份。原生异步解码/快照另检查Activity有效性、页面代次与active；旧任务不能回退出新页面。结束会清除原生地址；暂停也清除已显示的上传凭证，防止resume保留旧地址。没有删除相册文件或改变已通过的原生布局。

本地固定枚举新增UPLOAD阶段，记录创建、排队、进入、UI提交/拒绝、刷新拒绝及撤销；没有地址或用户内容。`UPLOAD_WEB_COMMITTED`表示提交evaluateJavascript请求，不是实际网页画面已绘制的证据。

## 测试层变化

| 范围 | 本次闸门与保留项 |
| --- | --- |
| R2结束 | 有限等待isDestroyed、destroy回调、closed、无upload/consentTicket及fakeClosed，保留finish返回时快照 |
| 迟到结果 | 等生产对应终态记录已入队后发布的计数；要求实际预期拒绝分支，fake返回或异常超时不能冒作成功 |
| 状态记录 | fakeClosed/closes/starts及时间、任意Settings.upload/status、NativeGallery.upload提交次数、finish/pause/destroy、每次UI提交/拒绝 |
| 旧核心用例 | R1/R2原字面断言标签、原合并安全断言、openedAt-1、长按/连按/取消保留；新增断言更严格，不用相同总数宣称通过 |
| 输入 | 明确探针Activity、所属包、焦点Window；同downTime的DOWN/UP只在捕获目标内dispatch。DOWN关闭或更换目标时UP明确未投递 |
| 失败报告 | teardown、组装、写回失败均变为ok:false；SecurityException不会被忽略或当通过，无全局注入、无新权限 |

[逐例清单](verification/s97-case-inventory-final.json)列出新增finish-before-pause、队列启动/队列UI、晚到错误状态、pause/resume不复活、destroy后回调、网页到原生owner切换、新明确原生fake确认、重复结束及迟到刷新用例。fake初始closed仍为false，只在真实close调用后改变；20秒是注入fake的有限释放闸门，不是产品超时延长，fake等待超时不能通过迟到URL分支。

`ProbeKeys`属于app内部/隔离输入层，不能替代系统遥控器全链。真实应用Back、三键诊断/原焦点、原生相册冷暖、四观看入口无来源/Back、退出取消和保数据仍需云端分别重验。

## 本地验证

[模型回执](verification/s97-unit-final.json)：原18/16/26/9/25模型、4组首页路由和新增16项上传代次模型通过。标准ARM/x86构建签名通过；217项产品输入相同：`3128e0896c95a3c7bbfc23455d269755ae8db03d7ce5a1891603cf28998e94a1`，证书沿用既有发行身份。

[最终探针编译](verification/s97-probe-compile-frozen.json)：javac/D8 exit0，仅编译。本轮本机未执行Android用例，没有真实listener、资源网络、用户LAN、真实照片或权限操作。此前探针编译结果保留为较早测试源，不能代替最终来源执行。旧d727的实际通过不当作修订候选通过。

## 云端顺序

新隔离包 `org.zhixiaoce.test.gallery39r1` 无网络权限，保留旧probe包和fixture；不清数据、不重签云产品、不申请INJECT_EVENTS。用本次冻结源码复建或使用本次探针，不能混用d727的产品包。

```sh
adb -s "$TEST_SERIAL" install --no-streaming -r artifacts/s97-native-frozen/probe.apk
adb -s "$TEST_SERIAL" shell am instrument -w org.zhixiaoce.test.gallery39r1/org.zhixiaoce.aperture.RecoveryRunner
adb -s "$TEST_SERIAL" shell am instrument -w org.zhixiaoce.test.gallery39r1/org.zhixiaoce.aperture.Round2Runner
adb -s "$TEST_SERIAL" shell am instrument -w org.zhixiaoce.test.gallery39r1/org.zhixiaoce.aperture.Round3Runner
```

检查s94-recovery.json、s95-round2.json、s96-round3.json及每份图片/帧回执。先看ok/error/teardownError等，再看逐例checks、lifecycleEvidence和keyReceipts，不能以ADB RC0或旧用例总数放行。确认原合并断言及新增两分支、终态种类/时间均符合预期，没有任何退出后的网页/原生URL提交。记录输入属于内部dispatch，真实系统链另验。

照片用例仍最后运行，仅空隔离存储可生成两张无私人内容图片；已有数据安全停止，不覆盖或删除，fixture继续保留。没有非空相册的像素通过主张。

```sh
adb -s "$TEST_SERIAL" shell am instrument -w org.zhixiaoce.test.gallery39r1/org.zhixiaoce.aperture.Round3PhotosRunner
```

同源真应用继续保数据覆盖，核base.apk SHA、Launcher快捷入口、原配置及默认守护关闭；确认取消/Back只做负向，不点真实开启。正式ARM、小米真机、现代网页路径单列，不拿内部dispatch/原生替代替验。

完整新提交、两包的路径/大小/SHA/证书、补丁及分层回执在 `artifacts/s97-v39-r1-delivery/REVISION-MANIFEST.json`。候选交接后停等，不公开发布、不开始v40。
