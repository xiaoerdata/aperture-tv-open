# S54 · 第二套标识与爸妈版

P56指定所有标识统一为第二个方案。标准版继续使用S53的[v2开放视窗原图](../s53/kaishi-icon-master-v2.png)；爸妈版以该图为编辑目标，通过内置image_gen加入👴👵表情元素，保留白色视窗及右下开口。

[爸妈版原图](kaishi-parents-master-v2.png) · [实际生图提示词](PROMPT-parents-v2.txt) · [制作前简报](BRIEF.txt) · [两版对照](logo-comparison.png)

Android：标准版继续使用main资源，爸妈版用同名flavor资源覆盖512px安装图标和640×360电视横幅。旧ic_window/banner资源名转为指向v2的兼容别名，不再绘制旧播放图形。网页：统一安装指引、家庭屏、轻量屏、工作台、影院和创作页的标识及全部页面favicon；爸妈入口使用emoji变体。第三方应用原厂Logo保持其自身标识。

原图由内置生图工具生成，无外部API密钥；衍生图仅做等比缩放和格式导出。横幅与对照板用真实图标、Noto字体和HTML排版，由浏览器截图导出，不用模型生成汉字。具体路径、尺寸和SHA-256见[清单](manifest.json)。

[浏览器检查](../../docs/verification/s54-brand.json)与[爸妈版首页](../../docs/images/s54-brand-parents.png)是本地页面结果，不是电视真机。本轮未构建APK、未提交、未推送、未改Release；公开v18安装包保持原样。后续发行前须递增版本并重新打包核对。
