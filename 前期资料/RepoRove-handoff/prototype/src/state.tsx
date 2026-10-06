import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { moduleOptions, tabOptions } from "./data";

export type Preferences = {
  tabs: string[];
  home: string;
  density: "comfortable" | "compact";
  textSize: number;
  modules: string[];
};
const defaults: Preferences = {
  tabs: ["discover", "feed", "inbox", "library"],
  home: "discover",
  density: "comfortable",
  textSize: 100,
  modules: ["status", "readme", "release", "activity"],
};
function useSaved<T>(
  key: string,
  initial: T,
  validate?: (value: unknown) => boolean,
) {
  const [value, setValue] = useState<T>(() => {
    try {
      const raw = localStorage.getItem("qiye-demo-" + key);
      const parsed = raw ? JSON.parse(raw) : initial;
      return !validate || validate(parsed) ? parsed : initial;
    } catch {
      return initial;
    }
  });
  useEffect(() => {
    try {
      localStorage.setItem("qiye-demo-" + key, JSON.stringify(value));
    } catch {
      /* Device storage is optional. */
    }
  }, [key, value]);
  return [value, setValue] as const;
}
const isStringArray = (value: unknown) =>
  Array.isArray(value) && value.every((x) => typeof x === "string");
const validPrefs = (value: unknown) => {
  const p = value as Preferences | null;
  return (
    !!p &&
    isStringArray(p.tabs) &&
    p.tabs.length > 0 &&
    p.tabs.length <= 5 &&
    new Set(p.tabs).size === p.tabs.length &&
    p.tabs.every((t) => tabOptions.some((x) => x.id === t)) &&
    isStringArray(p.modules) &&
    p.modules.every((m) => moduleOptions.some((x) => x.id === m)) &&
    ["comfortable", "compact"].includes(p.density) &&
    [90, 100, 110, 120].includes(p.textSize) &&
    p.tabs.includes(p.home)
  );
};
type DialogState = {
  title: string;
  content: ReactNode;
  action?: string;
  onAction?: () => void;
};
type ToastState = { text: string; undo?: () => void };
export function useAppState() {
  const [prefs, setPrefs] = useSaved<Preferences>(
    "preferences",
    defaults,
    validPrefs,
  );
  const [stars, setStars] = useSaved<string[]>(
    "stars",
    ["leaf-reader", "haze-terminal"],
    isStringArray,
  );
  const [later, setLater] = useSaved<string[]>(
    "later",
    ["mori-notes"],
    isStringArray,
  );
  const [watching, setWatching] = useSaved<string[]>(
    "watching",
    ["mori-notes", "leaf-reader"],
    isStringArray,
  );
  const [unread, setUnread] = useSaved<string[]>(
    "unread",
    ["n1", "n2", "n3"],
    isStringArray,
  );
  const [done, setDone] = useSaved<string[]>("done", [], isStringArray);
  const [downloads, setDownloads] = useSaved<string[]>(
    "downloads",
    [],
    isStringArray,
  );
  const [route, setRoute] = useState(
    () => window.location.hash.slice(1) || "/" + prefs.home,
  );
  const [dialog, setDialog] = useState<DialogState | null>(null);
  const [toast, setToast] = useState<ToastState | null>(null);
  const [comments, setComments] = useSaved<Record<string, string[]>>(
    "comments",
    {},
    (value) =>
      !!value &&
      typeof value === "object" &&
      !Array.isArray(value) &&
      Object.values(value).every(isStringArray),
  );
  const [searchText, setSearchText] = useState("");
  const [searchType, setSearchType] = useState("仓库");
  useEffect(() => {
    const handle = () => {
      setRoute(window.location.hash.slice(1) || "/" + prefs.home);
      setDialog(null);
    };
    window.addEventListener("hashchange", handle);
    return () => window.removeEventListener("hashchange", handle);
  }, [prefs.home]);
  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 4500);
    return () => window.clearTimeout(timer);
  }, [toast]);
  const navigate = (path: string) => {
    setDialog(null);
    if (path === route) return;
    setRoute(path);
    window.location.hash = path;
  };
  const notify = (text: string, undo?: () => void) => setToast({ text, undo });
  const toggleStar = (id: string) => {
    const exists = stars.includes(id);
    setStars(exists ? stars.filter((x) => x !== id) : [...stars, id]);
    notify(exists ? "已从 Star 收藏移除" : "已加入 Star 收藏");
  };
  const toggleLater = (id: string) => {
    const exists = later.includes(id);
    setLater(exists ? later.filter((x) => x !== id) : [...later, id]);
    notify(exists ? "已移出稍后看" : "已保存到稍后看");
  };
  const toggleWatch = (id: string) => {
    const exists = watching.includes(id);
    setWatching(exists ? watching.filter((x) => x !== id) : [...watching, id]);
    notify(exists ? "已取消示例订阅" : "已订阅示例仓库的更新");
  };
  const copy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      notify("已复制到剪贴板");
    } catch {
      setDialog({
        title: "复制内容",
        content: (
          <textarea
            className="copy-content"
            readOnly
            value={text}
            aria-label="可复制内容"
            onFocus={(e) => e.currentTarget.select()}
          />
        ),
      });
    }
  };
  return {
    route,
    navigate,
    prefs,
    setPrefs,
    stars,
    toggleStar,
    later,
    toggleLater,
    watching,
    toggleWatch,
    unread,
    setUnread,
    done,
    setDone,
    downloads,
    setDownloads,
    dialog,
    setDialog,
    toast,
    setToast,
    notify,
    comments,
    setComments,
    searchText,
    setSearchText,
    searchType,
    setSearchType,
    copy,
  };
}
export const AppContext = createContext<ReturnType<typeof useAppState> | null>(
  null,
);
export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error("Missing app provider");
  return ctx;
}
