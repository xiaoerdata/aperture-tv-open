# 安装包体积 · S55本地改动

仅适配小米电视。当前公开v18的两版安装包各约81.55 MiB，包含ARM32、ARM64、x86和x86_64。下一版源码将默认打包范围改为ARM32与ARM64，保留旧款32位系统和新款64位系统所需环境；模拟器架构按需启用。

## 先减少架构重复

对已有签名APK只读解析ZIP目录，原生库和Python的x86/x86_64专用内容合计占30,003,424字节。保留相同壁纸、核心逻辑与Python依赖后，按旧包内容估算如下：

| 版本 | v18实际体积 | 预计减少 | 估算下一包 |
| --- | --- | --- | --- |
| 标准版 | 81.55 MiB | 28.61 MiB（35.09%） | 52.94 MiB |
| 爸妈版 | 81.56 MiB | 28.61 MiB（35.08%） | 52.94 MiB |

**右列是估算，尚未生成瘦身APK。** ZIP对齐、生成元数据、签名和本地未发布的新Logo等会改变实际字节数。完整原始字节、分组和摘要见[基线报告](verification/s55-apk-size-baseline.json)。公开v18附件未改动；没有重封装或重签旧APK。

本阶段保留四张4K壁纸、全部JAR/JS/Python插件能力及既有Python依赖，继续使用现有播放器、SMB/WebDAV和NAS逻辑。没有启用新的代码混淆或删除反射调用所需类。壁纸约16.6 MiB，是另一个体积来源；画质与离线可用性已有明确要求，本阶段保留原图。

## 打包范围

单一配置源为 `android/abi-policy.json`。默认 `armeabi-v7a,arm64-v8a`。普通用户无需选择架构，仍按标准版或爸妈版安装，ARM32/64共用一个APK。

本地开发模拟器时可用：

```bash
APERTURE_ANDROID_ABIS=x86 npm run android:apk -- --edition standard
```

需要四架构开发包时，将值改为 `armeabi-v7a,arm64-v8a,x86,x86_64`。直接调用Gradle时使用 `-PkaishiAbis=x86` 等明确参数；对自定义APK运行验包或签名脚本时，`APERTURE_ANDROID_ABIS`需与构建选择一致。空值、重复值和未知架构会被拒绝。

调试包与release签名脚本核对实际ZIP中的原生库、Python架构包和引导库，并把实际ABI写入manifest。共享的 `requirements-common` / `stdlib-common` 保留为公共内容，不能作为CPU架构移除；ABI残留或Python运行时缺件会阻止验包/签名。

## 复核与下一步

`python3 scripts/analyze-apk-size.py --apk 路径/标准版.apk 路径/爸妈版.apk --output artifacts/new-size-report.json`只读分析，不生成APK，不修改旧签名。

`gradle --offline -q -p android :app:kaishiAbiReport`只查看打包配置，不编译Java/Python或组装APK。定向验证与边界见[本轮记录](verification/s55-abi-policy.json)。本轮无APK构建、无安装、无上传；下一次版本发布时需递增版本，重新构建两版、核对真实体积、实际ABI、Python冷启动和NAS/直播/影视。

## v19实际签名包

两版release包已按ARM32/64策略构建和签名；以下是实际文件，不再使用S55估算：

| 版本 | v18 | v19 | 减少 |
| --- | --- | --- | --- |
| 标准版 | 81.55 MiB | 52.61 MiB | 35.49% |
| 爸妈版 | 81.56 MiB | 52.63 MiB | 35.47% |

原生库与Python的实际ABI均核对为ARM32/64，无x86残留；四张4K壁纸字节不变。每架构Python依赖的有效文件仍为642项，没有增删；352项pyc仅编译头变化，代码部分相同，其他文件相同。此比较不冒充ARM电视的Python冷启动验收。见[实际体积](verification/s56-apk-size.json)与[依赖对照](verification/s56-python-payload.json)。新影视资料、图片缓存为运行时有界缓存，生图UI参考不打入APK。
