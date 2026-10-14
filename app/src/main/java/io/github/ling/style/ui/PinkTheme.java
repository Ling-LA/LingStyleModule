package io.github.ling.style.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Explicit colors keep QQ's host theme from changing the module's appearance. */
final class PinkTheme {
    static final int PINK=0xFFFF8FA3, DEEP=0xFFB83F60, SOFT=0xFFFFF0F4;
    static final int INK=0xFF382E34, MUTED=0xFF75666E, LINE=0xFFF2DFE5, WHITE=0xFFFFFFFF;
    private PinkTheme(){}
    static Context controls(Context c){return new android.view.ContextThemeWrapper(c,android.R.style.Theme_Material_Light_NoActionBar);}
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable shape(Context c,int color,int radius,int stroke){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));
        if(stroke!=0)d.setStroke(dp(c,1),stroke);return d;
    }
    static TextView label(Context c,String value,int size,int color){
        TextView v=new TextView(c);v.setText(value);v.setTextSize(size);v.setTextColor(color);
        v.setFontFeatureSettings("kern");v.setLineSpacing(dp(c,2),1);return v;
    }
    static void space(View v,int top,int bottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(v.getContext(),top);p.bottomMargin=dp(v.getContext(),bottom);v.setLayoutParams(p);
    }
    static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    static LinearLayout card(Context c,String heading){
        LinearLayout card=column(c);card.setPadding(dp(c,16),dp(c,16),dp(c,16),dp(c,16));
        card.setBackground(shape(c,WHITE,18,LINE));space(card,0,12);
        TextView title=label(c,heading,13,DEEP);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);space(title,0,10);card.addView(title);return card;
    }
    static LinearLayout header(Context c,String title,String subtitle){
        LinearLayout h=column(c);h.setPadding(dp(c,22),dp(c,22),dp(c,22),dp(c,20));
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFFFFF5F8,0xFFFFE0E8});
        float r=dp(c,24);bg.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});h.setBackground(bg);
        TextView eyebrow=label(c,"✿  LING · STYLE",11,DEEP);eyebrow.setLetterSpacing(.15f);h.addView(eyebrow);
        TextView name=label(c,title,24,INK);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);space(name,8,6);h.addView(name);
        h.addView(label(c,subtitle,12,MUTED));return h;
    }
    static Button button(Context c,String title,boolean filled){
        Button b=new Button(controls(c));b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xFF9B8F95,filled?WHITE:DEEP}));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22B83F60),shape(c,filled?DEEP:SOFT,12,filled?0:LINE),null));
        b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));b.setPadding(dp(c,12),dp(c,10),dp(c,12),dp(c,10));b.setStateListAnimator(null);space(b,8,0);return b;
    }
    static EditText input(Context c,String hint,String value){
        EditText e=new EditText(controls(c));e.setHint(hint);e.setText(value);e.setTextSize(14);e.setTextColor(INK);e.setHintTextColor(MUTED);
        e.setSingleLine(false);e.setMaxLines(4);e.setMinHeight(dp(c,48));e.setGravity(Gravity.TOP|Gravity.START);
        e.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));e.setBackground(shape(c,0xFFFFFBFD,12,LINE));space(e,6,8);return e;
    }
    static Switch toggle(Context c,String title,boolean checked){
        Switch s=new Switch(controls(c));s.setText(title);s.setTextSize(14);s.setTextColor(INK);s.setMinHeight(dp(c,52));s.setSwitchPadding(dp(c,12));s.setShowText(false);
        GradientDrawable track=shape(c,WHITE,14,0);track.setSize(dp(c,44),dp(c,24));s.setTrackDrawable(track);
        s.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{PINK,0xFFD8CDD2}));
        s.setThumbTintList(ColorStateList.valueOf(WHITE));s.setChecked(checked);return s;
    }
}
