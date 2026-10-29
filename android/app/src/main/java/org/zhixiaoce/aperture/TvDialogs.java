package org.zhixiaoce.aperture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Bounded television dialogs. Dimensions are derived from physical window pixels, not assumed density. */
final class TvDialogs {
    private static int px(Activity activity,float dp){return Math.round(dp*activity.getResources().getDisplayMetrics().density);}
    static AlertDialog panel(Activity activity,String title,View body){
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.setPadding(px(activity,14),0,px(activity,14),0);scroll.setFadingEdgeLength(0);scroll.setVerticalFadingEdgeEnabled(false);body.setPadding(body.getPaddingLeft(),body.getPaddingTop()+px(activity,8),body.getPaddingRight(),body.getPaddingBottom()+px(activity,20));scroll.addView(body);
        int height=activity.getResources().getDisplayMetrics().heightPixels;
        android.widget.FrameLayout bounded=new android.widget.FrameLayout(activity);bounded.addView(scroll,new android.widget.FrameLayout.LayoutParams(-1,Math.round(height*.60f)));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(title).setView(bounded).setNegativeButton("返回",null).create();
        dialog.setOnShowListener(d->{TvUi.styleButton(activity,dialog.getButton(DialogInterface.BUTTON_NEGATIVE));Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(TvUi.glass(activity,false));window.setLayout(Math.round(activity.getResources().getDisplayMetrics().widthPixels*.64f),-2);}});
        dialog.show();return dialog;
    }
    static AlertDialog menu(Activity activity,String title,String[] options,DialogInterface.OnClickListener action){
        LinearLayout list=new LinearLayout(activity);list.setOrientation(LinearLayout.VERTICAL);
        final AlertDialog[] shown=new AlertDialog[1];
        for(int i=0;i<options.length;i++){
            final int index=i;Button button=TvUi.button(activity,options[i],()->{});button.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,i==0?0:px(activity,4),0,0);list.addView(button,params);
            button.setOnClickListener(v->{AlertDialog dialog=shown[0];dialog.dismiss();action.onClick(dialog,index);});
        }
        shown[0]=panel(activity,title,list);if(list.getChildCount()>0)list.getChildAt(0).requestFocus();return shown[0];
    }
}
