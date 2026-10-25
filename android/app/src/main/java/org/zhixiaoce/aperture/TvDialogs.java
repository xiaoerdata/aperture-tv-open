package org.zhixiaoce.aperture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Bounded television dialogs. Dimensions are derived from physical window pixels, not assumed density. */
final class TvDialogs {
    private static int px(Activity activity,float dp){return Math.round(dp*activity.getResources().getDisplayMetrics().density);}
    static AlertDialog panel(Activity activity,String title,View body){
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.setPadding(px(activity,14),px(activity,8),px(activity,14),px(activity,8));scroll.addView(body);
        int height=activity.getResources().getDisplayMetrics().heightPixels;
        // Bound scrolling content while retaining the title and return control outside it.
        scroll.setLayoutParams(new android.view.ViewGroup.LayoutParams(-1,Math.round(height*.62f)));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setNegativeButton("返回",null).create();
        dialog.setOnShowListener(d->{Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xff18222c));window.setLayout(Math.round(activity.getResources().getDisplayMetrics().widthPixels*.46f),-2);}scroll.post(()->{android.view.ViewGroup.LayoutParams p=scroll.getLayoutParams();p.height=Math.min(Math.round(height*.62f),Math.max(px(activity,64),body.getMeasuredHeight()+scroll.getPaddingTop()+scroll.getPaddingBottom()));scroll.setLayoutParams(p);});});
        dialog.show();return dialog;
    }
    static AlertDialog menu(Activity activity,String title,String[] options,DialogInterface.OnClickListener action){
        LinearLayout list=new LinearLayout(activity);list.setOrientation(LinearLayout.VERTICAL);
        final AlertDialog[] shown=new AlertDialog[1];
        for(int i=0;i<options.length;i++){
            final int index=i;Button button=new Button(activity);button.setAllCaps(false);button.setText(options[i]);button.setTextColor(Color.WHITE);button.setTextSize(BuildConfig.PARENTS_EDITION?23:21);button.setMinHeight(px(activity,52));button.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);button.setPadding(px(activity,16),px(activity,8),px(activity,16),px(activity,8));
            android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
            for(boolean focused:new boolean[]{true,false}){GradientDrawable background=new GradientDrawable();background.setColor(focused?0xff3d505f:0xff23313d);background.setCornerRadius(px(activity,10));background.setStroke(px(activity,focused?2:1),focused?0xfff5f8fa:0xff5e7180);states.addState(focused?new int[]{android.R.attr.state_focused}:new int[]{},background);}button.setBackground(states);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,i==0?0:Math.max(12,px(activity,6)),0,0);list.addView(button,params);
            button.setOnClickListener(v->{AlertDialog dialog=shown[0];dialog.dismiss();action.onClick(dialog,index);});
        }
        shown[0]=panel(activity,title,list);if(list.getChildCount()>0)list.getChildAt(0).requestFocus();return shown[0];
    }
}
