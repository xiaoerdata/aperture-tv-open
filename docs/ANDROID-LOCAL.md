# 免费构建：保留私有仓库，在自己的机器生成APK

[GitHub官方计费说明](https://docs.github.com/en/billing/concepts/product-billing/github-actions)明确私有仓库有托管执行免费额度，超额或账户付款设置会影响执行；这不等于项目代码收费。GitHub的付款提示来自托管Actions执行入口，仓库代码并不要求付款。这个项目可以完全在自己的Linux电脑、服务器或WSL中构建，不使用GitHub托管runner，也不需要改公开仓库。Android Studio及官方命令行构建工具可用于这条路线；本轮实际结果另记在本文件末尾和过程日志中。

## 一次准备

本路径需要JDK17、Gradle8.9、AndroidSDK的platforms;android-35及build-tools;35.0.0。Android Studio用户可用随附JDK和SDK Manager安装对应组件；命令行工具从Google官方下载。JDK可以使用Eclipse Temurin17，Gradle使用官方8.9发行包，核对来源和官方校验值。工具不进入Git源码。

```bash
export JAVA_HOME=/absolute/path/to/jdk17
export ANDROID_HOME=/absolute/path/to/android-sdk
export GRADLE_BIN=/absolute/path/to/gradle-8.9/bin/gradle
export APERTURE_ANDROID_RUNTIME=/private/path/aperture-android-runtime
npm run android:apk
```

以上变量指向本机真实位置。脚本只执行所选版本的debug任务、签名/包信息验证和SHA-256；不发布、不安装到电视，不改原桌面。网络代理沿用已有配置，不修改代理节点；带认证的代理请在私有Gradle配置中设置，脚本拒绝把代理密码放进进程参数。

首次Gradle构建还需要下载Google/Maven依赖；之后可以复用本地缓存。免费表示不使用收费托管执行服务，并不表示无需网络、存储或满足SDK许可要求。Android原生Windows路径尚未实测，优先使用Linux/WSL。

## 产物和密钥

完成后在artifacts/android-local找到APK、manifest.json、SHA256SUMS、签名信息、包信息与环境记录。manifest包含源码提交、Android输入摘要和工作区是否有未提交变化；不会把含未提交变化的构建冒称为提交的精确产物。

本地运行目录保存debug密钥和依赖缓存，应为私有目录，不提交仓库。保留同一目录有助于后续内部debug更新；清空密钥后重新生成的签名可能不同。正式公开发行仍需要项目专用稳定签名及真机验收，不能把debug候选改个文件名就当release。

## 下载源码压缩包后构建

源码包无需包含`.git`。准备好上述工具后，在解压目录执行相同的`npm run android:apk`即可；脚本不借用外层其他仓库的提交号。

Git检出记录sourceCommit、buildCommit和实际工作区状态。项目导出包记录`sourceProvenance.kind=source-archive`、导出清单摘要，以及Android文件是否仍匹配清单；没有Git工作区时`workingTreeDirty`和`buildCommit`为null，不冒称干净Git构建。自行修改Android源码后仍可编译，但`androidMatchesExport=false`。没有导出清单的普通ZIP则记录`kind=unversioned`和未知提交。清单哈希是文件一致性记录，不是发布者的数字签名。

维护者可在已准备SDK的环境执行`node scripts/verify-source-android.mjs`，从导出包解压、核对文件并真实编译一次；它不安装到电视。先运行`npm run export:source`生成待测包；同版本已有归档时，用`npm run export:source -- --output artifacts/review-source.tar.gz`另存，工具不会覆盖已有文件，产物与日志保存在artifacts/source-android。

## 托管CI是可选项

.github/workflows里的流程仅保留手动触发。在GitHub账户付款/额度恢复前，不会靠切换runner标签解决启动问题；也不通过提前公开仓库规避限制。本地构建与私有Git推送可独立完成。

## 安装验证

构建和签名验证通过后，按ANDROID-PRIVATE-TEST的步骤，在明确型号电视上授权安装，配对、测试方向/确认/返回、断网与撤销，并保留2小时运行记录。APK存在不等于旧小米电视实测通过。

## 2026-10-24实际结果

已在Linux隔离目录使用Temurin17.0.21+9、Gradle8.9、SDK35成功编译。第一次33项Gradle任务实际执行，随后明确固定Build-Tools35并做增量验证。没有使用GitHub托管runner；构建时工作区干净。

私有debug APK为18,526字节，包名org.zhixiaoce.aperture，minSdk26/targetSdk35；签名验证通过。源码提交d2eaf838bf1d12e7d263c6090a0e7bdd180e9eb6，文件及SHA-256见private-preview/v0.2.0-alpha.1。这里的构建证据不包括电视安装或实体按键验收。

S13另外验证了从172文件的导出源码包（无.git）真实编译，产物SHA-256与上述候选相同；四项来源/边界检查通过。对应源码提交7fcec2074a29b3e1a8bca85bfdc3c364bacea138，源包SHA-256为457eeb8796648c6d071bc0167b7e0f29528958e5462621ab96270dc4df6e8530。该证据只针对这份归档，后续文档或代码变化后的包需用自己的摘要。

## 标准版与爸妈版

默认`npm run android:apk`生成标准版；`npm run android:apk -- --edition parents`生成独立爸妈版，`--edition both`在一次Gradle调用中打包两版。产物分别在artifacts/android-local与artifacts/android-local-parents。版本/包名/签名分别校验；内置客厅页面、脚本、样式与插画也纳入输入摘要。

P15两版私有debug候选已实际生成，versionCode3，详见[下载选择](DOWNLOADS.md)与[入口设计](LOBBY-AND-PARENTS.md)。上文versionCode2为此前阶段的构建证据，不是新版入口的证明。

## S20：新版v4候选

已从干净提交1933282ecfca7729ac730d60f8faad6efc0a66c0执行一次两版构建，Gradle报告53秒、66任务（26执行、40复用）。标准版2,237,201字节，爸妈版2,237,193字节；均versionCode4，签名与各自v3一致，十份内置主页资源逐字节对照通过。实际manifest与SHA-256见private-preview/launcher-v4。未安装到电视、未公开发布；此前构建段落保留作历史。

复查已有候选无需重建：`python3 scripts/verify-android-candidate.py --directory artifacts/android-local --previous private-preview/launcher-v3/standard/manifest.json`（爸妈版替换对应目录）。该脚本核对APK实际字节、包名、版本递增、签名摘要与打包资产；实际签名校验仍由构建步骤的apksigner完成。


正式发行使用独立的[release构建与稳定签名步骤](ANDROID-RELEASE.md)。本页`npm run android:apk`仍只生成debug内测包。
