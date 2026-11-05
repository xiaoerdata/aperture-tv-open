package org.zhixiaoce.aperture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.*;
import android.widget.*;

/** A single bounded window: heading, scrollable content and exit never compete for extra height. */
final class TvDialogs {
    private static int px(Activity a,float dp){return TvUi.dp(a,dp);}
    /** Settings lists use the right edge; destructive confirmations keep their own layout. */
    static final class SideBuilder extends AlertDialog.Builder {
        private final Activity owner;
        SideBuilder(Activity owner){super(owner);this.owner=owner;}
        @Override public AlertDialog show(){AlertDialog dialog=super.show();Window w=dialog.getWindow();if(w!=null){w.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);WindowManager.LayoutParams p=w.getAttributes();p.x=Math.round(TvUi.viewportWidth(owner)*.025f);w.setAttributes(p);w.setLayout(Math.round(TvUi.viewportWidth(owner)*.34f),dialog.getListView()!=null&&dialog.getListView().getCount()>8?Math.round(TvUi.viewportHeight(owner)*.9f):WindowManager.LayoutParams.WRAP_CONTENT);w.getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(()->compactText(owner,w.getDecorView()));}return dialog;}
    }
    private static void compactText(Activity owner,View view){
        if(view instanceof TextView){TextView text=(TextView)view;float size=17*TvUi.density(owner)*owner.getResources().getConfiguration().fontScale;if(Math.abs(text.getTextSize()-size)>.5f){text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);text.setMinHeight(px(owner,36));text.setMinimumHeight(px(owner,36));text.setPadding(px(owner,12),px(owner,8),px(owner,12),px(owner,8));}}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)compactText(owner,group.getChildAt(i));}
    }
    static AlertDialog panel(Activity a,String title,View body){return panel(a,title,body,a.getCurrentFocus());}
    static AlertDialog panel(Activity a,String title,View body,View returnFocus){
        final android.os.IBinder returnWindow=returnFocus==null?null:returnFocus.getWindowToken();
        LinearLayout sheet=new LinearLayout(a);sheet.setOrientation(LinearLayout.VERTICAL);sheet.setPadding(px(a,18),px(a,14),px(a,18),px(a,14));sheet.setBackground(TvUi.glass(a,false));
        TextView heading=TvUi.text(a,title,27,false);heading.setPadding(px(a,8),px(a,4),px(a,8),px(a,14));sheet.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new RemoteScrollView(a);scroll.setFillViewport(false);scroll.setClipToPadding(false);scroll.setPadding(px(a,10),px(a,10),px(a,10),px(a,16));scroll.setFadingEdgeLength(0);scroll.setVerticalFadingEdgeEnabled(false);scroll.addView(body);sheet.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        AlertDialog dialog=new AlertDialog.Builder(a).create();Button exit=TvUi.button(a,"返回",dialog::dismiss);LinearLayout.LayoutParams bottom=new LinearLayout.LayoutParams(-1,-2);bottom.topMargin=px(a,10);sheet.addView(exit,bottom);dialog.setView(sheet,0,0,0,0);
        dialog.setOnShowListener(d->{Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);int width=TvUi.viewportWidth(a),height=TvUi.viewportHeight(a);WindowManager.LayoutParams p=w.getAttributes();p.x=Math.round(width*.025f);p.dimAmount=.32f;w.setAttributes(p);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setLayout(Math.round(width*.34f),Math.round(height*.9f));}sheet.post(()->{View first=body.findFocus();if(first==null){body.requestFocus(View.FOCUS_DOWN);first=body.findFocus();}if(first!=null)first.requestRectangleOnScreen(new android.graphics.Rect(0,0,first.getWidth(),first.getHeight()),true);scroll.scrollTo(0,0);});});
        dialog.setOnDismissListener(d->{if(returnFocus!=null)returnFocus.post(()->{if(!a.isFinishing()&&!a.isDestroyed()&&returnFocus.isAttachedToWindow()&&returnFocus.isShown()&&returnFocus.isFocusable()&&returnFocus.getWindowToken()==returnWindow)returnFocus.requestFocus();});});
        dialog.show();return dialog;
    }
    static AlertDialog menu(Activity a,String title,String[] options,DialogInterface.OnClickListener action){return menu(a,title,options,action,-1);}
    static AlertDialog menu(Activity a,String title,String[] options,DialogInterface.OnClickListener action,int keepOpenAt){
        LinearLayout list=new LinearLayout(a);list.setOrientation(LinearLayout.VERTICAL);final AlertDialog[] shown=new AlertDialog[1];
        for(int i=0;i<options.length;i++){final int at=i;Button b=TvUi.button(a,options[i],()->{AlertDialog d=shown[0];if(at!=keepOpenAt)d.dismiss();action.onClick(d,at);});b.setTag("menu-entry-"+at);b.setFocusableInTouchMode(true);b.setSingleLine(false);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setOnFocusChangeListener((v,f)->{v.setScaleX(1);v.setScaleY(1);});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,px(a,4),0,px(a,4));list.addView(b,p);}
        shown[0]=panel(a,title,list);return shown[0];
    }
}
