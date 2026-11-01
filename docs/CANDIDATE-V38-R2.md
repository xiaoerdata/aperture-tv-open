# v38 标准版候选 · Round 2

`0.2.0-alpha.35` / versionCode 38，仅标准版。本轮是私有候选，未公开发布；爸妈版暂停。基线为 v37 冻结提交 `4ff9a8785c9990a4b0d0c1947d3acb3069f73779`，没有使用对象库的旧 HEAD。

相册初始焦点落在标题，确认键不会触发动作。扫码上传会先显示原生确认，说明本机临时局域网服务、10 分钟时限和离开/后台关闭条件，默认选择取消。只有确认窗打开后的一次新按下与释放才能启动会话；旧事件、长按重复、取消与失效页面不能创建上传。已接受但迟到的启动结果也受页面、生命周期及会话身份检查约束。所有本轮上传测试使用内存替身，不开启真实监听。

本机诊断移至标准设置第二项；风景首页四个观看入口可向上到设置，应用与工具页也有诊断入口。诊断返回保留原菜单和焦点；较高的中文说明行先在行内滚动，再移动到下一控件。这些原生交互已加入 Android 用例，仍待云端实际执行。

截图工具只在测试源码中：先等待真实目标附着、可见及窗口获得焦点，再等待该目标的 OnDraw、对应 Choreographer 帧和不早于该帧的 FrameMetrics，最后通过 PixelCopy 读取同一窗口。超时、目标替换、隐藏、销毁或复制失败都标记无效；回执读取 PackageManager 实际版本及活动页观察到的首页模式，不能确认的字段写空，不补预期值。截图有效与功能检查分别记录，旧加载帧不能作为恢复画面。本工具拒绝安全窗口和非空本机相册，未进入产品 APK。

## 本地完成的验证

- [确定性检查](verification/s95-unit.json)：18 项上传确认状态、16 项帧门槛模型、26 项期限、9 项焦点组合及 4 组原首页路由通过。模型通过不等同 Android 弹窗或真实绘制通过。
- [浏览器](verification/s95-browser.json)：1920×1080、960×540，各测空相册及两张合成照片；中性首焦点、连续两次确认无上传导航、方向键正常移至控件。全部请求进程内拦截，没有 HTTP 服务或上传端口。
- 标准 ARM 和 x86 release 构建及现有发行证书签名通过。213 项 Android/launcher 输入相同，指纹 `679928fa86bf5347ec90a4ed9887b2352dddf4ed4d7be10747683e3717be0675`；产品包拒绝包含探针、假会话和截图工具类。
- [Android 测试编译回执](verification/s95-probe-compile.json)：包含 R1 回归与 R2 原生确认、全程遥控诊断、长中文行、帧完成/延迟/隐藏/替换/销毁/超时测试。仅编译，未在本机执行。测试 APK 无网络权限，上传工厂只能返回内存替身。

首次浏览器启动因原脚本指向已不存在的 Chromium 路径失败，随后使用已有 Chrome 通过，没有安装软件。探针首次编译使用了非公开 Choreographer 方法，改为公开 FrameCallback；第二次编译修正 lambda 捕获的可变局部变量。失败日志保留，不能并入通过项。

## 已核对的上一轮云证据

[R1 独立复核](verification/s95-r1-cloud-review.json)绑定私有 zip SHA-256 `4c451b646ad6967dfd389802d94e61f515349960c6cd1b0e9bef797fce88a767`。API28 / WebView66 / 1280×720，实际运行同输入云 debug 包；51 项受控 Activity 检查、四入口无来源态及退出取消有通过证据。诊断入口用了触控，不算全程遥控验收；两张恢复图为旧加载帧，不算有效恢复画面。这些都是 v37 结果，不是 v38 通过结果。

## 云端复跑

在既有隔离环境覆盖安装测试探针，不操作用户电视，不开启真实上传，不授予权限或修改 density。测试 APK 的实际版本为 38 / `.35-probe`，回执中必须保留这个实际标识。

```sh
adb -s "$TEST_SERIAL" install --no-streaming -r artifacts/s95-native-final-v38/probe.apk
adb -s "$TEST_SERIAL" shell am instrument -w org.zhixiaoce.test.round2v38/org.zhixiaoce.aperture.RecoveryRunner
adb -s "$TEST_SERIAL" shell am instrument -w org.zhixiaoce.test.round2v38/org.zhixiaoce.aperture.Round2Runner
adb -s "$TEST_SERIAL" shell run-as org.zhixiaoce.test.round2v38 cat files/s94-recovery.json
adb -s "$TEST_SERIAL" shell run-as org.zhixiaoce.test.round2v38 cat files/s95-round2.json
```

从测试包私有目录逐项收集 `s95-*.json` 与关联图片，核对 `valid`、实际版本/模式/目标、时间和帧条件。R1 回归应保留 51 项功能；R2 结果及图片单独核对。生产 x86 候选还需独立验证四入口无来源、Back 焦点、首页退出取消、诊断元数据及菜单往返，不能用隔离探针替代整个安装包验收。

本地候选提交、源码 patch/bundle、两包的路径、大小、证书和 SHA-256 统一在 `artifacts/s95-v38-delivery/ROUND2-MANIFEST.json` 中记录。真实小米85 QLED默认显示、原桌面/HOME/冷开机、小爱/米家、正式 ARM、现代网页内核、照片上传和真实播放仍未取得本轮验收，不宣称系统广告已移除。
