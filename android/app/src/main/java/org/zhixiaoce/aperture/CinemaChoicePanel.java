package org.zhixiaoce.aperture;

import android.app.Activity;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import java.util.*;

/** In-window remote picker. Selection is explicit, including after touch input. */
final class CinemaChoicePanel {
    interface Select {void select(int index);}
    final LinearLayout panel;
    private final Activity activity;private final List<String> names;private final Select select;private final Runnable dismiss;
    private final ScrollView scroll;private final LinearLayout rows;private final TextView heading;private final String title;private final boolean grouped;
    private final int current;private int selected,held=-1;private boolean rendering;
    CinemaChoicePanel(Activity a,FrameLayout stage,String title,List<String> names,int selected,boolean episodes,Select select,Runnable dismiss){
        activity=a;this.names=names;this.selected=Math.max(0,Math.min(selected,names.size()-1));current=this.selected;this.select=select;this.dismiss=dismiss;this.title=title;grouped=episodes;
        panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(22),dp(20),dp(22),dp(20));panel.setBackground(TvUi.glass(a,false));heading=TvUi.text(a,title,24,false);panel.addView(heading);
        scroll=new ScrollView(a);scroll.setClipToPadding(false);scroll.setPadding(0,dp(12),0,dp(12));scroll.setFadingEdgeLength(0);rows=new LinearLayout(a);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        panel.addView(TvUi.text(a,episodes&&names.size()>20?"上下选集 · 左右换组 · 返回关闭":"上下选择 · 返回关闭",16,true));
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.round(a.getResources().getDisplayMetrics().widthPixels*(episodes?.58f:.42f)),Math.round(a.getResources().getDisplayMetrics().heightPixels*.9f),Gravity.END|Gravity.CENTER_VERTICAL);p.setMargins(dp(18),dp(12),dp(22),dp(12));stage.addView(panel,p);render();
    }
    private int dp(int n){return TvUi.dp(activity,n);}
    private int first(){return grouped?selected/20*20:0;}
    private void render(){rendering=true;rows.removeAllViews();int start=first(),end=grouped?Math.min(start+20,names.size()):names.size();heading.setText(title+(grouped&&names.size()>20?" · "+(start+1)+"–"+end:""));
        for(int i=start;i<end;i++){final int at=i;Button row=TvUi.button(activity,names.get(i)+(grouped&&i==current?"  · 播放中":""),()->select.select(at));row.setSingleLine(false);row.setMaxLines(Integer.MAX_VALUE);row.setEllipsize(null);row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);row.setFocusableInTouchMode(true);row.setMinHeight(dp(64));row.setPadding(dp(16),dp(12),dp(16),dp(12));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(4));rows.addView(row,p);row.setOnFocusChangeListener((v,f)->{v.setScaleX(1);v.setScaleY(1);if(f&&!rendering){selected=at;paint(false);}});}
        rendering=false;paint(true);
    }
    private void paint(boolean focus){int start=first();for(int i=0;i<rows.getChildCount();i++){View row=rows.getChildAt(i);row.setBackground(TvUi.glass(activity,start+i==selected));}View chosen=rows.getChildAt(selected-start);if(chosen!=null&&focus){chosen.requestFocus();chosen.post(()->{if(chosen.getParent()==rows&&rows.indexOfChild(chosen)==selected-first()){chosen.requestFocus();chosen.requestRectangleOnScreen(new Rect(0,0,chosen.getWidth(),chosen.getHeight()+dp(8)),true);if(selected==first())scroll.scrollTo(0,0);}});}}
    void cancelPress(){held=-1;}
    boolean key(KeyEvent e){int code=e.getKeyCode();boolean down=e.getAction()==KeyEvent.ACTION_DOWN;
        if(code==KeyEvent.KEYCODE_BACK){if(!down)dismiss.run();return true;}
        if(code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_NUMPAD_ENTER){if(down&&e.getRepeatCount()==0)held=selected;else if(!down){int at=held;held=-1;if(at>=0&&!e.isCanceled())select.select(at);}return true;}
        if(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN){if(down){int next=Math.max(0,Math.min(names.size()-1,selected+(code==KeyEvent.KEYCODE_DPAD_DOWN?1:-1)));boolean changed=grouped&&next/20!=selected/20;selected=next;if(changed)render();else paint(true);}return true;}
        if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT){if(down&&grouped&&names.size()>20){selected=Math.max(0,Math.min(names.size()-1,(selected/20+(code==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1))*20));render();}return true;}
        return false;
    }
}
