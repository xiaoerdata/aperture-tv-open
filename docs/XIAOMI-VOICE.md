# 小米电视、小爱同学与唤醒

状态：原生语音入口从私有v4起提供，v6包含NAS窗口中断处理；源码v7进一步处理独立音频焦点。实际APK以[私有下载索引](DOWNLOADS.md)为准，尚未在实体小米电视验证。

## 用电视原来的小爱

开窗保留小米电视的原生语音助手。它不替换默认助手，不接管麦克风，也不安装新的常驻唤醒服务。

- 蓝牙语音遥控器：按住语音键说话，说完松开。开窗的遥控路由明确让ASSIST、VOICE_ASSIST、SEARCH与HEADSETHOOK继续走系统处理，保留长按、重复与松开事件，不映射为OK。实际机型的厂商键码可能不同，仍需实测。
- 远场唤醒：支持的机型由原系统小爱同学管理开关和麦克风。协同唤醒可能由家中另一台设备应答；麦克风被遮挡、遥控器蓝牙/电量、网络也影响使用。
- 待机唤醒：属于电视固件、电源和硬件能力，不能从Android版本或发现助手入口推断。开窗不申请唤醒热词权限或修改电源设置。
- 小爱浮层：窗口失去焦点即暂停开窗WebView/定时器，并尝试暂停当前网页媒体；恢复焦点且Activity已恢复时继续当前页面，不重新导航、不重置遥控焦点、不自动继续视频。没有用“Activity没暂停”作为“助手没出现”的判断。

