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
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            getTargetContext().startActivity(new Intent().setClassName("app.reporove", "app.reporove.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
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
            waitFor("text", "版本说明"); capture("release-notes-first");
            click(waitFor("button", "跳到下载"));
            waitForWithScroll("text", "源码 ZIP"); capture("release-downloads");
            getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            click(waitFor("repo", "termux"));
            waitFor("text", "@termux · 组织"); capture("release-profile");
            if (!"app.reporove".contentEquals(getUiAutomation().getRootInActiveWindow().getPackageName())) throw new AssertionError("Profile left the app");
            result.putString("result", "PASS: R8 APK searches real GitHub data, renders README, collapses and restores repository header with persistent tabs, displays languages, single-row files and release downloads, and opens native organization profile");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            capture("release-failure"); result.putString("result", "FAIL"); result.putString("error", Log.getStackTraceString(error)); finish(Activity.RESULT_CANCELED, result);
        }
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
