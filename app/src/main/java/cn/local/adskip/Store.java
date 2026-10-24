package cn.local.adskip;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

final class Store {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    static Set<String> apps(Context c) { return new HashSet<>(prefs(c).getStringSet("apps", new HashSet<String>())); }
    static void diagnostic(Context c, String pkg, String result) {
        SharedPreferences p = prefs(c);
        if (pkg.equals(p.getString("diag_package", "")) && result.equals(p.getString("diag_result", ""))
                && System.currentTimeMillis() - p.getLong("diag_time", 0) < 2000) return;
        p.edit().putString("diag_package", pkg).putString("diag_result", result)
            .putLong("diag_time", System.currentTimeMillis()).apply();
    }
    static void record(Context c, String pkg, String path, long elapsed) {
        SharedPreferences p = prefs(c);
        p.edit().putInt("count", p.getInt("count", 0) + 1)
            .putString("last_package", pkg).putLong("last_time", System.currentTimeMillis())
            .putString("last_path", path).putLong("last_elapsed", elapsed).apply();
    }
    static void feedbackPrepared(Context c, String id, String category) {
        SharedPreferences p = prefs(c);
        p.edit().putInt("feedback_prepared_count", p.getInt("feedback_prepared_count", 0) + 1)
            .putString("last_feedback_id", id).putString("last_feedback_category", category)
            .putLong("last_feedback_time", System.currentTimeMillis()).apply();
    }
    static String exportText(Context c, boolean serviceEnabled) {
        SharedPreferences p = prefs(c);
        long lastTime = p.getLong("last_time", 0), diagTime = p.getLong("diag_time", 0);
        return "轻跳诊断（用户主动导出）\n"
            + "版本：0.3.0-beta.2 (13)\n"
            + "设备：" + Build.MANUFACTURER + " " + Build.MODEL + "\n"
            + "系统：Android " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT + "\n"
            + "无障碍状态：" + (serviceEnabled ? "系统已授权或服务已连接" : "未授权") + "\n"
            + "服务进程：" + (SkipService.connected ? "已连接" : "未确认连接") + "\n"
            + "总开关：" + (p.getBoolean("enabled", false) ? "开启" : "关闭") + "\n"
            + "已选应用数量：" + apps(c).size() + "\n"
            + "点击请求总数：" + p.getInt("count", 0) + "\n"
            + "最近请求：" + (lastTime == 0 ? "无" : DateFormat.getDateTimeInstance().format(new Date(lastTime))) + "\n"
            + "最近应用：" + p.getString("last_package", "无") + "\n"
            + "最近路径：" + p.getString("last_path", "无") + "\n"
            + "命中耗时：" + p.getLong("last_elapsed", -1) + " ms\n"
            + "最近诊断时间：" + (diagTime == 0 ? "无" : DateFormat.getDateTimeInstance().format(new Date(diagTime))) + "\n"
            + "最近诊断应用：" + p.getString("diag_package", "无") + "\n"
            + "最近诊断结果：" + p.getString("diag_result", "无") + "\n"
            + "说明：不包含页面正文、控件文字、账号或聊天内容。发送前仍请检查目标应用包名是否需要遮挡。";
    }
}
