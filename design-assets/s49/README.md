# 开视生成素材 · S49

使用内置 **imagegen** 工具生成，三次独立请求；没有使用CLI/API密钥路径，也没有上传私人照片或第三方应用标志。提示词和输出均保留。工具未返回具体模型版本，不推断模型名称。

| 原图 | 原始尺寸 | 页面用途 | 运行时文件 |
| --- | --- | --- | --- |
| [暮色湖面](originals/dusk-lake.png) | 1672×941，RGB | 可选壁纸，标记“生成背景” | `public/launcher/art/dusk-lake-v1.webp`，另有480px缩略图 |
| [玻璃档案](originals/memory-archive.png) | 1254×1254，RGBA | NAS摘要插图 | `public/launcher/art/memory-archive-v1.webp`，512px |
| [玻璃燕子](originals/glass-swallow.png) | 1254×1254，RGBA | 应用选择页入场动画主体 | `public/launcher/art/glass-swallow-v1.webp`，384px |

[背景提示词](prompts/dusk.txt)、[档案提示词](prompts/archive.txt)、[燕子提示词](prompts/swallow.txt)。原图不做重绘，透明背景保留。`scripts/prepare-generated-art.sh`仅编码与缩小运行时资源，不进行AI补画或放大；四份运行时文件合计216156字节（约211 KiB）。原图不打入APK；运行时文件由现有Android assets目录自动纳入后续构建。生成湖景为虚构场景，不标注真实地名、摄影作者或CC0。它不是原生4K图，现有4K摄影默认壁纸保持。

动画由真实页面CSS实现：应用卡片依次展开/收回、燕子一次入场、NAS插图一次获焦反馈。生成的是可复用位图原料，不把单张图冒充视频模型输出或可交互界面。减少动态/关闭动画/失焦时有回退。

完整记录：[首页说明](../../docs/HOME-PERSONALIZATION.md)、[素材摘要](manifest.json)、[录屏](../../media/captures/s49-fold-and-art.webm)。上述相对链接以本目录为准；原始提示词供后续二次生成，生成结果不保证逐像素可复现。

这些生成素材作为项目自制素材随源码提供，遵循项目LICENSE在适用权利范围内使用；不宣称排他版权或第三方品牌授权。正式应用标志只来自用户已安装软件，未纳入生成素材。
