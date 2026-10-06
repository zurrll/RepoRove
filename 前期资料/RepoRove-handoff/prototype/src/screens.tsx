import { useState } from "react";
import { useApp } from "./state";
import { repos, issues, pulls, notifications } from "./data";
import {
  Badge,
  DemoFootnote,
  Empty,
  Icon,
  IconButton,
  MenuRow,
  PageHeader,
  RepoItem,
  SectionHeading,
  Tabs,
} from "./ui";

export function Discover() {
  const [tab, setTab] = useState("为你");
  const [topic, setTopic] = useState("全部");
  const selected = repos.filter(
    (repo) => topic === "全部" || repo.topic === topic,
  );
  return (
    <section className="discover-page">
      <PageHeader title="发现" />
      <div className="page-body">
        <Tabs
          items={["为你", "最近更新", "专题"]}
          value={tab}
          onChange={setTab}
        />
        {tab === "专题" ? (
          <div className="topic-list">
            {["效率工具", "界面设计", "阅读", "开发工具"].map((name) => (
              <button
                className="topic-tile"
                key={name}
                onClick={() => {
                  setTopic(name);
                  setTab("为你");
                }}
              >
                <strong>{name}</strong>
                <span className="topic-project-count">
                  {repos.filter((repo) => repo.topic === name).length} 个项目
                </span>
                <Icon name="chevron" size={17} />
              </button>
            ))}
          </div>
        ) : (
          <>
            <div className="filter-chips" aria-label="项目主题">
              {["全部", "效率工具", "界面设计", "阅读", "开发工具"].map(
                (name) => (
                  <button
                    key={name}
                    className={name === topic ? "selected" : ""}
                    aria-pressed={name === topic}
                    onClick={() => setTopic(name)}
                  >
                    {name}
                  </button>
                ),
              )}
            </div>
            {selected.map((repo) => (
              <RepoItem repo={repo} key={repo.id} />
            ))}
          </>
        )}
        <DemoFootnote />
      </div>
    </section>
  );
}

const feedItems = [
  {
    icon: "tag",
    name: "little-forest",
    verb: "发布了新版本",
    repo: "leaf-reader",
    title: "v2.1.0 · 让订阅更有条理",
    description:
      "新的文件夹管理、全新的阅读进度。还有一些让阅读更舒服的小改进。",
    time: "35 分钟前",
    kind: "版本",
    route: "/repo/leaf-reader?tab=releases",
    label: "Release",
  },
  {
    icon: "pr",
    name: "yuki",
    verb: "发起了一个 Pull Request",
    repo: "mori-notes",
    title: "新增离线全文搜索 #132",
    description: "断开网络也能找到灵感。这次尝试将搜索索引保存在本地。",
    time: "2 小时前",
    kind: "代码",
    route: "/repo/mori-notes/pr/132",
    label: "+186 −24",
  },
  {
    icon: "message",
    name: "lin-mu",
    verb: "发起了一个讨论",
    repo: "mori-notes",
    title: "你希望笔记应用保留什么？",
    description: "少一点功能，还是多一点自由？聊聊你心中理想的笔记工具。",
    time: "4 小时前",
    kind: "讨论",
    route: "/repo/mori-notes?tab=more&section=discussions",
    label: "12 条回复",
  },
  {
    icon: "commit",
    name: "chen",
    verb: "推送了新的提交",
    repo: "lumen-ui",
    title: "让动效更轻，也更自然",
    description: "优化列表过渡，完善减少动态效果的系统设置支持。",
    time: "昨天",
    kind: "代码",
    route: "/repo/lumen-ui?tab=code",
    label: "3 个提交",
  },
];
export function Feed() {
  const { navigate, watching } = useApp();
  const [tab, setTab] = useState("全部");
  const items = feedItems.filter(
    (item) =>
      watching.includes(item.repo) && (tab === "全部" || item.kind === tab),
  );
  return (
    <>
      <PageHeader title="动态" subtitle="你关心的项目，正在发生的事" />
      <div className="page-body">
        <Tabs
          items={["全部", "版本", "代码", "讨论"]}
          value={tab}
          onChange={setTab}
        />
        <div className="timeline-list">
          {items.map((item, i) => (
            <article className="timeline-item" key={item.title}>
              <div className="timeline-track">
                <span
                  className={
                    "timeline-icon tone-" +
                    (item.kind === "版本" ? "olive" : "neutral")
                  }
                >
                  <Icon name={item.icon} size={17} />
                </span>
                {i < items.length - 1 && <span className="timeline-line" />}
              </div>
              <div className="timeline-content">
                <div className="event-person">
                  <strong>{item.name}</strong>
                  <span>{item.time}</span>
                </div>
                <p className="event-verb">{item.verb}</p>
                <button
                  className="feed-entry"
                  onClick={() => navigate(item.route)}
                >
                  <span className="eyebrow">{item.repo}</span>
                  <h3>{item.title}</h3>
                  <p>{item.description}</p>
                  <span className="feed-entry-footer">
                    <span>{item.label}</span>
                    <Icon name="diagonal" size={17} />
                  </span>
                </button>
              </div>
            </article>
          ))}
        </div>
        {!items.length && (
          <Empty
            icon="activity"
            title="这一栏还很安静"
            text="订阅感兴趣的仓库，它们的新变化会出现在这里。"
            action="去发现项目"
            onAction={() => navigate("/discover")}
          />
        )}
        <DemoFootnote text="示例动态 · 跟随你的仓库订阅" />
      </div>
    </>
  );
}

