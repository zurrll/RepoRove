# RepoRove · GitHub 安卓客户端原型

> 历史资料说明：以下记录前期原型的状态。正式 Android App 已在上一级 app/ 中实现，当前能力与验收见 [主 README](../README.md)。本目录继续保存演示原型和交接材料。

本目录集中保存前期材料：当前网页原型工程、`RepoRove-handoff/` 开发交接包、`RepoRove-handoff.zip`，以及 `docs/` 中的历史截图和风格探索。正式 Android 工程后续在上一级主目录开展。

面向 Android GitHub 客户端的可交互界面原型，默认采用纸感阅读风格，页面优先展示项目内容。英文名已确定为 RepoRove，中文名暂缓。当前运行于浏览器；尚未制作 APK，也未接入 GitHub API。

完整交接资料位于 [`RepoRove-handoff/`](./RepoRove-handoff/README.md)，包括开发说明、可直接打开的单文件原型和一份独立源码快照。

## 本地运行

需要 Node.js 22.12+ 或 24+。

以下命令在本目录（`前期资料/`）执行：

```sh
npm install
npm run dev
```

打开 http://localhost:5173 。手机与电脑在同一局域网时，可使用终端输出的 Network 地址访问。开发服务监听局域网地址；关闭运行它的终端即可停止。

```sh
npm run build
npm run preview
```

## 可以体验

- 发现：项目推荐、主题筛选、专题入口。
- 动态：按照订阅仓库显示版本、代码和讨论示例。
- 收件箱：未读筛选、标记已读、完成通知及撤销。
- 资料库：参与的仓库、Star、稍后看、订阅和模拟下载记录。
- 搜索：仓库、代码、Issue、PR 和用户；支持语言、状态与排序筛选。
- 仓库：可定制概览、README 阅读、文件树和文件内容、Issue 讨论、PR 变更和审核、版本发布、Actions 日志。
- 更多：讨论、项目看板、统计、安全、贡献者与部署等示例内容。
- 个性化：底部选项卡选择与排序、启动页、仓库首页模块选择与排序、舒展或紧凑密度、正文字号。

桌面提供手机预览和页面索引，窄屏自动切换为全屏移动布局。设置从个人主页进入；桌面也可直接点击「界面与内容」。

## 数据与边界

所有项目、账号、正文和活动均为演示数据。收藏、订阅、通知状态、评论及界面偏好保存在当前浏览器的 `localStorage`，以 `qiye-demo-` 为前缀。原型不会登录账号、发布评论、提交审核、触发 CI 或下载 APK。操作弹窗会明确说明模拟行为。

仓库统计展示产品所需的信息形态，列表仅含少量代表性示例，不对应完整统计数量。部分不同仓库共用讨论与代码样例。AI 翻译、镜像配置、更多主题和真实仓库管理留待后续实现。

## 主要文件

- `src/screens.tsx`：发现、动态、收件箱、资料库、搜索、个人主页和工作页。
- `src/Repository.tsx`：仓库及内容详情页。
- `src/Settings.tsx`：布局与阅读设置。
- `src/state.tsx`：页面导航、模拟操作和本机持久化。
- `src/data.ts`：示例数据。
- `src/styles.css`：纸感主题与响应式布局。

技术：React、TypeScript、Vite、Lucide 图标。字体使用设备系统字体，界面无需加载远程字体。此技术栈用于验证交互；正式 Android 实现方案见交接文档。

`docs/preview.jpg`、`docs/discover-compact.jpg` 和 `docs/github-directions.html` 属于早期参考材料。最新交付画面以 `RepoRove-handoff/screenshots/` 为准。
