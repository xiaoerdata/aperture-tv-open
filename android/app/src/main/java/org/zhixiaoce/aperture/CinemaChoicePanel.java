package org.zhixiaoce.aperture;

import android.app.Activity;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Bounded playback drawer: compact episode numbers; complete wrapping for descriptive titles. */
final class CinemaChoicePanel {
    interface Select {void select(int index);}
    final LinearLayout panel;
    private final Activity activity;private final List<String> names;private final Select select;private final Runnable dismiss;
    private final ScrollView scroll;private final GridLayout rows;private final TextView heading;private final String title;private final boolean grouped,compact;private final int columns;
    private final int current;private int selected,held=-1;private boolean rendering;
    CinemaChoicePanel(Activity a,FrameLayout stage,String title,List<String> names,int selected,boolean episodes,Select select,Runnable dismiss){
        activity=a;this.names=names;this.selected=Math.max(0,Math.min(selected,names.size()-1));current=this.selected;this.select=select;this.dismiss=dismiss;this.title=title;grouped=episodes;
        compact=episodes&&shortNames(names);columns=compact?3:1;
        panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(px(20),px(18),px(20),px(18));panel.setBackground(TvUi.glass(a,false));heading=text(title,30);panel.addView(heading);
        scroll=new ScrollView(a);scroll.setClipToPadding(false);scroll.setPadding(0,px(10),0,px(10));scroll.setFadingEdgeLength(0);rows=new GridLayout(a);rows.setColumnCount(columns);rows.setAlignmentMode(GridLayout.ALIGN_BOUNDS);scroll.addView(rows,new ScrollView.LayoutParams(-1,-2));panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        panel.addView(text(episodes?(compact?"方向键选集 · 确认播放 · 返回关闭":"上下选集 · 左右换组 · 返回关闭"):"上下选择 · 返回关闭",22));
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.round(a.getResources().getDisplayMetrics().widthPixels*(episodes?.33f:.36f)),Math.round(a.getResources().getDisplayMetrics().heightPixels*.90f),Gravity.END|Gravity.CENTER_VERTICAL);p.setMargins(px(18),px(12),px(22),px(12));stage.addView(panel,p);panel.setContentDescription(episodes?"选集面板":"播放菜单");render();
    }
    static boolean shortNames(List<String> names){for(String name:names)if(name.length()>8||name.contains("\n"))return false;return true;}
    private int px(float n){return Math.max(1,Math.round(n*activity.getResources().getDisplayMetrics().heightPixels/1080f));}
    private TextView text(String value,int size){TextView t=TvUi.text(activity,value,20,true);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));return t;}
    private int first(){return grouped?selected/20*20:0;}
    private void render(){rendering=true;rows.removeAllViews();int start=first(),end=grouped?Math.min(start+20,names.size()):names.size();heading.setText(title+(grouped&&names.size()>20?" · "+(start+1)+"–"+end:""));
        for(int i=start;i<end;i++){final int at=i;Button row=TvUi.button(activity,names.get(i)+(grouped&&i==current?(compact?" ▶":"  · 播放中"):""),()->select.select(at));row.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(BuildConfig.PARENTS_EDITION?29:27));row.setSingleLine(false);row.setMaxLines(Integer.MAX_VALUE);row.setEllipsize(null);row.setIncludeFontPadding(false);row.setGravity((compact?Gravity.CENTER:Gravity.START)|Gravity.CENTER_VERTICAL);row.setFocusableInTouchMode(true);row.setMinHeight(px(88));row.setMinimumHeight(px(88));row.setMinWidth(0);row.setMinimumWidth(0);row.setPadding(px(12),px(10),px(12),px(10));row.setContentDescription(names.get(i)+(grouped&&i==current?"，播放中":""));
            GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.columnSpec=GridLayout.spec((i-start)%columns,GridLayout.FILL,1f);p.rowSpec=GridLayout.spec((i-start)/columns,GridLayout.FILL);p.width=0;p.height=compact?px(96):-2;p.setMargins(px(3),px(4),px(3),px(4));rows.addView(row,p);row.setOnFocusChangeListener((v,f)->{v.setScaleX(1);v.setScaleY(1);if(f&&!rendering){selected=at;paint(false);}});}
        rendering=false;paint(true);
    }
    private void paint(boolean focus){int start=first();for(int i=0;i<rows.getChildCount();i++)rows.getChildAt(i).setBackground(TvUi.glass(activity,start+i==selected));View chosen=rows.getChildAt(selected-start);if(chosen!=null&&focus){chosen.requestFocus();chosen.post(()->{if(chosen.getParent()==rows&&rows.indexOfChild(chosen)==selected-first()){chosen.requestFocus();chosen.requestRectangleOnScreen(new Rect(0,0,chosen.getWidth(),chosen.getHeight()+px(8)),true);if(selected==first())scroll.scrollTo(0,0);}});}}
    void cancelPress(){held=-1;}
    boolean key(KeyEvent e){int code=e.getKeyCode();boolean down=e.getAction()==KeyEvent.ACTION_DOWN;
        if(code==KeyEvent.KEYCODE_BACK){if(!down)dismiss.run();return true;}
        if(code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_NUMPAD_ENTER){if(down&&e.getRepeatCount()==0)held=selected;else if(!down){int at=held;held=-1;if(at>=0&&!e.isCanceled())select.select(at);}return true;}
        int delta=code==KeyEvent.KEYCODE_DPAD_UP?-columns:code==KeyEvent.KEYCODE_DPAD_DOWN?columns:compact&&code==KeyEvent.KEYCODE_DPAD_LEFT?-1:compact&&code==KeyEvent.KEYCODE_DPAD_RIGHT?1:0;
        if(delta!=0){if(down){int next=Math.max(0,Math.min(names.size()-1,selected+delta));boolean changed=grouped&&next/20!=selected/20;selected=next;if(changed)render();else paint(true);}return true;}
        if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT){if(down&&grouped&&names.size()>20){selected=Math.max(0,Math.min(names.size()-1,(selected/20+(code==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1))*20));render();}return true;}return false;
    }
}
