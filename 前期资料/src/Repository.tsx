import { useState } from "react";
import { repos, issues, pulls, runs, compactNumber, type Repo } from "./data";
import { useApp } from "./state";
import {
  Badge,
  DemoFootnote,
  Empty,
  Icon,
  IconButton,
  MenuRow,
  PageHeader,
  RepoAvatar,
  SectionHeading,
  Tabs,
} from "./ui";

const repoTabs = [
  { id: "overview", label: "概览" },
  { id: "code", label: "代码" },
  { id: "issues", label: "Issue" },
  { id: "pulls", label: "PR" },
  { id: "releases", label: "发布" },
  { id: "actions", label: "Actions" },
  { id: "more", label: "更多" },
];

export default function RepositoryPage() {
  const { route, navigate } = useApp();
  const parts = route.split("?")[0].split("/");
  const repo = repos.find((r) => r.id === parts[2]);
  if (!repo)
    return (
      <>
        <PageHeader title="仓库" back="/discover" />
        <Empty
          title="这个示例仓库不存在"
          action="返回发现"
          onAction={() => navigate("/discover")}
        />
      </>
    );
  if (parts[3] === "issue")
    return <IssueDetail key={route} repo={repo} id={Number(parts[4])} />;
  if (parts[3] === "pr")
    return <PullDetail key={route} repo={repo} id={Number(parts[4])} />;
  if (parts[3] === "run")
    return <RunDetail key={route} repo={repo} id={Number(parts[4])} />;
  if (parts[3] === "readme") return <ReadmePage repo={repo} />;
  if (parts[3] === "file") return <FilePage key={route} repo={repo} />;
  return <RepoHome key={repo.id} repo={repo} />;
}

function RepoHome({ repo }: { repo: Repo }) {
  const {
    route,
    navigate,
    stars,
    toggleStar,
    later,
    toggleLater,
    watching,
    toggleWatch,
    setDialog,
    copy,
  } = useApp();
  const params = new URLSearchParams(route.split("?")[1]);
  const tab = params.get("tab") || "overview";
  const base = "/repo/" + repo.id;
  return (
    <>
      <PageHeader
        title="仓库"
        back="/discover"
        actions={
          <>
            <IconButton
              icon="bookmark"
              label={later.includes(repo.id) ? "移出稍后看" : "保存到稍后看"}
              active={later.includes(repo.id)}
              onClick={() => toggleLater(repo.id)}
            />
            <IconButton
              icon="more"
              label="仓库更多操作"
              onClick={() =>
                setDialog({
                  title: repo.name,
                  content: (
                    <>
                      <MenuRow
                        icon="copy"
                        title="复制示例仓库路径"
                        description={repo.owner + "/" + repo.name}
                        onClick={() => {
                          setDialog(null);
                          void copy(repo.owner + "/" + repo.name);
                        }}
                      />
                      <MenuRow
                        icon="file"
                        title="查看许可证"
                        description={repo.license}
                        onClick={() => navigate(base + "/file?path=LICENSE")}
                      />
                      <MenuRow
                        icon="sliders"
                        title="自定义仓库首页"
                        onClick={() => navigate("/settings")}
                      />
                    </>
                  ),
                })
              }
            />
          </>
        }
      />
      <div className="page-body repo-page">
        <div className="repo-identity">
          <RepoAvatar repo={repo} />
          <div>
            <span>{repo.owner} /</span>
            <h2>{repo.name}</h2>
          </div>
          <Badge>Public</Badge>
        </div>
        <p className="repository-description">{repo.description}</p>
        <div className="repo-facts">
          <span>
            <i className={"language-dot " + repo.language.toLowerCase()} />
            {repo.language}
          </span>
          <span>
            <Icon name="star" size={14} />
            {compactNumber(repo.stars + (stars.includes(repo.id) ? 1 : 0))}
          </span>
          <span>
            <Icon name="fork" size={14} />
            {repo.forks}
          </span>
          <span>
            <Icon name="shield" size={14} />
            {repo.license}
          </span>
        </div>
        <div className="repo-actions">
          <button
            className={
              "secondary-button" +
              (stars.includes(repo.id) ? " button-selected" : "")
            }
            aria-pressed={stars.includes(repo.id)}
            onClick={() => toggleStar(repo.id)}
          >
            <Icon name="star" size={16} />
            {stars.includes(repo.id) ? "已 Star" : "Star"}
          </button>
          <button
            className={
              "secondary-button" +
              (watching.includes(repo.id) ? " button-selected" : "")
            }
            aria-pressed={watching.includes(repo.id)}
            onClick={() => toggleWatch(repo.id)}
          >
            <Icon
              name={watching.includes(repo.id) ? "bell" : "bell-off"}
              size={16}
            />
            {watching.includes(repo.id) ? "已订阅" : "订阅更新"}
          </button>
          <button
            className="secondary-button"
            onClick={() => navigate(base + "?tab=code")}
          >
            <Icon name="code" size={16} />
            浏览代码
          </button>
        </div>
        <Tabs
          items={repoTabs.map((t) => t.label)}
          value={repoTabs.find((t) => t.id === tab)?.label || "概览"}
          onChange={(label) =>
            navigate(
              base + "?tab=" + repoTabs.find((t) => t.label === label)?.id,
            )
          }
          label="仓库栏目"
        />
        {tab === "overview" && <Overview repo={repo} />}
        {tab === "code" && <CodeBrowser repo={repo} />}
        {tab === "issues" && <IssueList repo={repo} />}
        {tab === "pulls" && <PullList repo={repo} />}
        {tab === "releases" && <Releases repo={repo} />}
        {tab === "actions" && <Actions repo={repo} />}
        {tab === "more" && <More repo={repo} section={params.get("section")} />}
        <DemoFootnote text="虚构仓库 · 展示数据与交互示例" />
      </div>
    </>
  );
}

