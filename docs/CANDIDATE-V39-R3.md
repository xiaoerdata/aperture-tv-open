# v39 标准版候选 · Round 3

39 / `0.2.0-alpha.36`，私有候选，未公开发布。爸妈版暂停。产品来源为v38 `0f09b9d218a0937f16c5d0418f0f546ff138174e`，真实Git父提交为修正测试 `31a379a8f5c1eb9741fccc0039fc147523b5811d`。原候选和两次测试修正均保留。

## 定位结论

真实旧内核相册超时的根因仍为 **unknown**。现有链路依赖精确URL的 `onPageFinished` 后再发ready探测；原来只有loading/timeout不足以知道卡在哪里。云端报告中的egl错误、缺少console异常、两次墙上间隔均不能单独定因。当前工具无法读取所给Library原件，没有接收ZIP或声称亲自读过原日志，见[协调摘要](verification/s96-parent-cloud-reported.json)。

新增 `KaishiGallery` 固定字段本地日志和设置中“相册加载记录”：Activity会话、代次、单调时间、前后台与焦点、文档开始/提交/完成及精确URL匹配布尔值、资源类别命中/拒绝/失败、API快照结果、ready请求/回调/固定结果、deadline启停/到期及剩余预算。旧会话不能覆盖新会话的记录。最近会话最多160条，资源成功按类别限量；没有网络遥测。

日志没有URL、query、照片名/路径/内容或上传凭证。console只保留Syntax/Reference/Type/Other固定类别、固定脚本类别和有界行号。IO线程资源事件不伪造UI线程预算：`budgetObserved=false`，预算值为未知。`active`按原有resumed预算语义，焦点另列；此次不改焦点预算行为。

`armed`和`dueAt`只表示仍在等待的alarm。暂停、ready、取消后`dueAt=-1`；调度延迟只由实际到期回调的 `callbackAt-callbackDueAt` 测量，并标记 `dispatchMeasured`，不会随后续时间增长。新代次清除上代测量，不重启或延长原预算。正常网页仍保留10秒前台预算、600ms返回兜底、严格URL/来源和旧回调隔离。

## 可操作的旧内核相册

低于WebView89或无法识别时，直接打开无需WebView的本机相册；89界限沿用首页的兼容策略，不能解释为已证明相册在所有89以上引擎通过。网页加载错误、超时或renderer退出后，同样进入可浏览的原生相册。

原生页有明确空态、中性标题首焦点、方向键入口、本机照片读取、上一张/下一张、全屏逐张轮播、确定暂停和返回。读取只按内部照片ID进行有界解码，浏览不删除/改写照片；原有模板和设置保留。恢复页首焦点仍为返回按钮，保留R1原生逃生键检查；直接打开原生相册时首焦点为标题。

原生模式的限制写在界面：扫码、双幅/推近等网页模板和照片管理仍需网页相册；原生模式只支持逐张轮播和手机浏览器手动输入临时上传地址，不宣称原生二维码、完整模板或网页屏保已验收。实际上传仍经过同一原生确认、默认取消、一次新按下与释放以及会话/页面/生命周期检查。原生owner与网页owner分别绑定，退出、后台都会停会话。临时地址只在用户确认后显示，窗口标为安全窗口；本轮从未开启真实服务或展示真实地址。

## 本地证据与边界

- [模型检查](verification/s96-unit-final.json)：18确认、16帧门槛、26期限、9焦点组合、4组首页路由，以及25项阶段/快照检查通过；包含暂停、取消、ready、到期历史延迟、换代及迟到回调。首次模型回执保留，没有覆盖。
- [资产审阅](verification/s96-asset-audit.json)：三份既有JS按ECMAScript2017语法解析通过；CSS存在clamp/inset/gap，仅记兼容待验，没有把它们当超时根因，也没有改资产。
- [探针编译](verification/s96-probe-compile.json)：javac/D8均exit0，未执行。保留R1和修正R2所有用例，配对downTime和 `openedAt-1` 保持不变。只把版本断言升级到实际39，并让受控页面明确选用网页路径；不能用该覆盖选择证明真实旧内核网页可用。
- 216项Android/launcher输入指纹 `53c55cb4838dfeef8ecf09292becea729ae3f5b0604caa6fe43091f970d3a325`，ARM/x86签名候选同源，沿用既有发行证书，测试类不进入产品APK。完整路径、字节数、SHA和精确提交见交接清单。

本机没有执行Android设备矩阵。v38的R1/R2通过是协调方给出的父版本结果，不能充作v39通过。真实WebView66、原生照片UI、正式ARM、小米默认显示/HOME/小爱/米家、真实上传与现代玻璃路径仍需分别验收。

## 云端复跑顺序

既有隔离环境使用测试探针新包 `org.zhixiaoce.test.gallery39`，无网络权限。不要操作用户电视、默认HOME或权限。真实产品升级测试单列：保数据覆盖37/38→39，核Launcher快捷入口、已有配置及默认守护关闭；四观看入口无来源态、Back焦点、退出取消、三键诊断及Back原焦点，都要重新记录v39结果。

```sh
adb -s "$TEST_SERIAL" install --no-streaming -r artifacts/s96-native/probe.apk
adb -s "$TEST_SERIAL" shell am instrument -w org.zhixiaoce.test.gallery39/org.zhixiaoce.aperture.RecoveryRunner
adb -s "$TEST_SERIAL" shell am instrument -w org.zhixiaoce.test.gallery39/org.zhixiaoce.aperture.Round2Runner
adb -s "$TEST_SERIAL" shell am instrument -w org.zhixiaoce.test.gallery39/org.zhixiaoce.aperture.Round3Runner
```

先读取私有目录中的s94-recovery.json、s95-round2.json、s96-round3.json及每份截图回执和图像，不能以shell RC0代替ok和逐项断言。R1预期51、R2预期66；阶段/真实页结果单列。实际版本是39 / `.36-probe`。R3NativeGalleryProbe真实走provider选择，只验统一上传确认的取消和Back，不点开启。R3WebGalleryProbe只在测试子类强制网页路径，实际WebView和本地资产仍是真实的；冷、暖各一轮，记录网页ready或原生替代，以及最后阶段、预算与固定console类别。原生替代成功不算网页ready通过。

照片测试放最后；它仅在隔离包相册为空时生成两张纯色JPEG，直接写本机测试存储并保留，不监听端口、不使用私人照片。若已有照片，安全停止，不清理或覆盖。R2的截图隐私门槛保持不变，非空相册不截图，因此照片测试的解码/遥控断言和空态帧证据必须分开，不冒充有照片画面的像素验收。

```sh
adb -s "$TEST_SERIAL" shell am instrument -w org.zhixiaoce.test.gallery39/org.zhixiaoce.aperture.Round3PhotosRunner
adb -s "$TEST_SERIAL" shell run-as org.zhixiaoce.test.gallery39 cat files/s96-photos.json
```

不要复跑空态截图用例覆盖已保留的照片fixture；后续复测使用另一个独立测试输出/包名或先报告所需决定，不能自动清理。正式产品ARM与云debug签名包分别记录，不重签或清除已有云产品数据。

交接目录 `artifacts/s96-v39-delivery/ROUND3-MANIFEST.json` 绑定真实父提交、测试来源、产品输入、两包及回执。候选交付后停等云测，不公开发布、不开始v40。
