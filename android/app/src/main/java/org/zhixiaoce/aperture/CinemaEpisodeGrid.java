package org.zhixiaoce.aperture;

import android.app.Activity;
import android.view.*;
import android.widget.*;
import java.util.List;
import java.util.ArrayList;

/** Two columns with full titles, equal row heights and an explicit return to Play. */
final class CinemaEpisodeGrid extends LinearLayout {
    interface Play {void play(int line,int episode);}
    interface Selection {void select(int line,int episode);}
    private static final int GROUP_SIZE=20;
    private final Activity activity;private final List<CinemaCatalog.Line> lines;private final Play play;private final Selection selection;private final View primary;
    private int source,group,episode;
    CinemaEpisodeGrid(Activity a,List<CinemaCatalog.Line> lines,int source,int episode,View primary,Selection selection,Play play){
        super(a);activity=a;this.lines=lines;this.source=source;this.episode=episode;group=episode/GROUP_SIZE;this.primary=primary;this.selection=selection;this.play=play;setOrientation(VERTICAL);render(false);
    }
    private void upToPlay(View v){v.setOnKeyListener((n,key,e)->{if(key==KeyEvent.KEYCODE_DPAD_UP&&primary!=null){if(e.getAction()==KeyEvent.ACTION_DOWN)primary.requestFocus();return true;}return false;});}
    private void render(boolean focus){
        removeAllViews();if(lines.isEmpty())return;List<View> navigation=new ArrayList<>();View selectedNavigation=null;
        if(lines.size()>1){HorizontalScrollView scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(activity);scroll.addView(row);addView(scroll);
            for(int i=0;i<lines.size();i++){final int at=i;Button b=episodeButton("线路"+(i+1),()->{source=at;episode=0;group=0;selection.select(source,episode);render(true);});b.setBackground(TvUi.states(activity));b.setTextColor(source==i?0xffe8d4ae:TvUi.INK);b.setSelected(source==i);b.setContentDescription("线路"+(i+1)+(source==i?"，当前":""));navigation.add(b);if(source==i)selectedNavigation=b;upToPlay(b);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(TvUi.dp(activity,140),-2);p.setMargins(0,0,TvUi.dp(activity,8),TvUi.dp(activity,8));row.addView(b,p);}}
        CinemaCatalog.Line line=lines.get(source);int groups=Math.max(1,(line.episodes.size()+GROUP_SIZE-1)/GROUP_SIZE);group=Math.max(0,Math.min(group,groups-1));
        if(groups>1){LinearLayout bar=new LinearLayout(activity);bar.setGravity(Gravity.CENTER_VERTICAL);addView(bar);
            Button prev=TvUi.button(activity,"上一组",()->{group--;render(true);});prev.setEnabled(group>0);prev.setAlpha(group>0?1:.4f);navigation.add(prev);if(selectedNavigation==null&&group>0)selectedNavigation=prev;upToPlay(prev);bar.addView(prev,new LinearLayout.LayoutParams(TvUi.dp(activity,110),-2));
            TextView range=TvUi.text(activity,(group*GROUP_SIZE+1)+"–"+Math.min(line.episodes.size(),(group+1)*GROUP_SIZE)+" / "+line.episodes.size(),19,true);range.setGravity(Gravity.CENTER);bar.addView(range,new LinearLayout.LayoutParams(0,-2,1));
            Button next=TvUi.button(activity,"下一组",()->{group++;render(true);});next.setEnabled(group+1<groups);next.setAlpha(group+1<groups?1:.4f);navigation.add(next);if(selectedNavigation==null&&group+1<groups)selectedNavigation=next;upToPlay(next);bar.addView(next,new LinearLayout.LayoutParams(TvUi.dp(activity,110),-2));}
        GridLayout grid=new GridLayout(activity);grid.setColumnCount(2);grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);addView(grid,new LinearLayout.LayoutParams(-1,-2));View target=null;
        int start=group*GROUP_SIZE,end=Math.min(line.episodes.size(),start+GROUP_SIZE);
        for(int i=start;i<end;i++){final int at=i;Button b=episodeButton(line.episodes.get(i).name,()->{episode=at;selection.select(source,at);play.play(source,at);});if(target==null||i==episode)target=b;
            if(i-start<2)upToPlay(b);GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.columnSpec=GridLayout.spec((i-start)%2,GridLayout.FILL,1f);p.rowSpec=GridLayout.spec((i-start)/2,GridLayout.FILL);p.width=0;p.height=-2;p.setMargins(TvUi.dp(activity,4),TvUi.dp(activity,5),TvUi.dp(activity,4),TvUi.dp(activity,5));grid.addView(b,p);
        }
        if(target!=null){View dest=target;for(View nav:navigation)nav.setOnKeyListener((v,key,e)->{if(key==KeyEvent.KEYCODE_DPAD_UP&&primary!=null){if(e.getAction()==KeyEvent.ACTION_DOWN)primary.requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(e.getAction()==KeyEvent.ACTION_DOWN)dest.requestFocus();return true;}return false;});}
        if(primary!=null&&target!=null){View dest=selectedNavigation!=null?selectedNavigation:target;primary.setOnKeyListener((v,key,e)->{if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(e.getAction()==KeyEvent.ACTION_DOWN)dest.requestFocus();return true;}return false;});}
        if(focus&&target!=null){View dest=target;dest.post(()->{dest.requestFocus();dest.requestRectangleOnScreen(new android.graphics.Rect(0,0,dest.getWidth(),dest.getHeight()));});}
    }
    private Button episodeButton(String title,Runnable action){Button b=TvUi.button(activity,title,action);b.setFocusableInTouchMode(true);b.setSingleLine(false);b.setMaxLines(Integer.MAX_VALUE);b.setEllipsize(null);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setMinHeight(TvUi.dp(activity,64));b.setPadding(TvUi.dp(activity,14),TvUi.dp(activity,12),TvUi.dp(activity,14),TvUi.dp(activity,12));b.setOnFocusChangeListener((v,f)->{v.setScaleX(1);v.setScaleY(1);});return b;}
}
