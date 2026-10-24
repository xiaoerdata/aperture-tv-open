# 第十九期：装好以后，从哪里开始

续作既有目标，没有新增用户提示词。过程见JOURNAL S33。

素材：

- docs/images/s33-install-guide-routes-phone.png：手机里的三条路线。
- docs/images/s33-install-guide-routes-desktop.png：桌面说明。
- docs/images/s33-guide-before-320.png：展开部署命令后的窄屏溢出，保留失败版本。
- docs/images/s33-install-guide-{desktop,phone}.png：修正后整页。

分镜草稿：先展示电视本机入口，再切手机指引。“本机应用先安装就能用；有NAS就连接共享目录；需要留言、日程和助手，再启动家庭服务。”不要把安装应用说成刷ROM，也不要把浏览器截图或源码干净启动说成电视安装成功。

证据：冻结v10源码包在临时目录重新npm ci并启动成功；新指引六种宽度和两个预览链接通过。前者针对冻结包，后者针对后续网页修改，两者不能合并冒称同一包。脚本：node scripts/verify-source-install.mjs artifacts/aperture-tv-v0.2.0-alpha.7-source.tar.gz；node scripts/capture-install-guide.mjs。

本期只有截图与分镜，没有新成片或配音。画面使用合成示意内容，没有家庭账户、配对码或真实NAS资料。尚未上传小红书。
