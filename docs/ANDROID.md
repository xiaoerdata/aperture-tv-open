# 小米电视外壳：使用与真机验收

本项目仅适配小米电视，Android 8+ 为最低系统要求。Android TV 模拟器只用于开发验证。

## 当前状态

已提供Java外壳、Manifest、Gradle配置和原创图标。按用户P13的免费替代要求，已在隔离的本地工具环境构建debug APK并验证签名、包信息与SHA-256，无GitHub托管执行费用。产物见private-preview；S35已在官方Android TV API26/WebView58模拟器实际安装启动v10两版并检查原生入口/方向键/偏好，见[模拟器记录](ANDROID-EMULATOR.md)；尚未在小米电视安装，正式发行签名和真机验收待完成。

目标 API 26 / Android 8.0 以上，编译SDK35，AGP8.7.3，JDK17，Gradle8.9。完整窗景要求WebView89+；/tv-lite轻量界面使用ES5和XMLHttpRequest，无Canvas、模块脚本和玻璃效果。当前源码本机首页在内核低于89时使用原生简洁入口，家庭屏在37–88时仍选轻量模式，89+可手动选择简洁首页或轻量家庭屏。两项设置独立，见[本机首页选择](HOME-MODES.md)。37为API特性目标下限，不是实测设备兼容承诺；Android 8+仍不等于特定固件可用。不能升级时先尝试简洁首页；本项目不适配其他品牌电视或电视盒子，不建议盲刷固件。

## 开发者生成APK

在有官方 Android SDK 的开发机，安装 Android Studio 所需工具，打开 `android/`。使用本机 Gradle8.9 生成 wrapper，或在 Android Studio 中配置 Gradle8.9：

```bash
cd android
gradle wrapper --gradle-version 8.9
./gradlew :app:assembleStandardDebug :app:assembleParentsDebug
```

本仓库不放未知来源的 wrapper 二进制；生成后应核对官方校验值。公开发布使用自己的签名配置，密钥不入库；debug包不可当正式版本。本轮使用等效的隔离本地Gradle路径完成两版debug定向构建；没有生成或上传wrapper二进制。

在已授权设备上启用安装外部应用，按该型号官方流程安装。若使用ADB，先由人明确批准电视上的调试连接，再对已确认的单台设备安装：

```bash
adb devices
adb -s <confirmed-serial> install -r app/build/outputs/apk/standard/debug/app-standard-debug.apk
```

不提供猜测的电视IP或包名，不扫描家庭网络，不禁用PatchWall/系统桌面，不Root。卸载本应用即可移除外壳；服务端撤销该电视会话。

## 首次使用

1. 安装新版标准版或爸妈版，启动先进入本机客厅；标准版可为直播、影视和原桌面选择电视已有应用。当前源码爸妈版的频道、电视剧和电影由家人通过家庭后台统一配置，原桌面留在家人设置。新版下载见[私有候选](DOWNLOADS.md)，版本区别见[客厅与爸妈版](LOBBY-AND-PARENTS.md)。
2. 需要家庭窗景时，输入自托管服务的HTTPS源地址，例如 `https://screen.example.com`，不带路径或凭证；进入家庭屏后点击连接得到6位码；也可在频道/剧集/电影页直接按「连接这个家」。
3. 手机登录同源工作台，在连接设备处审批；电视领取只读会话后同步。
4. 方向键移动，OK选择；开窗内返回先关闭浮层/沉浸，再回客厅首页。爸妈版首页返回仍留在首页；外部应用返回由其自身决定。
5. 本机客厅与菜单键均可打开应用、原桌面与系统设置。固定本机动作仅由APK内置文件页调用，不开放JS Java桥，不允许远程家庭网页传任意Intent或包名。默认桌面和开机行为仍须真机验证。

源码v11起，HOME别名默认关闭，仅由用户在设置主动开启后成为候选桌面。系统是否提供选择、Home按键是否回到本应用、休眠或重启后是否恢复，完全依赖具体固件；本项目没有启用无障碍劫持、开机广播保活或系统组件替换。

## 必填真机记录

