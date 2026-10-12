package cn.local.adskip;

import android.content.Context;
import android.content.SharedPreferences;
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
    static void record(Context c, String pkg) {
        SharedPreferences p = prefs(c);
        p.edit().putInt("count", p.getInt("count", 0) + 1)
            .putString("last_package", pkg).putLong("last_time", System.currentTimeMillis()).apply();
    }
}
