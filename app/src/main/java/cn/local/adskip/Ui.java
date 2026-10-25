package cn.local.adskip;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;

/** Shared visual tokens and small native components. No UI framework or network dependency. */
final class Ui {
    final Context context;
    final boolean dark;
    final int bg, surface, ink, muted, primary, soft, line, warning, hero, onHero;
    Ui(Context c) {
        context = c;
        dark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        bg = color(dark ? "#111A18" : "#F5F7F5");
        surface = color(dark ? "#1B2723" : "#FFFFFF");
        ink = color(dark ? "#EDF5EF" : "#182D27");
        muted = color(dark ? "#AEBFB5" : "#5C6E65");
        primary = color(dark ? "#8AD6B0" : "#256747");
        soft = color(dark ? "#293E33" : "#E7F0E9");
        line = color(dark ? "#34473C" : "#DCE5DD");
        warning = color(dark ? "#F1C482" : "#85521B");
        hero = color(dark ? "#213E30" : "#E0EFDF");
        onHero = color(dark ? "#DEF1E1" : "#214F35");
    }
    static int color(String value) { return Color.parseColor(value); }
    int dp(float v) { return (int)(v * context.getResources().getDisplayMetrics().density + .5f); }
    LinearLayout column() { LinearLayout v = new LinearLayout(context); v.setOrientation(LinearLayout.VERTICAL); return v; }
    LinearLayout row() { LinearLayout v = new LinearLayout(context); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    GradientDrawable shape(int fill, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); return d;
    }
    GradientDrawable outline(int fill, int radius, int stroke) {
        GradientDrawable d = shape(fill, radius); d.setStroke(dp(1), stroke); return d;
    }
    RippleDrawable ripple(int fill, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(dark ? 0x228AD6B0 : 0x22256747), shape(fill, radius), shape(Color.WHITE, radius));
    }
    TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(context); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif" + (bold ? "-medium" : ""), Typeface.NORMAL));
        v.setIncludeFontPadding(false); v.setLineSpacing(dp(3), 1); return v;
    }
    TextView heading(String value, int size) {
        TextView v = text(value, size, ink, true);
        if (Build.VERSION.SDK_INT >= 28) v.setAccessibilityHeading(true);
        return v;
    }
    void gap(LinearLayout parent, int height) { View v = new View(context); parent.addView(v, new LinearLayout.LayoutParams(1, dp(height))); }
    void divider(LinearLayout parent) { View v = new View(context); v.setBackgroundColor(line); parent.addView(v, new LinearLayout.LayoutParams(-1, dp(1))); }
    LinearLayout card(LinearLayout parent) {
        LinearLayout v = column(); v.setBackground(shape(surface, 24)); v.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(14); parent.addView(v, p); return v;
    }
    Button button(String value, boolean filled, Runnable action) {
        Button v = new Button(context); v.setText(value); v.setTextSize(15); v.setAllCaps(false);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.setTextColor(filled ? (dark ? color("#153325") : Color.WHITE) : primary);
        v.setBackground(ripple(filled ? primary : soft, 16)); v.setElevation(0); v.setStateListAnimator(null);
        v.setMinWidth(0); v.setMinimumWidth(0);
        v.setMinimumHeight(dp(50)); v.setMinHeight(dp(50)); v.setPadding(dp(18), dp(12), dp(18), dp(12));
        v.setOnClickListener(w -> action.run()); return v;
    }
    void fullButton(LinearLayout parent, String value, boolean filled, Runnable action) {
        parent.addView(button(value, filled, action), new LinearLayout.LayoutParams(-1, -2));
    }
    TextView chip(String value) {
        TextView v = text(value, 12, primary, true); v.setGravity(Gravity.CENTER);
        v.setPadding(dp(10), dp(5), dp(10), dp(5)); v.setBackground(shape(soft, 12)); return v;
    }
    EditText field(String hint, boolean multiline) {
        EditText v = new EditText(context); v.setHint(hint); v.setTextSize(15); v.setTextColor(ink); v.setHintTextColor(muted);
        StateListDrawable borders = new StateListDrawable();
        borders.addState(new int[]{android.R.attr.state_focused}, outline(surface, 16, primary));
        borders.addState(new int[]{}, outline(surface, 16, muted));
        v.setBackground(borders); v.setPadding(dp(16), dp(14), dp(16), dp(14));
        v.setMinHeight(dp(52)); v.setSingleLine(!multiline); v.setSelectAllOnFocus(false);
        if (multiline) { v.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE); v.setGravity(Gravity.TOP); v.setMinLines(4); }
        return v;
    }
    ColorStateList checks() { return new ColorStateList(new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}}, new int[] {primary, muted}); }
    LinearLayout action(LinearLayout parent, String icon, String title, String subtitle, Runnable run) {
        LinearLayout row = row(); row.setPadding(dp(2), dp(13), dp(2), dp(13)); row.setMinimumHeight(dp(64));
        row.setBackground(ripple(Color.TRANSPARENT, 14));
        ImageView glyph = new ImageView(context); glyph.setImageDrawable(new Glyph(icon, primary));
        glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(25), dp(25)));
        LinearLayout copy = column(); LinearLayout.LayoutParams copyP = new LinearLayout.LayoutParams(0, -2, 1); copyP.leftMargin = dp(14); copyP.rightMargin = dp(8);
        copy.addView(text(title, 15, ink, true));
        if (subtitle != null && !subtitle.isEmpty()) { gap(copy, 5); copy.addView(text(subtitle, 12, muted, false)); }
        row.addView(copy, copyP);
        ImageView arrow = new ImageView(context); arrow.setImageDrawable(new Glyph("chevron", muted));
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); row.addView(arrow, new LinearLayout.LayoutParams(dp(18), dp(18)));
        row.setFocusable(true); row.setContentDescription(title + (subtitle == null ? "" : "，" + subtitle));
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.setClassName(Button.class.getName());
            }
        });
        copy.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        row.setOnClickListener(v -> run.run()); parent.addView(row, new LinearLayout.LayoutParams(-1, -2)); return row;
    }
    static final class Glyph extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String name;
        Glyph(String name, int color) { this.name = name; paint.setColor(color); paint.setStrokeWidth(1.7f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); paint.setStyle(Paint.Style.STROKE); }
        private void path(Canvas c, float... xy) { Path p = new Path(); p.moveTo(xy[0], xy[1]); for (int i=2;i<xy.length;i+=2) p.lineTo(xy[i],xy[i+1]); c.drawPath(p, paint); }
        @Override public void draw(Canvas c) {
            Rect b = getBounds(); c.save(); c.translate(b.left, b.top); c.scale(b.width()/24f, b.height()/24f);
            switch (name) {
                case "home": path(c,3,10,12,3,21,10); path(c,5,9,5,21,10,21,10,15,14,15,14,21,19,21,19,9); break;
                case "apps": for(int x=4;x<=14;x+=10)for(int y=4;y<=14;y+=10)c.drawRoundRect(x,y,x+6,y+6,1.3f,1.3f,paint); break;
                case "user": c.drawCircle(12,8,4,paint); c.drawArc(4,14,20,27,190,160,false,paint); break;
                case "shield": path(c,12,2,21,6,20,14,17,19,12,22,7,19,4,14,3,6,12,2); path(c,8,12,11,15,16,9); break;
                case "bolt": path(c,14,2,5,14,11,14,10,22,20,9,14,9,14,2); break;
                case "chevron": path(c,9,5,16,12,9,19); break;
                case "back": path(c,14,5,7,12,14,19); path(c,7,12,21,12); break;
                case "play": path(c,8,4,20,12,8,20,8,4); break;
                case "chat": c.drawRoundRect(3,4,21,18,4,4,paint); path(c,7,18,7,22,12,18); path(c,8,9,16,9); path(c,8,13,13,13); break;
                case "share": c.drawCircle(18,4,3,paint);c.drawCircle(5,12,3,paint);c.drawCircle(18,20,3,paint);path(c,8,10,15,5);path(c,8,14,15,19);break;
                case "chart": path(c,4,4,4,21,21,21);path(c,8,16,8,12);path(c,13,16,13,8);path(c,18,16,18,5);break;
                case "search": c.drawCircle(10,10,6,paint);path(c,15,15,21,21);break;
                case "check": path(c,5,12,10,17,19,6); break;
                case "leaf": {Path p=new Path();p.moveTo(4,20);p.cubicTo(1,7,9,2,21,3);p.cubicTo(22,15,15,22,6,18);c.drawPath(p,paint);path(c,4,21,16,9);break;}
                case "info": c.drawCircle(12,12,9,paint);path(c,12,11,12,17);c.drawPoint(12,7,paint);break;
                default: c.drawCircle(12,12,8,paint);path(c,12,7,12,13,16,15);
            }
            c.restore();
        }
        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { paint.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
    static final class CalmArt extends View {
        private final Ui ui;
        CalmArt(Ui ui) { super(ui.context); this.ui=ui; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); float s=Math.min(getWidth(),getHeight())/120f;
            c.save();c.translate((getWidth()-120*s)/2,(getHeight()-120*s)/2);c.scale(s,s);
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(ui.dark?0x223F9361:0x33599468);c.drawCircle(60,60,51,p);
            p.setColor(ui.dark?0x334F9D6F:0x447DBB7D);c.drawCircle(60,60,36,p);
            Glyph g=new Glyph("shield",ui.onHero);g.setBounds(35,31,85,85);g.draw(c);
            p.setColor(ui.onHero);c.drawCircle(100,31,2.5f,p);c.drawCircle(24,85,2,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.3f);c.drawCircle(20,32,4,p);c.drawLine(96,91,104,91,p);c.drawLine(100,87,100,95,p);c.restore();
        }
    }
}
