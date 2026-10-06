# RepoRove 可编辑交互原型

这是 2026-10-06 的独立源码快照。产品需求和开发规则见 [开发说明](../开发说明.md)，直接体验版见 [原型.html](../原型.html)。目标是 Android GitHub 客户端；本项目是浏览器演示，未接入 GitHub，也不是正式 Android 工程。

## 运行

需要 Node.js 22.12+ 或 24+，以及 npm。

在本目录执行：

```sh
npm ci
npm run dev
```

浏览器打开 `http://localhost:5173`。端口被占用时，以终端给出的实际地址为准。开发服务监听局域网，手机和电脑在同一网络时可使用终端的 Network 地址体验。

生产构建与本地预览：

```sh
npm run build
npm run preview
```

默认预览地址为 `http://localhost:4173`。

## 更新独立 HTML

修改后先构建，再使用附带的 Python 标准库脚本重新生成交接包中的快照：

```sh
npm run build
python3 scripts/export_standalone.py --output ../原型.html
```

导出脚本将 Vite 的 JS、CSS 和 SVG 图标内嵌到 HTML。当前原型没有远程字体或外部图片依赖；如果后续引入动态分包、外部素材等，需要先调整导出策略。源码修改不会自动改变 `原型.html`。

## 文件职责

| 文件                           | 职责                                              |
| ------------------------------ | ------------------------------------------------- |
| `src/App.tsx`                  | 应用外壳、手机/桌面展示、页面分发                 |
| `src/screens.tsx`              | 发现、动态、收件箱、资料库、搜索、个人与工作      |
| `src/Repository.tsx`           | 仓库概览、代码、README、Issue、PR、版本与 Actions |
| `src/Settings.tsx`             | 导航和概览模块的显示/排序、启动页、字号与密度     |
| `src/state.tsx`                | 模拟状态、hash 导航、localStorage、反馈与弹窗     |
| `src/data.ts`                  | 虚构账号、仓库、通知、讨论与构建数据              |
| `src/ui.tsx`                   | 共用按钮、列表、弹窗等组件                        |
| `src/styles.css`               | 纸感样式、阅读配置、移动适配                      |
| `scripts/export_standalone.py` | 构建产物转换为独立 HTML                           |

依赖已锁定在 `package-lock.json`。包名为 `reporove-prototype`，存储仍沿用 `qiye-demo-` 前缀以兼容已有体验记录。

## 体验入口

`#/discover` → 仓库 → README/代码/Issue/PR；`#/inbox` → 未读通知 → 上下文；`#/settings` → 自定义选项卡和概览模块 → 刷新验证保存。

仓库可 Star、稍后看和模拟订阅；评论、审核、CI 重跑、版本下载均为演示。所有内容和状态只在本机浏览器中变化，不发送至 GitHub。多主题、AI 翻译与镜像配置是后续需求。

此快照不包含 `node_modules`、`dist` 或旧版本截图。请保持需求文档、交付 HTML 和截图同步更新。
