# v39-r1 共用测试焦点导航修正

来源为协调任务消息，只修测试层。产品基线 `72afdfe44695aa2bd398315af29a762761610d86`、217项输入、ARM/x86包、版本、manifest、权限和签名均不变，不重建产品、不开发v40、不公开发布。

协调方给出的正式云结果：R1修订51通过；21:43:17的R2只通过5项后在“remote Right focuses upload positive”失败，尚未进入迟到退出矩阵；R3通过4项后在“native remote Down reaches upload control”失败，Photos未运行。不能把焦点失败说成产品退化，也不能说原退出失败已解决。云日志原ZIP未传本机，没有重试被拒路线。

限定方向对照21:48:59–21:49:28显示原helper的DOWN未处理，focused.focusSearch自然返回合法目标，requestFocus可成功；没有Center，created/fakeStarts均0。已通过GitHub连接器读取Android9公开源码，确认默认方向导航在ViewRootImpl的processKeyEvent后续步骤，Dialog.dispatchKeyEvent本身不执行它。来源及原失败摘要见[分层记录](../verification/s98-focus-source-scope.json)，AOSP声明与Apache许可证随测试文件保存。

ProbeKeys现在仅在DOWN未处理、无修饰键、四个方向且捕获Activity/Dialog/Window/root/token仍有效时模拟默认导航。自然focused.focusSearch后严格验证同root/token、附着、可见、启用、可聚焦，再转换焦点矩形并requestFocus；失败可交同root的dispatchUnhandledMove。无当前焦点时，只有所有默认候选均合法才调用公开restoreDefaultFocus，否则自然FocusFinder候选也要通过同一验证。没有目标ID/按钮文本选择，不重导航已消费事件，不增加IME、触摸模式、系统策略或隐藏API。

每次记录DOWN/UP handled、捕获窗口/root/token身份、方向、前后焦点、自然候选合法性、导航结果及UP处置；不读控件内容。UP共用downTime且始终指向原捕获目标，结束/换窗/失焦时明确未投递。inputLayer为 `app-internal-window-dispatch+default-focus-navigation-simulation`，不是完整ViewRoot、IME或系统遥控链。

原R1/R2/R3/Photos共用助手。四个runner只把报告中的输入层字符串改为新公共常量，原焦点、安全、old=openedAt-1、长按、结束/销毁/生产终态闸门和合并退出断言逐字保持。新增FocusNavigationProbe/Runner使用普通A/B控件验证自然搜索、已消费/修饰事件不导航、外root/不可用候选拒绝、同root未处理移动、换窗与结束不投递UP；没有生产按钮、上传或照片操作。

本机[编译回执](../verification/s98-focus-compile.json)为javac/D8 exit0，所有设备用例未在本机执行。原probe/回执/失败保留，独立输出 `artifacts/s98-focus/probe`。产品保留证据见[核验](../verification/s98-focus-preservation.json)。复建需新输出/回执路径，不能覆盖既有文件：

```sh
python3 scripts/s98-build-focus-probe.py \
  --output artifacts/s98-focus/probe \
  --receipt docs/verification/s98-focus-compile.json
```

云端可先运行通用控件安全探针，再用同一包正式重跑R1/R2/R3；Photos仅按原空隔离存储规则最后运行，已有fixture停止不清理。各runner的输入层都是内部模拟，真应用/真实遥控链单独验。

```sh
adb -s "$TEST_SERIAL" install --no-streaming -r artifacts/s98-focus/probe/probe.apk
adb -s "$TEST_SERIAL" shell am instrument -w org.zhixiaoce.test.gallery39r1/org.zhixiaoce.aperture.FocusNavigationRunner
adb -s "$TEST_SERIAL" shell am instrument -w org.zhixiaoce.test.gallery39r1/org.zhixiaoce.aperture.RecoveryRunner
adb -s "$TEST_SERIAL" shell am instrument -w org.zhixiaoce.test.gallery39r1/org.zhixiaoce.aperture.Round2Runner
adb -s "$TEST_SERIAL" shell am instrument -w org.zhixiaoce.test.gallery39r1/org.zhixiaoce.aperture.Round3Runner
```

检查s98-focus.json及原有分层回执、keyReceipts、生产终态和原断言，不以shell RC0放行。新测试提交/patch/安全源码覆盖包/完整来源SHA与probe SHA统一在 `artifacts/s98-focus/TEST-CORRECTION-MANIFEST.json`。完成后停等正式复跑，不宣称原迟到退出问题已验收。
