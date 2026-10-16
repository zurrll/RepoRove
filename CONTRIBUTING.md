# 参与 RepoRove

欢迎通过 [Issues](https://github.com/zurrll/RepoRove/issues) 讨论改进。反馈请附版本、设备和复现步骤；不要提交密钥或私有仓库内容。

## 构建与测试

需要 JDK 17、Android SDK Platform 36、Build Tools 35.0.0。设置 `ANDROID_HOME`，或在本机 `local.properties` 中填写 `sdk.dir`。

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
```

设备测试需先启动模拟器或连接手机；确定性用例使用本机 HTTP 夹具。`smoke-test/` 验证实际 R8 安装包。内存有限时，将构建和模拟器分开运行，添加 `--no-daemon --max-workers=1`。

Release 默认未签名，官方预览包使用同一开发签名。`gradle.properties` 内是公开的 OAuth Client ID；派生项目可用 `-PgithubOAuthClientId=...` 替换为自己的 Device Flow 应用，不需要 Client Secret。

## 代码约定

Kotlin、Compose、Retrofit / OkHttp、DataStore；`core/` 管模型、存储和阅读处理，`data/` 管数据流程，`ui/` 管共用组件与页面。

- 使用真实数据，保留错误和缺失状态；异步结果绑定查询与账号会话。
- 原生和 HTML 共用主题；阅读改动兼顾在线 / 离线、表格、链接、滚动和返回位置。
- GitHub 凭据与翻译 Key 分开加密；只向对应主机发送，禁止重定向泄露。私有内容和缓存在账号间隔离。
- 下载删除确认磁盘结果，失败保留记录。源码、测试及必要许可公开；过程报告、本机设置与产物不提交。
- 更新功能时同步 `changelog.json` 和必要用户说明。目录数据用 `scripts/update_catalogs.py` 更新并保留来源、版本和许可。

采用 [MIT](LICENSE)。贡献分支建议使用 `codex/` 前缀，提交说明写清使用场景和验证结果。