function Overview({ repo }: { repo: Repo }) {
  const { prefs, navigate } = useApp();
  const base = "/repo/" + repo.id;
  return (
    <>
      {prefs.modules.map((module) => (
        <section className="repo-module" key={module}>
          {module === "status" && (
            <>
              <SectionHeading
                title="近况一览"
                detail="最近更新于 2 小时前"
                action="定制"
                onAction={() => navigate("/settings")}
              />
              <div className="status-grid">
                <button onClick={() => navigate(base + "?tab=releases")}>
                  <span>
                    <Icon name="tag" size={16} />
                    最新版本
                  </span>
                  <strong>v{repo.version}</strong>
                  <small>稳定版本 · 3 天前</small>
                </button>
                <button onClick={() => navigate(base + "?tab=actions")}>
                  <span>
                    <Icon name="circle-check" size={16} />
                    主分支构建
                  </span>
                  <strong className="ink-olive">检查通过</strong>
                  <small>main · 6 分钟前</small>
                </button>
                <button onClick={() => navigate(base + "?tab=issues")}>
                  <span>
                    <Icon name="circle-dot" size={16} />
                    开放 Issue
                  </span>
                  <strong>
                    {repo.issues}
                    <small> 个</small>
                  </strong>
                  <small>最近 7 天解决 4 个</small>
                </button>
                <button onClick={() => navigate(base + "?tab=pulls")}>
                  <span>
                    <Icon name="pr" size={16} />
                    待合并 PR
                  </span>
                  <strong>
                    {repo.prs}
                    <small> 个</small>
                  </strong>
                  <small>1 个等待你的审核</small>
                </button>
              </div>
            </>
          )}
          {module === "readme" && (
            <>
              <SectionHeading
                title="README"
                action="阅读全文"
                onAction={() => navigate(base + "/readme")}
              />
              <div className="readme-excerpt">
                <span className="readme-kicker">
                  {repo.name.toUpperCase().replaceAll("-", " / ")}
                </span>
                <h3>{repo.tagline}</h3>
                <p>
                  {repo.description}{" "}
                  我们希望让工具回到简单，把更多空间留给真正重要的内容。
                </p>
                <div className="readme-principles">
                  <span>
                    <Icon name="lock" size={15} />
                    本地优先
                  </span>
                  <span>
                    <Icon name="book" size={15} />
                    专注内容
                  </span>
                  <span>
                    <Icon name="code" size={15} />
                    自由开源
                  </span>
                </div>
              </div>
            </>
          )}
          {module === "release" && (
            <>
              <SectionHeading
                title="最新版本"
                action="全部版本"
                onAction={() => navigate(base + "?tab=releases")}
              />
              <button
                className="release-preview"
                onClick={() => navigate(base + "?tab=releases")}
              >
                <span className="release-icon">
                  <Icon name="tag" size={23} />
                </span>
                <span>
                  <strong>
                    v{repo.version}
                    <Badge tone="olive">Latest</Badge>
                  </strong>
                  <p>一些新想法，和更顺手的日常。</p>
                  <small>3 天前发布 · 3 个文件</small>
                </span>
                <Icon name="chevron" size={16} />
              </button>
            </>
          )}
          {module === "activity" && (
            <>
              <SectionHeading
                title="最近活动"
                action="查看代码"
                onAction={() => navigate(base + "?tab=code")}
              />
              <div className="mini-timeline">
                <button onClick={() => navigate(base + "?tab=code")}>
                  <Icon name="commit" size={16} />
                  <span>
                    <strong>优化大文件的加载速度</strong>
                    <small>yuki · a3f91c2 · 2 小时前</small>
                  </span>
                </button>
                <button onClick={() => navigate(base + "/pr/132")}>
                  <Icon name="pr" size={16} />
                  <span>
                    <strong>新增离线全文搜索</strong>
                    <small>Pull Request #132 · 等待审核</small>
                  </span>
                </button>
                <button onClick={() => navigate(base + "?tab=releases")}>
                  <Icon name="tag" size={16} />
                  <span>
                    <strong>发布 v{repo.version}</strong>
                    <small>chen · 3 天前</small>
                  </span>
                </button>
              </div>
            </>
          )}
          {module === "tasks" && (
            <>
              <SectionHeading title="我的待办" />
              <MenuRow
                icon="pr"
                title="新增离线全文搜索"
                description="#132 · yuki 请求你审核"
                onClick={() => navigate(base + "/pr/132")}
              />
              <MenuRow
                icon="message"
                title="支持按标签筛选笔记"
                description="#128 · lin-mu 提到了你"
                onClick={() => navigate(base + "/issue/128")}
              />
            </>
          )}
          {module === "ci" && (
            <>
              <SectionHeading
                title="构建与部署"
                action="全部运行"
                onAction={() => navigate(base + "?tab=actions")}
              />
              <MenuRow
                icon="circle-check"
                title="Android CI · 通过"
                description="main · 2 分 34 秒"
                onClick={() => navigate(base + "/run/486")}
              />
              <MenuRow
                icon="circle-x"
                title="图片加载 PR · 失败"
                description="fix/image-loading · 查看错误日志"
                onClick={() => navigate(base + "/run/485")}
              />
            </>
          )}
        </section>
      ))}
      {!prefs.modules.length && (
        <Empty
          icon="sliders"
          title="留白也很好"
          text="你已隐藏概览模块，其他仓库栏目仍然可以使用。"
          action="添加首页内容"
          onAction={() => navigate("/settings")}
        />
      )}
    </>
  );
}

