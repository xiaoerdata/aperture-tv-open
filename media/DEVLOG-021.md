# 第二十一期：第一次进入真正的Android运行时

S35使用官方Android TV模拟器，Android8/API26，实际WebView58。两版v10安装并显示原生首页；不是浏览器仿UA，也不是小米电视实拍。

画面：docs/images/s35-native-standard-api26.png、s35-native-parents-api26.png、s35-native-settings-api26.png。持续字幕应为“Android TV API26模拟器 · 原生简洁首页 · 非小米真机”。该老内核路线不展示现代玻璃背景，不用它冒充4K背景或真机性能。

分镜草稿：展示两版原生页，再展示方向键进入设置与保存偏好；字幕“旧内核还有原生入口”。说明原桌面仍由系统解析；P30提出后，下一阶段检查Home候选的主动开启与米家原生体验。

保留下载中断、SDK登记、早于安装启动、错误爸妈组件名、等待超时和系统codec异常。不要剪成“一次成功”“全部小米适配”或“影视已通过”。本期仅截图与分镜，没有新成片、配音或平台上传。

同时修复源码导出覆盖同版本归档的问题，两个临时夹具通过，原v10 tar散列未变。复现见docs/ANDROID-EMULATOR.md与tests/export-source.test.mjs。
