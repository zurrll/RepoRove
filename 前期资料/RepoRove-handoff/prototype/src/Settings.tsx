import { useApp } from "./state";
import { moduleOptions, tabOptions } from "./data";
import {
  DemoFootnote,
  Icon,
  IconButton,
  PageHeader,
  SectionHeading,
} from "./ui";

export default function SettingsPage() {
  const { prefs, setPrefs, navigate, notify, setDialog } = useApp();
  const toggleTab = (id: string) => {
    const exists = prefs.tabs.includes(id);
    if (exists && prefs.tabs.length === 1) {
      notify("至少保留一个底部选项卡");
      return;
    }
    if (!exists && prefs.tabs.length === 5) {
      notify("最多展示 5 个选项卡，先取消一个再添加");
      return;
    }
    const tabs = exists
      ? prefs.tabs.filter((t) => t !== id)
      : [...prefs.tabs, id];
    setPrefs({
      ...prefs,
      tabs,
      home: tabs.includes(prefs.home) ? prefs.home : tabs[0],
    });
  };
  const move = (field: "tabs" | "modules", id: string, direction: number) => {
    const values = [...prefs[field]];
    const from = values.indexOf(id);
    const to = from + direction;
    if (to < 0 || to >= values.length) return;
    [values[from], values[to]] = [values[to], values[from]];
    setPrefs({ ...prefs, [field]: values });
  };
  const options = [
    ...prefs.tabs.map((id) => tabOptions.find((t) => t.id === id)!),
    ...tabOptions.filter((t) => !prefs.tabs.includes(t.id)),
  ];
  const modules = [
    ...prefs.modules.map((id) => moduleOptions.find((t) => t.id === id)!),
    ...moduleOptions.filter((t) => !prefs.modules.includes(t.id)),
  ];
  return (
    <>
      <PageHeader
        title="界面与内容"
        back="/profile"
        actions={
          <span className="auto-save">
            <Icon name="check" size={14} />
            自动保存
          </span>
        }
      />
      <div className="page-body settings-page">
        <div className="settings-intro">
          <h2>把常用的，放在顺手处。</h2>
          <p>你的 GitHub，由你决定怎么读。</p>
        </div>
        <SectionHeading title="从一个习惯开始" />
        <div className="preset-grid">
          <button
            onClick={() => {
              setPrefs({
                ...prefs,
                tabs: ["discover", "feed", "inbox", "library"],
                home: "discover",
                modules: ["status", "readme", "release", "activity"],
              });
              notify("已应用「随心阅读」布局");
            }}
          >
            <Icon name="book" size={22} />
            <strong>随心阅读</strong>
            <small>发现 · 阅读 · 收藏</small>
          </button>
          <button
            onClick={() => {
              setPrefs({
                ...prefs,
                tabs: ["work", "inbox", "library", "discover"],
                home: "work",
                modules: ["tasks", "status", "ci", "activity", "readme"],
              });
              notify("已应用「项目维护」布局");
            }}
          >
            <Icon name="repo" size={22} />
            <strong>项目维护</strong>
            <small>待办 · 通知 · 状态</small>
          </button>
        </div>
        <SectionHeading
          title="底部选项卡"
          detail="勾选后立即显示，使用箭头调整顺序。"
        />
        <div className="settings-count">已选择 {prefs.tabs.length} / 5</div>
        <div className="option-list">
          {options.map((tab) => {
            const selected = prefs.tabs.includes(tab.id);
            const index = prefs.tabs.indexOf(tab.id);
            return (
              <div
                className={"option-row" + (selected ? " option-selected" : "")}
                key={tab.id}
              >
                <label>
                  <input
                    type="checkbox"
                    checked={selected}
                    onChange={() => toggleTab(tab.id)}
                  />
                  <Icon name={tab.icon} size={19} />
                  <span>
                    <strong>{tab.label}</strong>
                    <small>{tab.description}</small>
                  </span>
                </label>
                {selected && (
                  <div className="reorder-buttons">
                    <IconButton
                      icon="up"
                      label={`上移${tab.label}`}
                      disabled={index === 0}
                      onClick={() => move("tabs", tab.id, -1)}
                    />
                    <IconButton
                      icon="down"
                      label={`下移${tab.label}`}
                      disabled={index === prefs.tabs.length - 1}
                      onClick={() => move("tabs", tab.id, 1)}
                    />
                  </div>
                )}
              </div>
            );
          })}
        </div>
        <label className="setting-select">
          <span>
            <strong>启动时打开</strong>
            <small>下次从首页进入时生效</small>
          </span>
          <select
            value={prefs.home}
            aria-label="启动页面"
            onChange={(e) => setPrefs({ ...prefs, home: e.target.value })}
          >
            {prefs.tabs.map((id) => (
              <option key={id} value={id}>
                {tabOptions.find((t) => t.id === id)?.label}
              </option>
            ))}
          </select>
        </label>
        <SectionHeading
          title="仓库首页"
          detail="只显示你需要的模块，顺序也由你决定。"
          action="预览"
          onAction={() => navigate("/repo/mori-notes")}
        />
        <div className="option-list">
          {modules.map((module) => {
            const selected = prefs.modules.includes(module.id);
            const index = prefs.modules.indexOf(module.id);
            return (
              <div className="option-row" key={module.id}>
                <label>
                  <input
                    type="checkbox"
                    checked={selected}
                    onChange={() =>
                      setPrefs({
                        ...prefs,
                        modules: selected
                          ? prefs.modules.filter((id) => id !== module.id)
                          : [...prefs.modules, module.id],
                      })
                    }
                  />
                  <span>
                    <strong>{module.label}</strong>
                    <small>{module.description}</small>
                  </span>
                </label>
                {selected && (
                  <div className="reorder-buttons">
                    <IconButton
                      icon="up"
                      label={`上移${module.label}`}
                      disabled={index === 0}
                      onClick={() => move("modules", module.id, -1)}
                    />
                    <IconButton
                      icon="down"
                      label={`下移${module.label}`}
                      disabled={index === prefs.modules.length - 1}
                      onClick={() => move("modules", module.id, 1)}
                    />
                  </div>
                )}
              </div>
            );
          })}
        </div>
        <SectionHeading title="阅读方式" />
        <div className="reading-preview">
          <span className="eyebrow">READING PREVIEW</span>
          <h3>让灵感，有处可栖。</h3>
          <p>把想法留在本地，让阅读回到内容本身。好的工具，应该让你更专注。</p>
        </div>
        <div className="setting-line">
          <span>内容密度</span>
          <div className="segmented">
            <button
              className={prefs.density === "comfortable" ? "selected" : ""}
              aria-pressed={prefs.density === "comfortable"}
              onClick={() => setPrefs({ ...prefs, density: "comfortable" })}
            >
              舒展
            </button>
            <button
              className={prefs.density === "compact" ? "selected" : ""}
              aria-pressed={prefs.density === "compact"}
              onClick={() => setPrefs({ ...prefs, density: "compact" })}
            >
              紧凑
            </button>
          </div>
        </div>
        <label className="setting-select">
          <span>
            <strong>正文字号</strong>
            <small>应用于简介、正文和评论</small>
          </span>
          <select
            value={prefs.textSize}
            aria-label="正文字号"
            onChange={(e) =>
              setPrefs({ ...prefs, textSize: Number(e.target.value) })
            }
          >
            {[90, 100, 110, 120].map((size) => (
              <option key={size} value={size}>
                {size}%
              </option>
            ))}
          </select>
        </label>
        <div className="setting-line">
          <span>当前主题</span>
          <span className="theme-swatch">
            <i />
            纸感阅读
          </span>
        </div>
        <div className="settings-footer">
          <p>
            设置保存在当前浏览器。更多主题、AI 翻译和镜像配置，留给后续版本。
          </p>
          <button
            className="text-button"
            onClick={() =>
              setDialog({
                title: "恢复默认布局？",
                content: (
                  <p>
                    底部选项卡、首页模块与阅读方式会恢复到「随心阅读」的默认设置。收藏和通知状态不会被清除。
                  </p>
                ),
                action: "恢复默认布局",
                onAction: () => {
                  setPrefs({
                    tabs: ["discover", "feed", "inbox", "library"],
                    home: "discover",
                    density: "comfortable",
                    textSize: 100,
                    modules: ["status", "readme", "release", "activity"],
                  });
                  notify("已恢复默认布局");
                },
              })
            }
          >
            恢复默认布局
          </button>
        </div>
        <DemoFootnote text="所有调整即时生效 · 仅保存在本机" />
      </div>
    </>
  );
}
