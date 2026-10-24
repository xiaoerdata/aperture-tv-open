# 第十八期：按遥控器重新排

需求原文P28/P29，过程JOURNAL S31。先审阅，不上传。

图片：

1. 旧版：docs/images/current-panels-overview-1920.png。
2. 改版：docs/images/remote-final-overview-1920.png；八入口两行四列。
3. 爸妈：docs/images/s31-parents-1920.png；三主入口加常用应用。
4. 设置：docs/images/s31-home-mode-standard-1920.png；单列大按钮。
5. 播放：docs/images/s31-cinema-player.png；实际桌面浏览器VP9解码夹具，大按钮暂停/前后跳转。
6. 4K界面：docs/images/s31-standard-3840.png。

旁白草稿：“这次把入口的说明删了，按钮和字号重新放大。标准版两行四列，爸妈版三个观看入口。上下同列，左右到边就停，返回后还在原来的位置。”

背景另配字幕：“3840×2160；Cem Sagisman / CC0；随APK内置。”出处见THIRD_PARTY_NOTICES。不要将照片说成原创或把两次1672×941生图叫4K。原始生成提示词、输出、来源与失败保留在media/research/background-4k和过程日志。

复现：node scripts/capture-glass.mjs remote-final；node scripts/browser-tv-controls-qa.mjs；node scripts/browser-home-mode-qa.mjs。影院截图来自browser-cinema-qa，APERTURE_QA_IMAGE_PREFIX指向artifacts/s31-media，避免覆盖s20旧素材。录制遥控动作需沿用apertureRemoteKey，不用鼠标移动冒充物理遥控器。

全部是浏览器页面与合成家庭/媒体数据，不是电视实拍。4K像素检查不能证明电视观感、耗电或老机性能；原生NAS控件只有API编译，暂无实机画面。本轮不编造原生文件列表、用户反馈或性能数字。

目前保存静态图与可复现脚本，尚未制作本期成片或配音。后续剪辑持续标注浏览器演示，真机部分另拍。


打包补记（S32）：v10两版从干净fb147cd一次免费本地构建49秒。实际APK中3840×2160 JPEG、首页资源、原生类型与清单已核对；无Git源码归档输入与包相同。当前内测索引指向v10；尚无电视安装/性能画面。下载说明与这条补记在冻结源码包之后生成，不假称在包内。本期仍仅静态素材与分镜。
