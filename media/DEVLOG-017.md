# 第十七期：家里的电视，两种用法

本轮先给用户审阅当前界面，不上传小红书。画面是运行当前代码的Chromium截图，家庭记录和App名称为合成示例；背景为既有原创AI风景。没有真实电视、NAS连接或中老年用户测试，不添加“已兼容所有小米电视”的字幕。

## 图片顺序

1. `docs/images/current-panels-overview-1920.png`：标准版八入口、大时钟及家庭摘要。
2. `docs/images/current-panels-photos-1920.png`：相册未连接的真实空态与NAS入口。
3. `docs/images/current-panels-data-1920.png`：合成步数/阅读分页。
4. `docs/images/current-panels-information-1920.png`：合成日程和留言。
5. `docs/images/current-panels-cinema-1920.png`：已安装播放器、原生NAS与家庭服务三条入口。
6. `docs/images/current-panels-parents-shortcuts-1920.png`：三大观看入口与四个小快捷，App名明确标示例。
7. `docs/images/s30-home-mode-parents-1920.png`：家人设置里的首页选择；不是Android原生选择对话框。

标准版内容图复现：`node scripts/capture-glass.mjs current-panels`。爸妈快捷图来自`APERTURE_QA_IMAGE_PREFIX=../../artifacts/s30-regression node scripts/browser-launcher-qa.mjs`的parents-shortcuts-1920画面；复制到上述稳定路径。设置图复现：`node scripts/browser-home-mode-qa.mjs`。尺寸1920×1080；另有1280×720和390px设置验证图。

## 可编辑旁白

“我们给客厅准备了两种入口。想翻照片、看家里的消息，就用标准版；想拿起遥控器直接看节目，就用爸妈版。家人把常用应用和片库选好，日常少找几层。旧电视也在考虑范围里：界面吃力时，可以选择不用网页的简洁本机首页。”

最后一句配代码路由/文字讲解，不拿网页截图冒充原生简洁首页。新路由有生产Java测试和API编译；尚未在旧电视跑性能，也未生成本轮v9安装包。实际APK当前仍以私有下载索引为准。

## 开发过程保留

标准设置底部焦点裁切前图：`docs/images/s30-before-standard-focus-1280.png`。修正后两版设置图见s30-home-mode系列。浏览器用例的桌面外部协议拦截及761px小数滚动边界失败在JOURNAL S30保留；不把测试夹具问题写成小米电视故障。

本轮未生成新视频、无配音或平台上传。可按以上顺序剪辑，持续保留“浏览器界面 / 演示内容 / 非设备实测”标识；真机部分等设备证据齐全后另拍。
