# RepoRove 开发与反馈

当前为私有开发仓库，源码版本 0.4.1。项目源码的开源许可证尚待确定；官方目录资产的许可证在 app/src/main/assets/catalog/ 中保留。

## 开发前阅读

用户反馈先写入 [待处理体验反馈](./待处理体验反馈.md)，读代码和讨论方案后等待批次实施指令，不因单条反馈自动改代码或发布 APK。以 [协作约定](./AGENTS.md) 为准。

先阅读 [README](./README.md)、[文档索引](./文档索引.md)、[最新批次记录](./0.4.1布局与内容适配.md) 与 [工程说明](./工程说明.md)。历史原型位于前期资料/；生产功能在 app/ 中实现，使用真实 GitHub API。

## 本地构建与检查

准备 JDK 17、Android SDK Platform 36 与 Build Tools 35.0.0，在未跟踪的 local.properties 配置 sdk.dir，或设置 ANDROID_HOME。

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleRelease
```

CI 使用 .github/workflows/android.yml。设备测试需要连接设备或启动模拟器：

```sh
./gradlew :app:connectedDebugAndroidTest
```

真实 GitHub 网络测试单独启用，参考 [验收记录](./验收记录.md)；不要用真实凭据替代确定性测试夹具。测试只证明其覆盖的行为，目标手机与私有权限验证仍独立记录。

## 约束

- 生产页面读取官方数据，不添加假仓库、伪造成功状态或固定推荐名单；产品排序策略集中定义并注明来源。
- 主题共用语义色和组件，阅读页包含原生与 HTML 两种渲染，修改时同时检查。
- 稍后看、GitHub Star、Watch 与 App 内跟踪各有用途，不混用数据或删除语义。
- 删除下载文件需要核验磁盘结果；失败保留记录。保持账号隔离与错误状态。
- 不提交 PAT、OAuth 凭据、签名密钥、local.properties、SDK 或缓存。OAuth Client ID 通过构建参数提供，发布配置在本机或 CI 秘密配置中管理。
- 更新目录使用 scripts/update_catalogs.py，保留来源、固定 revision 和第三方许可证。
- 行为变化同步更新需求/实施文档，说明验证结果和剩余边界。反馈缺陷提供具体步骤，截图中遮盖凭据与私有信息。

## 仓库与产物

main 是当前开发基线。源码、文档、Gradle Wrapper 和审阅过的轻量验收材料纳入 Git；APK、R8 mapping、本机工具、源码备份和临时失败日志保留本地。安装包发布另行决定，不把构建产物写入源码历史。