export function InboxPage() {
  const { navigate, unread, setUnread, done, setDone, notify } = useApp();
  const [tab, setTab] = useState("全部");
  const items = notifications.filter((n) =>
    tab === "已完成"
      ? done.includes(n.id)
      : !done.includes(n.id) && (tab !== "未读" || unread.includes(n.id)),
  );
  const markAll = () => {
    const before = [...unread];
    setUnread([]);
    notify("已将全部通知标为已读", () => setUnread(before));
  };
  const complete = (id: string) => {
    const oldDone = [...done];
    const oldUnread = [...unread];
    if (done.includes(id)) {
      setDone(done.filter((n) => n !== id));
      notify("已移回收件箱");
    } else {
      setDone([...done, id]);
      setUnread(unread.filter((n) => n !== id));
      notify("已完成，可在「已完成」中查看", () => {
        setDone(oldDone);
        setUnread(oldUnread);
      });
    }
  };
  return (
    <>
      <PageHeader
        title="收件箱"
        subtitle={
          unread.length
            ? `${unread.length} 条未读，留意重要的变化`
            : "都看过了，安心继续阅读"
        }
        actions={
          <>
            <IconButton
              icon="checks"
              label="全部标为已读"
              onClick={markAll}
              disabled={!unread.length}
            />
            <IconButton
              icon="settings"
              label="通知设置"
              onClick={() => navigate("/settings")}
            />
          </>
        }
      />
      <div className="page-body">
        <Tabs
          items={["全部", "未读", "已完成"]}
          value={tab}
          onChange={setTab}
        />
        {["今天", "昨天"].map(
          (section) =>
            items.some((n) => n.section === section) && (
              <section key={section}>
                <div className="date-divider">{section}</div>
                {items
                  .filter((n) => n.section === section)
                  .map((n) => (
                    <article
                      key={n.id}
                      className={
                        "notification-row" +
                        (unread.includes(n.id) ? " is-unread" : "")
                      }
                    >
                      <span
                        className={
                          "notification-icon " +
                          (n.kind === "ci" ? "ink-red" : "ink-olive")
                        }
                      >
                        <Icon
                          name={
                            n.kind === "review"
                              ? "pr"
                              : n.kind === "release"
                                ? "tag"
                                : n.kind === "ci"
                                  ? "circle-x"
                                  : "message"
                          }
                          size={19}
                        />
                      </span>
                      <button
                        className="notification-content"
                        onClick={() => {
                          setUnread(unread.filter((id) => id !== n.id));
                          navigate(n.route);
                        }}
                      >
                        <span className="notification-meta">
                          {n.repo}
                          <time>{n.time}</time>
                        </span>
                        <strong>{n.title}</strong>
                        <span className="notification-reason">
                          {unread.includes(n.id) && <i />}
                          {n.reason}
                        </span>
                      </button>
                      <IconButton
                        icon={done.includes(n.id) ? "inbox" : "check"}
                        label={
                          done.includes(n.id)
                            ? `移回收件箱：${n.title}`
                            : `完成通知：${n.title}`
                        }
                        onClick={() => complete(n.id)}
                      />
                    </article>
                  ))}
              </section>
            ),
        )}
        {!items.length && (
          <Empty
            icon={tab === "已完成" ? "circle-check" : "inbox"}
            title={
              tab === "未读"
                ? "未读清零，心里清爽"
                : tab === "已完成"
                  ? "还没有已完成的通知"
                  : "收件箱很清爽"
            }
            text={
              tab === "已完成"
                ? "点击通知旁的对勾，给这件事画个句号。"
                : "新的消息会在这里与你相遇。"
            }
          />
        )}
        <DemoFootnote />
      </div>
    </>
  );
}

