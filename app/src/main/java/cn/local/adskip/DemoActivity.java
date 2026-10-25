package cn.local.adskip;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

public final class DemoActivity extends Activity {
    static volatile boolean active;
    private LinearLayout panel;
    private Ui ui;
    @Override public void onCreate(Bundle state) { super.onCreate(state); showMenu(); }
    @Override public void onResume() { super.onResume(); active=true; }
    @Override public void onPause() { active=false; super.onPause(); }
    private void base(String title, String subtitle) {
        ui=new Ui(this);getWindow().setStatusBarColor(ui.bg);getWindow().setNavigationBarColor(ui.bg);
        int flags=View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if(!ui.dark)flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        if(android.os.Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(ui.bg);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        panel=ui.column();panel.setPadding(ui.dp(24),ui.dp(24),ui.dp(24),ui.dp(24));scroll.addView(panel);setContentView(scroll);
        panel.addView(ui.chip("轻跳实验室"));ui.gap(panel,24);panel.addView(ui.heading(title,28));ui.gap(panel,12);panel.addView(ui.text(subtitle,14,ui.muted,false));ui.gap(panel,24);
    }
    private void showMenu(){
        base("试试轻轻一跳", "先开启总开关与无障碍权限。测试页无需加入应用名单；进入后请不要手动触碰广告按钮。");
        LinearLayout card=ui.card(panel);
        ui.action(card,"bolt","测试开屏广告","识别倒计时跳过按钮",()->ad(false));ui.divider(card);
        ui.action(card,"chat","测试明确关闭按钮","仅处理标注为广告的关闭按钮",()->ad(true));ui.divider(card);
        ui.action(card,"shield","测试普通按钮保护","观察至少 3 秒，不应自动点击",()->negative());
        ui.fullButton(panel,"返回轻跳",false,()->finish());
    }
    private void ad(boolean popup){
        base("广告 · 测试", "这是本机模拟页面，没有真实推广内容。请观察能否在不触碰按钮的情况下进入结果页。");
        LinearLayout card=ui.card(panel);card.addView(new Ui.CalmArt(ui),new LinearLayout.LayoutParams(-1,ui.dp(180)));ui.gap(card,24);
        ui.fullButton(card,popup?"关闭广告":"跳过 5 秒",true,()->{
            base("模拟广告已关闭","若刚才没有手动点击，说明本次自动跳过生效。这不代表所有第三方应用都能处理。");
            panel.addView(new Ui.CalmArt(ui),new LinearLayout.LayoutParams(-1,ui.dp(160)));ui.gap(panel,24);
            ui.fullButton(panel,"继续测试",true,()->showMenu());ui.gap(panel,12);ui.fullButton(panel,"返回轻跳",false,()->finish());
        });
    }
    private void negative(){
        base("普通操作页面","以下按钮不应该被自动点击。保持 3 秒后仍停留在这里，才符合本次测试预期。");
        LinearLayout card=ui.card(panel);
        ui.fullButton(card,"跳过",false,()->unexpected());ui.gap(card,12);
        ui.fullButton(card,"关闭",false,()->unexpected());ui.gap(card,12);
        ui.fullButton(card,"立即购买",false,()->unexpected());
        ui.fullButton(panel,"返回测试菜单",true,()->showMenu());
    }
    private void unexpected(){base("普通按钮被点击了","如果刚才不是你手动点击，请立即暂停服务并提交反馈。");ui.fullButton(panel,"返回首页",true,()->finish());}
}
