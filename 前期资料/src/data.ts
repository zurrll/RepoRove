export type Repo = {
  id: string;
  owner: string;
  name: string;
  description: string;
  tagline: string;
  language: string;
  stars: number;
  forks: number;
  updated: string;
  topic: string;
  version: string;
  license: string;
  initial: string;
  color: string;
  issues: number;
  prs: number;
};

export const repos: Repo[] = [
  {
    id: "mori-notes",
    owner: "mori-labs",
    name: "mori-notes",
    description: "把想法留在本地。一款安静、轻巧的 Markdown 笔记应用。",
    tagline: "让灵感有处可栖。",
    language: "Kotlin",
    stars: 2480,
    forks: 126,
    updated: "2 小时前",
    topic: "效率工具",
    version: "1.8.0",
    license: "Apache-2.0",
    initial: "m",
    color: "moss",
    issues: 12,
    prs: 3,
  },
  {
    id: "lumen-ui",
    owner: "lumen-team",
    name: "lumen-ui",
    description: "为 Android 打造的界面组件。细腻的动效，恰到好处的留白。",
    tagline: "每一次交互，都恰到好处。",
    language: "Kotlin",
    stars: 860,
    forks: 42,
    updated: "5 小时前",
    topic: "界面设计",
    version: "0.9.2",
    license: "MIT",
    initial: "L",
    color: "sand",
    issues: 8,
    prs: 2,
  },
  {
    id: "leaf-reader",
    owner: "little-forest",
    name: "leaf-reader",
    description: "订阅你在乎的声音。一个没有算法干扰的 RSS 阅读器。",
    tagline: "留一点时间，给阅读。",
    language: "TypeScript",
    stars: 1720,
    forks: 88,
    updated: "昨天",
    topic: "阅读",
    version: "2.1.0",
    license: "MIT",
    initial: "l",
    color: "clay",
    issues: 21,
    prs: 5,
  },
  {
    id: "haze-terminal",
    owner: "haze-dev",
    name: "haze-terminal",
    description: "一份轻量的命令行工作台，让终端里的日常更清晰。",
    tagline: "专注，从少一点开始。",
    language: "Rust",
    stars: 3200,
    forks: 204,
    updated: "2 天前",
    topic: "开发工具",
    version: "1.2.3",
    license: "MIT",
    initial: "h",
    color: "slate",
    issues: 16,
    prs: 4,
  },
];

export const issues = [
  {
    id: 128,
    title: "支持按标签筛选笔记",
    author: "lin-mu",
    label: "enhancement",
    labelText: "功能建议",
    comments: 8,
    time: "2 小时前",
    state: "open",
    body: "笔记慢慢多起来以后，希望能按标签快速找到相关内容。\n\n建议在笔记列表顶部增加一个轻量的标签筛选栏，同时支持多个标签组合筛选。最好还能保留上次的筛选条件。",
  },
  {
    id: 126,
    title: "大文件打开时，滚动位置偶尔跳回顶部",
    author: "yuki",
    label: "bug",
    labelText: "问题反馈",
    comments: 5,
    time: "昨天",
    state: "open",
    body: "打开比较长的 Markdown 文档时，图片加载完成后，阅读位置有时会跳回顶部。\n\n复现方式：打开含有多张图片的长文档，快速滚动到中间，等待图片加载。\n\n设备：Android 15。",
  },
  {
    id: 121,
    title: "增加阅读模式的行间距设置",
    author: "you",
    label: "enhancement",
    labelText: "功能建议",
    comments: 12,
    time: "3 天前",
    state: "open",
    body: "希望阅读模式可以分别设置字号和行间距，让长文档在小屏幕上也能舒服地阅读。",
  },
  {
    id: 114,
    title: "修复首次启动时主题闪烁",
    author: "chen",
    label: "bug",
    labelText: "问题反馈",
    comments: 4,
    time: "5 天前",
    state: "closed",
    body: "首次启动时短暂出现错误主题。此问题已通过提前加载偏好设置修复。",
  },
];

