# RepoRove 开发交接包

整理日期：2026-10-06。英文名已定为 **RepoRove**，中文名暂缓。

产品是一个重视内容占比、浏览效率和自由布局的 GitHub Android 客户端。默认采用纸感阅读，允许用户选择底部选项卡及仓库概览模块。多主题、AI 翻译和镜像配置作为后续扩展。

## 从这里开始

1. 阅读 [开发说明.md](./开发说明.md)，了解确认需求、页面与交互规则、功能范围、数据接入建议、验收条件和未定事项。
2. 使用浏览器打开 [原型.html](./原型.html)，体验最新的可交互原型。它已将脚本、样式和图标放进一个文件，无需 npm 或 GitHub 账号。
3. 要继续修改原型，进入 [prototype/](./prototype/README.md)，运行 `npm ci` 和 `npm run dev`。
4. 按开发说明启动正式 Android 工程。当前网页源码用于产品参考，不代表已敲定 WebView 或正式 Android 技术栈。

## 文件结构

```text
RepoRove-handoff/
├── README.md                  交接入口
├── 开发说明.md                需求、交互、技术建议、验收与未定事项
├── 原型.html                  可直接打开的独立交互原型
├── prototype/                 可继续修改的 React/TypeScript 源码
│   ├── src/
│   ├── public/
│   ├── package.json
│   ├── package-lock.json
│   ├── index.html
│   └── README.md
├── screenshots/               最新手机与桌面参考画面
└── 交接验证.md                本次构建与交互检查记录
```

## 原型快照说明

- 使用发现页缩减后的版本，大标语和装饰性页头已移除。
- 页面及收藏、通知、评论、布局设置可操作；所有 GitHub 内容均为虚构示例数据。
- 偏好与演示状态保存在当前浏览器；单文件版与服务器版可能使用不同的本机存储。
- 没有 GitHub 登录、真实评论/审核、CI 执行或 APK 下载；正式 Android App 尚未制作。
- 部分低频功能只是摘要或弹窗，完整程度以开发说明中的原型状态表为准。
- 单文件版是此次交付的固定快照，修改源码后需要重新打包。

最新画面：[发现](./screenshots/discover-mobile.jpg)、[仓库](./screenshots/repository-mobile.jpg)、[设置](./screenshots/settings-mobile.jpg)、[桌面预览](./screenshots/desktop.jpg)。截图用于快速对照，交互以 `原型.html` 为准。

如果当前环境不能直接打开本地 HTML，可在此文件夹运行：

```sh
python3 -m http.server 8780 --bind 127.0.0.1
```

然后打开 `http://localhost:8780/原型.html`。关闭运行服务器的终端即可停止。