| 项目 | 待填写证据 |
|---|---|
| 电视型号/年份/尺寸 | 系统关于页面照片，序列号打码 |
| Android / PatchWall / WebView | 实际版本；WebView最低要求是否满足 |
| 安装与首次连接 | APK签名摘要、安装结果、配对成功截图 |
| 遥控器 | 上下左右/OK/返回/Home/菜单；没有菜单键时验证备用入口 |
| 输出 | 720p/1080p/4K、缩放/过扫描、3米阅读、照片裁切 |
| 性能 | 三场景各帧时间/内存；2小时运行；后台/前台恢复 |
| 网络 | 断网、恢复、服务重启、会话过期、设备撤销 |
| 原系统 | 系统设置、其他应用、原桌面、退出、卸载恢复 |
| 日历 | 家庭时区午夜跨日；过去/未来日期和完成状态 |
| 摄影 | 真机画面无频闪，曝光/快门固定，隐私内容替换为测试资料 |

填完上述证据后才把该型号从“目标兼容”改为“实测通过”。禁止使用浏览器合成视频写“已经刷好小米电视”。

## 旧电视与轻量模式

`/tv-lite`沿用同一设备会话与家庭领域服务，支持一次配对、留言、今天的4条日程、轮换相册、3项指标、6项生活摘要、5秒更新与Agent写入回执。它不会在电视上运行模型；电脑/NAS承担家庭服务和MCP连接。没有动态窗景和内嵌影视播放，观影可从原生应用菜单打开已安装播放器，或在支持完整模式的设备使用影院。

服务端识别Chromium版本，旧内核访问/tv直接跳转轻量页；浏览器端另有ES5能力回退。Android菜单可切换轻量偏好，旧内核仍强制轻量模式。Android 7及更早系统目前不在APK目标范围；可尝试已安装浏览器访问，但未做保证，或用外接盒子保留电视显示面板。

已在当前Chromium模拟Chrome64 UA并关闭fetch/EventSource/WebSocket测试；ES5解析验证通过。这不是在真正WebView37/64或小米旧机上测试。截图见images/tv-lite-1920.png，记录见artifacts/lite-browser-report.json。

## 私有候选与原生按键入口

见[ANDROID-PRIVATE-TEST](ANDROID-PRIVATE-TEST.md)。新增隔离GitHub runner的debug候选流程；本地默认不做无关构建；P13已验证一次免费定向构建，详见ANDROID-LOCAL。工作流状态、APK、签名和设备安装分别核对，不互相替代。

Android现在直接向页面的apertureRemoteKey传入按键名称；完整和ES5轻量界面共用这一约定，不依赖旧内核的KeyboardEvent构造器，也不依赖只读的数字键码。浏览器专项验证包含从同一入口选择场景、激活链接和轻量控件；物理遥控映射仍待真机。


## 原生NAS与本机首页数据（源码v5）

新增独立、非导出的NasActivity，直连SMB2/3与HTTPS WebDAV，凭证由NasVault/AndroidKeystore加密。浏览/读取复用NasReader领域接口；照片缩放和EXIF方向、视频按位置读取与失焦暂停在原生执行。无需WebView89或家庭服务；真实电视解码与Keystore待验收。见[NAS说明](NAS.md)。

标准版本机HTTPS首页允许两类固定家庭资源代理，凭证不进JS，静态7资源白名单保持。配对后照片/数据/信息分页，过期与撤销清理详情；见[原生首页数据边界](NATIVE-HOUSEHOLD.md)。v4安装包不包含这两项。


## 源码v6：原生片库用途与中断恢复

NasLibrary负责固定ID、电影/剧集/相册用途与相对路径；NasCipher保持v5的AES-GCM记录格式，NasVault只装配本机存储与Keystore；NasNavigation限制选定目录根、保存有限页码/焦点历史。MainActivity只选择原生NAS或既有家庭服务入口，NAS凭证不进入网页。

NAS复用DisplayLifecycle的Activity/窗口组合状态和中断代次。准备影片时被小爱/其他应用打断，不在迟到的onPrepared中自动开始；Surface重建复用现有播放器；退出/停止连接后重进核验同一ID，不自动继续播放。纯Java模型/协议验证不代替实际Android事件顺序验收。
