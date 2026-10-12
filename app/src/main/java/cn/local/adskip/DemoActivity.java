package cn.local.adskip;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.*;

public final class DemoActivity extends Activity {
    static volatile boolean active;
    private LinearLayout panel;
    @Override public void onCreate(Bundle state) { super.onCreate(state); showMenu(); }
    @Override public void onResume() { super.onResume(); active=true; }
    @Override public void onPause() { active=false; super.onPause(); }
    private void base() {
        panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(36,60,36,36);
        panel.setBackgroundColor(Color.rgb(240,247,244));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(panel);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
    }
    private void text(String value) { TextView v=new TextView(this); v.setText(value); v.setTextSize(20); v.setPadding(0,20,0,20); panel.addView(v); }
    private void button(String value, Runnable action) { Button b=new Button(this); b.setText(value); b.setOnClickListener(v -> action.run()); panel.addView(b); }
    private void showMenu() {
        base(); text("模拟广告测试"); text("先开启总开关和无障碍权限。此页面无需添加到应用列表。自动点击与手动点击都会进入结果页，因此请观察是否在未触碰按钮时自动完成。");
        button("测试开屏广告", () -> ad(false)); button("测试明确关闭按钮", () -> ad(true));
        button("测试普通按钮不被点击", () -> negative()); button("返回首页", () -> finish());
    }
    private void ad(boolean popup) {
        base(); text("广告 · 测试"); text("这是模拟广告，没有真实推广内容。");
        button(popup ? "关闭广告" : "跳过 5 秒", () -> { base(); text("模拟广告已关闭"); text("如果没有手动点击便进入此页，说明本次自动跳过生效。此结果不能代表所有第三方 App。"); button("继续测试", () -> showMenu()); });
    }
    private void negative() {
        base(); text("普通操作页面"); text("以下按钮不应该被自动点击。观察至少 3 秒；若仍停留在这里，符合预期。");
        button("跳过", () -> unexpected()); button("关闭", () -> unexpected()); button("立即购买", () -> unexpected()); button("返回测试菜单", () -> showMenu());
    }
    private void unexpected() { base(); text("普通按钮被点击了"); text("如果不是你手动点击，请立即暂停服务并反馈。"); button("返回首页", () -> finish()); }
}
