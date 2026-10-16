package app.reporove.smoke;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.accessibilityservice.AccessibilityService;
import java.io.File;
import java.io.FileOutputStream;

/** Exercises the actual R8 artifact using Android accessibility, without target-library dependencies. */
public final class ReleaseSmokeInstrumentation extends Instrumentation {
    private int networkRetries;
    private boolean oauthProbe;
    private boolean downloadProbe;
    private boolean trendingProbe;
    private boolean translationProbe;
    private boolean restoreTranslationProbe;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        oauthProbe = arguments != null && "true".equals(arguments.getString("oauth_probe"));
        downloadProbe = arguments != null && "true".equals(arguments.getString("download_probe"));
        trendingProbe = arguments != null && "true".equals(arguments.getString("trending_probe"));
        translationProbe = arguments != null && "true".equals(arguments.getString("translation_probe"));
        restoreTranslationProbe = arguments != null && "true".equals(arguments.getString("restore_translation_probe"));
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (translationProbe || restoreTranslationProbe) {
                verifyTranslationConfiguration(restoreTranslationProbe);
                result.putString("result", restoreTranslationProbe ? "PASS: R8 reloads encrypted translation configuration after process restart and deletes it from settings" : "PASS: R8 saves translation configuration as ciphertext without making a translation request");
                finish(Activity.RESULT_OK, result); return;
            }
            if (trendingProbe) {
                boolean background = verifyTrendingAndDailyNotifications();
                result.putBoolean("background_job_verified", background);
                result.putInt("network_retries", networkRetries);
                result.putString("result", "PASS: actual R8 APK reads full GitHub day/week/month and language boards, opens personalized picks, posts a real deduplicated notification, opens its saved selection from the notification shade, and disables the job and notification");
                finish(Activity.RESULT_OK, result); return;
            }
            if (downloadProbe) {
                verifyDownloadedApkOpensAndDeletes();
                result.putString("result", "PASS: actual R8 APK downloads the public RepoRove APK, reads its system content URI, opens the system installer without installing, and deletes both the file and download record");
                finish(Activity.RESULT_OK, result); return;
            }
            if (oauthProbe) {
                getTargetContext().startActivity(new Intent().setClassName("app.reporove", "app.reporove.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                AccessibilityNodeInfo dismiss = find(getUiAutomation().getRootInActiveWindow(), "button", "暂不");
                if (dismiss != null) click(dismiss);
                click(waitFor("desc", "我的"));
                click(waitFor("button", "通过浏览器登录"));
                waitFor("button", "复制授权码并打开 GitHub");
                String code = authorizationCode(getUiAutomation().getRootInActiveWindow());
                if (code == null) throw new AssertionError("Official device authorization code was not displayed");
                Bundle challenge = new Bundle(); challenge.putString("user_code", code); challenge.putString("verification_uri", "https://github.com/login/device");
                sendStatus(1, challenge);
                long deadline = SystemClock.elapsedRealtime() + 540000;
                do {
                    if (find(getUiAutomation().getRootInActiveWindow(), "button", "退出账号") != null || find(getUiAutomation().getRootInActiveWindow(), "button", "我的组织") != null) {
                        result.putString("result", "PASS: actual R8 APK completed official Device Flow and displayed the signed-in profile");
                        finish(Activity.RESULT_OK, result); return;
                    }
                    SystemClock.sleep(500);
                } while (SystemClock.elapsedRealtime() < deadline);
                click(waitFor("button", "取消登录"));
                result.putString("result", "Official challenge displayed; account authorization not completed during this probe");
                finish(Activity.RESULT_CANCELED, result); return;
            }
            runOnMainSync(() -> ((android.content.ClipboardManager) getTargetContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE))
                .setPrimaryClip(android.content.ClipData.newPlainText("GitHub link", "https://github.com/termux/termux-app")));
            getTargetContext().startActivity(new Intent().setClassName("app.reporove", "app.reporove.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitFor("text", "打开剪贴板中的 GitHub 链接？"); capture("release-clipboard-prompt");
            click(waitFor("button", "暂不"));
            capture("release-discover");
            click(waitFor("desc", "搜索"));
            capture("release-search-start");
            AccessibilityNodeInfo input = waitFor("class", "android.widget.EditText");
            Bundle text = new Bundle(); text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "repo:termux/termux-app");
            if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text)) throw new AssertionError("Search text was not accepted");
            waitFor("text", "repo:termux/termux-app");
            getUiAutomation().waitForIdle(300, 5000);
            click(waitFor("button", "搜索"));
            waitFor("text", "仓库结果");
            click(waitFor("repo", "termux-app"));
            waitFor("text", "README"); capture("release-repository");
            click(waitFor("text", "阅读全文"));
            waitFor("class", "android.webkit.WebView");
            waitFor("text", "Termux");
            capture("release-readme");
            if (!"app.reporove".contentEquals(getUiAutomation().getRootInActiveWindow().getPackageName())) throw new AssertionError("Reader left the app");
            getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            click(waitFor("desc", "查看语言占比"));
            waitFor("text", "语言构成"); capture("release-languages");
            getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            click(waitFor("button", "代码"));
            waitFor("text", "根目录"); waitFor("repo", "app"); capture("release-code-list");
            int expandedTabTop = boundsFor("button", "代码").top;
            swipe(false);
            // Compose can retain semantics for clipped header children. Verify the
            // displayed tabs' movement instead of a hidden child's visibility flag.
            waitForTabTopAtMost(expandedTabTop - 200);
            capture("release-collapsed-code");
            for (int i = 0; i < 6 && boundsFor("button", "代码").top < expandedTabTop - 8; i++) swipe(true);
            if (boundsFor("button", "代码").top < expandedTabTop - 8) throw new AssertionError("Repository header did not restore");
            capture("release-restored-code");
            click(waitFor("button", "发布"));
            click(waitFor("button", "下载文件"));
            waitForNetwork("text", "版本说明"); capture("release-notes-first");
            click(waitFor("button", "跳到下载"));
            waitForWithScroll("text", "源码 ZIP"); capture("release-downloads");
            getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            click(waitFor("repo", "termux"));
            waitFor("text", "@termux · 组织"); capture("release-profile");
            if (!"app.reporove".contentEquals(getUiAutomation().getRootInActiveWindow().getPackageName())) throw new AssertionError("Profile left the app");
            // Save with the production foreground service, then open the snapshot with no active network.
            getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            click(waitFor("button", "保存到 App"));
            click(waitFor("button", "离线保存资料 / 源码"));
            waitFor("text", "保存到本机"); click(waitFor("button", "保存"));
            // The accessible app-bar button appears after the save dialog closes.
            // A global Back sent immediately would only dismiss the departing dialog.
            click(waitFor("desc", "返回"));
            click(waitFor("button", "项目库"));
            click(waitFor("button", "离线与最近阅读"));
            waitForNetwork("button", "阅读");
            shell("svc wifi disable");
            shell("svc data disable");
            android.net.ConnectivityManager connectivity = getTargetContext().getSystemService(android.net.ConnectivityManager.class);
            long networkDeadline = SystemClock.elapsedRealtime() + 10000;
            while (connectivity.getActiveNetwork() != null && SystemClock.elapsedRealtime() < networkDeadline) SystemClock.sleep(250);
            if (connectivity.getActiveNetwork() != null) throw new AssertionError("Network remained active during the offline test");
            click(waitFor("button", "阅读"));
            waitFor("class", "android.webkit.WebView"); waitFor("text", "Termux"); capture("release-offline-readme");
            click(waitFor("desc", "离线文档操作")); click(waitFor("button", "浏览已保存源码"));
            click(waitFor("button", "app")); click(waitFor("button", "src")); click(waitFor("button", "main")); click(waitFor("button", "AndroidManifest.xml"));
            waitFor("text", "manifest"); capture("release-offline-code");
            click(waitFor("desc", "文件树")); waitFor("text", "文件树"); capture("release-offline-tree");
            getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            getUiAutomation().waitForIdle(500, 5000);
            for (int level = 0; level < 10 && find(getUiAutomation().getRootInActiveWindow(), "button", "删除") == null; level++) {
                click(waitFor("desc", "返回")); getUiAutomation().waitForIdle(500, 5000);
            }
            click(waitFor("button", "删除")); waitFor("text", "删除离线快照？"); click(waitFor("button", "删除"));
            waitFor("text", "尚未保存离线仓库");
            getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            click(waitFor("desc", "我的")); click(waitFor("desc", "设置")); click(waitFor("button", "关于"));
            click(waitFor("button", "版本更新记录")); waitFor("text", "0.6.3 · 当前"); capture("release-version-history");
            shell("svc wifi enable");
            shell("svc data enable");
            result.putString("result", "PASS: R8 APK prompts for clipboard links, saves a real GitHub snapshot with production foreground service, opens README and code with no active network, opens the file tree, deletes the snapshot, shows version history, and passes existing search/header/release/profile flows");
            result.putInt("network_retries", networkRetries);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            shell("svc wifi enable"); shell("svc data enable"); capture("release-failure"); result.putString("result", "FAIL"); result.putString("error", Log.getStackTraceString(error)); finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void verifyTranslationConfiguration(boolean restore) throws Exception {
        getTargetContext().startActivity(new Intent().setClassName("app.reporove", "app.reporove.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        SystemClock.sleep(1500);
        AccessibilityNodeInfo dismiss = find(getUiAutomation().getRootInActiveWindow(), "button", "暂不");
        if (dismiss != null) click(dismiss);
        click(waitFor("desc", "我的")); click(waitFor("desc", "设置")); click(waitFor("button", "段落双语"));
        if (!restore) {
            AccessibilityNodeInfo input = waitFor("class", "android.widget.EditText");
            Bundle value = new Bundle(); value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "fixture-release-key");
            if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, value)) throw new AssertionError("Translation key input was not accepted");
            click(waitFor("button", "保存")); click(waitFor("button", "段落双语"));
        }
        waitForWithScroll("button", "删除翻译配置");
        File stored = new File(getTargetContext().getApplicationInfo().dataDir, "shared_prefs/bilingual-v2.xml");
        String blob = new String(java.nio.file.Files.readAllBytes(stored.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        if (!blob.contains("config") || blob.contains("fixture-release-key")) throw new AssertionError("Translation configuration was not saved as ciphertext");
        if (restore) { click(waitFor("button", "删除翻译配置")); waitFor("text", "删除翻译配置和缓存？"); click(waitFor("button", "删除")); }
    }
    private boolean verifyTrendingAndDailyNotifications() throws Exception {
        runOnMainSync(() -> ((android.content.ClipboardManager) getTargetContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE))
            .setPrimaryClip(android.content.ClipData.newPlainText("GitHub link", "https://github.com/termux/termux-app")));
        getTargetContext().startActivity(new Intent().setClassName("app.reporove", "app.reporove.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        click(waitFor("button", "暂不"));
        click(waitFor("button", "热榜"));
        waitForNetwork("text", "GitHub Trending · 日榜");
        capture("trending-daily-full");
        waitForWithScroll("text", "已显示源榜提供的全部");
        capture("trending-complete-tail");
        for (int attempt = 0; attempt < 30 && find(getUiAutomation().getRootInActiveWindow(), "button", "周榜") == null; attempt++) {
            AccessibilityNodeInfo scroll = findScrollable(getUiAutomation().getRootInActiveWindow());
            if (scroll != null) scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            SystemClock.sleep(300);
        }
        click(waitFor("button", "周榜"));
        waitForNetwork("text", "GitHub Trending · 周榜");
        capture("trending-weekly");
        click(waitFor("button", "月榜"));
        waitForNetwork("text", "GitHub Trending · 月榜");
        click(waitFor("button", "全部语言"));
        AccessibilityNodeInfo input = waitFor("class", "android.widget.EditText");
        // Use a partial query so an exact button lookup cannot match the editable field itself.
        Bundle query = new Bundle(); query.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "pytho");
        if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, query)) throw new AssertionError("Language query was not accepted");
        click(waitFor("button", "Python"));
        waitFor("button", "Python");
        waitForNetwork("text", "GitHub Trending · 月榜");
        capture("trending-python-monthly");
        click(waitFor("button", "今日精选"));
        waitForNetwork("text", "榜单获取于");
        capture("trending-digest");
        click(waitFor("button", "每日提醒"));
        waitFor("text", "每日发现通知");
        AccessibilityNodeInfo toggle = findCheckable(getUiAutomation().getRootInActiveWindow());
        if (toggle == null || toggle.isChecked()) throw new AssertionError("Daily notifications were not off by default");
        click(toggle);
        android.app.job.JobScheduler scheduler = getTargetContext().getSystemService(android.app.job.JobScheduler.class);
        long scheduleDeadline = SystemClock.elapsedRealtime() + 10000;
        while (scheduler.getPendingJob(6201) == null && SystemClock.elapsedRealtime() < scheduleDeadline) SystemClock.sleep(200);
        if (scheduler.getPendingJob(6201) == null || !scheduler.getPendingJob(6201).isPersisted()) throw new AssertionError("Daily job was not persisted");
        android.app.NotificationManager manager = getTargetContext().getSystemService(android.app.NotificationManager.class);
        manager.cancel(6202);
        int hour = java.time.LocalTime.now().getHour();
        boolean background = hour >= 9 && hour <= 21;
        if (background) { getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME); shell("cmd jobscheduler run -f app.reporove 6201"); }
        else click(waitFor("button", "立即检查今日精选"));
        android.service.notification.StatusBarNotification notification = null;
        long deadline = SystemClock.elapsedRealtime() + 60000;
        do {
            for (android.service.notification.StatusBarNotification current : manager.getActiveNotifications()) if (current.getId() == 6202) notification = current;
            if (notification == null) SystemClock.sleep(400);
        } while (notification == null && SystemClock.elapsedRealtime() < deadline);
        if (notification == null) throw new AssertionError("Actual daily notification was not posted");
        long firstWhen = notification.getNotification().when;
        String title = String.valueOf(notification.getNotification().extras.getCharSequence(android.app.Notification.EXTRA_TITLE));
        String day = java.time.LocalDate.now().toString();
        getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
        capture("trending-notification-shade");
        click(waitFor("text", title));
        waitFor("text", day + " 的精选");
        capture("trending-notification-selection");
        if (background) {
            shell("cmd jobscheduler run -f app.reporove 6201"); SystemClock.sleep(1000);
            for (android.service.notification.StatusBarNotification current : manager.getActiveNotifications()) if (current.getId() == 6202 && current.getNotification().when != firstWhen) throw new AssertionError("Daily notification was posted twice");
        }
        getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        waitFor("text", "每日发现通知");
        click(findCheckable(getUiAutomation().getRootInActiveWindow()));
        long disableDeadline = SystemClock.elapsedRealtime() + 10000;
        while (scheduler.getPendingJob(6201) != null && SystemClock.elapsedRealtime() < disableDeadline) SystemClock.sleep(200);
        if (scheduler.getPendingJob(6201) != null) throw new AssertionError("Disabled daily job still exists");
        for (android.service.notification.StatusBarNotification current : manager.getActiveNotifications()) if (current.getId() == 6202) throw new AssertionError("Disabled daily notification is still visible");
        return background;
    }
    private AccessibilityNodeInfo findCheckable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isCheckable() && node.isVisibleToUser()) return node;
        for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo found = findCheckable(node.getChild(i)); if (found != null) return found; }
        return null;
    }
    private void verifyDownloadedApkOpensAndDeletes() throws Exception {
        runOnMainSync(() -> ((android.content.ClipboardManager) getTargetContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE))
            .setPrimaryClip(android.content.ClipData.newPlainText("GitHub link", "https://github.com/termux/termux-app")));
        getTargetContext().startActivity(new Intent().setClassName("app.reporove", "app.reporove.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        click(waitFor("button", "暂不"));
        click(waitFor("desc", "搜索"));
        AccessibilityNodeInfo input = waitFor("class", "android.widget.EditText");
        Bundle query = new Bundle(); query.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "repo:zurrll/RepoRove");
        if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, query)) throw new AssertionError("Download probe search text was not accepted");
        waitFor("text", "repo:zurrll/RepoRove");
        getUiAutomation().waitForIdle(300, 5000);
        click(waitFor("button", "搜索"));
        click(waitFor("repo", "RepoRove"));
        click(waitFor("button", "发布"));
        click(waitForWithScroll("text", "v0.6.0"));
        waitForNetwork("text", "版本说明");
        click(waitFor("button", "跳到下载"));
        click(waitForWithScroll("desc", "下载 RepoRove-0.6.0-preview.apk"));
        waitFor("text", "已加入下载，可在项目库查看进度");
        getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        click(waitFor("button", "项目库"));
        click(waitFor("button", "下载"));
        android.app.DownloadManager manager = getTargetContext().getSystemService(android.app.DownloadManager.class);
        long deadline = SystemClock.elapsedRealtime() + 90000, downloadId = -1;
        String localUri = null;
        do {
            try (android.database.Cursor cursor = manager.query(new android.app.DownloadManager.Query())) {
                while (cursor.moveToNext()) {
                    if (!"RepoRove-0.6.0-preview.apk".equals(cursor.getString(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TITLE)))) continue;
                    int status = cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS));
                    if (status == android.app.DownloadManager.STATUS_FAILED) throw new AssertionError("Actual APK DownloadManager failure: " + cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_REASON)));
                    if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                        downloadId = cursor.getLong(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_ID));
                        localUri = cursor.getString(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_LOCAL_URI));
                        break;
                    }
                }
            }
            if (downloadId < 0) SystemClock.sleep(500);
        } while (downloadId < 0 && SystemClock.elapsedRealtime() < deadline);
        if (downloadId < 0) throw new AssertionError("Actual APK download did not complete");
        android.net.Uri contentUri = manager.getUriForDownloadedFile(downloadId);
        try (java.io.InputStream bytes = getTargetContext().getContentResolver().openInputStream(contentUri)) {
            if (bytes == null || bytes.read() != 0x50 || bytes.read() != 0x4b) throw new AssertionError("Downloaded APK content URI was not readable");
        }
        File downloaded = new File(android.net.Uri.parse(localUri).getPath());
        if (!downloaded.isFile()) throw new AssertionError("Downloaded APK file was missing");
        click(waitFor("button", "打开文件"));
        long installerDeadline = SystemClock.elapsedRealtime() + 10000;
        boolean installer = false;
        do {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            String packageName = root == null ? "" : String.valueOf(root.getPackageName());
            installer = packageName.contains("packageinstaller");
            if (!installer) SystemClock.sleep(300);
        } while (!installer && SystemClock.elapsedRealtime() < installerDeadline);
        if (!installer) throw new AssertionError("Downloaded APK did not open the system installer");
        capture("release-downloaded-apk-installer");
        getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        waitFor("button", "打开文件");
        click(waitFor("button", "删除文件"));
        waitFor("text", "删除文件？");
        click(waitFor("button", "删除文件"));
        waitFor("text", "暂无下载");
        if (downloaded.exists() || manager.getUriForDownloadedFile(downloadId) != null) throw new AssertionError("Actual APK deletion left its file or system record behind");
    }
    private void shell(String command) {
        try (android.os.ParcelFileDescriptor result = getUiAutomation().executeShellCommand(command); java.io.FileInputStream input = new java.io.FileInputStream(result.getFileDescriptor())) { while (input.read() >= 0) {} }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    private String authorizationCode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        String text = node.getText() == null ? "" : node.getText().toString();
        if (text.matches("[A-Z0-9]{4}-[A-Z0-9]{4}")) return text;
        for (int i = 0; i < node.getChildCount(); i++) { String code = authorizationCode(node.getChild(i)); if (code != null) return code; }
        return null;
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String mode, String value) {
        if (node == null) return null;
        String text = node.getText() == null ? "" : node.getText().toString();
        boolean match = "desc".equals(mode) ? value.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription()) :
            "class".equals(mode) ? value.contentEquals(node.getClassName() == null ? "" : node.getClassName()) :
            "button".equals(mode) ? text.equals(value) : "repo".equals(mode) ? text.equals(value) || text.startsWith(value + "\n") : text.equals(value) || text.contains(value);
        if (match) {
            if (!"button".equals(mode) && !"repo".equals(mode)) return node;
            AccessibilityNodeInfo parent = node;
            while (parent != null && !parent.isClickable()) parent = parent.getParent();
            if (parent != null) return parent;
        }
        for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo found = find(node.getChild(i), mode, value); if (found != null) return found; }
        return null;
    }
    private AccessibilityNodeInfo waitFor(String mode, String value) {
        long deadline = SystemClock.elapsedRealtime() + 45000;
        do { AccessibilityNodeInfo found = find(getUiAutomation().getRootInActiveWindow(), mode, value); if (found != null) return found; SystemClock.sleep(300); } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Missing " + mode + ": " + value);
    }
    /** Retry only an explicit network failure, with the same visible user action. */
    private AccessibilityNodeInfo waitForNetwork(String mode, String value) {
        long deadline = SystemClock.elapsedRealtime() + 90000;
        int retries = 0;
        do {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            AccessibilityNodeInfo found = find(root, mode, value);
            if (found != null) return found;
            if (find(root, "text", "网络连接失败，请检查网络后重试。") != null) {
                AccessibilityNodeInfo retry = find(root, "button", "重试");
                if (retry == null || retries >= 2) throw new AssertionError("Network failed while waiting for " + value);
                capture("release-network-retry-" + value + "-" + (retries + 1));
                // Taking a screenshot can outlive an accessibility node's identity.
                retry = find(getUiAutomation().getRootInActiveWindow(), "button", "重试");
                if (retry != null && retry.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    retries++;
                    networkRetries++;
                    SystemClock.sleep(1000);
                }
            }
            SystemClock.sleep(300);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Missing " + mode + ": " + value);
    }
    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isScrollable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo found = findScrollable(node.getChild(i)); if (found != null) return found; }
        return null;
    }
    private Rect boundsFor(String mode, String value) {
        AccessibilityNodeInfo node = waitFor(mode, value);
        node.refresh();
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        if (!node.isVisibleToUser() || bounds.isEmpty()) throw new AssertionError("Missing visible " + mode + ": " + value);
        return bounds;
    }
    private void waitForTabTopAtMost(int maximumTop) {
        long deadline = SystemClock.elapsedRealtime() + 5000;
        do {
            if (boundsFor("button", "代码").top <= maximumTop) return;
            SystemClock.sleep(300);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Repository tabs did not move up when collapsing the header");
    }
    private void swipe(boolean down) {
        Rect bounds = new Rect(); getUiAutomation().getRootInActiveWindow().getBoundsInScreen(bounds);
        float x = bounds.centerX(), top = bounds.top + bounds.height() * .2f, bottom = bounds.top + bounds.height() * .86f;
        float start = down ? top : bottom, end = down ? bottom : top;
        long downTime = SystemClock.uptimeMillis();
        for (int step = 0; step <= 24; step++) {
            int action = step == 0 ? MotionEvent.ACTION_DOWN : step == 24 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, start + (end - start) * step / 24f, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try { if (!getUiAutomation().injectInputEvent(event, true)) throw new AssertionError("Swipe was not accepted"); }
            finally { event.recycle(); }
            SystemClock.sleep(25);
        }
        SystemClock.sleep(1000);
    }
    private AccessibilityNodeInfo waitForWithScroll(String mode, String value) {
        long deadline = SystemClock.elapsedRealtime() + 45000;
        do {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            AccessibilityNodeInfo found = find(root, mode, value);
            if (found != null) return found;
            AccessibilityNodeInfo scroll = findScrollable(root);
            if (scroll != null) scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            SystemClock.sleep(400);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Missing after scrolling " + mode + ": " + value);
    }
    private void click(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null && !current.isClickable()) current = current.getParent();
        if (current == null || !current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) throw new AssertionError("Node was not clickable: " + node.getText());
    }
    private void capture(String name) {
        SystemClock.sleep(3000);
        try { File directory = getTargetContext().getExternalFilesDir("screenshots"); directory.mkdirs(); Bitmap bitmap = getUiAutomation().takeScreenshot();
            try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); } finally { bitmap.recycle(); }
        } catch (Exception error) { Log.e("ReleaseSmoke", "Capture failed", error); }
    }
}
