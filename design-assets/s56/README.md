# S56 · 影视内页参考

使用内置imagegen技能生成两张设计参考：

- `cinema-library-reference.png`：目录、图像渐隐、海报与大焦点；完整提示词`PROMPT-library.txt`。
- `cinema-interior-reference.png`：直播侧栏与详情/选集；完整提示词`PROMPT-interior.txt`。

这些是模型生成的方案参考，不是已实现画面或真实节目资料，不打入APK。采用深灰背景、暖白焦点、明确图像/文字分工和大按钮；原生实现采用轻量渐变/描边，浏览器参考相同层级。没有把生图里的CCTV台标、虚构海报、人物或节目时刻表当成内置频道、授权内容或EPG。

实际页面见`docs/images/s56-cinema-*`：浏览器通过真实本地HTTP服务读取合成Jellyfin/资料服务记录；海报是项目既有风景图作为测试夹具，视频由Canvas/MediaRecorder生成。原生代码不是这些HTML的截图贴图，两者分别验证。爸妈版简化搜索/导航、保留大字号，并压低海报高度，避免首屏看不到片名。

官方TMDB Logo原字节保存在`public/cinema/tmdb.svg`；原生PNG为浏览器按相同比例栅格化，用于资料服务署名，不是imagegen生成的商标。来源：https://www.themoviedb.org/about/logos-attribution 。对应下载URL、尺寸和SHA-256见manifest。其商标和资料不适用本项目MIT。
