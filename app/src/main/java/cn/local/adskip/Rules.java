package cn.local.adskip;

import java.util.Locale;

/** Deliberately conservative, deterministic rules. No arbitrary close/X clicking. */
public final class Rules {
    private Rules() {}
    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
    public static boolean adMarker(String value) {
        String s = normalize(value);
        if (s.equals("advertisement") || s.equals("ad")) return true;
        // Ad SDKs commonly expose short labels such as 互动广告/开屏广告 while the button only says 跳过.
        // Keep this bounded and exclude settings/management text to avoid treating ordinary pages as ads.
        return s.length() <= 8 && (s.equals("广告 · 测试") || s.equals("广告标识")
            || (s.contains("广告") && !s.contains("设置") && !s.contains("管理") && !s.contains("偏好")));
    }
    public static boolean adSkipViewId(String value) {
        String s = viewIdName(value);
        if (!s.contains("skip")) return false;
        // A plain "skip" id is also common in onboarding pages. Require the id itself to
        // identify an ad or splash implementation before it can bypass text matching.
        return s.contains("adskip") || s.contains("ad_skip") || s.contains("skip_ad")
            || s.contains("splash") || s.contains("advert");
    }
    public static boolean skipLikeViewId(String value) {
        return viewIdName(value).contains("skip");
    }
    private static String viewIdName(String value) {
        String s = normalize(value).replace('-', '_');
        int slash = s.lastIndexOf('/');
        return slash >= 0 ? s.substring(slash + 1) : s;
    }
    public static boolean candidate(String value, boolean hasAdMarker, long elapsed, boolean popups) {
        String s = normalize(value);
        if (s.equals("关闭广告") || s.equals("close ad") || s.equals("close advertisement")) return popups;
        if (elapsed < 0 || elapsed > 15000) return false;
        if (skipLabel(s, "(?:跳过广告|skip ad|skip advertisement)")) return true;
        if (s.matches(".*[0-9].*") && skipLabel(s, "(?:跳过|skip)")) return true;
        return hasAdMarker && skipLabel(s, "(?:跳过|skip)");
    }
    private static boolean skipLabel(String text, String word) {
        String timer = "[0-9]{1,2}\\s*(?:s|秒)?";
        return text.matches(word + "(?:\\s*(?:[（(]\\s*" + timer + "\\s*[）)]|[|｜·]?\\s*" + timer + "))?")
            || text.matches(timer + "\\s*[|｜·]?\\s*" + word);
    }
    public static boolean compactParent(int cw, int ch, int pw, int ph, int children, float density) {
        return cw > 0 && ch > 0 && children == 1 && density > 0
            && pw >= cw && ph >= ch && pw <= cw + 32 * density && ph <= ch + 24 * density
            && pw <= 240 * density && ph <= 96 * density;
    }
    public static boolean mayAct(boolean enabled, boolean allowed, long now, long lastClick, int clicks) {
        return enabled && allowed && clicks < 5 && (lastClick < 0 || now - lastClick >= 250);
    }
}
