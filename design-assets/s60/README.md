# S60 详情页参考

`detail-reference.png` 由内置生图模型生成。完整实际提示词见 [prompts.json](prompts.json)。依据用户v20测试报告，探索深色柔化背景、海报、真实资料、单主要动作和同片单内容。图片中的片名、评分和影片资料均为合成设计内容，不作为产品数据。

采用信息层级与构图；不采用固定六条推荐、假评分、重画的品牌资产或截图中的精确几何作为硬性布局。实现继续复用TvUi、CinemaArtwork和既有授权/播放模型。动画为代码中的背景淡入；没有生成动画素材，也不声称图片能够证明动画或交互。

参考图不打包进APK。实际浏览器截图为 [详情](../../docs/images/s60-cinema-detail.png)、[首页](../../docs/images/s60-home.png)、[播放器](../../docs/images/s60-cinema-player.png)。它们也不是Android原生截图；原生运行限制见 [修复对照](../../docs/V20-FEEDBACK-S60.md)。