const sourceCode = `package dev.mori.notes\n\nimport androidx.activity.ComponentActivity\nimport androidx.activity.compose.setContent\nimport android.os.Bundle\n\nclass MainActivity : ComponentActivity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        setContent {\n            MoriTheme {\n                NotesScreen()\n            }\n        }\n    }\n}\n`;
function CodeBrowser({ repo }: { repo: Repo }) {
  const { navigate, copy } = useApp();
  const [branch, setBranch] = useState("main");
  const [folder, setFolder] = useState("");
  const files = folder
    ? [
        { name: "MainActivity.kt", folder: false, note: "优化启动流程" },
        { name: "ui", folder: true, note: "更新阅读界面" },
        { name: "data", folder: true, note: "本地存储与搜索" },
      ]
    : [
        { name: ".github", folder: true, note: "完善构建工作流" },
        { name: "app", folder: true, note: "优化大文件的加载速度" },
        { name: "docs", folder: true, note: "补充使用说明" },
        { name: "README.md", folder: false, note: "更新项目介绍" },
        { name: "build.gradle.kts", folder: false, note: "更新依赖版本" },
        { name: "LICENSE", folder: false, note: repo.license },
      ];
  const deeper =
    folder.includes("/") || folder === ".github" || folder === "docs";
  const visible = deeper
    ? [
        {
          name: folder.includes("github")
            ? "ci.yml"
            : folder.includes("docs")
              ? "guide.md"
              : "MainActivity.kt",
          folder: false,
          note: "更新文档与实现",
        },
      ]
    : files;
  return (
    <div className="code-browser">
      <div className="code-toolbar">
        <label>
          <Icon name="branch" size={16} />
          <select
            aria-label="分支"
            value={branch}
            onChange={(e) => setBranch(e.target.value)}
          >
            <option>main</option>
            <option>develop</option>
          </select>
        </label>
        <button
          className="text-button"
          onClick={() =>
            void copy(repo.owner + "/" + repo.name + " (" + branch + ")")
          }
        >
          <Icon name="copy" size={15} />
          复制路径
        </button>
      </div>
      <div className="commit-summary">
        <span className="mini-avatar">{branch === "main" ? "y" : "c"}</span>
        <span>
          <strong>
            {branch === "main"
              ? "优化大文件的加载速度"
              : "准备下一版本的阅读主题"}
          </strong>
          <small>
            {branch === "main"
              ? "yuki · a3f91c2 · 2 小时前"
              : "chen · 7f238ac · 20 分钟前"}
          </small>
        </span>
        <Badge>{branch}</Badge>
      </div>
      <div className="breadcrumb">
        <button onClick={() => setFolder("")}>{repo.name}</button>
        {folder && (
          <>
            <span>/</span>
            <span>{folder}</span>
          </>
        )}
      </div>
      {folder && (
        <button
          className="file-row"
          onClick={() => setFolder(folder.split("/").slice(0, -1).join("/"))}
        >
          <Icon name="back" size={17} />
          <strong>..</strong>
          <small>上一级</small>
        </button>
      )}
      {visible.map((file) => (
        <button
          key={file.name}
          className="file-row"
          onClick={() =>
            file.folder
              ? setFolder((folder ? folder + "/" : "") + file.name)
              : navigate(
                  "/repo/" +
                    repo.id +
                    "/file?path=" +
                    encodeURIComponent(
                      (folder ? folder + "/" : "") + file.name,
                    ) +
                    "&branch=" +
                    branch,
                )
          }
        >
          <Icon name={file.folder ? "folder" : "file-code"} size={18} />
          <span>
            <strong>{file.name}</strong>
            <small>{file.note}</small>
          </span>
          <Icon name="chevron" size={14} />
        </button>
      ))}
      <p className="muted help-text">
        示例文件树 · 可切换分支、展开目录、阅读文件
      </p>
    </div>
  );
}

function IssueList({ repo }: { repo: Repo }) {
  const { navigate, setDialog, notify } = useApp();
  const [filter, setFilter] = useState("开放");
  const [query, setQuery] = useState("");
  const list = issues.filter(
    (i) =>
      (filter === "开放" ? i.state === "open" : i.state === "closed") &&
      i.title.includes(query),
  );
  return (
    <>
      <div className="section-toolbar">
        <Tabs
          items={["开放", "已关闭"]}
          value={filter}
          onChange={setFilter}
          compact
        />
        <button
          className="small-button"
          onClick={() =>
            setDialog({
              title: "新建 Issue",
              content: (
                <IssueComposer
                  onSubmit={(text) => {
                    notify("已在本机保存示例 Issue 草稿：" + text);
                    setDialog(null);
                  }}
                />
              ),
            })
          }
        >
          <Icon name="plus" size={15} />
          新建
        </button>
      </div>
      <label className="search-box small-search">
        <Icon name="search" size={16} />
        <input
          placeholder="筛选 Issue"
          aria-label="筛选 Issue"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </label>
      {list.map((issue) => (
        <button
          className="issue-row"
          key={issue.id}
          onClick={() => navigate("/repo/" + repo.id + "/issue/" + issue.id)}
        >
          <Icon
            name={issue.state === "open" ? "circle-dot" : "circle-check"}
            size={20}
            className="ink-olive"
          />
          <div>
            <strong>{issue.title}</strong>
            <Badge tone={issue.label === "bug" ? "red" : "olive"}>
              {issue.labelText}
            </Badge>
            <small>
              #{issue.id} · {issue.author} · {issue.time}
            </small>
          </div>
          <span className="comment-count">
            <Icon name="message" size={14} />
            {issue.comments}
          </span>
        </button>
      ))}
      {!list.length && (
        <Empty title="没有匹配的 Issue" text="试试另一个状态或关键词。" />
      )}
    </>
  );
}
function IssueComposer({ onSubmit }: { onSubmit: (text: string) => void }) {
  const [title, setTitle] = useState(
    () => localStorage.getItem("qiye-demo-issue-draft-title") || "",
  );
  const [body, setBody] = useState(
    () => localStorage.getItem("qiye-demo-issue-draft-body") || "",
  );
  return (
    <form
      className="composer-form"
      onSubmit={(e) => {
        e.preventDefault();
        if (!title.trim()) return;
        localStorage.setItem("qiye-demo-issue-draft-title", title.trim());
        localStorage.setItem("qiye-demo-issue-draft-body", body);
        onSubmit(title.trim());
      }}
    >
      <label>
        标题
        <input
          required
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="用一句话说明你的想法"
        />
      </label>
      <label>
        描述
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          placeholder="背景、期望行为，或复现步骤…"
          rows={5}
        />
      </label>
      <p className="muted">
        仅保存为本机草稿，不会提交至 GitHub。再次新建时可继续编辑。
      </p>
      <button
        type="submit"
        className="primary-button full-width"
        disabled={!title.trim()}
      >
        保存示例草稿
      </button>
    </form>
  );
}
function PullList({ repo }: { repo: Repo }) {
  const { navigate } = useApp();
  const [filter, setFilter] = useState("开放");
  return (
    <>
      <div className="section-toolbar">
        <Tabs
          items={["开放", "已合并"]}
          value={filter}
          onChange={setFilter}
          compact
        />
        <span className="muted">示例 Pull Requests</span>
      </div>
      {pulls
        .filter((p) =>
          filter === "开放" ? p.state !== "merged" : p.state === "merged",
        )
        .map((pr) => (
          <button
            className="issue-row"
            key={pr.id}
            onClick={() => navigate("/repo/" + repo.id + "/pr/" + pr.id)}
          >
            <Icon
              name="pr"
              size={20}
              className={pr.state === "draft" ? "muted" : "ink-olive"}
            />
            <div>
              <strong>{pr.title}</strong>
              <Badge
                tone={
                  pr.ci === "失败"
                    ? "red"
                    : pr.state === "draft"
                      ? "neutral"
                      : "olive"
                }
              >
                {pr.status}
              </Badge>
              <small>
                #{pr.id} · {pr.author} · {pr.time}
              </small>
            </div>
            <Icon name="chevron" size={16} />
          </button>
        ))}
    </>
  );
}

