# v39 R3 修订 · 结束边界与测试输入

来源为协调任务消息，不是新增产品提示词或v40开发。以 `d72791867192394e0f121cf80583ffab1f4ad0ea` 为父提交，版本Code仍39，名称为 `0.2.0-alpha.36-r1`。保持原包名和签名，所有旧候选、引用、正式制品、失败和回执保留；修订产品与测试单独冻结，不amend、不公开发布、不清理。

协调方已经核验旧候选的真实原生相册冷暖空态、默认取消/Center取消/Back和返回入口通过，R1隔离51项通过；正式R2在21项后以合并断言失败。闭合与未提交UI两个分支没有拆开，不能确定哪支失败。独立纯内存诊断没有复现正式失败，但观察到finish请求已返回、pause未到时，旧代码仍resumed=true/closed=false/fakeClosed=false；它只证明结束窗口存在，不能覆盖原正式失败。原R3在9项原生检查后因Back的全局UP跨应用注入报SecurityException，未到真实网页/照片路径。

当前执行器没有收到Library诊断ZIP或读取入口，没有复制、重试被拒路线或换通道。所给23,613B和SHA只保留为协调方报告值，[分层摘要](../verification/s97-parent-cloud-reported.json)不冒充原件。异步结果缺少isFinishing/isDestroyed保护在v38已存在，不能标作已证实的v39新回归或唯一根因。

产品修订让finish/finishAndRemoveTask立即撤销上传代次、确认与会话，后续pause/destroy保持幂等。队列、已经进入的工作、回UI、刷新和原生回退分别检查当前owner/permit及有效生命周期；resume不恢复旧资格。真实session还受同一撤销条件约束，关闭标志先行发布。保留secure窗口、10分钟、严格来源及照片数据。

测试改为同一探针窗口内分发，不申请INJECT_EVENTS。目标若已结束，UP明确记录未投递；内部事件不能冒充真实遥控链验收。R2分开等待结束/销毁/撤销和真正UI终态，fake.returned与queue idle不作完成闸门。保留原合并断言、openedAt-1旧手势、长按及全部原核心用例，新增更严格边界；失败与报告/teardown异常均保留失败。

实现、复跑和限制见 [修订候选说明](../CANDIDATE-V39-R3-R1.md)、[逐例变化](../verification/s97-case-inventory-final.json)及 [第74期底稿](../../media/DEVLOG-074.md)。完成私有交接后停等云测，爸妈版暂停，不启动v40。
