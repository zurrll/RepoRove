import { useEffect, useRef, type ReactNode } from "react";
import {
  Activity,
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  ArrowUpRight,
  Bell,
  BellOff,
  BookOpen,
  Bookmark,
  Check,
  CheckCheck,
  ChevronDown,
  ChevronRight,
  Circle,
  CircleCheck,
  CircleDot,
  CircleX,
  Clock3,
  Code2,
  Compass,
  Copy,
  Download,
  Ellipsis,
  ExternalLink,
  FileCode2,
  FileText,
  Filter,
  Folder,
  FolderGit2,
  GitBranch,
  GitCommitHorizontal,
  GitFork,
  GitPullRequest,
  Globe,
  Hash,
  Inbox,
  Layers,
  LayoutGrid,
  Library,
  ListFilter,
  LockKeyhole,
  MessageCircle,
  Monitor,
  Package,
  PanelLeft,
  Pin,
  Play,
  Plus,
  Search,
  Settings2,
  ShieldCheck,
  SlidersHorizontal,
  Star,
  Tag,
  Trash2,
  UserRound,
  Users,
  X,
  type LucideIcon,
} from "lucide-react";
import { compactNumber, type Repo } from "./data";
import { useApp } from "./state";

const icons: Record<string, LucideIcon> = {
  activity: Activity,
  down: ArrowDown,
  back: ArrowLeft,
  up: ArrowUp,
  diagonal: ArrowUpRight,
  bell: Bell,
  "bell-off": BellOff,
  book: BookOpen,
  bookmark: Bookmark,
  check: Check,
  checks: CheckCheck,
  chevron: ChevronRight,
  expand: ChevronDown,
  circle: Circle,
  "circle-check": CircleCheck,
  "circle-dot": CircleDot,
  "circle-x": CircleX,
  clock: Clock3,
  code: Code2,
  compass: Compass,
  copy: Copy,
  download: Download,
  more: Ellipsis,
  external: ExternalLink,
  "file-code": FileCode2,
  file: FileText,
  filter: Filter,
  folder: Folder,
  repo: FolderGit2,
  branch: GitBranch,
  commit: GitCommitHorizontal,
  fork: GitFork,
  pr: GitPullRequest,
  globe: Globe,
  hash: Hash,
  inbox: Inbox,
  layers: Layers,
  grid: LayoutGrid,
  library: Library,
  sort: ListFilter,
  lock: LockKeyhole,
  message: MessageCircle,
  monitor: Monitor,
  package: Package,
  panel: PanelLeft,
  pin: Pin,
  play: Play,
  plus: Plus,
  search: Search,
  settings: Settings2,
  shield: ShieldCheck,
  sliders: SlidersHorizontal,
  star: Star,
  tag: Tag,
  trash: Trash2,
  user: UserRound,
  users: Users,
  close: X,
};
export function Icon({
  name,
  size = 20,
  className = "",
}: {
  name: string;
  size?: number;
  className?: string;
}) {
  const Element = icons[name] || Circle;
  return (
    <Element
      size={size}
      strokeWidth={1.65}
      className={className}
      aria-hidden="true"
    />
  );
}
export function IconButton({
  icon,
  label,
  onClick,
  active = false,
  children,
  disabled = false,
}: {
  icon: string;
  label: string;
  onClick?: () => void;
  active?: boolean;
  children?: ReactNode;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      className={"icon-button" + (active ? " is-active" : "")}
      aria-label={label}
      title={label}
      aria-pressed={active || undefined}
      onClick={onClick}
      disabled={disabled}
    >
      <Icon name={icon} />
      {children}
    </button>
  );
}
export function PageHeader({
  title,
  subtitle,
  back,
  actions,
  eyebrow,
}: {
  title: string;
  subtitle?: string;
  back?: string;
  actions?: ReactNode;
  eyebrow?: string;
}) {
  const { navigate } = useApp();
  return (
    <header className={"page-header" + (back ? " detail-header" : "")}>
      {back && (
        <IconButton icon="back" label="返回" onClick={() => navigate(back)} />
      )}
      <div className="page-title">
        {eyebrow && <div className="eyebrow">{eyebrow}</div>}
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
      </div>
      <div className="header-actions">
        {actions ?? (
          <>
            <IconButton
              icon="search"
              label="打开搜索"
              onClick={() => navigate("/search")}
            />
            <button
              className="user-avatar"
              aria-label="打开个人主页"
              onClick={() => navigate("/profile")}
            >
              木
            </button>
          </>
        )}
      </div>
    </header>
  );
}
export function Tabs({
  items,
  value,
  onChange,
  label = "内容分类",
  compact = false,
}: {
  items: string[];
  value: string;
  onChange: (value: string) => void;
  label?: string;
  compact?: boolean;
}) {
  return (
    <div
      className={"text-tabs" + (compact ? " compact-tabs" : "")}
      role="group"
      aria-label={label}
    >
      {items.map((item) => (
        <button
          key={item}
          className={item === value ? "selected" : ""}
          onClick={() => onChange(item)}
          aria-pressed={item === value}
        >
          {item}
        </button>
      ))}
    </div>
  );
}
export function SectionHeading({
  title,
  action,
  onAction,
  detail,
}: {
  title: string;
  action?: string;
  onAction?: () => void;
  detail?: string;
}) {
  return (
    <div className="section-heading">
      <div>
        <h2>{title}</h2>
        {detail && <p>{detail}</p>}
      </div>
      {action && (
        <button className="text-button" onClick={onAction}>
          {action}
          <Icon name="chevron" size={14} />
        </button>
      )}
    </div>
  );
}
export function Empty({
  icon = "search",
  title = "没有找到内容",
  text,
  action,
  onAction,
}: {
  icon?: string;
  title?: string;
  text?: string;
  action?: string;
  onAction?: () => void;
}) {
  return (
    <div className="empty-state">
      <Icon name={icon} size={30} />
      <h3>{title}</h3>
      {text && <p>{text}</p>}
      {action && (
        <button className="secondary-button" onClick={onAction}>
          {action}
        </button>
      )}
    </div>
  );
}
export function Badge({
  children,
  tone = "neutral",
}: {
  children: ReactNode;
  tone?: string;
}) {
  return <span className={"badge badge-" + tone}>{children}</span>;
}
export function RepoAvatar({
  repo,
  small = false,
}: {
  repo: Repo;
  small?: boolean;
}) {
  return (
    <span
      className={"repo-avatar " + repo.color + (small ? " small-avatar" : "")}
    >
      {repo.initial}
    </span>
  );
}
export function RepoItem({
  repo,
  featured = false,
  simple = false,
}: {
  repo: Repo;
  featured?: boolean;
  simple?: boolean;
}) {
  const { navigate, stars, toggleStar, later, toggleLater } = useApp();
  return (
    <article
      className={
        "repo-item" +
        (featured ? " featured-repo" : "") +
        (simple ? " simple-repo" : "")
      }
    >
      {featured && (
        <div className="feature-caption">
          <span className="tiny-line" />
          值得一读<span className="feature-index">01</span>
        </div>
      )}
      <div className="repo-item-top">
        <button
          className="repo-link"
          onClick={() => navigate("/repo/" + repo.id)}
        >
          {!featured && <RepoAvatar repo={repo} small />}
          <span>
            <small>{repo.owner}</small>
            <strong>{repo.name}</strong>
          </span>
        </button>
        <IconButton
          icon="star"
          label={(stars.includes(repo.id) ? "取消 Star " : "Star ") + repo.name}
          active={stars.includes(repo.id)}
          onClick={() => toggleStar(repo.id)}
        />
      </div>
      <button
        className="repo-description-button"
        onClick={() => navigate("/repo/" + repo.id)}
      >
        <p>{repo.description}</p>
      </button>
      {!simple && (
        <div className="repo-tags">
          <span>{repo.topic}</span>
          <span>开源</span>
          {featured && <span>本地优先</span>}
        </div>
      )}
      <div className="repo-item-bottom">
        <div className="repo-meta">
          <span>
            <i className={"language-dot " + repo.language.toLowerCase()} />
            {repo.language}
          </span>
          <span>
            <Icon name="star" size={13} />
            {compactNumber(repo.stars + (stars.includes(repo.id) ? 1 : 0))}
          </span>
          <span>{repo.updated}</span>
        </div>
        {!simple && (
          <IconButton
            icon="bookmark"
            label={
              (later.includes(repo.id) ? "移出稍后看 " : "稍后看 ") + repo.name
            }
            active={later.includes(repo.id)}
            onClick={() => toggleLater(repo.id)}
          />
        )}
      </div>
    </article>
  );
}
export function MenuRow({
  icon,
  title,
  description,
  suffix,
  onClick,
}: {
  icon: string;
  title: string;
  description?: string;
  suffix?: ReactNode;
  onClick?: () => void;
}) {
  return (
    <button className="menu-row" onClick={onClick}>
      <span className="menu-row-icon">
        <Icon name={icon} />
      </span>
      <span className="menu-row-text">
        <strong>{title}</strong>
        {description && <small>{description}</small>}
      </span>
      {suffix}
      <Icon name="chevron" size={16} />
    </button>
  );
}
export function DemoFootnote({
  text = "示例数据 · 不连接 GitHub 账号",
}: {
  text?: string;
}) {
  return (
    <div className="demo-footnote">
      <span />
      {text}
      <span />
    </div>
  );
}
export function GlobalOverlays() {
  const { dialog, setDialog, toast, setToast } = useApp();
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    if (dialog && ref.current && !ref.current.open) ref.current.showModal();
    if (!dialog && ref.current?.open) ref.current.close();
  }, [dialog]);
  return (
    <>
      <dialog
        ref={ref}
        className="app-dialog"
        aria-labelledby="dialog-title"
        onCancel={() => setDialog(null)}
        onClick={(event) => {
          if (event.target === event.currentTarget) setDialog(null);
        }}
      >
        <div className="dialog-head">
          <h2 id="dialog-title">{dialog?.title}</h2>
          <IconButton
            icon="close"
            label="关闭对话框"
            onClick={() => setDialog(null)}
          />
        </div>
        <div className="dialog-content">{dialog?.content}</div>
        {dialog?.action && (
          <button
            className="primary-button full-width"
            onClick={() => {
              const action = dialog.onAction;
              setDialog(null);
              action?.();
            }}
          >
            {dialog.action}
          </button>
        )}
      </dialog>
      {toast && (
        <div className="toast" role="status">
          <Icon name="check" size={17} />
          <span>{toast.text}</span>
          {toast.undo && (
            <button
              onClick={() => {
                toast.undo?.();
                setToast(null);
              }}
            >
              撤销
            </button>
          )}
          <button aria-label="关闭提示" onClick={() => setToast(null)}>
            <Icon name="close" size={16} />
          </button>
        </div>
      )}
    </>
  );
}
