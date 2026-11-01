# v38 R2 测试时序修正 · 第二个独立快照

第一处测试修正已冻结为 `57172237a16fb2e7383d0d9267522b114a6541a8`，本次保留其提交、探针、回执、patch及bundle，不amend、不移动原引用。产品候选仍为 `0f09b9d218a0937f16c5d0418f0f546ff138174e`，213项输入与两份产品APK不变。本次是同一R2测试修正的追加快照，不开发v39、不发布。

协调方随后报告：诊断副本配对按键后前6项通过，第7项旧手势用例失败。票据 `openedAt=10846027`，Right后当前时间 `10848098`，原 `now-1000=10847098` 仍晚于票据创建。产品把它当作新手势接受假会话符合保护条件，不能当成漏洞。原始云回执没有传到本机，[报告摘要](../verification/s95-key-fix2-cloud-reported.json)保留为协调方提供的诊断结果，不冒充正式冻结测试复跑或本机实测。

旧手势用例现在通过现有测试反射只读取得当前票据，使用 `ticket.openedAt-1` 作为DOWN/UP共享downTime；两个eventTime分别读取实际SystemClock，不改产品时间、不固定sleep、不修改保护或断言。第一处配对key助手保持不变，人工长按及其他测试也不改。

在该用例断言前输出 `openedAt`、`observedAt`、DOWN/UP.downTime和eventTime、created/fakeStarts、确认窗可见与引用状态；同时进入 `KaishiRound2Probe` 日志和 `s95-round2.json` 的 `gestureEvidence`。即使断言失败，也保留观察结果，不能仅凭“旧手势”这个用例名称判断注入正确。

独立编译：

```sh
python3 scripts/s95-build-r2-probe.py \
  --output artifacts/s95-r2-key-fix2/probe \
  --receipt docs/verification/s95-key-fix2-compile.json
```

使用新路径，已有路径不得清理或覆写。[编译回执](../verification/s95-key-fix2-compile.json)单列javac/D8状态与全部测试源SHA。本机不执行设备测试；云端需对新冻结来源正式复跑，再核对gestureEvidence的时间先后及原断言。第一处修正的诊断成功不是本次正式验收。

新的提交、patch/bundle和探针SHA统一在 `artifacts/s95-r2-key-fix2/TEST-CORRECTION-MANIFEST.json`，同时区分产品提交、第一测试修正与本次测试修正。旧失败、A/B摘要、两处编译回执和原产品清单均保留，完成后停等结果。
