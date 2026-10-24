package cn.local.adskip;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.graphics.Rect;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

public final class SkipService extends AccessibilityService {
    private static final String TAG = "QingTiao";
    static volatile boolean connected;
    static volatile String liveState = "尚未收到服务连接回调";
    private String currentPackage = "";
    private int currentWindowId = -1;
    private boolean windowStateChanged;
    private long enteredAt, lastClick = -1;
    private int clicks, emptyRetries;
    private String lastLoggedState = "";
    private long lastLoggedAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean queued;
    private final Runnable scan = () -> { queued = false; scanWindow(); };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        liveState = "服务已连接";
        Log.i(TAG, "SERVICE connected selected=" + Store.apps(this));
        schedule(0, true);
    }
    private void schedule(long delay, boolean expedite) {
        if (!Store.prefs(this).getBoolean("enabled", false)) return;
        if (queued && !expedite) return;
        if (queued) handler.removeCallbacks(scan);
        queued = true;
        handler.postDelayed(scan, delay);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        emptyRetries = 0;
        boolean windowChanged = event != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        if (windowChanged) windowStateChanged = true;
        schedule(windowChanged ? 0 : 10, windowChanged);
    }
    private void report(String pkg, String reason) {
        liveState = reason;
        long now = SystemClock.elapsedRealtime();
        String state = pkg + " " + reason;
        if (!state.equals(lastLoggedState) || now - lastLoggedAt >= 2000) {
            Log.i(TAG, "STATE pkg=" + pkg + " reason=" + reason);
            lastLoggedState = state;
            lastLoggedAt = now;
        }
        if (!pkg.equals(getPackageName()) || DemoActivity.active) Store.diagnostic(this, pkg, reason);
    }
    private void recordClick(String pkg, String path, long now) {
        Store.record(this, pkg, path, Math.max(0, now - enteredAt));
        Log.i(TAG, "CLICK pkg=" + pkg + " path=" + path + " elapsedMs=" + Math.max(0, now - enteredAt));
    }
    private void scanWindow() {
        if (!Store.prefs(this).getBoolean("enabled", false)) { liveState = "总开关已暂停"; return; }
        long now = SystemClock.elapsedRealtime();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            liveState = "无法读取当前窗口；等待界面就绪";
            if (++emptyRetries <= 8) schedule(30, false);
            return;
        }
        emptyRetries = 0;
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        nodes.add(root);
        String pkg = String.valueOf(root.getPackageName());
        boolean allowed = false, acted = false;
        try {
            int windowId = root.getWindowId();
            boolean newSession = !pkg.equals(currentPackage)
                || (windowStateChanged && currentWindowId != -1 && windowId != currentWindowId);
            if (newSession) {
                currentPackage = pkg; enteredAt = now; clicks = 0; lastClick = -1;
                Log.i(TAG, "SESSION pkg=" + pkg + " windowId=" + windowId
                    + " selected=" + Store.apps(this).contains(pkg));
            }
            currentWindowId = windowId;
            windowStateChanged = false;
            boolean demo = pkg.equals(getPackageName()) && DemoActivity.active;
            if (pkg.equals(getPackageName()) && !demo) return;
            if (!demo && Rules.protectedPackage(pkg)) { report(pkg, "系统敏感应用受保护，不执行自动点击"); return; }
            allowed = demo || Store.apps(this).contains(pkg);
            if (!allowed) { report(pkg, "未选中此应用，请在首页勾选并保存"); return; }
            KeyguardManager keyguard = (KeyguardManager)getSystemService(KEYGUARD_SERVICE);
            if (!demo && keyguard != null && keyguard.isKeyguardLocked()) { report(pkg, "设备处于锁屏状态，已停止自动点击"); return; }
            if (!demo && sensitiveWindow(root)) { report(pkg, "界面包含输入框、密码或身份验证内容，已停止自动点击"); return; }
            if (clicks >= 5) { report(pkg, "已达本次会话 5 次点击上限"); return; }
            if (lastClick >= 0 && now - lastClick < 250) { schedule(250 - (now - lastClick), false); return; }

            long elapsed = demo ? 0 : now - enteredAt;
            boolean popups = Store.prefs(this).getBoolean("popups", true);
            // Fast path for every selected app: use the framework text index instead of walking
            // up to 400 nodes. The full scan below remains as a compatibility fallback.
            if (fastIndexedAdControl(root, pkg, elapsed, popups)) {
                lastClick = now; clicks++; acted = true; recordClick(pkg, "indexed-text", now);
                report(pkg, "通用开屏快速路径已发送触摸");
                return;
            }

            // Confirmed QQ Music splash layout: bypass the general tree traversal as soon as
            // its ad marker and top-right skip control are exposed to accessibility.
            if (fastQqMusicSplash(root, pkg)) {
                lastClick = now; clicks++; acted = true; recordClick(pkg, "qqmusic", now);
                report(pkg, "QQ 音乐开屏快速路径已发送触摸");
                return;
            }

            boolean marker = false;
            AccessibilityNodeInfo strongIdTarget = null;
            AccessibilityNodeInfo conditionalSkipIdTarget = null;
            AccessibilityNodeInfo knownTarget = null;
            for (int i = 0; i < nodes.size() && i < 400; i++) {
                AccessibilityNodeInfo node = nodes.get(i);
                if (node.isVisibleToUser() && !node.isEditable() && !node.isPassword()
                        && (Rules.adMarker(str(node.getText())) || Rules.adMarker(str(node.getContentDescription())))) marker = true;
                if (node.isVisibleToUser() && node.isEnabled() && !node.isEditable() && !node.isPassword()) {
                    String viewId = str(node.getViewIdResourceName());
                    if (strongIdTarget == null && Rules.adSkipViewId(viewId)) strongIdTarget = node;
                    if (conditionalSkipIdTarget == null && Rules.skipLikeViewId(viewId)) conditionalSkipIdTarget = node;
                    if (knownTarget == null && knownPackageControl(pkg, viewId, popups)) knownTarget = node;
                }
                for (int j = 0; j < node.getChildCount() && nodes.size() < 400; j++) {
                    AccessibilityNodeInfo child = node.getChild(j);
                    if (child != null) nodes.add(child);
                }
            }
            AccessibilityNodeInfo idTarget = knownTarget != null ? knownTarget
                : strongIdTarget != null ? strongIdTarget
                : marker ? conditionalSkipIdTarget : null;
            if (idTarget != null && tapMatchedNode(idTarget, pkg, marker, elapsed, popups)) {
                lastClick = now; clicks++; acted = true;
                recordClick(pkg, knownTarget == idTarget ? "known-app-control" : "ad-skip-view-id", now);
                report(pkg, "广告控件 ID 快速路径已发送触摸");
                return;
            }
            int matches = 0, targets = 0;
            for (AccessibilityNodeInfo node : nodes) {
                if (!node.isVisibleToUser() || !node.isEnabled() || node.isEditable() || node.isPassword()) continue;
                if (!Rules.candidate(str(node.getText()), marker, elapsed, popups)
                        && !Rules.candidate(str(node.getContentDescription()), marker, elapsed, popups)) continue;
                matches++;
                targets++;
                boolean success = tapMatchedNode(node, pkg, marker, elapsed, popups);
                if (success) {
                    lastClick = now; clicks++; acted = true; recordClick(pkg, "tree-text", now);
                    report(pkg, "系统已接受点击请求；请观察广告是否消失");
                    break;
                }
            }
            if (!acted) {
                String reason = targets > 0 ? "识别到按钮，但系统或应用未接受点击"
                    : matches > 0 ? "识别到文字，但没有符合安全范围的可点击控件"
                    : elapsed > 15000 ? "已超过 15 秒开屏识别窗口"
                    : !marker ? "未找到可读广告按钮或广告标识，可能是图片/自绘广告"
                    : "找到广告标识，但未找到符合规则的跳过或关闭文字";
                report(pkg, reason + "；控件 " + nodes.size() + "，匹配文字 " + matches);
            }
        } catch (IllegalStateException e) {
            report(pkg, "扫描时窗口已变化，等待重试");
        } finally {
            for (AccessibilityNodeInfo node : nodes) node.recycle();
            if (allowed && clicks < 5) {
                long age = SystemClock.elapsedRealtime() - enteredAt;
                if (age < 15000) schedule(acted ? 30 : (age < 2000 ? 40 : 180), false);
            }
        }
    }
    private AccessibilityNodeInfo clickTarget(AccessibilityNodeInfo node, String pkg, boolean marker, long elapsed, boolean popups) {
        Rect childBounds = new Rect();
        node.getBoundsInScreen(childBounds);
        if (childBounds.isEmpty()) return null;
        if (node.isClickable()) return node;
        AccessibilityNodeInfo cursor = node;
        for (int depth = 0; depth < 2; depth++) {
            AccessibilityNodeInfo parent = cursor.getParent();
            if (cursor != node) cursor.recycle();
            if (parent == null) return null;
            Rect bounds = new Rect();
            parent.getBoundsInScreen(bounds);
            boolean safe = parent.isVisibleToUser() && parent.isEnabled() && !parent.isEditable() && !parent.isPassword()
                && pkg.equals(str(parent.getPackageName())) && parent.getWindowId() == node.getWindowId()
                && bounds.contains(childBounds)
                && Rules.compactParent(childBounds.width(), childBounds.height(), bounds.width(), bounds.height(),
                    parent.getChildCount(), getResources().getDisplayMetrics().density)
                && safeParentLabel(parent.getText(), marker, elapsed, popups)
                && safeParentLabel(parent.getContentDescription(), marker, elapsed, popups);
            if (!safe) { parent.recycle(); return null; }
            if (parent.isClickable()) return parent;
            cursor = parent;
        }
        if (cursor != node) cursor.recycle();
        return null;
    }
    private boolean safeParentLabel(CharSequence value, boolean marker, long elapsed, boolean popups) {
        String text = str(value);
        return text.trim().isEmpty() || Rules.candidate(text, marker, elapsed, popups);
    }
    private boolean tapMatchedNode(AccessibilityNodeInfo node, String pkg, boolean marker, long elapsed, boolean popups) {
        // Re-read the live node immediately before acting. This prevents a candidate from a
        // previous animation frame being clicked after the screen has already changed.
        if (!node.refresh() || !node.isVisibleToUser() || !node.isEnabled()
                || node.isEditable() || node.isPassword()
                || !pkg.equals(str(node.getPackageName())) || node.getWindowId() != currentWindowId) return false;
        String viewId = str(node.getViewIdResourceName());
        boolean textStillMatches = Rules.candidate(str(node.getText()), marker, elapsed, popups)
            || Rules.candidate(str(node.getContentDescription()), marker, elapsed, popups);
        boolean idStillMatches = Rules.adSkipViewId(viewId) || (marker && Rules.skipLikeViewId(viewId))
            || knownPackageControl(pkg, viewId, popups);
        if (!textStillMatches && !idStillMatches) return false;
        // A visible matched label is the fastest target even when the SDK handles touch in a parent.
        if (gestureTap(node)) return true;
        AccessibilityNodeInfo target = clickTarget(node, pkg, marker, elapsed, popups);
        if (target == null) return false;
        try { return target.performAction(AccessibilityNodeInfo.ACTION_CLICK); }
        finally { if (target != node) target.recycle(); }
    }
    private boolean knownPackageControl(String pkg, String viewId, boolean popups) {
        return popups && "cn.damai".equals(pkg)
            && viewId.endsWith(":id/homepage_popup_window_close_btn");
    }
    private boolean gestureTap(AccessibilityNodeInfo node) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) return false;
        Path path = new Path();
        path.moveTo(bounds.exactCenterX(), bounds.exactCenterY());
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, 12)).build();
        return dispatchGesture(gesture, null, null);
    }
    private boolean fastQqMusicSplash(AccessibilityNodeInfo root, String pkg) {
        if (!"com.tencent.qqmusic".equals(pkg)) return false;
        List<AccessibilityNodeInfo> markers = root.findAccessibilityNodeInfosByViewId("com.tencent.qqmusic:id/kvo");
        List<AccessibilityNodeInfo> skips = root.findAccessibilityNodeInfosByText("跳过");
        try {
            boolean hasMarker = false;
            for (AccessibilityNodeInfo marker : markers) {
                if (marker.isVisibleToUser() && Rules.adMarker(str(marker.getText()))) { hasMarker = true; break; }
            }
            if (!hasMarker) return false;
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            for (AccessibilityNodeInfo skip : skips) {
                if (!skip.isVisibleToUser() || !skip.isEnabled() || !"跳过".equals(str(skip.getText()).trim())) continue;
                Rect bounds = new Rect(); skip.getBoundsInScreen(bounds);
                if (bounds.isEmpty() || bounds.centerX() < screenWidth * 2 / 3 || bounds.centerY() > screenHeight / 4) continue;
                return tapMatchedNode(skip, pkg, true, 0, true);
            }
            return false;
        } finally {
            for (AccessibilityNodeInfo marker : markers) marker.recycle();
            for (AccessibilityNodeInfo skip : skips) skip.recycle();
        }
    }
    private boolean fastIndexedAdControl(AccessibilityNodeInfo root, String pkg, long elapsed, boolean popups) {
        if (elapsed < 0 || elapsed > 15000) return false;
        boolean marker = indexedMarker(root, "广告") || indexedMarker(root, "Advertisement");
        String[] queries = {"跳过广告", "跳过", "Skip ad", "Skip"};
        for (String query : queries) {
            List<AccessibilityNodeInfo> hits = root.findAccessibilityNodeInfosByText(query);
            try {
                for (AccessibilityNodeInfo node : hits) {
                    if (!node.isVisibleToUser() || !node.isEnabled() || node.isEditable() || node.isPassword()) continue;
                    if (!Rules.candidate(str(node.getText()), marker, elapsed, popups)
                            && !Rules.candidate(str(node.getContentDescription()), marker, elapsed, popups)) continue;
                    if (tapMatchedNode(node, pkg, marker, elapsed, popups)) return true;
                }
            } finally {
                for (AccessibilityNodeInfo hit : hits) hit.recycle();
            }
        }
        return false;
    }
    private boolean indexedMarker(AccessibilityNodeInfo root, String query) {
        List<AccessibilityNodeInfo> hits = root.findAccessibilityNodeInfosByText(query);
        try {
            for (AccessibilityNodeInfo node : hits) {
                if (node.isVisibleToUser() && !node.isEditable() && !node.isPassword()
                        && (Rules.adMarker(str(node.getText())) || Rules.adMarker(str(node.getContentDescription())))) return true;
            }
            return false;
        } finally {
            for (AccessibilityNodeInfo hit : hits) hit.recycle();
        }
    }
    private boolean sensitiveWindow(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focus != null) {
            try {
                if (focus.isVisibleToUser() && (focus.isEditable() || focus.isPassword()
                        || Rules.sensitiveContext(str(focus.getText()))
                        || Rules.sensitiveContext(str(focus.getContentDescription())))) return true;
            } finally { if (focus != root) focus.recycle(); }
        }
        String[] labels = {"支付密码", "登录密码", "交易密码", "短信验证码", "银行卡号", "身份验证"};
        for (String label : labels) {
            List<AccessibilityNodeInfo> hits = root.findAccessibilityNodeInfosByText(label);
            try {
                for (AccessibilityNodeInfo node : hits) {
                    if (node.isVisibleToUser() && (Rules.sensitiveContext(str(node.getText()))
                            || Rules.sensitiveContext(str(node.getContentDescription())))) return true;
                }
            } finally {
                for (AccessibilityNodeInfo hit : hits) hit.recycle();
            }
        }
        return false;
    }
    private static String str(CharSequence value) { return value == null ? "" : value.toString(); }
    @Override public void onInterrupt() {
        handler.removeCallbacks(scan); queued = false;
        liveState = "服务收到中断，等待下一次界面事件";
    }
    @Override public void onDestroy() {
        connected = false; liveState = "服务已断开";
        handler.removeCallbacks(scan);
        super.onDestroy();
    }
}
