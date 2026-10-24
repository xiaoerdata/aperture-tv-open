# Android TV 模拟器检查

S35 在官方 Android TV API26 x86 revision14、WebView58.0.3029.125 中实际安装并启动了原 v10 两版 APK。模拟器37.2.12使用软件CPU/SwiftShader，无KVM；不是小米电视或物理遥控器。证据见 [实际Android报告](verification/android-tv-api26.json)。

标准版：原生首页显示，ADB方向键可从直播依次移动到屏幕下方设置并打开；继续滚动列表进入首页样式，选择简洁首页，结束应用后重开，偏好仍在。爸妈版：三个观看入口实际显示。两个包的偏好分别读取，标准版true没有自动复制给爸妈版。首页按钮实际目标96px（213dpi，约72dp），系统设置列表项64px（约48dp），不能把所有原生控件都称为72dp。

检查现有v10 HOME候选列表包含两个开窗版本；系统解析仍是原TV Launcher。该结果不证明其他小米固件行为相同。P30后将进一步改为主动启用Home候选；本页S35画面和报告是改动之前的基线。

## 在自己的测试环境复现

使用Android SDK Manager安装emulator和system-images;android-26;android-tv;x86。创建专用AVD ApertureTvApi26Local，设备tv_720p；测试工具与镜像不进入源码包。建议使用支持KVM的开发机；软件模拟的启动、安装与等待时间不能当电视性能指标。

隔离ADB服务端口5049、AVD端口5578。启动前确认端口空闲；示意命令中的SDK工具需位于自己的PATH，ANDROID_AVD_HOME指向自己专用的测试目录。

```bash
adb -P 5049 start-server
ANDROID_ADB_SERVER_PORT=5049 emulator -avd ApertureTvApi26Local -port 5578 -no-window -no-snapshot -no-audio -accel off -gpu swiftshader
# 另一终端，先确认是自己的AVD；所有设备操作都显式指定这个serial：
adb -P 5049 -s emulator-5578 emu avd name
adb -P 5049 -s emulator-5578 shell getprop sys.boot_completed
adb -P 5049 -s emulator-5578 install -r private-preview/launcher-v10/standard/aperture-tv-standard-debug.apk
adb -P 5049 -s emulator-5578 install -r private-preview/launcher-v10/parents/aperture-tv-parents-debug.apk
adb -P 5049 -s emulator-5578 shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER -p org.zhixiaoce.aperture.parents
```

等待安装明确Success，再启动解析得到的完整组件。爸妈版applicationId是org.zhixiaoce.aperture.parents，但MainActivity的Java命名空间仍是org.zhixiaoce.aperture；不能直接用parents/.MainActivity猜测类名。

```bash
adb -P 5049 -s emulator-5578 shell am start -n org.zhixiaoce.aperture.parents/org.zhixiaoce.aperture.MainActivity
adb -P 5049 -s emulator-5578 shell input keyevent 20
adb -P 5049 -s emulator-5578 shell uiautomator dump /sdcard/aperture-check.xml
```

导航证据使用Android input keyevent，而不是浏览器KeyboardEvent。uiautomator结构与screencap都需要等待真实Activity显示；am start -W一次超时不能单独证明进程退出或应当重启。测试只用空白或合成家庭，不导出账号、配对码、NAS密码或私人相册。

## 失败和范围

两次官方组件下载中断，断点续传后长度/SHA1与官方元数据一致，另保留SHA256。手工解压首次丢失可执行位，修正官方ZIP原权限；SDK CLI12识别新XML有警告，补登记官方模拟器版本后AVD创建成功。

首次启动命令早于安装完成；爸妈版首次组件命名错误；am start -W两次超时，但同一应用进程和页面随后实际可见。模拟器控制台的SYN_REPORT符号无效，数字同步可接受但未观察到菜单变化，不计入按键成功证据。均保留过程，不以重启或修改APK掩盖。

镜像自己的media.codec进程在应用启动前发生HEVC/SSE4.2异常；这轮没有影视解码、AudioManager、Keystore、实际NAS或小爱/米家结论。没有禁用系统安全或改编译过滤器来宣称安装通过。小米设备型号、固件、原生唤醒、系统账号与家电仍按真实设备矩阵验收。


## S36：主动开启与移除HOME（标准版v11）

标准版从v10覆盖升级到本地中间v11。主机安装客户端在系统dex2oat阶段结束，exit143且没有观察到Success；未重复发送安装。随后通过PackageManager读到versionCode11，再用dumpsys package的codePath读取实际base.apk SHA256，与v11中间包完全相同。原simple_home=true仍在。不能把这次无KVM的安装耗时当小米电视安装耗时。

升级后HOME列表不再包含标准版。实际方向键进入默认桌面菜单、确认开启后，列表加入标准版HomeEntry；重新进入显示“移除本版桌面候选”，确认移除后它消失。三个阶段系统默认解析一直是原TV Launcher。爸妈版此时仍v10，其旧HOME项未被标准版操作改变。没有将别名选为默认，不证明所有固件的系统选择页有效；也没有测试原本已默认选v10的迁移。截图见images/s36-native-home-api26.png，结构报告见verification/home-entry-api26.json。

复现：确认安装版本和数据后，按菜单进入设置。每次先用uiautomator观察当前选择，再逐次方向键进入默认桌面，分别确认开启/移除；不要把打开菜单和大量方向键无等待地合并，软件模拟会使当前选择不符合预期。每步用`cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME`与resolve-activity核验候选/默认值。只有自己的HomeEntry应变化。原桌面作为默认时，按Home应回原桌面；用户已选开窗为默认的路线仍需另测。

一次自动导航误入影视应用选择，保留失败XML，返回后观察设置当前选项再操作成功。pm path超时60秒，API26的cmd package不支持path；改用dumpsys package读取安装目录后实际散列成功。该报告仅对应v11标准版，v12加入安装指引后的包验证和运行证据另外记录。


S38补记：爸妈版v10→v12覆盖安装明确返回Success，读到versionCode12，随后HOME候选只剩原TV Launcher和系统FallbackHome，两版开窗均已默认不参选。API26镜像无OPEN_DOCUMENT/GET_CONTENT选择器，有系统InstallStart与来源设置；这些是能力查询，不是APK安装/卸载业务通过。v13实际安装器和WorkBuddy新能力由维护者按P34验收，不用v12运行代替v13结果。专用模拟器与5049 ADB服务已关闭。
