package org.zhixiaoce.aperture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.*;
import android.widget.*;

/** A single bounded window: heading, scrollable content and exit never compete for extra height. */
final class TvDialogs {
    private static int px(Activity a,float dp){return TvUi.dp(a,dp);}
    static AlertDialog panel(Activity a,String title,View body){
        LinearLayout sheet=new LinearLayout(a);sheet.setOrientation(LinearLayout.VERTICAL);sheet.setPadding(px(a,18),px(a,14),px(a,18),px(a,14));sheet.setBackground(TvUi.glass(a,false));
        TextView heading=TvUi.text(a,title,27,false);heading.setPadding(px(a,8),px(a,4),px(a,8),px(a,14));sheet.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);scroll.setClipToPadding(false);scroll.setPadding(px(a,10),px(a,10),px(a,10),px(a,16));scroll.setFadingEdgeLength(0);scroll.setVerticalFadingEdgeEnabled(false);scroll.addView(body);sheet.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        AlertDialog dialog=new AlertDialog.Builder(a).create();Button exit=TvUi.button(a,"返回",dialog::dismiss);LinearLayout.LayoutParams bottom=new LinearLayout.LayoutParams(-1,-2);bottom.topMargin=px(a,10);sheet.addView(exit,bottom);dialog.setView(sheet,0,0,0,0);
        dialog.setOnShowListener(d->{Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(Math.round(a.getResources().getDisplayMetrics().widthPixels*.64f),Math.round(a.getResources().getDisplayMetrics().heightPixels*.88f));}sheet.post(()->{View first=body.findFocus();if(first==null){body.requestFocus(View.FOCUS_DOWN);first=body.findFocus();}if(first!=null)first.requestRectangleOnScreen(new android.graphics.Rect(0,0,first.getWidth(),first.getHeight()),true);scroll.scrollTo(0,0);});});
        dialog.show();return dialog;
    }
    static AlertDialog menu(Activity a,String title,String[] options,DialogInterface.OnClickListener action){
        LinearLayout list=new LinearLayout(a);list.setOrientation(LinearLayout.VERTICAL);final AlertDialog[] shown=new AlertDialog[1];
        for(int i=0;i<options.length;i++){final int at=i;Button b=TvUi.button(a,options[i],()->{AlertDialog d=shown[0];d.dismiss();action.onClick(d,at);});b.setFocusableInTouchMode(true);b.setSingleLine(false);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setOnFocusChangeListener((v,f)->{v.setScaleX(1);v.setScaleY(1);});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,px(a,4),0,px(a,4));list.addView(b,p);}
        shown[0]=panel(a,title,list);return shown[0];
    }
}
