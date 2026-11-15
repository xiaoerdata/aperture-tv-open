# 照片屏空白与原生管理 · S119

这是S118未发布照片补丁的正式收口，随后汇入[V46](RELEASE-V46.md)。S118的独立phototest包不是正式版升级包，不读取正式相册；V46沿用正式包名和发行证书。

## 处理的问题

部分旧WebView不支持CSS `inset:0` 时，照片舞台和帧虽已读到图片，容器仍可能变成零尺寸。为照片背景、舞台、帧、空态及全屏容器补 `top/right/bottom/left:0`，并补 `.gallery-empty[hidden]`，使独立相册也能隐藏已有照片时的空态。没有改变照片存储、上传二维码协议或单张20MiB上限。

原生兼容相册增加“管理照片”，提供当前照片删除确认和8/12/20秒轮播。默认焦点为“保留”；暂停/销毁关闭弹窗，回调同时检查所属弹窗和显示状态，旧弹窗恢复后也不能再写入。删除仅从电视本机相册移除，手机原图不受影响。本轮不提供裁剪、调色等照片编辑器。

## 可复现证据与限制

- 测试基线 `tests/fixtures/photo-v45/gallery.css` 与本地已核对过公开摘要的v45 APK资源逐字节一致。
- `scripts/s119-photo-test.mjs` 模拟忽略inset：960/1920/3840下，基线舞台均0×0，修复后全屏；上传后刷新、重新进入、图片解码与空态隐藏通过。现代Chromium模拟CSS兼容行为，不代表旧Android WebView真机已复现同一根因。
- `scripts/s119-native-gallery-test.py` 执行生产NativeGallery的菜单和确认回调，18项检查通过。Android视图、对话框、存储为明确JVM替身；实际文件删除沿用GalleryStore，不能以该替身声称电视触控/遥控、原生绘制或用户相片已经验收。
- 126份当前Java源码定向编译及29项尺寸案例、12组标准首页回归通过；爸妈版回归独立保留。没有修改电视全局density或分辨率。

[兼容检查](verification/s119-photo-browser.json) · [菜单回调检查](verification/s119-native-gallery.json) · [原生编译](verification/s119-native-compile.json) · [标准首页](verification/s119-home-regression.json) · [爸妈版](verification/s119-parents-browser.json)

优先真机复测：同签名覆盖安装后原照片仍在；关闭上传二维码返回照片屏即可显示；重复进入无空态遮挡；原生管理中“保留”不删、“删除”仅删所选照片；间隔设置保存，后台再回来旧弹窗不再操作。

![修复后照片舞台：浏览器兼容夹具、合成纯色照片，非真机](images/s119-photo-fix/home-1920.png)
