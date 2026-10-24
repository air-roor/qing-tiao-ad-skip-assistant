package cn.local.adskip;

import android.app.Activity;
import android.app.AlertDialog;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;

public final class MainActivity extends Activity {
    private static final String REPOSITORY = "https://github.com/air-roor/qing-tiao-ad-skip-assistant";
    private static final String RELEASES = REPOSITORY + "/releases/tag/v0.3.0-beta.2";
    private LinearLayout body;
    private final int ink = Color.rgb(22, 44, 53), teal = Color.rgb(0, 111, 101);
    @Override public void onCreate(Bundle state) { super.onCreate(state); }
    @Override public void onResume() { super.onResume(); render(); }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
    private void render() {
        getWindow().setStatusBarColor(Color.rgb(240, 247, 244));
        getWindow().setNavigationBarColor(Color.rgb(240, 247, 244));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        scroll.setBackgroundColor(Color.rgb(240, 247, 244));
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(24), dp(30), dp(24), dp(32));
        scroll.addView(body); setContentView(scroll);
        label(body, "轻 跳  /  PRODUCT BETA", 13, teal, true);
        label(body, "少一点打扰\n多一点专注", 32, ink, true);
        label(body, "广告跳过助手 · 0.3.0-beta.2", 14, ink, false);
        LinearLayout status = card();
        boolean enabled = Store.prefs(this).getBoolean("enabled", false);
        boolean access = serviceEnabled();
        label(status, !enabled ? "已暂停" : !access ? "等待无障碍授权" : Store.apps(this).isEmpty() ? "请选择要处理的应用" : "已启用 · 等待匹配广告", 21, teal, true);
        label(status, "仅处理你选择的应用。无法识别时保持原样。", 14, ink, false);
        Switch master = new Switch(this); master.setText("自动跳过广告"); master.setTextSize(18); master.setChecked(enabled); status.addView(master);
        master.setOnCheckedChangeListener((button, checked) -> {
            if (checked && !Store.prefs(this).getBoolean("consent", false)) {
                master.setChecked(false); disclosure();
            } else { Store.prefs(this).edit().putBoolean("enabled", checked).apply(); render(); }
        });
        LinearLayout setup = card(); label(setup, "开始使用", 20, ink, true);
        button(setup, access ? "1  无障碍服务已授权 · 查看" : "1  开启无障碍服务", () -> disclosure());
        button(setup, "2  选择应用（已选 " + Store.apps(this).size() + " 个）", () -> chooseApps());
        button(setup, "3  打开模拟广告，测试效果", () -> startActivity(new Intent(this, DemoActivity.class)));
        LinearLayout options = card(); label(options, "处理方式", 20, ink, true);
        label(options, "开屏跳过：进入应用后 15 秒内，优先识别明确的“跳过广告”、倒计时跳过和带广告标识的“跳过”。", 14, ink, false);
        Switch popups = new Switch(this); popups.setText("处理明确的“关闭广告”按钮"); popups.setTextSize(15);
        popups.setChecked(Store.prefs(this).getBoolean("popups", true)); options.addView(popups);
        popups.setOnCheckedChangeListener((v, value) -> Store.prefs(this).edit().putBoolean("popups", value).apply());
        label(options, "点击前会重新读取实时控件。锁屏、系统敏感应用，以及含输入框、密码或身份验证内容的页面会被硬性跳过。", 13, ink, false);
        label(options, "不点击普通“关闭”、无标识叉号或付款按钮。每次前台应用会话最多点击 5 次，重复点击间隔至少 250 毫秒。", 13, ink, false);
        LinearLayout stats = card(); label(stats, "本机记录", 20, ink, true);
        label(stats, "服务进程：" + (SkipService.connected ? "正在运行" : "系统显示已授权，但当前进程尚未确认连接"), 15, SkipService.connected ? teal : ink, true);
        label(stats, "当前状态：" + SkipService.liveState, 13, ink, false);
        label(stats, "已发出 " + Store.prefs(this).getInt("count", 0) + " 次成功点击请求", 18, teal, true);
        label(stats, "包含模拟测试；请求成功不等于已确认广告消失。", 12, ink, false);
        long time = Store.prefs(this).getLong("last_time", 0);
        if (time > 0) label(stats, "最近一次：" + DateFormat.getDateTimeInstance().format(new Date(time)) + "\n"
            + Store.prefs(this).getString("last_package", "") + "\n路径：" + Store.prefs(this).getString("last_path", "未知")
            + " · 命中耗时 " + Store.prefs(this).getLong("last_elapsed", -1) + " ms", 12, ink, false);
        long diagTime = Store.prefs(this).getLong("diag_time", 0);
        if (diagTime > 0) label(stats, "最近诊断：" + DateFormat.getDateTimeInstance().format(new Date(diagTime)) + "\n"
            + Store.prefs(this).getString("diag_package", "") + "\n" + Store.prefs(this).getString("diag_result", ""), 12, ink, false);
        button(stats, "导出诊断（发送前可检查）", () -> exportDiagnostics());
        LinearLayout feedback = card(); label(feedback, "免费公测与反馈", 20, ink, true);
        label(feedback, "当前公测免费且不需要账号。反馈会先生成可检查的文字，不会自动上传；由你决定复制、系统分享或在 GitHub 提交。", 13, ink, false);
        button(feedback, "填写公测反馈", () -> feedbackForm());
        button(feedback, "分享轻跳下载地址", () -> shareApp());
        button(feedback, "查看公测、账号与付费计划", () -> openUrl(REPOSITORY + "/blob/main/docs/BETA_FEEDBACK_AND_ACCOUNT_PLAN.md"));
        button(feedback, "查看隐私说明", () -> openUrl(REPOSITORY + "/blob/main/docs/PRIVACY.md"));
        label(body, "界面识别完全在本机进行，无联网权限，不截图、不上传屏幕内容。仅保存开关、应用选择、点击统计、最近诊断和反馈预览记录；不保存反馈正文。只有你主动复制、打开浏览器或使用系统分享页时，内容才会离开轻跳。\n\n当前仍是测试版，尚未承诺第三方 App 的兼容率；图片广告、强制倒计时和没有可读按钮的广告可能无法处理。", 13, ink, false);
    }
    private boolean serviceEnabled() {
        if (SkipService.connected) return true;
        AccessibilityManager manager = (AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo() != null && getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)) return true;
        }
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String own = new ComponentName(this, SkipService.class).flattenToString();
        String ownShort = new ComponentName(this, SkipService.class).flattenToShortString();
        return enabled.contains(own) || enabled.contains(ownShort);
    }
    private void disclosure() {
        new AlertDialog.Builder(this).setTitle("开启前请了解")
            .setMessage("轻跳使用无障碍权限读取你所选应用公开的界面控件文字、控件 ID 和位置，并代表你点击符合固定规则的广告跳过或关闭按钮。\n\n识别只在本机运行，不联网、不截图、不保存页面正文或控件文字；只保存开关、所选应用包名、点击统计和最近诊断。规则可能漏识别或误判。你可以取消某个应用、关闭首页总开关，或在系统设置撤销权限。\n\n同意后会打开系统无障碍设置，请找到“轻跳 · 自动跳过”手动启用。")
            .setNegativeButton("暂不开启", null).setPositiveButton("同意并前往设置", (d, w) -> {
                Store.prefs(this).edit().putBoolean("consent", true).putBoolean("enabled", true).apply();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }).show();
    }
    private void chooseApps() {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String,String> labels = new TreeMap<>();
        for (ResolveInfo app : getPackageManager().queryIntentActivities(launcher, 0)) {
            String pkg = app.activityInfo.packageName;
            if (!pkg.equals(getPackageName()) && !pkg.equals("com.android.settings") && !pkg.equals("com.android.systemui"))
                labels.put(pkg, app.loadLabel(getPackageManager()).toString());
        }
        Set<String> selected = Store.apps(this);
        // Retain previously selected packages even if launcher visibility changes.
        for (String pkg : selected) if (!labels.containsKey(pkg)) labels.put(pkg, "未找到启动入口");
        List<String> packages = new ArrayList<>(labels.keySet());
        Collections.sort(packages, (a,b) -> labels.get(a).compareToIgnoreCase(labels.get(b)));
        Set<String> draft = new HashSet<>(selected);
        List<String> visiblePackages = new ArrayList<>();
        LinearLayout picker = new LinearLayout(this); picker.setOrientation(LinearLayout.VERTICAL);
        picker.setPadding(dp(18), dp(4), dp(18), 0);
        EditText search = new EditText(this); search.setHint("搜索应用名称或包名"); search.setSingleLine(true); picker.addView(search);
        TextView count = new TextView(this); count.setTextSize(13); count.setTextColor(ink); count.setPadding(0, dp(8), 0, dp(4)); picker.addView(count);
        ListView list = new ListView(this); list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        picker.addView(list, new LinearLayout.LayoutParams(-1, dp(420)));
        Runnable refresh = () -> {
            String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            visiblePackages.clear();
            List<String> rows = new ArrayList<>();
            for (String pkg : packages) {
                String appLabel = labels.get(pkg);
                if (!query.isEmpty() && !appLabel.toLowerCase(Locale.ROOT).contains(query) && !pkg.toLowerCase(Locale.ROOT).contains(query)) continue;
                visiblePackages.add(pkg); rows.add(appLabel + "\n" + pkg);
            }
            list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, rows));
            for (int i = 0; i < visiblePackages.size(); i++) list.setItemChecked(i, draft.contains(visiblePackages.get(i)));
            count.setText("已选择 " + draft.size() + " 个 · 当前显示 " + visiblePackages.size() + " 个");
        };
        list.setOnItemClickListener((parent, view, position, id) -> {
            String pkg = visiblePackages.get(position);
            if (list.isItemChecked(position)) draft.add(pkg); else draft.remove(pkg);
            count.setText("已选择 " + draft.size() + " 个 · 当前显示 " + visiblePackages.size() + " 个");
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { refresh.run(); }
            public void afterTextChanged(Editable s) {}
        });
        refresh.run();
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("只在选中的应用内处理")
            .setView(picker).setNeutralButton("清空选择", null)
            .setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                Set<String> snapshot = new HashSet<>(draft);
                boolean saved = Store.prefs(this).edit().putStringSet("apps", snapshot).commit();
                if (!saved) { Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_LONG).show(); return; }
                dialog.dismiss();
                body.postDelayed(() -> { render(); Toast.makeText(this, "已保存 " + snapshot.size() + " 个应用", Toast.LENGTH_SHORT).show(); }, 120);
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> { draft.clear(); refresh.run(); });
        });
        dialog.show();
    }
    private void shareApp() {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "轻跳 · Android 开屏广告跳过助手")
            .putExtra(Intent.EXTRA_TEXT, "轻跳是一款本地运行、无需 Root 的 Android 开屏广告跳过助手。下载与说明：" + RELEASES);
        startActivity(Intent.createChooser(send, "分享轻跳"));
    }
    private void exportDiagnostics() {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "轻跳 0.3.0-beta.2 诊断")
            .putExtra(Intent.EXTRA_TEXT, Store.exportText(this, serviceEnabled()));
        startActivity(Intent.createChooser(send, "导出诊断；发送前请检查内容"));
    }
    private void feedbackForm() {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(4), dp(18), 0);
        TextView note = new TextView(this); note.setText("先选择问题类型并描述现象。下一步会显示完整反馈内容，确认后才会离开轻跳。");
        note.setTextColor(ink); note.setTextSize(13); note.setPadding(0, dp(4), 0, dp(8)); form.addView(note);
        Spinner category = new Spinner(this);
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Feedback.CATEGORIES);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        category.setAdapter(categoryAdapter); form.addView(category);
        EditText targetApp = new EditText(this); targetApp.setHint("相关应用名称或包名（例如：QQ音乐）"); targetApp.setSingleLine(true); form.addView(targetApp);
        EditText detail = new EditText(this); detail.setHint("请写明第几次启动、广告表现、是否出现按钮，以及你期望的结果");
        detail.setMinLines(4); detail.setMaxLines(8); detail.setGravity(android.view.Gravity.TOP); form.addView(detail);
        CheckBox includeDiagnostic = new CheckBox(this); includeDiagnostic.setText("附带本机诊断（包含设备型号、系统版本和最近应用包名）");
        includeDiagnostic.setChecked(true); form.addView(includeDiagnostic);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("填写公测反馈").setView(form)
            .setNegativeButton("取消", null).setPositiveButton("预览反馈", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String categoryText = String.valueOf(category.getSelectedItem());
            String description = Feedback.clean(detail.getText().toString(), 800);
            if (description.length() < 4) { detail.setError("请至少写 4 个字，便于复现问题"); detail.requestFocus(); return; }
            String id = Feedback.newId();
            String report = Feedback.build(id, categoryText, targetApp.getText().toString(), description,
                includeDiagnostic.isChecked(), Store.exportText(this, serviceEnabled()));
            Store.feedbackPrepared(this, id, categoryText);
            dialog.dismiss();
            previewFeedback(categoryText, targetApp.getText().toString(), report);
        }));
        dialog.show();
    }
    private void previewFeedback(String category, String targetApp, String report) {
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(4), dp(18), 0);
        TextView note = new TextView(this); note.setText("下面内容可以编辑。请先检查应用包名和诊断，再选择发送方式。GitHub Issue 是公开内容；涉及隐私时请使用复制或系统分享发送给开发者。");
        note.setTextColor(ink); note.setTextSize(13); note.setPadding(0, dp(4), 0, dp(8)); content.addView(note);
        EditText preview = new EditText(this); preview.setText(report); preview.setGravity(android.view.Gravity.TOP); preview.clearFocus();
        preview.setMinLines(10); preview.setMaxLines(16); content.addView(preview, new LinearLayout.LayoutParams(-1, dp(390)));
        Button copy = new Button(this); copy.setText("复制反馈文字"); copy.setAllCaps(false); content.addView(copy);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("检查反馈内容").setView(content)
            .setNegativeButton("返回", null).setNeutralButton("提交到 GitHub", null).setPositiveButton("系统分享", null).create();
        dialog.setOnShowListener(ignored -> {
            copy.setOnClickListener(v -> { copyText(preview.getText().toString()); Toast.makeText(this, "反馈已复制", Toast.LENGTH_SHORT).show(); });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> shareFeedback(category, preview.getText().toString()));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> openGitHubFeedback(category, targetApp, preview.getText().toString()));
        });
        dialog.show();
    }
    private void shareFeedback(String category, String report) {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "轻跳公测反馈 · " + Feedback.clean(category, 40))
            .putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(send, "选择反馈接收方式"));
    }
    private void openGitHubFeedback(String category, String targetApp, String report) {
        String app = Feedback.clean(targetApp, 50).replace('\n', ' ');
        String title = "[公测反馈] " + Feedback.clean(category, 40) + (app.isEmpty() ? "" : " · " + app);
        Uri url = Uri.parse(REPOSITORY + "/issues/new").buildUpon()
            .appendQueryParameter("title", title).appendQueryParameter("body", report).build();
        openUrl(url.toString());
    }
    private void copyText(String text) {
        ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("轻跳公测反馈", text));
    }
    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(this, "没有可打开链接的应用", Toast.LENGTH_LONG).show(); }
    }
    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); view.setPadding(dp(18),dp(14),dp(18),dp(18));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(20)); view.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(18); p.bottomMargin=dp(4); body.addView(view,p); return view;
    }
    private void label(LinearLayout parent, String text, int size, int color, boolean bold) {
        TextView view=new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); view.setPadding(0,dp(6),0,dp(10));
        view.setLineSpacing(dp(3),1); if(bold) view.setTypeface(Typeface.DEFAULT,Typeface.BOLD); parent.addView(view);
    }
    private void button(LinearLayout parent, String text, Runnable action) {
        Button b=new Button(this); b.setText(text); b.setTextColor(teal); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); parent.addView(b,new LinearLayout.LayoutParams(-1,-2));
    }
}
