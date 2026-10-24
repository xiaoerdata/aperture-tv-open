# 爸妈版：把准备工作留给家人

对应P21/P22，开发底稿见docs/process/JOURNAL.md S18，原话见PROMPTS-001。所有画面来自实际Chromium页面；背景为原创AI山湖，影片与米家状态为合成测试，不是真机/真实账号录像。未上传小红书。

建议35秒，3:4剪辑时将1920×1080画面完整嵌入上下字幕区，保留三入口和焦点边框，不裁成看不清遥控操作的局部。

| 时长 | 画面 / 可复现素材 | 字幕 |
|---|---|---|
| 0–6秒 | docs/images/parents-update-parents-1920.png，三个大入口依次突出 | 「给爸妈用，首页就留三件事。」 |
| 6–12秒 | docs/images/parents-series-1920.png，进剧集并返回 | 「电视、电视剧、电影，分清楚就好。」 |
| 12–22秒 | docs/images/parents-media-admin.png，来源与检测结果 | 「片源我来准备。打不开的，后台先发现。」 |
| 22–29秒 | docs/images/xiaomi-home-data-1920.png | 「普通版还能放上家里的温度。先选好，再分享。」 |
| 29–35秒 | 回到爸妈版三入口 | 「新界面还在内测。下一步，是放到家里的电视上试。」 |

口播：以前给爸妈装电视应用，总怕他们点进设置后找不到回来。这次把首页收成三个选择，配置都放在家人的后台。每次更新片库会自动检测，平时定时复查，但检测出媒体能读，也不等于电视一定能解码。米家则走自己的Home Assistant授权，只显示选中的状态。现在先把页面和流程跑通，还没把浏览器演示说成真机成功。

复现：`node scripts/capture-glass.mjs parents-update`生成本阶段首页（后续源码已增加小快捷栏，S18原图按文件保留）；`FFMPEG_PATH=/path/to/ffmpeg npm run qa:cinema`生成后台/剧集；`node scripts/browser-home-assistant-qa.mjs`生成设备数据；`node scripts/capture-glass.mjs parents-update`生成同阶段八张视觉截图。运行脚本使用临时服务与合成账户，不录入口令、二维码、token或真实设备编号。脚本默认不产出剪辑视频，不将静态转场称为真实遥控录像。

保留失败：首次影院脚本未找到PATH中的ffmpeg；改用已存在的隔离媒体运行时。最初后台截图发生在轮询前，状态仍为“配置来源后开始”；截图条件改为检测结果实际显示后再拍。