export function LibraryPage() {
  const {
    route,
    navigate,
    stars,
    later,
    watching,
    downloads,
    setDownloads,
    notify,
  } = useApp();
  const initial = new URLSearchParams(route.split("?")[1]).get("tab") || "仓库";
  const [tab, setTab] = useState(initial);
  const [query, setQuery] = useState("");
  const list = repos.filter(
    (repo) =>
      (tab === "仓库"
        ? ["mori-notes", "lumen-ui"].includes(repo.id)
        : tab === "Star"
          ? stars.includes(repo.id)
          : tab === "稍后看"
            ? later.includes(repo.id)
            : watching.includes(repo.id)) &&
      (repo.name + repo.description)
        .toLowerCase()
        .includes(query.toLowerCase()),
  );
  return (
    <>
      <PageHeader title="资料库" subtitle="你收藏的，也是你在乎的" />
      <div className="page-body">
        <Tabs
          items={["仓库", "Star", "稍后看", "订阅", "下载"]}
          value={tab}
          onChange={setTab}
        />
        {tab !== "下载" && (
          <label className="search-box small-search">
            <Icon name="search" size={18} />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="在资料库中查找"
              aria-label="在资料库中查找"
            />
            {query && (
              <IconButton
                icon="close"
                label="清除资料库搜索"
                onClick={() => setQuery("")}
              />
            )}
          </label>
        )}
        {tab === "下载" ? (
          <>
            <p className="muted help-text">
              这里展示模拟下载记录，不会生成安装包。
            </p>
            {downloads.map((name) => (
              <div className="download-row" key={name}>
                <Icon name="package" />
                <div>
                  <strong>{name}</strong>
                  <small>已加入演示列表 · 无实际文件</small>
                </div>
                <IconButton
                  icon="trash"
                  label={"移除下载记录 " + name}
                  onClick={() => {
                    setDownloads(downloads.filter((n) => n !== name));
                    notify("已移除演示记录");
                  }}
                />
              </div>
            ))}
            {!downloads.length && (
              <Empty
                icon="download"
                title="还没有下载记录"
                text="在仓库的「发布」中体验版本下载流程。"
                action="查看最新版本"
                onAction={() => navigate("/repo/mori-notes?tab=releases")}
              />
            )}
          </>
        ) : (
          <>
            <div className="list-summary">
              {list.length} 个{tab === "稍后看" ? "待读项目" : "仓库"}
              <span>
                {tab === "仓库"
                  ? "你参与的项目"
                  : tab === "订阅"
                    ? "更新会出现在动态中"
                    : "为下一次灵感留个位置"}
              </span>
            </div>
            {list.map((repo) => (
              <RepoItem key={repo.id} repo={repo} simple />
            ))}
            {!list.length && (
              <Empty
                icon={query ? "search" : "bookmark"}
                title={query ? "没有匹配的仓库" : "还没有收藏到这里"}
                text={
                  query
                    ? "换个关键词试试。"
                    : "去发现页逛逛，带回一点新鲜的想法。"
                }
                action={query ? "清空搜索" : "去发现"}
                onAction={() => (query ? setQuery("") : navigate("/discover"))}
              />
            )}
          </>
        )}
        <DemoFootnote />
      </div>
    </>
  );
}

