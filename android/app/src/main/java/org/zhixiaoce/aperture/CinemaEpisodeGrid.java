package org.zhixiaoce.aperture;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.List;

/** Inline source/episode selection, full original titles and bounded groups. */
final class CinemaEpisodeGrid extends LinearLayout {
    interface Play { void play(CinemaCatalog.Line line, int episode); }
    private static final int GROUP_SIZE = 20;
    private final Activity activity;
    private final List<CinemaCatalog.Line> lines;
    private final Play play;
    private int source, group;
    CinemaEpisodeGrid(Activity activity,List<CinemaCatalog.Line> lines,Play play) {
        super(activity);this.activity=activity;this.lines=lines;this.play=play;
        setOrientation(VERTICAL);render(false);
    }
    private void render(boolean focus) {
        removeAllViews();if(lines.isEmpty())return;
        if(lines.size()>1){HorizontalScrollView scroll=new HorizontalScrollView(activity);LinearLayout row=new LinearLayout(activity);scroll.addView(row);addView(scroll);
            for(int i=0;i<lines.size();i++){final int at=i;Button b=episodeButton(lines.get(i).name,()->{source=at;group=0;render(true);});b.setBackground(TvUi.glass(activity,source==i));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(TvUi.dp(activity,180),-2);p.setMargins(0,0,TvUi.dp(activity,8),TvUi.dp(activity,8));row.addView(b,p);}}
        CinemaCatalog.Line line=lines.get(source);int groups=Math.max(1,(line.episodes.size()+GROUP_SIZE-1)/GROUP_SIZE);group=Math.max(0,Math.min(group,groups-1));
        if(groups>1){LinearLayout bar=new LinearLayout(activity);bar.setGravity(Gravity.CENTER_VERTICAL);addView(bar);
            Button prev=TvUi.button(activity,"上一组",()->{group--;render(true);});prev.setEnabled(group>0);bar.addView(prev,new LinearLayout.LayoutParams(TvUi.dp(activity,110),-2));
            TextView range=TvUi.text(activity,(group*GROUP_SIZE+1)+"–"+Math.min(line.episodes.size(),(group+1)*GROUP_SIZE)+" / "+line.episodes.size(),19,true);range.setGravity(Gravity.CENTER);bar.addView(range,new LinearLayout.LayoutParams(0,-2,1));
            Button next=TvUi.button(activity,"下一组",()->{group++;render(true);});next.setEnabled(group+1<groups);bar.addView(next,new LinearLayout.LayoutParams(TvUi.dp(activity,110),-2));}
        LinearLayout grid=new LinearLayout(activity);grid.setOrientation(VERTICAL);addView(grid,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout row=null;Button first=null;
        for(int i=group*GROUP_SIZE;i<Math.min(line.episodes.size(),(group+1)*GROUP_SIZE);i++){
            final int at=i;if((i-group*GROUP_SIZE)%2==0){row=new LinearLayout(activity);row.setBaselineAligned(false);grid.addView(row,new LinearLayout.LayoutParams(-1,-2));}
            Button b=episodeButton(line.episodes.get(i).name,()->play.play(line,at));if(first==null)first=b;LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(TvUi.dp(activity,4),TvUi.dp(activity,5),TvUi.dp(activity,4),TvUi.dp(activity,5));row.addView(b,p);
            if(i==line.episodes.size()-1&&(i-group*GROUP_SIZE)%2==0)row.addView(new View(activity),new LinearLayout.LayoutParams(0,1,1));
        }
        if(focus&&first!=null){Button target=first;target.post(()->{target.requestFocus();target.requestRectangleOnScreen(new android.graphics.Rect(0,0,target.getWidth(),target.getHeight()));});}
    }
    private Button episodeButton(String title,Runnable action) {
        Button b=TvUi.button(activity,title,action);b.setFocusableInTouchMode(true);b.setSingleLine(false);b.setMaxLines(Integer.MAX_VALUE);b.setEllipsize(null);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setMinHeight(TvUi.dp(activity,64));b.setPadding(TvUi.dp(activity,14),TvUi.dp(activity,12),TvUi.dp(activity,14),TvUi.dp(activity,12));return b;
    }
}