export const pulls = [
  {
    id: 132,
    title: "新增离线全文搜索",
    author: "yuki",
    status: "等待审核",
    state: "open",
    branch: "feature/offline-search",
    time: "35 分钟前",
    additions: 186,
    deletions: 24,
    files: 4,
    ci: "通过",
  },
  {
    id: 130,
    title: "优化 Markdown 图片的加载方式",
    author: "lin-mu",
    status: "检查失败",
    state: "open",
    branch: "fix/image-loading",
    time: "2 小时前",
    additions: 48,
    deletions: 17,
    files: 2,
    ci: "失败",
  },
  {
    id: 127,
    title: "支持自定义阅读主题",
    author: "chen",
    status: "草稿",
    state: "draft",
    branch: "feature/themes",
    time: "昨天",
    additions: 230,
    deletions: 68,
    files: 8,
    ci: "等待运行",
  },
  {
    id: 118,
    title: "修复启动时的主题闪烁",
    author: "yuki",
    status: "已合并",
    state: "merged",
    branch: "fix/startup-theme",
    time: "4 天前",
    additions: 32,
    deletions: 11,
    files: 2,
    ci: "通过",
  },
];

export const notifications = [
  {
    id: "n1",
    kind: "review",
    repo: "mori-notes",
    title: "新增离线全文搜索",
    reason: "请求你审核",
    time: "35 分钟前",
    route: "/repo/mori-notes/pr/132",
    section: "今天",
  },
  {
    id: "n2",
    kind: "mention",
    repo: "mori-notes",
    title: "支持按标签筛选笔记",
    reason: "lin-mu 提到了你",
    time: "2 小时前",
    route: "/repo/mori-notes/issue/128",
    section: "今天",
  },
  {
    id: "n3",
    kind: "release",
    repo: "leaf-reader",
    title: "v2.1.0：让订阅更有条理",
    reason: "你订阅的版本更新",
    time: "5 小时前",
    route: "/repo/leaf-reader?tab=releases",
    section: "今天",
  },
  {
    id: "n4",
    kind: "ci",
    repo: "mori-notes",
    title: "Android CI 运行失败",
    reason: "你参与的 PR",
    time: "昨天",
    route: "/repo/mori-notes/run/485",
    section: "昨天",
  },
  {
    id: "n5",
    kind: "issue",
    repo: "lumen-ui",
    title: "阅读模式的行间距设置有新回复",
    reason: "你参与的讨论",
    time: "昨天",
    route: "/repo/lumen-ui/issue/121",
    section: "昨天",
  },
];

export const runs = [
  {
    id: 486,
    name: "Android CI",
    branch: "main",
    commit: "a3f91c2",
    message: "优化大文件的加载速度",
    status: "success",
    time: "6 分钟前",
    duration: "2 分 34 秒",
    author: "yuki",
  },
  {
    id: 485,
    name: "Android CI",
    branch: "fix/image-loading",
    commit: "f24b851",
    message: "调整图片缓存策略",
    status: "failure",
    time: "2 小时前",
    duration: "1 分 12 秒",
    author: "lin-mu",
  },
  {
    id: 484,
    name: "Release",
    branch: "main",
    commit: "bb039d1",
    message: "发布新版本",
    status: "success",
    time: "昨天",
    duration: "4 分 08 秒",
    author: "chen",
  },
];

export const tabOptions = [
  {
    id: "discover",
    label: "发现",
    icon: "compass",
    description: "寻找有趣的开源项目",
  },
  {
    id: "feed",
    label: "动态",
    icon: "activity",
    description: "关注项目的新变化",
  },
  {
    id: "inbox",
    label: "收件箱",
    icon: "inbox",
    description: "通知与需要处理的事",
  },
  {
    id: "library",
    label: "资料库",
    icon: "library",
    description: "仓库、Star 与稍后看",
  },
  {
    id: "work",
    label: "我的工作",
    icon: "circle-check",
    description: "Issue、PR 与审核请求",
  },
  { id: "profile", label: "我的", icon: "user", description: "个人主页与账号" },
  {
    id: "search",
    label: "搜索",
    icon: "search",
    description: "按类型查找内容",
  },
];

export const moduleOptions = [
  { id: "status", label: "状态摘要", description: "版本、提交、Issue 与 PR" },
  { id: "readme", label: "README", description: "先了解项目是什么" },
  { id: "release", label: "最新版本", description: "更新内容与下载入口" },
  { id: "activity", label: "最近活动", description: "项目近期发生的变化" },
  { id: "tasks", label: "我的待办", description: "审核请求与参与的事项" },
  { id: "ci", label: "构建与部署", description: "工作流结果与环境状态" },
];

export const compactNumber = (n: number) =>
  n >= 1000 ? (n / 1000).toFixed(1).replace(/\.0$/, "") + "k" : String(n);