小米一手资料：[语音控制电视无响应问题](https://cdn.cnbj1.fds.api.mi-img.com/ics-resources/articles/648fb1df17f0c706451d47e5.html)。该手册明确按型号/系统版本可能不同，本项目不把手册路径当所有小米电视的固定界面。

## 开窗里的入口

标准版「家庭工具 → 小爱同学与语音」提供：打开系统语音助手、语音键与唤醒指引、语音与系统设置。爸妈版在「家人设置」内提供同样指引，首页仍为三个主要入口；原生菜单也保留该入口。

只探测公开ASSIST与VOICE_COMMAND的可启动系统Activity，要求系统/已更新系统应用、exported、enabled且当前应用有启动权限；优先系统默认项，多候选时明确由用户选择。不使用猜测的小爱包名、私有广播、反射或signature权限。语音设置先尝试公开VOICE_INPUT_SETTINGS，固件未开放则转系统设置或给出手动路径。

ACTION_VOICE_ASSIST在当前AOSP标记为隐藏/SystemApi，不作为第三方启动方式。来源：[Android Intent源码](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/content/Intent.java)。发现公开入口只表示入口可尝试打开，不能证明原生唤醒已开启；没有公开入口也不能认定没有小爱。

小爱理解“打开开窗”、搜索片库或直接控制开窗分区，取决于小米对第三方应用的语义/分发支持，尚无验收证据。不能把Google Assistant App Actions当作小爱能力。自定义家庭事务仍使用既有受授权Agent入口，没有新建绕过授权的语音写接口。

## 新版离线资源

排查原生外壳时发现，旧精确白名单仅含HTML/CSS/JS/客厅SVG，会拦住深色版新增PNG和字体。改为本机虚拟HTTPS源`https://appassets.androidplatform.net/assets/index.html`，精确服务7份必要资源；未知路径、外网与家庭API不回退到网络。不开放任意文件前缀、跨源文件访问或JavaScript原生对象。

采用Android推荐的HTTPS同源资源思路，项目自行实现小型固定路径处理器，没有声称使用或重新实现完整WebViewAssetLoader库。来源：[Android加载应用内内容](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content)。`DisplayLifecycle`处理原生焦点，`LobbyAssets`处理资源边界，`VoiceAssistant`处理系统助手，MainActivity只装配这三部分。

WebView.onPause不会自动暂停JavaScript，定时器需pauseTimers；该调用作用于本进程WebView，所以这里只有一个WebView并在生命周期恢复时配对resume。媒体暂停是尽力处理，厂商音频焦点和实际播放恢复仍需设备观察。来源：[Android WebView API](https://developer.android.com/reference/android/webkit/WebView#onPause())。

## 已验证与未验证

### NAS影片与音频焦点（源码v7）

仅观察窗口焦点还不够：助手可能只占用音频而不遮住页面。新增`PlaybackSession`管理明确的播放意图，`PlaybackAudioFocus`只装配Android API26+音频焦点；NasActivity保留播放器和界面，所有开始播放均通过同一控制器。

- 用户选择影片且Activity已恢复并有窗口焦点时，为这次异步打开/准备申请音频焦点；只有焦点已获准、准备完成、Surface可用且意图没有被中断时才开始。选择目录/照片不会申请音频。准备阶段占用焦点是为了收到期间的音频中断，避免迟到onPrepared重新抢回助手音频；返回、暂停、失败会取消。
- 使用相同的`USAGE_MEDIA / CONTENT_TYPE_MOVIE`属性配置请求与MediaPlayer。设置`willPauseWhenDucked=true`：临时失去、永久失去或要求降低音量，均暂停并撤销本次意图、放弃焦点，不继续压低音量播放对白。
- 不接受延迟获得焦点。申请失败显示「声音正忙，稍后按播放继续」。`AUDIOFOCUS_GAIN`不是播放指令；小爱退出、重新得到窗口焦点、Surface重建都不会续播。按「播放」才发起新请求。
- 暂停、播完、出错、返回与Activity停止释放音频焦点。每次请求有独立代次，旧影片/旧请求的迟到回调不能暂停新影片；同步中断先于申请返回也不会被返回的granted覆盖。准备中按暂停同样取消自动开始。
- 不申请麦克风权限，不添加前台服务、不替换小爱或接管系统语音键。目标SDK35设备对非顶层/无前台服务的音频请求有限制，失败按拒绝处理；没有后台继续出声的回退。

依据：[Android音频焦点指南](https://developer.android.com/media/optimize/audio-focus)、[AudioFocusRequest.Builder](https://developer.android.com/reference/android/media/AudioFocusRequest.Builder)。这些是通用Android机制，不证明小米固件一定发送相同事件。外部直播/影视应用与家庭服务网页播放器不由这个原生NAS控制器接管，须分别验证。

`node --test tests/playback-session.test.mjs`实际编译运行生产控制器，覆盖10组准备/播放中断、拒绝、同步/旧回调、窗口/Surface、主动暂停/继续、播放错误场景；音频驱动和播放器是合成端口，未执行Android AudioManager或MediaPlayer。报告见[本轮验证](verification/playback-focus.json)，可复现素材见[第十五期](../media/DEVLOG-015.md)。

真机补测：在准备影片和正常播放时分别唤醒小爱，观察窗口不失焦时是否也暂停；关闭助手后保持暂停，主动按播放才继续。再测声音忙/请求被拒、播完、断网、返回目录及另一影片，检查无旧声音和无意外续播。保留型号、固件、结果和错误文字即可，不录家庭语音、凭证或私人片库。

- `node scripts/check-native-compat.mjs`：两种edition对Android35 API进行javac源码检查；实际运行纯Java生命周期与资源边界用例。不是Gradle/资源/Dex/APK构建，不是真实Android执行。
- `node scripts/browser-xiaomi-qa.mjs`：用实际Java资源策略服务模拟HTTPS源，实际Chromium加载背景/字体、验证不访问家庭API、拒绝未知路径、语音分区与返回、原生焦点调用契约和非消费语音键名。不是系统小爱/热词测试。
- `node scripts/browser-launcher-qa.mjs`：确认原遥控入口、标准/爸妈版、家庭屏往返与布局仍可用。

必须完成的真机记录：

| 环境 | 操作 | 证据 |
| --- | --- | --- |
| 原生桌面与开窗首页 | 同样长按、说话、松开语音键 | 是否均出现原生小爱，是否结束监听 |
| 开窗分区、弹层与播放页 | 唤出小爱后退出 | 页面/焦点是否保留，媒体是否暂停且不自动播放 |
| 支持远场的型号 | 先在原桌面，再在开窗内喊小爱 | 唤醒是否一致，物理麦克风与协同唤醒设置 |
| 待机 | 原系统支持的条件下唤醒 | 是否能唤醒、回到哪个桌面；失败不得归咎于未证实的开窗原因 |
| 无公开助手Activity的固件 | 用语音键；点击开窗语音入口 | 原生语音键继续由系统处理，开窗给出指引而非崩溃 |
| 旧WebView与断网 | 打开首页、工具与原系统 | 离线背景/字体可用或正常回退，本机应用不依赖家庭服务 |

记录厂商、型号、系统/固件和遥控器类型即可；不用采集设备序列号、账号、MAC或家庭录音。当前没有这些实机证据，不标记“全型号兼容”。

开发机可运行`npm run qa:xiaomi`和`npm run check:native`。需要JDK17；后者还需Android35 SDK的android.jar。可通过`APERTURE_CHECK_JDK`、`APERTURE_CHECK_ANDROID_JAR`指定隔离路径，也识别常规JAVA_HOME和ANDROID_HOME/ANDROID_SDK_ROOT。本机既有隔离工具只是开发回退路径，工具/JDK/SDK均不随源码分发。
