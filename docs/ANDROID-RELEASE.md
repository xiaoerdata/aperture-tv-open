# Android 正式发行：构建、签名与升级

**本项目仅适配小米电视，Android 8+。** 这是维护者的发行操作说明，普通用户从[下载与版本](DOWNLOADS.md)和[安装说明](../INSTALL.md)开始。

当前标准版和爸妈版均为 v13 debug 内测包。下列参数已与本机 Android Build Tools 35.0.0 的帮助输出核对，但尚未完整执行 release 构建与正式签名，见[检查记录](verification/s42-release-preparation.json)。WorkBuddy 和小米真机按维护者现场结果记录；本说明不代表正式版已发布。

## 签名前先确定

- 使用经过审阅的源码快照；记录提交或 SOURCE-MANIFEST 摘要、完整 Android 输入摘要与实际版本。保留现有私有研发库，不直接改为 Public。
- 由维护者保管专用稳定签名密钥，做好私密备份。两版建议使用同一发行证书，但包名始终不同；不要沿用临时 CI/debug 密钥。
- 密钥和密码文件放在源码、产物目录之外的私有位置；Linux/macOS 使用仅本人可访问的目录及文件权限。不要将密码直接写进命令、聊天、GitHub Secrets 示例或录屏。
- 已装私有 debug 版的电视，不能承诺覆盖升级到不同证书的 release 包。核对签名、保留现有数据，再由用户决定迁移；不自动卸载。稳定发行后的正常升级保持包名和证书一致，并递增 versionCode。

## 生成两个未签名 release 包

先按[免费本地构建](ANDROID-LOCAL.md)准备 JDK17、Gradle8.9、SDK35/Build Tools35。以下为 Linux/macOS/WSL 的 Bash 命令，原生 Windows 尚未验证。所有命令从项目根目录执行；`npm run android:apk`仍只生成 debug 包，不用它替代本节。

```bash
set -euo pipefail
: "${JAVA_HOME:?请设置JDK17目录}"
: "${ANDROID_HOME:?请设置SDK目录}"
: "${GRADLE_BIN:?请设置Gradle8.9程序路径}"
: "${APERTURE_ANDROID_RUNTIME:?请设置私有运行目录}"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_USER_HOME="$APERTURE_ANDROID_RUNTIME/android-user"
export GRADLE_USER_HOME="$APERTURE_ANDROID_RUNTIME/gradle-user"

"$GRADLE_BIN" --no-daemon -p android \
  :app:assembleStandardRelease :app:assembleParentsRelease
```

预期输入分别是：

- `android/app/build/outputs/apk/standard/release/app-standard-release-unsigned.apk`
- `android/app/build/outputs/apk/parents/release/app-parents-release-unsigned.apk`

逐个用 `aapt dump badging`核对版本、包名、minSdk26/targetSdk35，并确认没有`application-debuggable`。标准版包名为`org.zhixiaoce.aperture`，爸妈版为`org.zhixiaoce.aperture.parents`，其 versionName 有`-parents`后缀。不能只把已签 debug APK 改名或重新签名后称为正式版。

## 对齐、签名、验证

维护者先在私有环境设置以下路径和别名：

- `APERTURE_RELEASE_KEYSTORE`：稳定发行密钥库文件。
- `APERTURE_RELEASE_ALIAS`：其中的真实密钥别名。
- `APERTURE_RELEASE_STORE_PASSWORD_FILE`：密钥库密码文件，单行。
- `APERTURE_RELEASE_KEY_PASSWORD_FILE`：密钥密码文件，单行。

**两个密码使用不同文件**，即使内容相同。apksigner 在同一文件被用于多个密码时会逐行读取，不会自动重复使用第一行。这里不提供密钥、不自动创建或更换现有密钥。

