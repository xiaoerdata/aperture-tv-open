# 窗光 · 开窗的设计语言 v1

2026-10-24，回应提示词 P08。当前为可评审实现，用户尚未确认最终审美。

## 设计简报

电视为三米外的家人呈现当下重要的内容；电脑上的个人助手负责受权编排，手机负责首次确认和撤销。电视首屏主角是窗景与一句家庭消息，指引首屏主动作是“走进示例家庭”。大屏保持舒展但有明确对齐，手机按任务顺序重排，不缩印电视画面。

我们的识别来自拱窗、日光、照片留白与中文生活内容。透明材质只落在导航、场景切换与小型工具栏；照片、日程、数据、长文仍有稳定底色。避免整页玻璃卡片、动态折射文字、荧光描边和无意义的漂浮。

## 参考与取舍

| 一手来源 | 借鉴 | 不移植 |
| --- | --- | --- |
| [Apple HIG Materials](https://developer.apple.com/design/human-interface-guidelines/materials) | 功能层与内容层分开；克制使用透明；减少透明/增强对比时降级 | 专有资产、Apple 字体、原生 Liquid Glass 名称与性能承诺 |
| [Radix / WorkOS Typography](https://www.radix-ui.com/themes/docs/theme/typography) | 字号、行高与行宽成套；系统字体回退；层次来自阅读比例 | 英文网站字号直接套入中文电视；组件栈整体引入 |
| [Carbon / IBM 2x Grid](https://www.carbondesignsystem.com/building-blocks/foundations/2x-grid/overview) | 8px布局节奏、网格与基线、按断点重排 | 企业后台的信息密度与复杂装饰 |

本轮实际读到 Apple 官方 Markdown、Radix 和 Carbon 文档；浏览器打开并查看 Apple、Radix 的桌面实际页面截图。参考证据保存在开发目录 artifacts/design-references；未复制参考图片到产品。Radix Themes MIT、Carbon Apache-2.0 的已有平台许可核查仅供参考，本次没有引入二者组件或字体；Apple 文档与图片保持原权利，不随源包再分发。

## 材质、文字和动效

- 内容底色 #f8faf9，正文 #223632，辅助 #53655f，主动作 #246754。内容自然色来自照片与场景；不把全部面板染成同一种强调色。
- 玻璃采用88%浅色底、12px局部模糊、一条亮边与轻投影。不支持 backdrop-filter 时使用实底；“减少透明”跨刷新记忆，系统减少透明/增强对比优先。
- TV只在导航和工具栏采样背景；场景Canvas不会被全屏模糊。没有持续更新CSS模糊或液体折射滤镜。是否能在小米电视流畅运行仍需实测。
- 控制反馈180ms；连续场景动画由已有引擎统一管理。尊重减少动态、暂停、离屏与后台状态。
- 电视主要信息以相对大字号呈现；手机正文与操作维持现有共享控件，760px切换模式。材料层不重写共享控件尺寸。
- 焦点轮廓、按钮语义、选中状态文字均保留。长标题最多三行；真实内容密度、电视过扫描与三米观看需要现场验收。

实现：public/ui/material.css 与 material.js；电视、工作台、指引、影院复用。插画来自项目原创CSS/Canvas，未把外部界面截图当背景。

## 评审证据

docs/images/guide-hero.png、guide-desktop.png、guide-phone-hero.png、guide-phone.png、workbuddy-tv.png、workbuddy-phone.png；另见验收记录的宽度、键盘、减少动态与主要任务测试。设计稿可以继续迭代；测试通过不代表已经获得用户审美确认。
