# 播放返回 · S140

P152反馈播放影视剧时返回会直接到开视首页。本轮修正可确认的代码路径；没有电视日志，不能据本地验证认定用户设备的完整根因。

播放返回顺序：先关闭选集、线路或节目面板，再取消进度预览或退出主动进入的工具条；返回影片详情，再返回打开影片前的片单。只有片单根页面的独立一次返回才退出影视模块。直播已有侧栏与节目面板仍优先关闭。

详情在本次访问期间保留已解析的影片和线路模型。从播放器回来不重新请求来源，来源此时断网也能显示原详情；从片单重新进入影片仍重新获取详情。更新播放线路、集数和续播状态，选集入口恢复当前集焦点；普通详情保留滚动位置。片单沿用已有来源、分类、页码、已加载影片、搜索、年份、地区、排序、焦点及滚动状态。收藏或历史直接播放返回影片概览，再回原收藏或历史页。

影视Activity统一接管返回键的按下和松开，只有匹配本次按下、未取消且页面代次未变的松开才执行一次返回。重复、孤立、过期的松开不会再次关闭页面；暂停、失焦和销毁取消待处理返回。系统语音键依旧交给系统。不改系统HOME、density或分辨率。

## 本地验证

```bash
python3 scripts/s140-back-test.py
python3 scripts/s138-player-keys-test.py
python3 scripts/s125-episodes-test.py
python3 scripts/s123-live-test.py
python3 scripts/s130-source-return-test.py
JAVA_HOME="$PWD/.android-local/jdk17" node --test tests/playback-session.test.mjs tests/playback-entry.test.mjs tests/player-remote.test.mjs
```

23项返回/缓存详情检查逐字提取生产dispatch、返回路由和详情回调，结合真实目录、续播、播放计划和收藏类，UI、网络及播放启动使用明确替身。覆盖播放后来源断网、原片单上下文、切线/选集/进度、收藏入口、重复/取消/过期返回及面板关闭优先级。选集20项检查包含恢复当前集焦点；左右键22项Timeline、10项Cinema及8项NAS提取检查独立回归。最初夹具编译遇到私有访问、前向引用和替身override声明问题，修正夹具后通过，不把这些失败当作产品根因。

[返回验证](verification/s140-back-navigation.json) · [选集验证](verification/s125-episode-append.json) · [左右键](verification/s138-player-keys.json) · [原生API编译](verification/s140-native-compile.json)

没有连接电视，不声称Android真实返回分派、实体遥控器或小米解码已验收。可复现取证脚本[s140-device-return-check.mjs](../scripts/s140-device-return-check.mjs)使用已配对局域网会话：先在影视播放画面运行`node scripts/s140-device-return-check.mjs --output /绝对路径/新证据目录 --screenshots`。仅发一次返回，保存前后状态、UI与可选截图，超时不盲目重发；证据保持私有，不上传凭证或私人片单。WorkBuddy仍需分别复测片单、收藏、切线后播放、断网返回及长按返回。