```bash
: "${APERTURE_RELEASE_KEYSTORE:?}"
: "${APERTURE_RELEASE_ALIAS:?}"
: "${APERTURE_RELEASE_STORE_PASSWORD_FILE:?}"
: "${APERTURE_RELEASE_KEY_PASSWORD_FILE:?}"
APERTURE_BUILD_TOOLS="$ANDROID_HOME/build-tools/35.0.0"
APERTURE_RELEASE_VERSION="$(node -p "JSON.parse(require('fs').readFileSync('package.json','utf8')).version")"
APERTURE_RELEASE_OUTPUT="$PWD/artifacts/release-$APERTURE_RELEASE_VERSION"
# 不使用 mkdir -p：已有同名产物时停止，另选新目录。
mkdir "$APERTURE_RELEASE_OUTPUT"

for aperture_edition in standard parents; do
  aperture_input="$PWD/android/app/build/outputs/apk/$aperture_edition/release/app-$aperture_edition-release-unsigned.apk"
  aperture_aligned="$APERTURE_RELEASE_OUTPUT/$aperture_edition-aligned-unsigned.apk"
  aperture_signed="$APERTURE_RELEASE_OUTPUT/aperture-tv-$APERTURE_RELEASE_VERSION-$aperture_edition.apk"
  "$APERTURE_BUILD_TOOLS/zipalign" -P 16 4 "$aperture_input" "$aperture_aligned"
  "$APERTURE_BUILD_TOOLS/apksigner" sign \
    --ks "$APERTURE_RELEASE_KEYSTORE" --ks-key-alias "$APERTURE_RELEASE_ALIAS" \
    --ks-pass "file:$APERTURE_RELEASE_STORE_PASSWORD_FILE" \
    --key-pass "file:$APERTURE_RELEASE_KEY_PASSWORD_FILE" \
    --debuggable-apk-permitted false --v2-signing-enabled true \
    --v4-signing-enabled false --alignment-preserved true \
    --in "$aperture_aligned" --out "$aperture_signed"
  "$APERTURE_BUILD_TOOLS/zipalign" -c -P 16 4 "$aperture_signed"
  "$APERTURE_BUILD_TOOLS/apksigner" verify --min-sdk-version 26 \
    --verbose --print-certs "$aperture_signed" \
    > "$APERTURE_RELEASE_OUTPUT/$aperture_edition-signature.txt"
  "$APERTURE_BUILD_TOOLS/aapt" dump badging "$aperture_signed" \
    > "$APERTURE_RELEASE_OUTPUT/$aperture_edition-package.txt"
done
```

`--debuggable-apk-permitted false`明确拒绝用发行密钥签可调试包；`--out`保留原输入。签名后不再修改APK。两份`signature.txt`中的证书 SHA-256 必须与维护者独立保管的预期证书一致；首发记录真实证书，此后每次逐字核对。签名工具成功不证明该密钥就是计划中的正式密钥。

## 冻结发行资料

仅对`aperture-tv-<版本>-standard.apk`和`aperture-tv-<版本>-parents.apk`生成 SHA256SUMS；不要把目录中间的`*-aligned-unsigned.apk`加入下载附件。发行附件使用明确文件白名单，不上传整个工作目录。

每版记录实际文件名/字节数/SHA-256、证书 SHA-256、包名、versionCode/versionName、min/targetSDK、源码来源、完整 Android 输入摘要、构建工具与对应型号测试结果。不要把 debug 的 manifest.json 复制为 release 证明；不能依据首页资源一致就宣称全部原生代码来源一致。

release 关闭网页调试和明文家庭服务连接；原生应用/NAS仍可独立使用。需要家庭屏、米家只读分享或 WorkBuddy 时，先验证可达的可信 HTTPS，不以 debug 的 HTTP 测试代替正式版连接验证。双版分别检查原桌面、Home、小爱、安装/卸载确认、升级和网络恢复。

完成现场验收、签名核对与维护者发布确认后，才发布白名单附件和同源源码包，再从外部匿名下载回读摘要。Hub仓库链接和Stars在实际公共仓库可匿名读取后更新；本地签名不会自动公开仓库、发Release或部署Hub。
