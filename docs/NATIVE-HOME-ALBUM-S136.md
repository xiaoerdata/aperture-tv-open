# 首页照片显示路径 · S136

用户在V48海信激光电视仍见黑底，但能看到“确定 · 相册设置”，进入管理后照片正常。这个反馈推翻了上一轮以刷新/首帧本地检查作为充分覆盖的判断；没有连接该设备，不能确定是WebView合成、图片请求还是厂商绘制实现的具体根因。

## 本轮实现

玻璃首页翻到照片屏时，直接打开同一应用内的`HomeAlbumActivity`原生照片页，不再用首页WebView中的全屏图片图层。无需多按一次确定。原生页读取同一个`GalleryStore`和已有照片，用与原生管理页相同的`ImageView/FIT_CENTER`显示。共用的`HomeAlbumView`改为单张不透明图片，移除透明度动画和双图层；保留8/12/20秒间隔、左右换图、5秒检查上传/删除及10秒隐藏提示。原生兼容页采用直接切图，暂不执行网页的渐变、漂移或双图模板；网页预览和独立网页相册保留原模板。

向上返回应用屏，向下返回留言屏，返回键回首页。确定进入原来的相册管理，再返回仍回照片屏。原生小爱/语音与系统HOME保持系统处理。返回页号只接受0/1/3；待MainActivity恢复前台及JS就绪后才恢复，不在暂停网页里反复导航。单次进入有防重，失焦/恢复不叠加多个照片页。极端任务恢复把照片子页当根时，先恢复开视首页。

旧网页内核使用本机风景首页时继续嵌入同一个单图原生控件。父母版没有新增标准版照片入口。未变更电视density、分辨率、系统组件、照片目录、NAS配置或账号数据。`HomeAlbumActivity`为非导出页面，沿用现有本机控制授权和FLAG_SECURE检查；相册管理仍不在远程控制允许列表中。

## 验证层级

```bash
python3 scripts/s131-album-test.py
python3 scripts/s136-home-album-test.py
CHROMIUM_PATH=/opt/google/chrome/chrome node scripts/s136-home-album-browser.mjs
KAISHI_NATIVE_RECEIPT=docs/verification/s136-native-compile.json KAISHI_NATIVE_OUTPUT=artifacts/s136-photo/native-compile python3 scripts/s129-native-compile.py
```

原生控件实际生产逻辑在存储/UI替身下运行；Activity在明确生命周期替身下运行，Main返回方法逐字提取执行。浏览器加载完整HTTPS首页，方向键进入照片页；只把自定义scheme发送替换成记录，验证只请求一次原生页、网页照片层关闭、恢复焦点不重入，以及三个分辨率返回0/1/3。它没有执行Android图片绘制或海信合成器。

[原生控件](verification/s131-album-native.json) · [页面与返回](verification/s136-home-album-native.json) · [网页到原生的切换](verification/s136-home-album-browser.json) · [原生API编译](verification/s136-native-compile.json)

## 海信覆盖复测

覆盖安装对应新版，不卸载、不清数据、不重新上传。翻到照片屏，应直接看到已有照片；左右切图，向上到应用、向下到留言；确定打开管理再返回。小爱与系统HOME另测。若仍黑屏，WorkBuddy用已授权局域网控制读取`status/ui`：当前页面应为`HomeAlbumActivity`，图片节点标签有“已解码”和真实宽高。此标签只证明位图已解码及控件尺寸，不代表电视面板已显示。可用`screenshot`保存到本地新文件核对；照片、二维码、会话文件不得自动上传公开仓库。

正式产品仍仅声明适配小米电视Android 8+；本轮处理海信兼容反馈，不等于海信全机型认证或真机通过。