export function SearchPage() {
  const {
    navigate,
    searchText,
    setSearchText,
    searchType,
    setSearchType,
    setDialog,
  } = useApp();
  const [language, setLanguage] = useState("全部语言");
  const [sort, setSort] = useState("最佳匹配");
  const [status, setStatus] = useState("全部状态");
  const q = searchText.trim().toLowerCase();
  const match = (str: string) => str.toLowerCase().includes(q);
  const repoResults = repos
    .filter(
      (repo) =>
        match(
          repo.name +
            repo.description +
            repo.topic +
            repo.owner +
            repo.language,
        ) &&
        (language === "全部语言" || repo.language === language),
    )
    .sort((a, b) => (sort === "Star 最多" ? b.stars - a.stars : 0));
  const issueResults = issues.filter(
    (i) =>
      match(i.title + i.author + i.id) &&
      (status === "全部状态" ||
        (status === "开放" ? i.state === "open" : i.state !== "open")),
  );
  const prResults = pulls.filter(
    (i) =>
      match(i.title + i.author + i.id) &&
      (status === "全部状态" ||
        (status === "开放"
          ? i.state === "open" || i.state === "draft"
          : i.state === "merged")),
  );
  const files = [
    {
      name: "MainActivity.kt",
      repo: "mori-notes",
      content: "setContent { MoriTheme { NotesScreen() } }",
    },
    {
      name: "README.md",
      repo: "mori-notes",
      content: "Mori Notes — 让灵感有处可栖。",
    },
    {
      name: "Button.kt",
      repo: "lumen-ui",
      content: "@Composable fun LumenButton()",
    },
  ].filter((i) => match(i.name + i.repo + i.content));
  const users = ["lin-mu", "yuki", "chen", "mori-labs"].filter(match);
  const count =
    searchType === "仓库"
      ? repoResults.length
      : searchType === "Issue"
        ? issueResults.length
        : searchType === "PR"
          ? prResults.length
          : searchType === "代码"
            ? files.length
            : users.length;
  return (
    <>
      <PageHeader
        title="搜索"
        back="/discover"
        actions={
          <IconButton
            icon="sliders"
            label="界面设置"
            onClick={() => navigate("/settings")}
          />
        }
      />
      <div className="page-body">
        <label className="search-box">
          <Icon name="search" size={20} />
          <input
            autoFocus
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            placeholder="在 GitHub 中寻找…"
            aria-label="搜索关键词"
          />
          {searchText && (
            <IconButton
              icon="close"
              label="清除搜索"
              onClick={() => setSearchText("")}
            />
          )}
        </label>
        <Tabs
          items={["仓库", "代码", "Issue", "PR", "用户"]}
          value={searchType}
          onChange={setSearchType}
          label="搜索类型"
        />
        <div className="search-filters">
          {searchType === "仓库" && (
            <>
              <select
                aria-label="语言筛选"
                value={language}
                onChange={(e) => setLanguage(e.target.value)}
              >
                {["全部语言", "Kotlin", "TypeScript", "Rust"].map((s) => (
                  <option key={s}>{s}</option>
                ))}
              </select>
              <select
                aria-label="搜索排序"
                value={sort}
                onChange={(e) => setSort(e.target.value)}
              >
                {["最佳匹配", "Star 最多"].map((s) => (
                  <option key={s}>{s}</option>
                ))}
              </select>
            </>
          )}
          {["Issue", "PR"].includes(searchType) && (
            <select
              aria-label="状态筛选"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
            >
              {["全部状态", "开放", "已关闭"].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          )}
          <span>{count} 项示例结果</span>
        </div>
        {!q && (
          <div className="search-suggestions">
            <span>试着找找</span>
            {(searchType === "仓库"
              ? ["笔记", "Kotlin", "阅读"]
              : searchType === "代码"
                ? ["README", "MainActivity"]
                : searchType === "用户"
                  ? ["yuki", "mori"]
                  : ["搜索", "主题", "标签"]
            ).map((word) => (
              <button
                key={word}
                onClick={() => {
                  setSearchText(word);
                }}
              >
                {word}
                <Icon name="diagonal" size={12} />
              </button>
            ))}
          </div>
        )}
        {searchType === "仓库" &&
          repoResults.map((repo) => (
            <RepoItem repo={repo} key={repo.id} simple />
          ))}
        {searchType === "Issue" &&
          issueResults.map((item) => (
            <button
              className="work-item"
              key={item.id}
              onClick={() => navigate("/repo/mori-notes/issue/" + item.id)}
            >
              <Icon
                name={item.state === "open" ? "circle-dot" : "circle-check"}
                className="ink-olive"
              />
              <span>
                <strong>{item.title}</strong>
                <small>
                  mori-notes · #{item.id} · {item.author}
                </small>
              </span>
              <Icon name="chevron" size={16} />
            </button>
          ))}
        {searchType === "PR" &&
          prResults.map((item) => (
            <button
              className="work-item"
              key={item.id}
              onClick={() => navigate("/repo/mori-notes/pr/" + item.id)}
            >
              <Icon name="pr" className="ink-olive" />
              <span>
                <strong>{item.title}</strong>
                <small>
                  mori-notes · #{item.id} · {item.status}
                </small>
              </span>
              <Icon name="chevron" size={16} />
            </button>
          ))}
        {searchType === "代码" &&
          files.map((file) => (
            <button
              className="code-result"
              key={file.name}
              onClick={() =>
                navigate(
                  "/repo/" +
                    file.repo +
                    "/file?path=" +
                    encodeURIComponent(file.name),
                )
              }
            >
              <span>
                <Icon name="file-code" size={17} />
                {file.repo} / <strong>{file.name}</strong>
              </span>
              <code>{file.content}</code>
            </button>
          ))}
        {searchType === "用户" &&
          users.map((user, index) => (
            <MenuRow
              key={user}
              icon={user === "mori-labs" ? "users" : "user"}
              title={user}
              description={
                user === "mori-labs"
                  ? "开源小组 · 4 个公开仓库"
                  : [
                      "写代码，也记下一些想法。",
                      "Building little things.",
                      "关注开源与界面设计。",
                    ][index % 3]
              }
              onClick={() =>
                setDialog({
                  title: user,
                  content: (
                    <div className="user-preview">
                      <span className="profile-avatar">
                        {user[0].toUpperCase()}
                      </span>
                      <p>热爱开源，创造简单而有用的工具。</p>
                      <div className="profile-stats">
                        <span>
                          <b>12</b>仓库
                        </span>
                        <span>
                          <b>286</b>关注者
                        </span>
                        <span>
                          <b>48</b>正在关注
                        </span>
                      </div>
                      <p className="muted">示例用户资料</p>
                    </div>
                  ),
                })
              }
            />
          ))}
        {!count && (
          <Empty
            title="暂时没有找到"
            text="试试其他关键词，或切换搜索类型与筛选条件。"
            action="清空条件"
            onAction={() => {
              setSearchText("");
              setLanguage("全部语言");
              setStatus("全部状态");
            }}
          />
        )}
        <DemoFootnote text="搜索仅覆盖本原型内的示例内容" />
      </div>
    </>
  );
}

export function ProfilePage() {
  const { navigate, stars, watching, setDialog } = useApp();
  return (
    <>
      <PageHeader
        title="我的"
        actions={
          <IconButton
            icon="settings"
            label="打开界面设置"
            onClick={() => navigate("/settings")}
          />
        }
      />
      <div className="page-body">
        <section className="profile-intro">
          <span className="profile-avatar">木</span>
          <div>
            <h2>
              林木 <Badge>演示账号</Badge>
            </h2>
            <span className="muted">@lin-mu</span>
          </div>
          <p>
            写代码，也记下一些想法。
            <br />
            喜欢简单、安静、有用的小东西。
          </p>
          <span className="profile-location">
            <Icon name="globe" size={14} /> 在互联网的一角
          </span>
        </section>
        <div className="profile-stats">
          <button onClick={() => navigate("/library")}>
            <b>2</b>仓库
          </button>
          <button onClick={() => navigate("/library?tab=Star")}>
            <b>{stars.length}</b>Star
          </button>
          <button onClick={() => navigate("/library?tab=订阅")}>
            <b>{watching.length}</b>订阅
          </button>
        </div>
        <SectionHeading title="最近在写" />
        <div
          className="contribution-chart"
          aria-label="示例贡献日历，过去 12 周共 86 次贡献"
        >
          {Array.from({ length: 84 }, (_, i) => (
            <span key={i} data-level={(i * 13 + (i % 7) * 3) % 5} />
          ))}
        </div>
        <div className="contribution-caption">
          <span>过去 12 周 · 86 次示例贡献</span>
          <span>
            少 <i /> <i /> <i /> 多
          </span>
        </div>
        <div className="menu-section">
          <MenuRow
            icon="circle-check"
            title="我的工作"
            description="待审核、已分配与参与的事项"
            onClick={() => navigate("/work")}
          />
          <MenuRow
            icon="sliders"
            title="界面与内容"
            description="选项卡、首页模块、阅读密度"
            onClick={() => navigate("/settings")}
          />
          <MenuRow
            icon="users"
            title="我的组织"
            suffix={<span className="muted">1</span>}
            onClick={() =>
              setDialog({
                title: "mori-labs",
                content: (
                  <>
                    <p>让工具安静地陪伴日常。</p>
                    <MenuRow
                      icon="repo"
                      title="mori-notes"
                      description="2 位维护者 · 126 次 Fork"
                      onClick={() => navigate("/repo/mori-notes")}
                    />
                    <p className="muted">组织主页示例</p>
                  </>
                ),
              })
            }
          />
          <MenuRow
            icon="book"
            title="关于RepoRove"
            description="GitHub 安卓客户端交互原型"
            onClick={() =>
              setDialog({
                title: "RepoRove · 交互原型",
                content: (
                  <>
                    <p>一个以阅读为中心的 GitHub 手机客户端构想。</p>
                    <p>
                      当前版本使用虚构项目和模拟数据，展示发现、仓库阅读、通知与个性化布局。收藏和偏好保存在本机浏览器。
                    </p>
                    <p className="muted">纸感阅读 / Edition 001</p>
                  </>
                ),
              })
            }
          />
        </div>
        <DemoFootnote />
      </div>
    </>
  );
}

export function WorkPage() {
  const { navigate } = useApp();
  const [tab, setTab] = useState("待审核");
  return (
    <>
      <PageHeader title="我的工作" subtitle="先处理重要的，其他慢慢来" />
      <div className="page-body">
        <div className="work-summary">
          <div>
            <strong>01</strong>
            <span>待我审核</span>
          </div>
          <div>
            <strong>02</strong>
            <span>进行中的 PR</span>
          </div>
          <div>
            <strong>03</strong>
            <span>参与的 Issue</span>
          </div>
        </div>
        <Tabs
          items={["待审核", "我的 PR", "我的 Issue"]}
          value={tab}
          onChange={setTab}
        />
        {tab === "我的 Issue"
          ? issues
              .filter((i) => i.state === "open")
              .map((item) => (
                <button
                  className="work-item"
                  key={item.id}
                  onClick={() => navigate("/repo/mori-notes/issue/" + item.id)}
                >
                  <Icon name="circle-dot" className="ink-olive" />
                  <span>
                    <strong>{item.title}</strong>
                    <small>
                      mori-notes · #{item.id} · {item.time}
                    </small>
                  </span>
                  <Icon name="chevron" size={16} />
                </button>
              ))
          : pulls
              .slice(tab === "待审核" ? 0 : 1, tab === "待审核" ? 1 : 3)
              .map((item) => (
                <button
                  className="work-item"
                  key={item.id}
                  onClick={() => navigate("/repo/mori-notes/pr/" + item.id)}
                >
                  <Icon name="pr" className="ink-olive" />
                  <span>
                    <strong>{item.title}</strong>
                    <small>
                      mori-notes · #{item.id} · {item.status}
                    </small>
                  </span>
                  <Icon name="chevron" size={16} />
                </button>
              ))}
        <DemoFootnote />
      </div>
    </>
  );
}
