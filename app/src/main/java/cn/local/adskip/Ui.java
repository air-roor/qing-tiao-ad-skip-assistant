package cn.local.adskip;

import android.content.Context;
import android.content.res.ColorStateList;
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
        // This visual edition uses one deliberate dark palette, including native dialogs.
        dark = true;
        bg = color("#090D14");
        surface = color("#121A26");
        ink = color("#EDF3FA");
        muted = color("#97A9BC");
        primary = color("#6DE5ED");
        soft = color("#17313D");
        line = color("#29384A");
        warning = color("#F4C989");
        hero = color("#122631");
        onHero = ink;
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
    GradientDrawable panel() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{color("#16303B"),color("#111C2A"),color("#121824")});
        d.setCornerRadius(dp(24));d.setStroke(dp(1),color("#34505F"));return d;
    }
    TextView mono(String value,int size,int color) {
        TextView v=text(value,size,color,false);v.setTypeface(Typeface.create("monospace",Typeface.NORMAL));v.setLetterSpacing(.06f);return v;
    }
    RippleDrawable ripple(int fill, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x336DE5ED), fill == Color.TRANSPARENT ? shape(fill,radius) : outline(fill, radius, fill == primary ? primary : line), shape(Color.WHITE, radius));
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
        LinearLayout v = column(); v.setBackground(outline(surface, 20, line)); v.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(14); parent.addView(v, p); return v;
    }
    Button button(String value, boolean filled, Runnable action) {
        Button v = new Button(context); v.setText(value); v.setTextSize(15); v.setAllCaps(false);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.setTextColor(filled ? bg : ink);
        v.setBackground(ripple(filled ? primary : surface, 14)); v.setElevation(0); v.setStateListAnimator(null);
        v.setMinWidth(0); v.setMinimumWidth(0);
        v.setMinimumHeight(dp(50)); v.setMinHeight(dp(50)); v.setPadding(dp(18), dp(12), dp(18), dp(12));
        v.setOnClickListener(w -> action.run()); return v;
    }
    void fullButton(LinearLayout parent, String value, boolean filled, Runnable action) {
        parent.addView(button(value, filled, action), new LinearLayout.LayoutParams(-1, -2));
    }
    TextView chip(String value) {
        TextView v = text(value, 12, primary, true); v.setGravity(Gravity.CENTER);
        v.setPadding(dp(10), dp(6), dp(10), dp(6)); v.setBackground(outline(soft, 8, line)); return v;
    }
    EditText field(String hint, boolean multiline) {
        EditText v = new EditText(context); v.setHint(hint); v.setTextSize(15); v.setTextColor(ink); v.setHintTextColor(muted);
        StateListDrawable borders = new StateListDrawable();
        borders.addState(new int[]{android.R.attr.state_focused}, outline(surface, 16, primary));
        borders.addState(new int[]{}, outline(surface, 16, line));
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
        glyph.setPadding(dp(10),dp(10),dp(10),dp(10));glyph.setBackground(shape(soft,12));
        glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(42), dp(42)));
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
    /** Static vector instrument: no timers, shaders refreshed only when the view is drawn. */
    static class CoreArt extends View {
        private final Ui ui;private final boolean ready;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc=new RectF();
        CoreArt(Ui ui,boolean ready) { super(ui.context); this.ui=ui;this.ready=ready;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);float s=Math.min(getWidth()/300f,getHeight()/190f);int accent=ready?ui.primary:ui.muted;
            c.save();c.translate((getWidth()-300*s)/2,(getHeight()-190*s)/2);c.scale(s,s);
            p.setStyle(Paint.Style.FILL);p.setShader(new RadialGradient(150,95,100,new int[]{ready?0x306DE5ED:0x1497A9BC,0x006DE5ED},null,Shader.TileMode.CLAMP));c.drawCircle(150,95,100,p);p.setShader(null);
            p.setColor(0x123E6C83);p.setStrokeWidth(.5f);
            for(int x=6;x<300;x+=18)c.drawLine(x,4,x,186,p);
            for(int y=5;y<190;y+=18)c.drawLine(6,y,294,y,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0xFF34505F);
            c.drawCircle(150,95,79,p);c.drawCircle(150,95,64,p);
            for(int i=0;i<60;i++){double a=Math.toRadians(i*6-90);float r=i%5==0?85:88;p.setColor(i%5==0?0xFF638499:0xFF304454);c.drawLine(150+(float)Math.cos(a)*r,95+(float)Math.sin(a)*r,150+(float)Math.cos(a)*91,95+(float)Math.sin(a)*91,p);}
            arc.set(71,16,229,174);p.setStrokeWidth(3);p.setStrokeCap(Paint.Cap.ROUND);
            p.setShader(new SweepGradient(150,95,new int[]{0x006DE5ED,accent,0x006DE5ED},new float[]{0,.7f,1}));c.drawArc(arc,-70,235,false,p);p.setShader(null);
            p.setColor(ready?0xFF818CF8:0xFF607288);p.setStrokeWidth(2);arc.set(86,31,214,159);c.drawArc(arc,75,75,false,p);
            p.setStyle(Paint.Style.FILL);p.setColor(0xFF142C39);c.drawCircle(150,95,47,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0xFF426374);c.drawCircle(150,95,47,p);
            Glyph bolt=new Glyph("bolt",accent);bolt.setBounds(130,72,170,116);bolt.draw(c);
            p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("monospace",Typeface.NORMAL));p.setTextSize(7);p.setTextAlign(Paint.Align.CENTER);p.setColor(ui.muted);c.drawText("QINGTIAO",150,128,p);
            p.setStrokeWidth(1);p.setColor(0xFF426374);c.drawLine(9,95,49,95,p);c.drawLine(251,95,291,95,p);
            p.setColor(accent);c.drawCircle(49,95,2,p);c.drawCircle(251,95,2,p);
            p.setColor(ui.muted);p.setTextSize(7);c.drawText("LOCAL",27,85,p);c.drawText(ready?"READY":"IDLE",273,85,p);
            c.restore();
        }
    }
    static final class CalmArt extends CoreArt { CalmArt(Ui ui){super(ui,true);} }
}
