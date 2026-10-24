package cn.local.adskip;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Feedback {
    static final String[] CATEGORIES = {
        "广告没有跳过", "跳过速度较慢", "发生误触", "无障碍服务自动关闭", "应用选择问题", "其他"
    };

    private static final SecureRandom RANDOM = new SecureRandom();

    private Feedback() {}

    static String newId() {
        String time = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(new Date());
        return "QJ-" + time + "-" + String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1000000));
    }

    static String clean(String value, int maxLength) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replace("\r\n", "\n").replace('\r', '\n')
            .replaceAll("[\\p{Cc}&&[^\\n\\t]]", "")
            .trim();
        if (normalized.length() <= maxLength) return normalized;
        return normalized.substring(0, maxLength) + "…";
    }

    static String build(String id, String category, String targetApp, String description,
                        boolean includeDiagnostic, String diagnostic) {
        String safeId = clean(id, 40);
        String safeCategory = clean(category, 40);
        String safeApp = clean(targetApp, 120);
        String safeDescription = clean(description, 800);
        StringBuilder out = new StringBuilder();
        out.append("轻跳公测反馈\n")
            .append("反馈编号：").append(safeId.isEmpty() ? "未生成" : safeId).append('\n')
            .append("问题类型：").append(safeCategory.isEmpty() ? "其他" : safeCategory).append('\n')
            .append("相关应用：").append(safeApp.isEmpty() ? "未填写" : safeApp).append('\n')
            .append("问题描述：\n").append(safeDescription.isEmpty() ? "未填写" : safeDescription).append('\n');
        if (includeDiagnostic) {
            String safeDiagnostic = clean(diagnostic, 1800);
            out.append("\n--- 用户确认附带的本机诊断 ---\n")
                .append(safeDiagnostic.isEmpty() ? "无" : safeDiagnostic).append('\n');
        }
        out.append("\n隐私提醒：发送前请再次检查并删除不希望分享的信息；不要发送密码、验证码、账号或私人页面正文。");
        return out.toString();
    }
}
