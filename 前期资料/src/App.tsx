import { useLayoutEffect, useRef, type ReactNode } from "react";
import { AppContext, useAppState, useApp } from "./state";
import { tabOptions } from "./data";
import { Icon, GlobalOverlays } from "./ui";
import {
  Discover,
  Feed,
  InboxPage,
  LibraryPage,
  SearchPage,
  ProfilePage,
  WorkPage,
} from "./screens";
import RepositoryPage from "./Repository";
import SettingsPage from "./Settings";

function AppProvider({ children }: { children: ReactNode }) {
  const value = useAppState();
  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

function Shell() {
  const { route, navigate, prefs, unread } = useApp();
  const scroll = useRef<HTMLElement>(null);
  const positions = useRef<Record<string, number>>({});
  const previous = useRef(route);
  useLayoutEffect(() => {
    const el = scroll.current;
    if (!el) return;
    positions.current[previous.current] = el.scrollTop;
    el.scrollTop = positions.current[route] || 0;
    previous.current = route;
  }, [route]);
  const page = route.split("?")[0].split("/")[1];
  const active = page === "repo" ? "library" : page;
  const sidebar = [
    ...tabOptions.slice(0, 4),
    { id: "work", label: "我的工作", icon: "circle-check" },
  ];
  return (
    <div className="prototype-stage">
      <aside className="preview-sidebar">
        <button
          className="brand"
          onClick={() => navigate("/discover")}
          aria-label="RepoRove首页"
        >
          <span className="brand-mark">
            <Icon name="book" size={27} />
          </span>
          <span className="brand-name">
            RepoRove<small>GitHub 客户端</small>
          </span>
        </button>
        <div className="preview-label">页面索引</div>
        <nav aria-label="原型页面导航">
          {sidebar.map((tab) => (
            <button
              key={tab.id}
              className={page === tab.id ? "active" : ""}
              onClick={() => navigate("/" + tab.id)}
            >
              <Icon name={tab.icon} size={19} />
              {tab.label}
              {tab.id === "inbox" && unread.length > 0 && (
                <span className="side-count">{unread.length}</span>
              )}
            </button>
          ))}
        </nav>
        <div className="sidebar-rule" />
        <button
          className="side-link"
          onClick={() => navigate("/repo/mori-notes")}
        >
          <Icon name="repo" size={19} />
          仓库详情
        </button>
        <button className="side-link" onClick={() => navigate("/settings")}>
          <Icon name="sliders" size={19} />
          界面与内容
        </button>
        <div className="sidebar-bottom">
          <span className="prototype-chip">交互展示原型</span>
          <p>
            纸感阅读 · 示例数据
            <br />
            操作仅影响本机演示状态
          </p>
          <div className="folio-number">EDITION / 001</div>
        </div>
      </aside>
      <div className="device-wrap">
        <div
          className="device"
          data-density={prefs.density}
          style={
            { "--reading-scale": prefs.textSize / 100 } as React.CSSProperties
          }
        >
          <div className="phone-status">
            <span>9:41</span>
            <span>
              <span className="signal-bars">▂▄▆</span>
              <Icon name="globe" size={13} />
              <span className="battery">
                <i />
              </span>
            </span>
          </div>
          <main className="phone-scroll" ref={scroll} id="main-content">
            {page === "discover" && <Discover />}
            {page === "feed" && <Feed />}
            {page === "inbox" && <InboxPage />}
            {page === "library" && <LibraryPage />}
            {page === "search" && <SearchPage />}
            {page === "profile" && <ProfilePage />}
            {page === "work" && <WorkPage />}
            {page === "settings" && <SettingsPage />}
            {page === "repo" && <RepositoryPage />}
            {![
              "discover",
              "feed",
              "inbox",
              "library",
              "search",
              "profile",
              "work",
              "settings",
              "repo",
            ].includes(page) && <Discover />}
          </main>
          <nav className="bottom-nav" aria-label="底部导航">
            {prefs.tabs.map((id) => {
              const tab = tabOptions.find((t) => t.id === id)!;
              return (
                <button
                  key={id}
                  aria-current={active === id ? "page" : undefined}
                  onClick={() => navigate("/" + id)}
                >
                  <span className="nav-icon">
                    <Icon name={tab.icon} size={21} />
                    {id === "inbox" && unread.length > 0 && (
                      <i className="nav-count">{unread.length}</i>
                    )}
                  </span>
                  <span>{tab.label}</span>
                </button>
              );
            })}
          </nav>
          <div className="home-indicator">
            <span />
          </div>
        </div>
        <div className="preview-caption">
          <span>RepoRove</span>
          <span>纸感阅读 / 可交互原型</span>
        </div>
      </div>
      <aside className="preview-note">
        <span className="note-kicker">INTERACTIVE PROTOTYPE</span>
        <h2>体验路径</h2>
        <div className="note-rule" />
        <p>
          发现 → 仓库详情
          <br />
          资料库 → 收藏与订阅
          <br />
          设置 → 定制布局
        </p>
        <button onClick={() => navigate("/settings")}>
          <Icon name="sliders" size={16} />
          调整我的界面
        </button>
        <small>页面可点击 · 无需登录</small>
      </aside>
      <GlobalOverlays />
    </div>
  );
}
export default function App() {
  return (
    <AppProvider>
      <Shell />
    </AppProvider>
  );
}
