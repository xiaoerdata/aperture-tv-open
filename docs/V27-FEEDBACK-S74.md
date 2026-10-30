# v27反馈：相册收起、频道与选集

本轮为本地开发迭代；公开v27、版本号、包名和稳定签名不变。

## 本轮行为

- 发行构建关闭相册：首页相册、NAS相册、照片文件预览和相册目录绑定入口关闭。`FeaturePolicy.photos()`仅开发构建开启；浏览器开发预览显式使用`/home?photos=1`。打包首页由原生端传入策略，NAS原生操作另设门禁，不能只靠CSS隐藏。旧照片、NAS连接和相册目录记录保留，服务端存储/开发家庭屏代码保留。
- 原来的家庭屏入口在发行版打开“安排与留言”，保留日程、留言、数据和NAS影片。标准版没有安排时也有可达的空状态入口；爸妈版保持四个观看入口。
- 播放画面获得初始焦点，确认键打开直播频道或点播选集；左右默认控制点播进度。直播上下直接换台；列表打开时上下选节目、左右换频道分组或选集组。播放/暂停通过工具栏或媒体播放键操作，菜单键/点播上下进入工具栏；音量与小爱键仍交给系统。
- 频道、选集列表在浏览时不自动关闭；选中效果有明确状态，支持触摸后恢复方向键。返回先关闭列表。确认键必须与同一页面的按下事件配对，取消、失焦或跨页面残留的抬起事件不启动新操作。
- 详情仅展示该资源的线路和集数，去掉相似影片及其目录采集。选集两列宽行，原始集名自动换行、按内容增高，不删掉描述文字；每组20集，所有集数可在原页换组。多线路在选集上方切换，不再另进“线路→选集”页面。
- 播放中的选集使用宽侧栏、完整标题、当前集定位；超过20集支持左右换组。单集电影同样可以打开列表。
- 分类进入片单，不强行打开第一部影片；详情返回恢复所选海报和滚动位置。顶层返回退出影院。
- 播放舞台申请全窗口与沉浸显示，视频保留原比例；来源内嵌黑边或非16:9内容不会拉伸处理。

## 证据与根因界限

报告称使用小米电视、ADB和截图；这是维护者提供的外部报告，不等于本轮独立真机验收。代码确认此前`sectionLoad`主动打开第一条详情，播放器初始焦点主动放在暂停按钮；报告所猜测的跨Activity按键泄漏不能替代这两处已确认事实。

此前频道高亮依赖`requestFocus()`成功后的回调；触摸模式下请求可能失败，手动选中索引变化不一定有可见反馈。本轮为列表增加独立选中状态和触摸模式焦点支持。该原因仍需系统输入和小米复测验证，不能只用`Activity.dispatchKeyEvent`宣称真实遥控链路已通过。

## 开源参考与取舍

参考[Jellyfin Android TV](https://github.com/jellyfin/jellyfin-androidtv)，读取树版本`7ec47a0014b8ac8bac56d66ed75cbe442706f0ce`的：

- [VideoPlayerControls](https://github.com/jellyfin/jellyfin-androidtv/blob/7ec47a0014b8ac8bac56d66ed75cbe442706f0ce/app/src/main/java/org/jellyfin/androidtv/ui/player/video/VideoPlayerControls.kt)：独立进度控制与播放队列前后操作。
- [CustomPlaybackTransportControlGlue](https://github.com/jellyfin/jellyfin-androidtv/blob/7ec47a0014b8ac8bac56d66ed75cbe442706f0ce/app/src/main/java/org/jellyfin/androidtv/ui/playback/overlay/CustomPlaybackTransportControlGlue.java)：频道列表是明确播放操作。
- [TextItemPresenter](https://github.com/jellyfin/jellyfin-androidtv/blob/7ec47a0014b8ac8bac56d66ed75cbe442706f0ce/app/src/main/java/org/jellyfin/androidtv/ui/presentation/TextItemPresenter.kt)：电视文本目标、焦点与触摸模式兼容。

参考项目GPL-2.0；仅参考交互职责和可达性，没有复制源码。没有照搬其固定文本格尺寸；开视采用随标题增高的两列选集和分组，满足来源长标题。这里引用的是Git树标识，不冒称提交标识。

## 验证入口

- `node --test tests/player-remote.test.mjs tests/cinema-browse.test.mjs tests/cinema-revision.test.mjs`
- `node scripts/browser-s74-photos-qa.mjs`：默认/开发预览、方向键与真实浏览器截图。
- `node scripts/check-native-compat.mjs`：两版API编译，不代表系统输入验收。
- `scripts/run-s74-native.py`、`tests/android/RemoteRegressionRunner.java`：本轮自有模拟器、合成分类/频道/47集长标题与测试视频；注入系统按键和触摸，禁止用Activity直接分派代替此项结果。脚本启动并清理自有AVD；不接管已连接设备。先完成两版x86 debug定向构建并保留脚本指定的构建日志。

最终结果见[本轮清单](verification/s74-summary.json)。小米遥控器、小爱/米家、来源解码和电视性能仍需维护者复测。报告其余应用墙、检索、EPG等建议不作为本次已完成项。
