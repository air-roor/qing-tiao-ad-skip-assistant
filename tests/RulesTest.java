package cn.local.adskip;

public final class RulesTest {
    private static int count;
    private static void expect(boolean condition, String name) { count++; if(!condition) throw new AssertionError(name); }
    public static void main(String[] args) {
        expect(Rules.candidate("跳过广告",false,100,true), "explicit skip");
        expect(Rules.candidate("Skip ad",false,15000,true), "boundary");
        expect(!Rules.candidate("跳过广告",true,15001,true), "expired");
        expect(Rules.candidate("跳过 5 秒",true,100,true), "timer label");
        expect(Rules.candidate("跳过1",false,100,true), "compact timer without marker");
        expect(Rules.candidate("0秒跳过",false,100,true), "leading timer without marker");
        expect(!Rules.candidate("跳过",false,100,true), "onboarding skip protected");
        expect(!Rules.candidate("关闭",true,100,true), "generic close protected");
        expect(!Rules.candidate("X",true,100,true), "X protected");
        expect(!Rules.candidate("立即购买",true,100,true), "purchase protected");
        expect(!Rules.candidate("跳过广告并领取奖励",true,100,true), "compound action protected");
        expect(Rules.candidate("关闭广告",false,90000,true), "popup after window");
        expect(!Rules.candidate("关闭广告",true,100,false), "popup disabled");
        expect(Rules.adMarker(" 广告 "), "marker");
        expect(!Rules.adMarker("广告设置"), "settings not marker");
        expect(Rules.adMarker("互动广告"), "interactive ad marker");
        expect(Rules.adMarker("开屏广告"), "splash ad marker");
        expect(!Rules.adMarker("广告管理"), "ad management protected");
        expect(Rules.adSkipViewId("com.example:id/tt_splash_skip_btn"), "splash skip id");
        expect(Rules.adSkipViewId("com.example:id/ad_skip"), "ad skip id");
        expect(Rules.adSkipViewId("com.example:id/advertSkipView"), "advert skip id");
        expect(!Rules.adSkipViewId("com.example:id/skip"), "plain onboarding skip id protected");
        expect(!Rules.adSkipViewId("com.advertiser:id/skip"), "package name is not a control signal");
        expect(!Rules.adSkipViewId("com.example:id/splash_logo"), "splash without skip protected");
        expect(Rules.skipLikeViewId("com.example:id/btn_skip_hot_zone"), "generic skip id");
        expect(!Rules.mayAct(false,true,3000,-1,0), "master disabled");
        expect(!Rules.mayAct(true,false,3000,-1,0), "unselected app");
        expect(!Rules.mayAct(true,true,3000,2800,1), "cooldown");
        expect(!Rules.mayAct(true,true,3000,-1,5), "click budget");
        expect(Rules.mayAct(true,true,3000,2750,4), "cooldown boundary");
        expect(Rules.candidate("5秒 跳过",true,100,true), "leading timer");
        expect(Rules.candidate("跳过｜5s",true,100,true), "separator timer");
        expect(Rules.compactParent(100,40,120,50,1,1), "compact parent");
        expect(!Rules.compactParent(100,40,500,500,1,1), "large parent protected");
        expect(!Rules.compactParent(100,40,120,50,2,1), "multi child parent protected");
        expect(Rules.candidate("Ｓｋｉｐ Ａｄ",false,100,true), "full-width normalized");
        expect(Rules.candidate("跳\u200B过1",false,100,true), "zero-width normalized");
        expect(Rules.protectedPackage("com.android.settings"), "settings protected");
        expect(Rules.protectedPackage("com.google.android.permissioncontroller"), "permission controller protected");
        expect(!Rules.protectedPackage("com.example.video"), "ordinary package allowed");
        expect(Rules.sensitiveContext("请输入支付密码"), "payment password protected");
        expect(Rules.sensitiveContext("短信验证码"), "sms code protected");
        expect(!Rules.sensitiveContext("广告跳过"), "ad page is not sensitive context");
        System.out.println("PASS: " + count + " rule and guard checks");
    }
}
