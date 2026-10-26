package org.zhixiaoce.aperture;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared native television surfaces. No blur API, external font or GPU requirement. */
final class TvUi {
    static final int INK=0xfff4f2ed,MUTED=0xffb9b9bd;
    static int typeSize(int legacy){return Math.max(15,Math.round(legacy*.82f));}
    static int dp(Activity a,float n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    static GradientDrawable glass(Activity a,boolean focus){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,focus?new int[]{0xff555352,0xff343536,0xff292a2c}:new int[]{0xff292b2f,0xff1c1e22,0xff181a1e});
        g.setCornerRadius(dp(a,10));g.setStroke(dp(a,focus?2:1),focus?0xfffff1d9:0xff47494d);return g;
    }
    static GradientDrawable background(){return new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff1b1d21,0xff0d1014,0xff080a0d});}
    static StateListDrawable states(Activity a){StateListDrawable s=new StateListDrawable();s.addState(new int[]{android.R.attr.state_focused},glass(a,true));s.addState(new int[]{android.R.attr.state_pressed},glass(a,true));s.addState(new int[]{},glass(a,false));return s;}
    static TextView text(Activity a,String text,int size,boolean muted){TextView v=new TextView(a);v.setText(text);v.setTextColor(muted?MUTED:INK);v.setTextSize(typeSize(size));v.setIncludeFontPadding(false);v.setPadding(0,dp(a,3),0,dp(a,3));return v;}
    static void styleButton(Activity a,Button b){b.setAllCaps(false);b.setTextColor(INK);b.setTextSize(BuildConfig.PARENTS_EDITION?20:17);b.setTypeface(Typeface.DEFAULT,Typeface.NORMAL);b.setMinHeight(dp(a,44));b.setMinimumHeight(dp(a,44));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(a,14),dp(a,10),dp(a,14),dp(a,10));b.setBackground(states(a));b.setElevation(dp(a,2));b.setGravity(Gravity.CENTER);b.setOnFocusChangeListener((v,f)->{v.animate().cancel();v.animate().scaleX(f?1.015f:1).scaleY(f?1.015f:1).setDuration(130).start();});}
    static Button button(Activity a,String title,Runnable run){Button b=new Button(a);styleButton(a,b);b.setText(title);b.setOnClickListener(v->run.run());return b;}
    static void showForm(Activity a,LinearLayout layout){
        android.widget.FrameLayout stage=new android.widget.FrameLayout(a);stage.setBackground(background());
        android.widget.ScrollView scroll=new android.widget.ScrollView(a);scroll.setFillViewport(false);scroll.addView(layout);
        int width=Math.min(dp(a,560),Math.round(a.getResources().getDisplayMetrics().widthPixels*.72f));
        stage.addView(scroll,new android.widget.FrameLayout.LayoutParams(width,Math.round(a.getResources().getDisplayMetrics().heightPixels*.84f),Gravity.CENTER));a.setContentView(stage);
    }
    static void addAction(Activity a,LinearLayout row,View view){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(a,44),1);p.setMargins(0,dp(a,4),dp(a,8),dp(a,4));row.addView(view,p);}
}
