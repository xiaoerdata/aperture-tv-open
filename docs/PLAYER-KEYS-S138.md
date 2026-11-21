# 播放画面左右键 · S138

P151要求电视上的影视资源播放默认用左右键快退、快进。确认V49的点播分派依赖`getCurrentFocus()==videoSurface`或工具条隐藏：Android若把初始焦点放到播放按钮，即使用户没有进入操作菜单，左右键也会变成切按钮。NAS旧逻辑还会先用方向键唤出工具条并聚焦播放按钮。

## 调整后的操作

- 普通点播和NAS播放画面：左键快退、右键快进。工具条自动出现或Android初始焦点落到按钮，都不改变默认进度操作。
- 短按预览10秒，松开直接跳转，不需再按确定；长按沿用20秒/40秒步长，松开只提交一次，边界停在0和总时长。
- 只有主动按上下或菜单键进入工具条后，方向键才用于选择按钮；返回先退出工具条，恢复默认进度操作，再按返回退出播放。选集/线路等已打开的选择列表仍优先导航，不被进度拦截。
- 直播侧栏左右换分组保留。语音/小爱按键仍交还系统。没有时长的流不虚构可跳转进度。

`PlayerTimeline`继续只管进度，不接管播放状态或音频焦点。松开需匹配本次方向键和downTime；取消或失焦撤销未提交预览，迟到的旧keyup不提交新预览。NAS的默认远程进度提示改为“松开跳转”，手动进入进度控件时仍保留其显式操作方式。

## 验证

```bash
python3 scripts/s138-player-keys-test.py
python3 scripts/s123-live-test.py
JAVA_HOME="$PWD/.android-local/jdk17" node --test tests/playback-session.test.mjs tests/playback-entry.test.mjs tests/player-remote.test.mjs
KAISHI_NATIVE_RECEIPT=docs/verification/s138-native-compile.json KAISHI_NATIVE_OUTPUT=artifacts/s138-player/native-compile python3 scripts/s129-native-compile.py
```

实际`PlayerTimeline`在明确UI与Media替身下22项检查；Cinema/NAS的实际dispatch方法逐字提取，分别10/8项检查，覆盖按钮初始焦点、工具条显隐、默认左右松开提交、显式进入控件、返回、语音、选集列表与直播组优先级。另回归直播侧栏及既有播放意图控制。源码API编译和安装包验证独立记录。

[左右键验证](verification/s138-player-keys.json) · [直播回归](verification/s123-live-controls.json) · [API编译](verification/s138-native-compile.json)

这些检查没有执行实体遥控器、Android焦点管理或解码器。V50同时保留[V49原生照片屏](NATIVE-HOME-ALBUM-S136.md)，海信照片实际显示仍待用户覆盖复测。界面样式未修改，无新增冒充真机的截图。