function Comments({ itemKey }: { itemKey: string }) {
  const { comments, setComments, notify } = useApp();
  const [text, setText] = useState("");
  const [reacted, setReacted] = useState(false);
  return (
    <>
      <section className="comment-card">
        <div className="comment-author">
          <span className="mini-avatar">y</span>
          <strong>yuki</strong>
          <Badge>维护者</Badge>
          <small>1 小时前</small>
        </div>
        <p>
          这个方向不错。我会先整理一个小的实现方案，欢迎继续补充你使用时的场景。
        </p>
        <button
          className={"reaction" + (reacted ? " selected" : "")}
          aria-pressed={reacted}
          onClick={() => setReacted(!reacted)}
        >
          👍 {reacted ? 4 : 3}
        </button>
      </section>
      {(comments[itemKey] || []).map((body, i) => (
        <section className="comment-card" key={i}>
          <div className="comment-author">
            <span className="mini-avatar">木</span>
            <strong>lin-mu</strong>
            <Badge>本机示例</Badge>
            <small>刚刚</small>
          </div>
          <p>{body}</p>
        </section>
      ))}
      <form
        className="reply-form"
        onSubmit={(e) => {
          e.preventDefault();
          if (!text.trim()) return;
          setComments({
            ...comments,
            [itemKey]: [...(comments[itemKey] || []), text.trim()],
          });
          setText("");
          notify("已添加本机示例评论");
        }}
      >
        <label htmlFor="reply">参与讨论</label>
        <textarea
          id="reply"
          rows={4}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="留下你的想法…"
        />
        <div>
          <small>评论仅保存在本机</small>
          <button
            type="submit"
            className="primary-button"
            disabled={!text.trim()}
          >
            添加评论
            <Icon name="diagonal" size={15} />
          </button>
        </div>
      </form>
    </>
  );
}
function IssueDetail({ repo, id }: { repo: Repo; id: number }) {
  const { navigate } = useApp();
  const issue = issues.find((i) => i.id === id);
  if (!issue)
    return (
      <Empty
        title="未找到这个示例 Issue"
        action="返回 Issue"
        onAction={() => navigate("/repo/" + repo.id + "?tab=issues")}
      />
    );
  return (
    <>
      <PageHeader
        title={"Issue #" + id}
        subtitle={repo.name}
        back={"/repo/" + repo.id + "?tab=issues"}
        actions={
          <IconButton
            icon="code"
            label="查看仓库"
            onClick={() => navigate("/repo/" + repo.id)}
          />
        }
      />
      <div className="page-body discussion-page">
        <div className="discussion-heading">
          <Badge tone="olive">
            <Icon
              name={issue.state === "open" ? "circle-dot" : "circle-check"}
              size={13}
            />
            {issue.state === "open" ? "Open" : "Closed"}
          </Badge>
          <h2>{issue.title}</h2>
          <p>
            {issue.author} 创建于 {issue.time}
          </p>
          <Badge tone={issue.label === "bug" ? "red" : "olive"}>
            {issue.labelText}
          </Badge>
        </div>
        <section className="issue-body reading-content">
          {issue.body.split("\n\n").map((p) => (
            <p key={p}>{p}</p>
          ))}
        </section>
        <div className="discussion-divider">
          <Icon name="message" size={16} />
          <span>讨论</span>
          <span className="muted">精选示例回复</span>
        </div>
        <Comments itemKey={repo.id + "-issue-" + id} />
        <DemoFootnote />
      </div>
    </>
  );
}

