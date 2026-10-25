package cn.local.adskip;

import android.app.Activity;
import android.app.AlertDialog;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.*;
import android.content.ClipboardManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    static final String VERSION = "0.3.0-beta.4";
    private static final String REPOSITORY = "https://github.com/air-roor/qing-tiao-ad-skip-assistant";
    private static final String RELEASES = REPOSITORY + "/releases/tag/v" + VERSION;
    private Ui ui;
    private LinearLayout shell, content, body;
    private String screen = "home", appQuery = "", feedbackApp = "", feedbackDetail = "", feedbackPreview = "", feedbackId = "";
    private int categoryIndex;
    private boolean includeDiagnostic = true, selectedOnly;
    private Set<String> draftApps;
    private final Map<String, String> appNames = new HashMap<>();
    private final Map<String, Drawable> appIcons = new HashMap<>();
    private List<String> packages = new ArrayList<>();
    private boolean loadingApps, appsLoaded;
    private AppAdapter appAdapter;
    private TextView selectionCount;
    private Button saveButton;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean displayedConnection;
    private final Runnable connectionRefresh = new Runnable() {
        @Override public void run() {
            if (isFinishing() || isDestroyed()) return;
            if (screen.equals("home") && displayedConnection != SkipService.connected) render();
            uiHandler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            screen=state.getString("screen", "home"); appQuery=state.getString("query", "");
            feedbackApp=state.getString("feedbackApp", ""); feedbackDetail=state.getString("feedbackDetail", "");
            feedbackPreview=state.getString("preview", ""); feedbackId=state.getString("feedbackId", "");
            categoryIndex=state.getInt("category"); includeDiagnostic=state.getBoolean("include", true); selectedOnly=state.getBoolean("selectedOnly");
            ArrayList<String> restored=state.getStringArrayList("draft"); if(restored!=null) draftApps=new HashSet<>(restored);
        }
    }
    @Override public void onResume() { super.onResume(); render(); uiHandler.removeCallbacks(connectionRefresh); uiHandler.postDelayed(connectionRefresh,1000); }
    @Override public void onPause() { uiHandler.removeCallbacks(connectionRefresh); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("screen",screen);state.putString("query",appQuery);state.putString("feedbackApp",feedbackApp);
        state.putString("feedbackDetail",feedbackDetail);state.putString("preview",feedbackPreview);state.putString("feedbackId",feedbackId);
        state.putInt("category",categoryIndex);state.putBoolean("include",includeDiagnostic);state.putBoolean("selectedOnly",selectedOnly);
        if(draftApps!=null)state.putStringArrayList("draft",new ArrayList<>(draftApps));super.onSaveInstanceState(state);
    }
    private void go(String destination) { hideKeyboard(); screen=destination; render(); }
    private void render() {
        displayedConnection=SkipService.connected;
        ui=new Ui(this);getWindow().setStatusBarColor(ui.bg);getWindow().setNavigationBarColor(ui.surface);
        int flags=View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if(!ui.dark)flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if(!ui.dark)flags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        if(android.os.Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        shell=ui.column();shell.setBackgroundColor(ui.bg);shell.setFocusableInTouchMode(true);
        shell.setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(safe.left,safe.top,safe.right,safe.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
        });
        setContentView(shell);
        content=ui.column();shell.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        switch(screen){case "apps":renderApps();break;case "me":renderMe();break;case "feedback":renderFeedback();break;case "preview":renderPreview();break;default:renderHome();}
        if(!screen.equals("feedback")&&!screen.equals("preview")) navigation();
    }
    private LinearLayout scrollBody() {
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout frame=new FrameLayout(this);scroll.addView(frame);
        LinearLayout column=ui.column();int width=Math.min(getResources().getDisplayMetrics().widthPixels,ui.dp(640));
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        column.setPadding(ui.dp(20),ui.dp(12),ui.dp(20),ui.dp(20));frame.addView(column,p);return column;
    }
    private void pageTitle(LinearLayout parent,String title,String subtitle) {
        parent.addView(ui.heading(title,28));ui.gap(parent,8);parent.addView(ui.text(subtitle,13,ui.muted,false));ui.gap(parent,22);
    }
    private void renderHome() {
        body=scrollBody();
        LinearLayout brand=ui.row();ImageView leaf=new ImageView(this);leaf.setImageDrawable(new Ui.Glyph("leaf",ui.primary));leaf.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        brand.addView(leaf,new LinearLayout.LayoutParams(ui.dp(28),ui.dp(28)));
        TextView title=ui.heading("轻跳",24);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=ui.dp(10);brand.addView(title,tp);
        brand.addView(ui.chip("免费公测"));body.addView(brand);ui.gap(body,22);
        boolean enabled=Store.prefs(this).getBoolean("enabled",false),access=serviceEnabled();int selected=Store.apps(this).size();
        boolean ready=enabled&&access&&SkipService.connected&&selected>0;
        LinearLayout hero=ui.card(body);hero.setBackground(ui.shape(ui.hero,28));hero.setPadding(ui.dp(22),ui.dp(22),ui.dp(22),ui.dp(22));
        LinearLayout upper=ui.row();LinearLayout copy=ui.column();upper.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        copy.addView(ui.text("少一点打扰",13,ui.onHero,true));ui.gap(copy,10);
        String state=!enabled?"轻跳已暂停":!access?"还差一步，就绪":!SkipService.connected?"等待服务连接":selected==0?"选择你的应用":"轻跳正在守候";
        copy.addView(ui.text(state,24,ui.onHero,true));ui.gap(copy,10);
        copy.addView(ui.text(ready?"打开所选应用，自动寻找跳过按钮。":!enabled?"随时开启，把时间留给喜欢的事。":!access?"开启无障碍权限，让轻跳为你跳过广告。":!SkipService.connected?"系统已授权，服务尚未连接。":"只在你选择的应用中自动跳过。",13,ui.onHero,false));
        upper.addView(new Ui.CalmArt(ui),new LinearLayout.LayoutParams(ui.dp(86),ui.dp(106)));hero.addView(upper);ui.gap(hero,18);
        LinearLayout toggleRow=ui.row();toggleRow.addView(ui.text("自动跳过",15,ui.onHero,true),new LinearLayout.LayoutParams(0,-2,1));
        Switch master=new Switch(this);master.setContentDescription("自动跳过广告总开关");master.setChecked(enabled);master.setThumbTintList(ui.checks());master.setMinimumHeight(ui.dp(48));master.setMinimumWidth(ui.dp(56));toggleRow.addView(master);
        master.setOnCheckedChangeListener((b,checked)->{if(checked&&!Store.prefs(this).getBoolean("consent",false)){disclosure();}else{Store.prefs(this).edit().putBoolean("enabled",checked).apply();render();}});
        hero.addView(toggleRow);
        if(enabled&&(!access||!SkipService.connected||selected==0)){ui.gap(hero,10);ui.fullButton(hero,!access?"开启无障碍权限":!SkipService.connected?"查看服务设置":"选择应用",true,()->{if(!access||!SkipService.connected)disclosure();else go("apps");});}
        LinearLayout metrics=ui.row();metric(metrics,String.valueOf(Store.prefs(this).getInt("count",0)),"次点击请求",false);metric(metrics,String.valueOf(selected),"个已选应用",true);body.addView(metrics);ui.gap(body,7);
        body.addView(ui.text("点击请求包含模拟测试，不等于已确认广告消失。",11,ui.muted,false));ui.gap(body,22);
        section(body,"准备好，即刻出发");LinearLayout setup=ui.card(body);
        ui.action(setup,"shield","无障碍服务",SkipService.connected?"已连接 · 可以开始测试":access?"已授权 · 等待服务连接":"待开启 · 仅用于识别和点击广告",()->disclosure());ui.divider(setup);
        ui.action(setup,"apps","管理应用","已选择 "+selected+" 个 · 自己决定处理范围",()->go("apps"));ui.divider(setup);
        ui.action(setup,"play","试一下跳过效果","模拟广告与普通按钮保护测试",()->startActivity(new Intent(this,DemoActivity.class)));
        LinearLayout note=ui.row();ImageView shield=new ImageView(this);shield.setImageDrawable(new Ui.Glyph("shield",ui.muted));shield.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);note.addView(shield,new LinearLayout.LayoutParams(ui.dp(17),ui.dp(17)));
        TextView privacy=ui.text("本机识别  ·  无需 Root  ·  无网络权限",12,ui.muted,false);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-2,-2);pp.leftMargin=ui.dp(8);note.addView(privacy,pp);note.setGravity(Gravity.CENTER);body.addView(note);
    }
    private void metric(LinearLayout parent,String value,String label,boolean second){
        LinearLayout cell=ui.column();cell.setPadding(ui.dp(18),ui.dp(16),ui.dp(18),ui.dp(16));cell.setBackground(ui.shape(ui.surface,22));
        cell.addView(ui.text(value,28,ui.ink,true));ui.gap(cell,6);cell.addView(ui.text(label,12,ui.muted,false));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(second)p.leftMargin=ui.dp(12);parent.addView(cell,p);
    }
    private void section(LinearLayout p,String title){p.addView(ui.heading(title,17));ui.gap(p,12);}
    private void navigation(){
        boolean compact=getResources().getConfiguration().screenHeightDp<500;
        LinearLayout nav=ui.row();nav.setPadding(ui.dp(12),ui.dp(5),ui.dp(12),ui.dp(7));nav.setBackgroundColor(ui.surface);
        String[] labels={"首页","应用","我的"},destinations={"home","apps","me"},icons={"home","apps","user"};
        for(int i=0;i<3;i++){
            final String destination=destinations[i];boolean selected=screen.equals(destination);
            Button b=ui.button(labels[i],false,()->go(destination));b.setTextSize(12);b.setTextColor(selected?ui.primary:ui.muted);
            b.setBackground(ui.ripple(selected?ui.soft:ui.surface,20));b.setPadding(ui.dp(4),ui.dp(8),ui.dp(4),ui.dp(8));
            if(!compact){Ui.Glyph icon=new Ui.Glyph(icons[i],selected?ui.primary:ui.muted);icon.setBounds(0,0,ui.dp(23),ui.dp(23));b.setCompoundDrawables(null,icon,null,null);b.setCompoundDrawablePadding(ui.dp(5));}b.setSelected(selected);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=ui.dp(5);p.rightMargin=ui.dp(5);nav.addView(b,p);
        }
        shell.addView(nav,new LinearLayout.LayoutParams(-1,-2));
    }
    private boolean serviceEnabled(){
        if(SkipService.connected)return true;AccessibilityManager manager=(AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
        for(AccessibilityServiceInfo info:manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK))if(info.getResolveInfo()!=null&&getPackageName().equals(info.getResolveInfo().serviceInfo.packageName))return true;
        String enabled=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled!=null&&(enabled.contains(new ComponentName(this,SkipService.class).flattenToString())||enabled.contains(new ComponentName(this,SkipService.class).flattenToShortString()));
    }
    private void disclosure(){
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("开启前，请了解权限用途")
            .setMessage("轻跳使用无障碍服务读取你所选应用公开的界面控件文字、控件 ID 和位置，并代表你点击符合固定规则的广告跳过或关闭按钮。\n\n识别只在本机运行，不联网、不截图、不保存页面正文或控件文字。只保存开关、应用选择、点击统计、最近诊断和反馈预览编号。\n\n规则可能漏识别或误判。你可以随时暂停总开关、取消应用选择，或在系统设置撤销权限。\n\n同意后将前往系统设置，请手动启用“轻跳 · 自动跳过”。")
            .setNegativeButton("暂不开启",(d,w)->render()).setPositiveButton("同意并前往设置",(d,w)->{
                Store.prefs(this).edit().putBoolean("consent",true).putBoolean("enabled",true).apply();
                try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Exception e){toast("请在系统设置中手动开启无障碍服务");}
            }).create();dialog.setOnCancelListener(d->render());dialog.show();
    }
    private void loadApps(){
        if(loadingApps||appsLoaded)return;loadingApps=true;
        new Thread(()->{
            Map<String,String> names=new HashMap<>();Map<String,Drawable> icons=new HashMap<>();
            try{
                Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                for(ResolveInfo app:getPackageManager().queryIntentActivities(launcher,0)){
                    String pkg=app.activityInfo.packageName;if(pkg.equals(getPackageName())||Rules.protectedPackage(pkg))continue;
                    names.put(pkg,app.loadLabel(getPackageManager()).toString());icons.put(pkg,app.loadIcon(getPackageManager()));
                }
                for(String pkg:Store.apps(this))if(!names.containsKey(pkg))names.put(pkg,"未找到启动入口");
                List<String> sorted=new ArrayList<>(names.keySet());Collections.sort(sorted,(a,b)->names.get(a).compareToIgnoreCase(names.get(b)));
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;appNames.clear();appNames.putAll(names);appIcons.clear();appIcons.putAll(icons);packages=sorted;appsLoaded=true;loadingApps=false;if(screen.equals("apps"))render();});
            }catch(Exception error){runOnUiThread(()->{loadingApps=false;if(!isFinishing())toast("应用列表加载失败，请重试");});}
        },"QingTiao-AppList").start();
    }
    private void renderApps(){
        if(draftApps==null)draftApps=Store.apps(this);
        boolean compact=getResources().getConfiguration().screenHeightDp<500;
        LinearLayout head=ui.column();head.setPadding(ui.dp(20),ui.dp(compact?4:16),ui.dp(20),ui.dp(8));content.addView(head);
        LinearLayout titleRow=ui.row();titleRow.addView(ui.heading("应用管理",compact?20:28),new LinearLayout.LayoutParams(compact?-2:0,-2,compact?0:1));
        EditText search=ui.field("搜索应用名称或包名",false);search.setContentDescription("搜索应用名称或包名");search.setText(appQuery);
        if(compact){LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(0,-2,1);qp.leftMargin=ui.dp(16);qp.rightMargin=ui.dp(12);titleRow.addView(search,qp);}
        Button clear=ui.button("清空",false,()->new AlertDialog.Builder(this).setTitle("清空选择？").setMessage("这只会修改草稿，点击保存后才会生效。").setNegativeButton("取消",null).setPositiveButton("清空草稿",(d,w)->{draftApps.clear();refreshApps();}).show());titleRow.addView(clear);head.addView(titleRow);
        if(!compact){ui.gap(head,8);head.addView(ui.text("只在你选择的应用中，轻轻跳过。",13,ui.muted,false));ui.gap(head,18);head.addView(search);}
        LinearLayout filters=ui.row();ui.gap(head,compact?4:10);head.addView(filters);
        Button all=ui.button("全部应用",!selectedOnly,()->{selectedOnly=false;render();});Button picked=ui.button("已选择",selectedOnly,()->{selectedOnly=true;render();});
        filters.addView(all,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,-2,1);fp.leftMargin=ui.dp(10);filters.addView(picked,fp);
        selectionCount=ui.text("",12,ui.muted,false);
        if(compact){LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,2);cp.leftMargin=ui.dp(16);filters.addView(selectionCount,cp);}
        else{ui.gap(head,12);head.addView(selectionCount);}
        FrameLayout listFrame=new FrameLayout(this);content.addView(listFrame,new LinearLayout.LayoutParams(-1,0,1));
        ListView list=new ListView(this);list.setDivider(null);list.setClipToPadding(false);list.setPadding(ui.dp(20),ui.dp(4),ui.dp(20),ui.dp(8));list.setSelector(ui.ripple(ui.surface,18));
        listFrame.addView(list,new FrameLayout.LayoutParams(-1,-1));TextView empty=ui.text(appsLoaded?"没有找到应用\n试试其他名称或切换筛选":"正在读取应用列表…",15,ui.muted,false);empty.setGravity(Gravity.CENTER);listFrame.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);
        appAdapter=new AppAdapter();list.setAdapter(appAdapter);refreshApps();
        list.setOnItemClickListener((parent,v,position,id)->{String pkg=appAdapter.visible.get(position);if(!draftApps.add(pkg))draftApps.remove(pkg);refreshApps();});
        search.addTextChangedListener(watcher(value->{appQuery=value;refreshApps();}));
        LinearLayout footer=ui.row();footer.setPadding(ui.dp(20),ui.dp(10),ui.dp(20),ui.dp(10));footer.setBackgroundColor(ui.surface);
        Button cancel=ui.button("取消修改",false,()->{draftApps=Store.apps(this);refreshApps();toast("已恢复已保存的选择");});footer.addView(cancel,new LinearLayout.LayoutParams(0,-2,1));
        saveButton=ui.button("保存选择",true,()->saveApps());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-2,1.5f);sp.leftMargin=ui.dp(12);footer.addView(saveButton,sp);content.addView(footer);updateSelection();loadApps();
    }
    private void refreshApps(){if(appAdapter!=null){appAdapter.filter();appAdapter.notifyDataSetChanged();}updateSelection();}
    private void updateSelection(){
        if(selectionCount==null||draftApps==null)return;boolean dirty=!draftApps.equals(Store.apps(this));
        selectionCount.setText("已选择 "+draftApps.size()+" 个"+(dirty?"  ·  修改尚未保存":"  ·  已保存"));
        if(saveButton!=null)saveButton.setText(dirty?"保存选择（"+draftApps.size()+"）":"已保存 "+draftApps.size()+" 个");
    }
    private void saveApps(){
        if(!Store.prefs(this).edit().putStringSet("apps",new HashSet<>(draftApps)).commit()){toast("保存失败，请重试");return;}
        hideKeyboard();updateSelection();toast("已保存 "+draftApps.size()+" 个应用");
    }
    private final class AppAdapter extends BaseAdapter {
        final class Holder { LinearLayout row; ImageView icon; TextView name, pkg; CheckBox check; }
        final List<String> visible=new ArrayList<>();
        void filter(){visible.clear();String q=appQuery.trim().toLowerCase(Locale.ROOT);for(String pkg:packages)if((!selectedOnly||draftApps.contains(pkg))&&(q.isEmpty()||pkg.toLowerCase(Locale.ROOT).contains(q)||appNames.get(pkg).toLowerCase(Locale.ROOT).contains(q)))visible.add(pkg);}
        @Override public int getCount(){return visible.size();}@Override public Object getItem(int p){return visible.get(p);}@Override public long getItemId(int p){return visible.get(p).hashCode();}
        @Override public View getView(int position,View recycled,ViewGroup parent){
            String pkg=visible.get(position);Holder h;
            if(recycled==null){
                h=new Holder();h.row=ui.row();h.row.setMinimumHeight(ui.dp(78));h.row.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));h.row.setBackground(ui.shape(ui.surface,16));
                h.icon=new ImageView(MainActivity.this);h.icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);h.row.addView(h.icon,new LinearLayout.LayoutParams(ui.dp(40),ui.dp(40)));
                LinearLayout copy=ui.column();h.name=ui.text("",15,ui.ink,true);h.name.setMaxLines(2);h.name.setEllipsize(TextUtils.TruncateAt.END);copy.addView(h.name);ui.gap(copy,5);h.pkg=ui.text("",11,ui.muted,false);h.pkg.setSingleLine(true);h.pkg.setEllipsize(TextUtils.TruncateAt.MIDDLE);copy.addView(h.pkg);
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=ui.dp(12);cp.rightMargin=ui.dp(8);h.row.addView(copy,cp);copy.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                h.check=new CheckBox(MainActivity.this);h.check.setButtonTintList(ui.checks());h.check.setFocusable(false);h.check.setClickable(false);h.check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);h.row.addView(h.check,new LinearLayout.LayoutParams(ui.dp(32),ui.dp(48)));h.row.setTag(h);
                h.row.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);Holder bound=(Holder)host.getTag();info.setClassName(CheckBox.class.getName());info.setCheckable(true);info.setChecked(bound.check.isChecked());}});
            }else h=(Holder)recycled.getTag();
            Drawable drawable=appIcons.get(pkg);h.icon.setImageDrawable(drawable==null?new Ui.Glyph("apps",ui.primary):drawable);h.name.setText(appNames.get(pkg));h.pkg.setText(pkg);h.check.setChecked(draftApps.contains(pkg));h.row.setContentDescription(appNames.get(pkg)+"，"+(draftApps.contains(pkg)?"已选择":"未选择"));return h.row;
        }
    }
    private void renderMe(){
        body=scrollBody();pageTitle(body,"我的轻跳","简单一点，也安心一点。");
        LinearLayout beta=ui.card(body);beta.setBackground(ui.shape(ui.hero,24));beta.addView(ui.chip("免费公测 · 无需登录"));ui.gap(beta,14);beta.addView(ui.heading("一起让轻跳更好用",20));ui.gap(beta,8);beta.addView(ui.text("遇到漏跳、速度慢或服务关闭？\n告诉我们，它会成为下一次改进的起点。",14,ui.muted,false));ui.gap(beta,16);ui.fullButton(beta,"填写公测反馈",true,()->go("feedback"));
        section(body,"使用偏好");LinearLayout options=ui.card(body);LinearLayout toggle=ui.row();LinearLayout copy=ui.column();copy.addView(ui.text("关闭广告弹窗",15,ui.ink,true));ui.gap(copy,5);copy.addView(ui.text("仅处理明确标注的“关闭广告”按钮",12,ui.muted,false));toggle.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        Switch popups=new Switch(this);popups.setContentDescription("处理明确关闭广告按钮");popups.setThumbTintList(ui.checks());popups.setMinimumHeight(ui.dp(48));popups.setChecked(Store.prefs(this).getBoolean("popups",true));toggle.addView(popups);popups.setOnCheckedChangeListener((v,value)->Store.prefs(this).edit().putBoolean("popups",value).apply());options.addView(toggle);ui.divider(options);
        ui.action(options,"play","模拟广告测试","看看自动跳过是否正常",()->startActivity(new Intent(this,DemoActivity.class)));ui.divider(options);
        ui.action(options,"shield","服务与后台运行","查看授权状态和华为后台设置建议",()->serviceHelp());
        section(body,"本机记录");LinearLayout records=ui.card(body);
        ui.action(records,"chart","查看最近诊断",SkipService.connected?"服务已连接 · 本机处理":"服务未连接 · 请检查系统授权",()->showDiagnostics());ui.divider(records);
        ui.action(records,"share","导出诊断","预览、编辑后再主动分享",()->{feedbackPreview=Store.exportText(this,serviceEnabled());feedbackId="diagnostic";go("preview");});
        section(body,"关于轻跳");LinearLayout about=ui.card(body);
        ui.action(about,"share","分享轻跳","把少一点打扰，分享给朋友",()->shareText("轻跳 · 开屏广告跳过助手","轻跳免费公测中，无需 Root、本机识别。下载与说明："+RELEASES));ui.divider(about);
        ui.action(about,"shield","隐私与权限","无网络权限 · 不截图 · 不上传页面",()->openUrl(REPOSITORY+"/blob/main/docs/PRIVACY.md"));ui.divider(about);
        ui.action(about,"info","版本与公测计划","v"+VERSION+" · 账号与永久权益规划",()->openUrl(REPOSITORY+"/blob/main/docs/BETA_FEEDBACK_AND_ACCOUNT_PLAN.md"));
        TextView footer=ui.text("轻跳 QingTiao\n无法识别时，保持原样。",12,ui.muted,false);footer.setGravity(Gravity.CENTER);ui.gap(body,6);body.addView(footer);
    }
    private void serviceHelp(){new AlertDialog.Builder(this).setTitle("让轻跳保持连接").setMessage("系统授权："+(serviceEnabled()?"已授权":"未授权")+"\n服务连接："+(SkipService.connected?"已连接":"未连接")+"\n\n华为 / HarmonyOS 建议：\n1. 在最近任务中锁定轻跳。\n2. 在应用启动管理中允许自动启动、关联启动和后台活动。\n3. 清后台、重启或更新后，检查无障碍服务是否仍开启。\n\n系统可能主动关闭服务，轻跳不能静默重新开启权限。").setNegativeButton("知道了",null).setPositiveButton("前往无障碍设置",(d,w)->disclosure()).show();}
    private void showDiagnostics(){new AlertDialog.Builder(this).setTitle("最近的本机记录").setMessage(Store.exportText(this,serviceEnabled())).setPositiveButton("知道了",null).setNeutralButton("预览导出",(d,w)->{feedbackPreview=Store.exportText(this,serviceEnabled());feedbackId="diagnostic";go("preview");}).show();}
    private void backHeader(String title,Runnable back){
        LinearLayout row=ui.row();row.setPadding(ui.dp(12),ui.dp(4),ui.dp(20),ui.dp(4));Button button=ui.button("",false,back);Ui.Glyph icon=new Ui.Glyph("back",ui.ink);icon.setBounds(0,0,ui.dp(24),ui.dp(24));button.setCompoundDrawables(null,icon,null,null);button.setBackground(ui.ripple(ui.bg,18));button.setContentDescription("返回");row.addView(button,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        TextView text=ui.heading(title,18);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=ui.dp(8);row.addView(text,p);content.addView(row);
    }
    private void renderFeedback(){
        backHeader("公测反馈",()->go("me"));body=scrollBody();pageTitle(body,"你的反馈，很有用","先在本机整理，检查后再发送。");
        LinearLayout types=ui.card(body);types.addView(ui.heading("遇到了什么问题？",16));ui.gap(types,10);
        RadioGroup group=new RadioGroup(this);for(int i=0;i<Feedback.CATEGORIES.length;i++){
            RadioButton choice=new RadioButton(this);choice.setId(3000+i);choice.setText(Feedback.CATEGORIES[i]);choice.setTextSize(14);choice.setTextColor(ui.ink);choice.setButtonTintList(ui.checks());choice.setMinHeight(ui.dp(48));group.addView(choice);}
        group.check(3000+categoryIndex);group.setOnCheckedChangeListener((g,id)->categoryIndex=id-3000);types.addView(group);
        body.addView(ui.heading("相关应用",15));ui.gap(body,8);EditText target=ui.field("例如：QQ 音乐，或填写包名",false);target.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});target.setText(feedbackApp);target.setContentDescription("相关应用名称或包名");target.addTextChangedListener(watcher(v->feedbackApp=v));body.addView(target);ui.gap(body,18);
        body.addView(ui.heading("发生了什么？",15));ui.gap(body,8);EditText detail=ui.field("第几次启动？广告按钮是什么样？\n请描述可以重复出现的操作。",true);detail.setFilters(new InputFilter[]{new InputFilter.LengthFilter(800)});detail.setText(feedbackDetail);detail.setContentDescription("问题描述");detail.addTextChangedListener(watcher(v->feedbackDetail=v));body.addView(detail);ui.gap(body,16);
        CheckBox diagnostic=new CheckBox(this);diagnostic.setText("附带本机诊断");diagnostic.setTextColor(ui.ink);diagnostic.setButtonTintList(ui.checks());diagnostic.setMinHeight(ui.dp(48));diagnostic.setChecked(includeDiagnostic);diagnostic.setOnCheckedChangeListener((b,checked)->includeDiagnostic=checked);body.addView(diagnostic);
        body.addView(ui.text("包含设备型号、系统版本和最近应用包名。\n下一步可检查、修改；不会自动上传。",12,ui.muted,false));ui.gap(body,20);
        ui.fullButton(body,"预览反馈内容",true,()->{if(Feedback.clean(feedbackDetail,800).length()<4){detail.setError("请至少填写 4 个字，便于复现");detail.requestFocus();return;}
            feedbackId=Feedback.newId();feedbackPreview=Feedback.build(feedbackId,Feedback.CATEGORIES[categoryIndex],feedbackApp,feedbackDetail,includeDiagnostic,Store.exportText(this,serviceEnabled()));Store.feedbackPrepared(this,feedbackId,Feedback.CATEGORIES[categoryIndex]);go("preview");});
    }
    private void renderPreview(){
        boolean diag="diagnostic".equals(feedbackId);backHeader(diag?"预览诊断":"检查反馈",()->go(diag?"me":"feedback"));body=scrollBody();
        pageTitle(body,"发送前，再看一眼","可以删除任何你不想分享的信息。");
        EditText preview=ui.field("反馈内容",true);preview.setText(feedbackPreview);preview.setContentDescription("可编辑的反馈预览");preview.setMinLines(4);preview.addTextChangedListener(watcher(v->feedbackPreview=v));body.addView(preview,new LinearLayout.LayoutParams(-1,ui.dp(Math.max(150,Math.min(340,getResources().getConfiguration().screenHeightDp/2)))));ui.gap(body,16);
        body.addView(ui.text("请勿发送密码、验证码或私人页面内容。GitHub Issue 公开可见；涉及隐私时请通过私信发送给开发者。",13,ui.muted,false));ui.gap(body,20);
        LinearLayout actions=ui.column();actions.setPadding(ui.dp(20),ui.dp(10),ui.dp(20),ui.dp(10));actions.setBackgroundColor(ui.surface);LinearLayout primaryActions=ui.row();actions.addView(primaryActions);
        Button copy=ui.button("复制反馈文字",true,()->{ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("轻跳反馈",feedbackPreview));toast("已复制，请选择接收方后粘贴");});primaryActions.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        Button share=ui.button("系统分享",false,()->shareText(diag?"轻跳诊断":"轻跳公测反馈",feedbackPreview));LinearLayout.LayoutParams shareParams=new LinearLayout.LayoutParams(0,-2,1);shareParams.leftMargin=ui.dp(12);primaryActions.addView(share,shareParams);
        if(!diag){ui.gap(actions,8);ui.fullButton(actions,"在 GitHub 提交（公开）",false,()->{
            String title="[公测反馈] "+Feedback.CATEGORIES[categoryIndex]+" · "+Feedback.clean(feedbackApp,50).replace('\n',' ');
            Uri url=Uri.parse(REPOSITORY+"/issues/new").buildUpon().appendQueryParameter("title",title).appendQueryParameter("body",feedbackPreview).build();openUrl(url.toString());});}content.addView(actions);
    }
    private interface OnText{void change(String value);}
    private TextWatcher watcher(OnText change){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){change.change(s.toString());}public void afterTextChanged(Editable e){}};}
    private void shareText(String title,String text){try{startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,title).putExtra(Intent.EXTRA_TEXT,text),"选择分享方式"));}catch(Exception error){toast("没有可用的分享应用，可以先复制文字");}}
    private void openUrl(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception error){toast("没有可打开链接的应用");}}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    private void hideKeyboard(){View focus=getCurrentFocus();if(focus!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0);}
    @Override public void onBackPressed(){
        if(screen.equals("preview")){go("diagnostic".equals(feedbackId)?"me":"feedback");return;}
        if(screen.equals("feedback")){go("me");return;}
        if(!screen.equals("home")){go("home");return;}
        if(draftApps!=null&&!draftApps.equals(Store.apps(this))){new AlertDialog.Builder(this).setTitle("应用选择尚未保存").setMessage("保存这次修改，还是放弃后退出？").setNeutralButton("继续编辑",(d,w)->go("apps")).setNegativeButton("放弃退出",(d,w)->{draftApps=null;finish();}).setPositiveButton("保存退出",(d,w)->{if(Store.prefs(this).edit().putStringSet("apps",new HashSet<>(draftApps)).commit())finish();else toast("保存失败，请重试");}).show();return;}
        super.onBackPressed();
    }
}
