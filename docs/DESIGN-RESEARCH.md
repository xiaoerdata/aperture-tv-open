# 设计研究与取舍

2026-10-23。本次先读取智小策前端视觉/内容、移动UI v2、人机双入口和模块化约定，再做独立电视产品设计。不是把现有金融/业务界面搬到电视。

## 一手参考

| 来源 | 已查看内容 | 采用 | 不采用/权利边界 |
|---|---|---|---|
| [Android / Design for TV](https://developer.android.com/design/ui/tv/guides/foundations/design-for-tv) | 官方10英尺界面、D-pad、共享设备说明；实际打开桌面与手机页并截图 | 三米阅读、明确焦点、少量遥控动作、家庭共享隐私 | 不照抄页面中的影视封面列表，不使用Google/Android品牌资产 |
| [Android / Create and run a TV app](https://developer.android.com/training/tv/get-started/create) | 官方电视Activity、Manifest和平台支持说明 | leanback声明、touchscreen非必需、电视外壳 | 不将Android版本等同于具体小米固件兼容性 |
| [Primer / Primitives](https://primer.style/product/primitives/) | GitHub维护的设计令牌文档，实际查看桌面/390px手机版 | 控件职责、清晰文字层级、统一颜色/间距 | 不引入整个React组件栈，不复制GitHub标志；其代码MIT不意味着品牌可复制 |
| [FLauncher fork / osrosal](https://github.com/osrosal/flauncher) | 仓库README、目录和GPL-3.0许可元数据 | 作为已有TV Launcher类别参考 | 不复制代码或截图。此前etienn01 GitHub候选链接404，不当作有效来源；项目原始来源为其README指向的GitLab |
| [Projectivy / spocky](https://github.com/spocky/miproja1) | 维护者公开仓库及产品说明 | 作为已有电视桌面和设备差异参考 | 公共发布仓库不等于整套产品代码开源；不把它标为MIT示例或我们的功能实现 |

网页参考的实时桌面/手机截图和成功状态记录位于开发证据 `artifacts/research/`，不纳入公开源码或媒体包，不冒充我们自己的设计成品。没有基于搜索摘要声称完整试用过这些电视产品。

## 自己的设计

电视界面以时间、今日安排、最新优先留言和照片为核心内容；三种窗景有明显不同的空间构成。拱窗形态贯穿标志、相册和媒体作品。日光庭院用浅绿程序地景，记忆展厅用可切换的照片与透视线，数据群岛用有明确数据含义的三座层叠山体。

颜色由内容承担：深绿用于文字与选中，浅绿/青蓝用于场景，橙色用于太阳和纸签边缘。所有场景为本项目自行编程绘制；不是引用真实地形或科学色表，因此不宣称科学测量准确性。照片样例也是原创SVG插画，未使用网络人物照片。

手机侧沿用760px断点与共享控件几何。普通操作按内容宽度，主操作通过共享tone变量着色；不覆盖触控尺寸。家庭草稿留在当前页面的独立表单中，切栏目不重建。

## 实际看图后的修订

- 首屏在1920×1080查看后保留高识别的拱窗相册与左侧便签；没有用屏幕边框或巨大留白撑版面。
- 数据群岛首稿的文字与山脚重叠，已抬高山体基线并下移数值标注。
- 320px工作台初次检查发现日期输入撑开Grid，改为minmax列并在极窄屏重排；没有通过隐藏横向滚动条掩盖。
- 共享移动控件把普通按钮表现为文本入口；主按钮补充 `data-mobile-tone="primary"`，遵循共用规则恢复清晰主动作。
- 录制时发现广义 `[data-scene]` 会同时选中导航与场景容器，收紧为 `button[data-scene]`，避免焦点和交互歧义。

这些是开发稿的观察与修复；用户尚未确认最终美术方向，不以测试通过宣称审美已获认可。