function PullDetail({ repo, id }: { repo: Repo; id: number }) {
  const { navigate, setDialog, notify } = useApp();
  const [tab, setTab] = useState("对话");
  const [review, setReview] = useState("");
  const pr = pulls.find((p) => p.id === id);
  if (!pr)
    return (
      <Empty
        title="未找到这个示例 PR"
        action="返回 PR"
        onAction={() => navigate("/repo/" + repo.id + "?tab=pulls")}
      />
    );
  return (
    <>
      <PageHeader
        title={"Pull Request #" + id}
        subtitle={repo.name}
        back={"/repo/" + repo.id + "?tab=pulls"}
        actions={
          <IconButton
            icon="repo"
            label="仓库概览"
            onClick={() => navigate("/repo/" + repo.id)}
          />
        }
      />
      <div className="page-body discussion-page">
        <div className="discussion-heading">
          <Badge tone={pr.state === "draft" ? "neutral" : "olive"}>
            <Icon name="pr" size={13} />
            {pr.state === "merged"
              ? "Merged"
              : pr.state === "draft"
                ? "Draft"
                : "Open"}
          </Badge>
          <h2>{pr.title}</h2>
          <p>
            {pr.author} 希望将 3 个提交合并到 <code>main</code>
          </p>
          <div className="branch-flow">
            <Icon name="branch" size={14} />
            <span>{pr.branch}</span>
            <Icon name="back" size={12} className="rotate-arrow" />
            <strong>main</strong>
          </div>
        </div>
        <Tabs
          items={["对话", "变更", "提交", "检查"]}
          value={tab}
          onChange={setTab}
        />
        {tab === "对话" && (
          <>
            <section className="issue-body reading-content">
              <p>
                这次变更主要完善了
                {pr.title.replace("新增", "").replace("优化", "")}
                ，并补充对应的状态处理。
              </p>
              <ul>
                <li>保持现有的阅读与编辑流程</li>
                <li>补充边界场景处理和回归检查</li>
                <li>更新使用说明</li>
              </ul>
            </section>
            <div className="review-banner">
              <Icon
                name={pr.ci === "失败" ? "circle-x" : "circle-check"}
                size={20}
                className={pr.ci === "失败" ? "ink-red" : "ink-olive"}
              />
              <span>
                <strong>{review || pr.status}</strong>
                <small>持续集成：{pr.ci}</small>
              </span>
              {pr.state !== "merged" && (
                <button
                  className="small-button"
                  onClick={() =>
                    setDialog({
                      title: "提交示例审核",
                      content: (
                        <>
                          <p>选择审核结论，预览在移动端处理 PR 的体验。</p>
                          {["批准变更", "请求修改", "仅评论"].map((result) => (
                            <MenuRow
                              key={result}
                              icon={result === "批准变更" ? "check" : "message"}
                              title={result}
                              onClick={() => {
                                setReview("你的审核：" + result);
                                setDialog(null);
                                notify("已记录本机示例审核");
                              }}
                            />
                          ))}
                          <p className="muted">不会向 GitHub 提交审核。</p>
                        </>
                      ),
                    })
                  }
                >
                  审核
                </button>
              )}
            </div>
            <Comments itemKey={repo.id + "-pr-" + id} />
          </>
        )}
        {tab === "变更" && (
          <>
            <div className="diff-summary">
              <span>{pr.files} 个文件变更</span>
              <span>
                <b className="ink-olive">+{pr.additions}</b>{" "}
                <b className="ink-red">−{pr.deletions}</b>
              </span>
            </div>
            <div className="diff-file">
              <div>
                <Icon name="file-code" size={15} />
                <strong>app/search/SearchRepository.kt</strong>
              </div>
              <pre>
                <code>
                  <span className="diff-context">
                    {" "}
                    01 class SearchRepository {"{"}
                    {"\n"}
                  </span>
                  <span className="diff-removed">
                    - 02 fun search(query: String) = remote.find(query){"\n"}
                  </span>
                  <span className="diff-added">
                    + 02 suspend fun search(query: String) ={"\n"}+ 03
                    localIndex.find(query.trim()){"\n"}
                  </span>
                  <span className="diff-context"> 04 {"}"}</span>
                </code>
              </pre>
            </div>
            <p className="muted help-text">部分变更示例 · 横向滑动查看长行</p>
          </>
        )}
        {tab === "提交" && (
          <div className="mini-timeline">
            {[
              "添加本地索引与搜索入口",
              "补充搜索边界场景测试",
              "更新项目使用说明",
            ].map((message, i) => (
              <button
                key={message}
                onClick={() =>
                  setDialog({
                    title: message,
                    content: (
                      <>
                        <p>作者：{pr.author}</p>
                        <p>提交：{["2c98a61", "7f10b34", "3b91f02"][i]}</p>
                        <p className="muted">
                          示例提交包含搜索实现、测试和文档变更。
                        </p>
                      </>
                    ),
                  })
                }
              >
                <Icon name="commit" size={18} />
                <span>
                  <strong>{message}</strong>
                  <small>
                    {pr.author} · {i + 1} 小时前
                  </small>
                </span>
              </button>
            ))}
          </div>
        )}
        {tab === "检查" && (
          <>
            <MenuRow
              icon={pr.ci === "失败" ? "circle-x" : "circle-check"}
              title={"Android CI · " + pr.ci}
              description="构建、静态检查与单元测试"
              onClick={() =>
                navigate(
                  "/repo/" + repo.id + "/run/" + (pr.ci === "失败" ? 485 : 486),
                )
              }
            />
            <MenuRow
              icon="circle-check"
              title="Code style · 通过"
              description="格式和命名检查通过"
              onClick={() =>
                setDialog({
                  title: "Code style",
                  content: (
                    <pre className="log-code">
                      ✓ ktlint check passed{"\n"}✓ No formatting issues{"\n"}
                      Completed in 8s
                    </pre>
                  ),
                })
              }
            />
          </>
        )}
        <DemoFootnote />
      </div>
    </>
  );
}

