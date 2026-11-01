# v38 R2 测试按键构造修正

来源为协调任务消息，不是新版本开发，也不是新增产品需求。产品候选保持 `0f09b9d218a0937f16c5d0418f0f546ff138174e`，原 `candidate-v38-r2` 引用和全部安装包/清单不移动、不覆盖。测试修正独立冻结为该产品候选的子提交，版本仍为38；不公开发布、不启动v39。

协调方提供的云端失败为 `ok:false`，前三项检查通过，`INSTRUMENTATION_CODE=0`；返回码不是通过依据。原测试的 `sendKeyDownUpSync` 产生不同的 downTime：DOWN=10686047、UP=10686108。取消框仍显示，引用非空，但 `created=0`、`fakeStarts=0`。诊断B用共用 downTime=10692370 的成对事件，取消框关闭，引用清空，pending=false，同样没有创建或启动假会话。该A/B定位测试事件构造问题，不能说已证实产品误开上传。原始云文件未传到本机，单列 [协调方提供的A/B摘要](../verification/s95-key-fix-cloud-ab-reported.json)，不冒充本机原始设备回执。

只修改 `Round2Runner.key(int code)`：一次读取真实单调时钟作为 downTime，使用 `sendKeySync` 依次发送按下、抬起；抬起的 eventTime 取当时真实时钟，仍等待 `waitForIdleSync`。没有固定 sleep、产品私有状态调用或产品时间替身。取消合并断言、人工旧手势/长按重复用例及其他断言全部保留。产品的 openedAt、gesture 和生命周期检查完全不改。

构建器只增加显式 `--receipt` 参数，显式回执必须不存在；本次写独立目录与新回执，不覆盖原冻结回执，也没有事后恢复。复建命令：

```sh
python3 scripts/s95-build-r2-probe.py \
  --output artifacts/s95-r2-key-fix/probe \
  --receipt docs/verification/s95-key-fix-compile.json
```

若路径已存在，另选新输出与新回执，不删除旧证据。编译结果见 [独立回执](../verification/s95-key-fix-compile.json)。本机不执行设备测试，不启动监听、不创建环境，不重建产品包。协调方报告R1在v38另通过51项，4张新截图valid=true但像素未复核；这些不替代修正测试的正式云端复跑。

交接清单 `artifacts/s95-r2-key-fix/TEST-CORRECTION-MANIFEST.json` 将同时列产品候选提交和测试修正提交、精确源码/patch/bundle/探针SHA、javac/D8退出码及原两包/原回执保留核验。完成后停等云端结果。
