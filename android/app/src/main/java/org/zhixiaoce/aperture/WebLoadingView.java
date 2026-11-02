package org.zhixiaoce.aperture;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Native status and escape action remain available before any JavaScript is ready. */
final class WebLoadingView {
    final FrameLayout root;private final LinearLayout cover;private final WebView web;
    WebLoadingView(Activity activity,WebView web,String title,String subtitle,String action,Runnable escape){
        this.web=web;root=new FrameLayout(activity);root.setBackgroundColor(Color.rgb(8,15,21));
        web.setBackgroundColor(Color.rgb(8,15,21));root.addView(web,new FrameLayout.LayoutParams(-1,-1));
        cover=new LinearLayout(activity);cover.setTag("kaishi-web-loading");cover.setOrientation(LinearLayout.VERTICAL);cover.setGravity(Gravity.CENTER_VERTICAL);
        int pad=TvUi.dp(activity,28);cover.setPadding(pad,pad,pad,pad);cover.setBackground(TvUi.glass(activity,false));
        cover.addView(TvUi.text(activity,title,30,false));
        android.widget.TextView note=TvUi.text(activity,subtitle,20,true);note.setPadding(0,TvUi.dp(activity,14),0,TvUi.dp(activity,20));cover.addView(note);
        Button exit=TvUi.button(activity,action,escape);cover.addView(exit,new LinearLayout.LayoutParams(-1,-2));
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.min(TvUi.dp(activity,620),Math.round(activity.getResources().getDisplayMetrics().widthPixels*.78f)),-2,Gravity.CENTER);
        root.addView(cover,p);exit.requestFocus();
    }
    void ready(){if(cover.getParent()==root)root.removeView(cover);web.requestFocus();}
}