function Releases({ repo }: { repo: Repo }) {
  const { downloads, setDownloads, setDialog, notify } = useApp();
  const [older, setOlder] = useState(false);
  const download = (name: string, size: string) =>
    setDialog({
      title: "下载版本文件",
      content: (
        <>
          <div className="download-detail">
            <Icon name="package" size={28} />
            <strong>{name}</strong>
            <span>{size} · GitHub Release</span>
          </div>
          <p>
            当前为界面演示。确认后会添加一条模拟下载记录，不会下载真实文件。
          </p>
        </>
      ),
      action: downloads.includes(name)
        ? "已在演示下载列表中"
        : "模拟加入下载列表",
      onAction: () => {
        if (!downloads.includes(name)) setDownloads([...downloads, name]);
        notify("可以在「资料库 → 下载」查看演示记录");
      },
    });
  return (
    <section className="release-page">
      <div className="release-title">
        <h3>v{repo.version}</h3>
        <Badge tone="olive">Latest</Badge>
      </div>
      <div className="muted release-byline">chen 发布于 3 天前 · main</div>
      <div className="reading-content">
        <h4>一些新想法，和更顺手的日常。</h4>
        <p>
          谢谢大家的反馈。这次更新让日常使用更轻快，也带来了一些期待已久的小功能。
        </p>
        <h4>新增</h4>
        <ul>
          <li>支持自定义阅读字号与行间距</li>
          <li>增加快速标签筛选和本地搜索</li>
        </ul>
        <h4>改进与修复</h4>
        <ul>
          <li>优化长文档滚动和图片加载</li>
          <li>修复首次启动时的主题闪烁</li>
        </ul>
      </div>
      <SectionHeading title="版本文件" detail="3 个文件" />
      <div className="release-assets">
        {[
          {
            name:
              repo.id +
              "-" +
              repo.version +
              (repo.language === "Kotlin" ? ".apk" : ".zip"),
            size: "18.6 MB",
          },
          { name: "Source code.zip", size: "2.4 MB" },
          { name: "Source code.tar.gz", size: "2.1 MB" },
        ].map((file) => (
          <button
            key={file.name}
            onClick={() => download(file.name, file.size)}
          >
            <Icon name="package" size={18} />
            <span>
              <strong>{file.name}</strong>
              <small>{file.size}</small>
            </span>
            <Icon name="download" size={17} />
          </button>
        ))}
      </div>
      <button className="older-releases" onClick={() => setOlder(!older)}>
        更早的版本
        <Icon name={older ? "up" : "down"} size={16} />
      </button>
      {older && (
        <div className="older-release-body">
          <h4>上一个稳定版本</h4>
          <p>改善离线体验，修复已知问题，更新使用文档。</p>
          <small>发布于 3 周前</small>
        </div>
      )}
    </section>
  );
}
function Actions({ repo }: { repo: Repo }) {
  const { navigate } = useApp();
  const [filter, setFilter] = useState("全部工作流");
  return (
    <>
      <label className="actions-filter">
        <Icon name="filter" size={16} />
        <select
          value={filter}
          aria-label="工作流筛选"
          onChange={(e) => setFilter(e.target.value)}
        >
          <option>全部工作流</option>
          <option>Android CI</option>
          <option>Release</option>
        </select>
      </label>
      {runs
        .filter((run) => filter === "全部工作流" || run.name === filter)
        .map((run) => (
          <button
            className="run-row"
            key={run.id}
            onClick={() => navigate("/repo/" + repo.id + "/run/" + run.id)}
          >
            <Icon
              name={run.status === "success" ? "circle-check" : "circle-x"}
              className={run.status === "success" ? "ink-olive" : "ink-red"}
            />
            <span>
              <strong>{run.message}</strong>
              <small>
                {run.name} #{run.id} · {run.branch}
              </small>
              <small>
                {run.time} · {run.duration}
              </small>
            </span>
            <Icon name="chevron" size={16} />
          </button>
        ))}
    </>
  );
}
function RunDetail({ repo, id }: { repo: Repo; id: number }) {
  const { setDialog, notify } = useApp();
  const run = runs.find((r) => r.id === id) || runs[0];
  const [open, setOpen] = useState(
    run.status === "failure" ? "运行测试" : "构建应用",
  );
  const [rerun, setRerun] = useState(false);
  return (
    <>
      <PageHeader
        title={run.name + " #" + run.id}
        subtitle={repo.name}
        back={"/repo/" + repo.id + "?tab=actions"}
        actions={<span />}
      />
      <div className="page-body">
        <div className="run-heading">
          <Icon
            name={run.status === "success" ? "circle-check" : "circle-x"}
            size={36}
            className={run.status === "success" ? "ink-olive" : "ink-red"}
          />
          <h2>{run.message}</h2>
          <Badge tone={run.status === "success" ? "olive" : "red"}>
            {run.status === "success" ? "构建成功" : "构建失败"}
          </Badge>
        </div>
        <dl className="info-grid">
          <div>
            <dt>分支</dt>
            <dd>{run.branch}</dd>
          </div>
          <div>
            <dt>触发者</dt>
            <dd>{run.author}</dd>
          </div>
          <div>
            <dt>耗时</dt>
            <dd>{run.duration}</dd>
          </div>
          <div>
            <dt>提交</dt>
            <dd>{run.commit}</dd>
          </div>
        </dl>
        <SectionHeading title="运行步骤" />
        {["检出代码", "配置 JDK 17", "运行测试", "构建应用"].map((step, i) => {
          const fail = run.status === "failure" && i === 2;
          const skipped = run.status === "failure" && i === 3;
          return (
            <div className="job-step" key={step}>
              <button
                aria-expanded={open === step}
                onClick={() => setOpen(open === step ? "" : step)}
              >
                <Icon
                  name={fail ? "circle-x" : skipped ? "circle" : "check"}
                  size={17}
                  className={fail ? "ink-red" : "ink-olive"}
                />
                <strong>{step}</strong>
                <span>
                  {fail
                    ? "失败"
                    : skipped
                      ? "跳过"
                      : ["2 秒", "14 秒", "48 秒", "1 分 30 秒"][i]}
                </span>
                <Icon name={open === step ? "up" : "expand"} size={16} />
              </button>
              {open === step && (
                <pre className={"log-code" + (fail ? " error-log" : "")}>
                  {fail
                    ? "> Task :app:testDebugUnitTest FAILED\n\nImageCacheTest > loads_cached_image FAILED\n  Expected: image cached\n  Actual: cache entry missing\n\nProcess completed with exit code 1."
                    : skipped
                      ? "Skipped because a previous step failed."
                      : "> " +
                        step +
                        "\n✓ Completed successfully\nProcess completed with exit code 0."}
                </pre>
              )}
            </div>
          );
        })}
        <button
          className="secondary-button full-width rerun-button"
          onClick={() =>
            setDialog({
              title: "模拟重新运行",
              content: (
                <p>
                  将演示重新运行入口，并添加一条本机排队状态。不会触发真实构建。
                </p>
              ),
              action: "模拟重新运行",
              onAction: () => {
                setRerun(true);
                notify("已添加示例运行记录");
              },
            })
          }
        >
          <Icon name="play" size={16} />
          重新运行
        </button>
        {rerun && (
          <div className="inline-notice">
            <Icon name="clock" size={18} />
            <span>
              新的示例运行正在排队
              <br />
              <small>演示状态，不会执行真实工作流。</small>
            </span>
          </div>
        )}
        <DemoFootnote />
      </div>
    </>
  );
}

