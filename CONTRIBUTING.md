# 参与 RepoRove

欢迎先通过 [Issues](https://github.com/zurrll/RepoRove/issues) 分享体验问题或讨论改进。请说明版本、设备、步骤及实际表现；不要提交令牌、私有仓库内容或签名文件。

项目采用 [MIT](LICENSE)。生产 App 位于 `app/`，早期网页原型在 `前期资料/`，仅供回看产品设计。

## 构建

使用 JDK 17、Android SDK Platform 36 和 Build Tools 35.0.0。设置 `ANDROID_HOME`，或在不提交的 `local.properties` 写入 `sdk.dir`。

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleRelease
```

Release 默认启用 R8，并生成未签名 APK；自行构建需要自己的签名。官方预览包可从 [Releases](https://github.com/zurrll/RepoRove/releases) 下载。设备授权登录需要配置自己的 GitHub OAuth App Client ID：`-PgithubOAuthClientId=...`；Client ID 无默认生产值，PAT 登录可直接使用。

## 测试

```sh
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

确定性设备测试使用本机 HTTP 夹具，不依赖真实令牌。真实 GitHub API / 下载测试需主动启用；独立 `smoke-test` 用于实际 R8 包的生产流程验证。CI 构建、运行单元测试并检查 Debug / Release lint，不冒充已完成真机测试。

内存较小的电脑建议限制为两个 worker，并把构建与模拟器分开运行：

```sh
./gradlew :app:assembleDebug --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs='-Xmx2048m -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process
```

## 修改约定

先读 [工程说明](工程说明.md)、[当前批次](0.6.0阅读修复与公开发布.md) 与 [协作约定](AGENTS.md)。修改应有明确使用场景，并验证涉及的真实行为。

- 使用真实 GitHub 数据，保留错误与缺失状态；不把假结果写入生产页面。
- 原生和文档共用主题语义色；阅读改动同时检查文档、普通源码和离线内容。
- 保持账号隔离；令牌仅发送到受约束的 API 主机。
- 下载删除核验磁盘结果；失败保留记录。稍后看、Star、跟踪与 Watch 的语义保持清楚。
- 更新目录使用 `scripts/update_catalogs.py`，保留来源版本、署名和第三方许可证。
- 行为变化更新本轮文档与 `changelog.json`；历史记录保留其当时状态。

APK、密钥、SDK、本机设置、缓存和 R8 mapping 不提交到源码仓库。集中完成授权批次后再出包，日常反馈先记录。新建分支默认用 `codex/` 前缀。