function More({ repo, section }: { repo: Repo; section: string | null }) {
  const { setDialog, navigate } = useApp();
  const show = (title: string, content: React.ReactNode) =>
    setDialog({ title, content });
  const discussion = (
    <>
      <Badge tone="olive">想法交流</Badge>
      <h3>你希望笔记应用保留什么？</h3>
      <p>
        少一点功能，还是多一点自由？我们想把常用功能做得更好，而不是不断增加入口。
      </p>
      <p className="muted">lin-mu · 12 条示例回复</p>
      <Comments itemKey={repo.id + "-discussion"} />
    </>
  );
  return (
    <div className="more-page">
      {section === "discussions" && (
        <section className="discussion-highlight">{discussion}</section>
      )}
      <MenuRow
        icon="message"
        title="Discussions"
        description="交流想法、提问和分享"
        suffix={<span className="muted">6</span>}
        onClick={() =>
          navigate("/repo/" + repo.id + "?tab=more&section=discussions")
        }
      />
      <MenuRow
        icon="panel"
        title="Projects"
        description="项目看板与开发进度"
        onClick={() =>
          show(
            "下一版本 · 项目看板",
            <div className="project-board">
              {["待办", "进行中", "已完成"].map((name, i) => (
                <section key={name}>
                  <h3>
                    {name}
                    <Badge>{i === 0 ? 2 : 1}</Badge>
                  </h3>
                  {(i === 0
                    ? ["批量管理标签", "优化导出格式"]
                    : i === 1
                      ? ["离线全文搜索"]
                      : ["修复主题闪烁"]
                  ).map((t) => (
                    <p key={t}>
                      <Icon name="circle-dot" size={15} />
                      {t}
                    </p>
                  ))}
                </section>
              ))}
            </div>,
          )
        }
      />
      <MenuRow
        icon="activity"
        title="Insights"
        description="活跃度、贡献与语言统计"
        onClick={() =>
          show(
            "仓库统计",
            <>
              <div className="stats-banner">
                <strong>近 30 天</strong>
                <span>28 次提交 · 4 位贡献者</span>
              </div>
              <div
                className="activity-bars"
                aria-label="示例每周提交量 5、8、6、9"
              >
                {[5, 8, 6, 9].map((n, i) => (
                  <div key={i}>
                    <span style={{ height: n * 10 }} />
                    <small>
                      第 {i + 1} 周 · {n}
                    </small>
                  </div>
                ))}
              </div>
              <p>主要语言：{repo.language} 94.2%</p>
              <p className="muted">
                开放 {repo.issues} 个 Issue，{repo.prs} 个 PR。
              </p>
            </>,
          )
        }
      />
      <MenuRow
        icon="shield"
        title="Security"
        description="安全策略与公开公告"
        onClick={() =>
          show(
            "安全与策略",
            <>
              <div className="inline-notice">
                <Icon name="shield" size={22} />
                <span>示例项目暂无公开安全公告</span>
              </div>
              <h3>报告漏洞</h3>
              <p>
                通过项目的私密漏洞报告入口联系维护者，避免在公开 Issue
                中暴露未修复的细节。
              </p>
              <p className="muted">此处仅展示公开安全信息，无私有扫描数据。</p>
            </>,
          )
        }
      />
      <MenuRow
        icon="users"
        title="贡献者"
        description="一起让项目更好的人"
        onClick={() =>
          show(
            "贡献者",
            <>
              {["yuki", "lin-mu", "chen", "little-forest"].map((n, i) => (
                <div className="contributor" key={n}>
                  <span className="mini-avatar">{n[0]}</span>
                  <strong>{n}</strong>
                  <small>{[126, 84, 48, 12][i]} 次提交</small>
                </div>
              ))}
            </>,
          )
        }
      />
      <MenuRow
        icon="book"
        title="Wiki 与文档"
        description="快速开始、使用说明与贡献指南"
        onClick={() => navigate("/repo/" + repo.id + "/readme")}
      />
      <MenuRow
        icon="package"
        title="Packages"
        description="构建产物与软件包"
        onClick={() =>
          show(
            "Packages",
            <Empty
              icon="package"
              title="尚未发布软件包"
              text="当前示例通过 Releases 提供版本文件。"
              action="查看发布"
              onAction={() => navigate("/repo/" + repo.id + "?tab=releases")}
            />,
          )
        }
      />
      <MenuRow
        icon="globe"
        title="部署与环境"
        description="线上环境与部署记录"
        onClick={() =>
          show(
            "部署与环境",
            <>
              <Badge tone="olive">Production · 正常</Badge>
              <h3>项目文档站</h3>
              <p>
                最近一次部署：3 天前
                <br />
                提交：bb039d1
                <br />
                执行者：chen
              </p>
              <p className="muted">示例部署信息，不对应真实服务。</p>
            </>,
          )
        }
      />
      <MenuRow
        icon="sliders"
        title="定制仓库首页"
        description="模块显示、排序与阅读方式"
        onClick={() => navigate("/settings")}
      />
    </div>
  );
}

function ReadmePage({ repo }: { repo: Repo }) {
  const { copy, navigate } = useApp();
  return (
    <>
      <PageHeader
        title="README"
        subtitle={repo.name}
        back={"/repo/" + repo.id}
        actions={
          <IconButton
            icon="sliders"
            label="调整阅读设置"
            onClick={() => navigate("/settings")}
          />
        }
      />
      <article className="page-body readme-full reading-content">
        <div className="document-masthead">
          <span className="eyebrow">{repo.name.toUpperCase()}</span>
          <h2>{repo.tagline}</h2>
          <p>{repo.description}</p>
          <div>
            <Badge>{repo.license}</Badge>
            <Badge tone="olive">v{repo.version}</Badge>
          </div>
        </div>
        <h3>让工具回到简单</h3>
        <p>
          我们习惯了功能越来越多的应用，却常常忘记自己最初为什么打开它。这个项目想做一点不同的尝试：让常用功能更容易找到，让内容有足够的呼吸空间。
        </p>
        <blockquote>好工具的存在感，应该恰到好处。</blockquote>
        <h3>为什么做这个项目</h3>
        <p>
          你的内容属于你。无需创建新账号，也不用等待网络连接，就可以从一个小想法开始。
        </p>
        <ul>
          <li>
            <strong>本地优先。</strong>离线也能使用，内容保存在设备上。
          </li>
          <li>
            <strong>专注阅读。</strong>清晰的排版，克制的界面。
          </li>
          <li>
            <strong>保持开放。</strong>代码开源，支持通用文件格式。
          </li>
        </ul>
        <h3>快速开始</h3>
        <p>
          在「发布」页面选择适合你设备的版本。对于开发者，也可以阅读代码并在本地构建。
        </p>
        <div className="code-snippet">
          <div>
            <span>示例构建命令</span>
            <IconButton
              icon="copy"
              label="复制构建命令"
              onClick={() => void copy("./gradlew assembleDebug")}
            />
          </div>
          <pre>
            <code>./gradlew assembleDebug</code>
          </pre>
        </div>
        <button
          className="secondary-button"
          onClick={() => navigate("/repo/" + repo.id + "?tab=releases")}
        >
          <Icon name="tag" size={16} />
          查看发布版本
          <Icon name="diagonal" size={15} />
        </button>
        <h3>一起参与</h3>
        <p>
          一个好的问题、一段使用体验，或一次微小的改进，都是有价值的贡献。欢迎先在
          Issue 中聊聊你的想法。
        </p>
        <button
          className="text-button"
          onClick={() => navigate("/repo/" + repo.id + "?tab=issues")}
        >
          浏览正在讨论的事
          <Icon name="diagonal" size={15} />
        </button>
        <h3>许可证</h3>
        <p>本项目示例采用 {repo.license} 许可证。</p>
        <DemoFootnote text="README 为原型撰写的示例内容" />
      </article>
    </>
  );
}
function FilePage({ repo }: { repo: Repo }) {
  const { route, copy, navigate } = useApp();
  const params = new URLSearchParams(route.split("?")[1]);
  const path = params.get("path") || "MainActivity.kt";
  const [wrap, setWrap] = useState(false);
  const text = path.endsWith(".md")
    ? `# ${repo.name}\n\n${repo.tagline}\n\n${repo.description}\n\n## 快速开始\n\n在 Releases 中选择适合设备的版本。\n\n## 贡献\n\n欢迎在 Issue 中分享建议。\n`
    : path === "LICENSE"
      ? `${repo.license}\n\nCopyright 2026 ${repo.owner}\n\n这是许可证文件的展示占位内容。\n正式版本中将显示仓库原始许可证全文。\n`
      : path.endsWith(".yml")
        ? "name: Android CI\non: [push, pull_request]\njobs:\n  build:\n    runs-on: ubuntu-latest\n    steps:\n      - uses: actions/checkout@v4\n      - run: ./gradlew test assembleDebug\n"
        : path.endsWith(".kts")
          ? 'plugins {\n    id("com.android.application")\n    id("org.jetbrains.kotlin.android")\n}\n\nandroid {\n    namespace = "dev.mori.notes"\n    compileSdk = 35\n}\n'
          : sourceCode;
  return (
    <>
      <PageHeader
        title={path.split("/").at(-1)!}
        subtitle={repo.name + " · " + (params.get("branch") || "main")}
        back={"/repo/" + repo.id + "?tab=code"}
        actions={
          <IconButton
            icon="copy"
            label="复制文件内容"
            onClick={() => void copy(text)}
          />
        }
      />
      <div className="page-body file-page">
        <div className="file-path">{path}</div>
        <div className="file-view-toolbar">
          <span>{text.split("\n").length - 1} 行 · 示例文件</span>
          <button
            className={wrap ? "selected text-button" : "text-button"}
            aria-pressed={wrap}
            onClick={() => setWrap(!wrap)}
          >
            自动换行 {wrap ? "开" : "关"}
          </button>
          {path.endsWith(".md") && (
            <button
              className="text-button"
              onClick={() => navigate("/repo/" + repo.id + "/readme")}
            >
              阅读
            </button>
          )}
        </div>
        <div className={"source-view" + (wrap ? " wrap-code" : "")}>
          <pre>
            {text
              .trimEnd()
              .split("\n")
              .map((line, index) => (
                <span className="source-line" key={index}>
                  <span className="line-number">{index + 1}</span>
                  <code>{line || " "}</code>
                </span>
              ))}
          </pre>
        </div>
        <DemoFootnote />
      </div>
    </>
  );
}
