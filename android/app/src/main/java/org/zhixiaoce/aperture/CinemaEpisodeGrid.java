package org.zhixiaoce.aperture;

import android.app.Activity;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Full episode titles; remote Down appends more without replacing existing cards. */
final class CinemaEpisodeGrid extends LinearLayout {
    interface Play {void play(int line,int episode);}
    interface Selection {void select(int line,int episode);}
    private static final int BATCH_SIZE=20;
    private final Activity activity;private final List<CinemaCatalog.Line> lines;private final Play play;private final Selection selection;private final View primary;
    private final TreeMap<Integer,Button> cards=new TreeMap<>();private GridLayout grid;private TextView range;private int source,episode,start,end,columns,focusEpoch;
    CinemaEpisodeGrid(Activity a,List<CinemaCatalog.Line> lines,int source,int episode,View primary,Selection selection,Play play){
        super(a);activity=a;this.lines=lines;this.source=source;this.episode=episode;this.primary=primary;this.selection=selection;this.play=play;setOrientation(VERTICAL);render(false);
    }
    private void render(boolean focus){
        focusEpoch++;removeAllViews();cards.clear();if(lines.isEmpty())return;source=Math.max(0,Math.min(source,lines.size()-1));CinemaCatalog.Line line=lines.get(source);episode=Math.max(0,Math.min(episode,line.episodes.size()-1));List<View> navigation=new ArrayList<>();View selectedNavigation=null;
        if(lines.size()>1){HorizontalScrollView scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(activity);scroll.addView(row);addView(scroll);
            for(int i=0;i<lines.size();i++){final int at=i;Button b=episodeButton("线路"+(i+1),()->{source=at;episode=0;selection.select(source,episode);render(true);});b.setBackground(TvUi.states(activity));b.setTextColor(source==i?0xffe8d4ae:TvUi.INK);b.setSelected(source==i);b.setContentDescription("线路"+(i+1)+(source==i?"，当前":""));navigation.add(b);if(source==i)selectedNavigation=b;LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(TvUi.dp(activity,140),-2);p.setMargins(0,0,TvUi.dp(activity,8),TvUi.dp(activity,8));row.addView(b,p);}}
        range=TvUi.text(activity,"",19,true);addView(range);
        grid=new GridLayout(activity);columns=CinemaChoicePanel.shortNames(line.episodes.stream().map(e->e.name).collect(java.util.stream.Collectors.toList()))?4:2;grid.setColumnCount(columns);grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);addView(grid,new LinearLayout.LayoutParams(-1,-2));start=episode/BATCH_SIZE*BATCH_SIZE;end=start;append(Math.min(line.episodes.size(),start+BATCH_SIZE));
        for(View nav:navigation)nav.setOnKeyListener((v,key,e)->{if(key==KeyEvent.KEYCODE_DPAD_UP&&primary!=null){if(e.getAction()==KeyEvent.ACTION_DOWN)primary.requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(e.getAction()==KeyEvent.ACTION_DOWN)focus(episode);return true;}return false;});
        if(primary!=null&&!cards.isEmpty()){final View nav=selectedNavigation;primary.setOnKeyListener((v,key,e)->{if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(e.getAction()==KeyEvent.ACTION_DOWN){if(nav!=null)nav.requestFocus();else focus(episode);}return true;}return false;});}
        if(focus&&!cards.isEmpty())focus(episode);
    }
    private void append(int until){for(int at=end;at<until;at++)add(at,false);end=until;range();}
    private void prepend(){int before=Math.max(0,start-BATCH_SIZE);for(int at=start-1;at>=before;at--)add(at,true);start=before;for(Map.Entry<Integer,Button> entry:cards.entrySet())entry.getValue().setLayoutParams(position(entry.getKey()));range();}
    private void range(){range.setText(cards.isEmpty()?"暂无集数":(start+1)+"–"+end+" / "+lines.get(source).episodes.size());}
    private GridLayout.LayoutParams position(int at){GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.columnSpec=GridLayout.spec((at-start)%columns,GridLayout.FILL,1f);p.rowSpec=GridLayout.spec((at-start)/columns,GridLayout.FILL);p.width=0;p.height=-2;p.setMargins(TvUi.dp(activity,4),TvUi.dp(activity,5),TvUi.dp(activity,4),TvUi.dp(activity,5));return p;}
    private void add(int at,boolean before){
        Button b=episodeButton(lines.get(source).episodes.get(at).name,()->{episode=at;selection.select(source,at);play.play(source,at);});cards.put(at,b);grid.addView(b,before?0:grid.getChildCount(),position(Math.max(start,at)));
        b.setOnFocusChangeListener((v,f)->{v.setScaleX(1);v.setScaleY(1);if(f){episode=at;selection.select(source,at);}});
        b.setOnKeyListener((v,key,e)->{
            if(key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
            if(e.getAction()==KeyEvent.ACTION_DOWN){int next=at+(key==KeyEvent.KEYCODE_DPAD_DOWN?columns:-columns);if(next<0){if(primary!=null)primary.requestFocus();return true;}next=Math.min(lines.get(source).episodes.size()-1,next);if(next<start)prepend();if(next>=end)append(Math.min(lines.get(source).episodes.size(),end+BATCH_SIZE));focus(next);}return true;
        });
    }
    private void focus(int at){Button target=cards.get(at);if(target==null)return;episode=at;final int turn=++focusEpoch;target.requestFocus();target.post(()->{if(turn==focusEpoch&&target.getParent()==grid){target.requestFocus();target.requestRectangleOnScreen(new android.graphics.Rect(0,0,target.getWidth(),target.getHeight()));}});}
    void restoreFocus(){focus(episode);}
    private Button episodeButton(String title,Runnable action){Button b=TvUi.button(activity,title,action);b.setFocusableInTouchMode(true);b.setSingleLine(false);b.setMaxLines(Integer.MAX_VALUE);b.setEllipsize(null);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setMinHeight(TvUi.dp(activity,64));b.setPadding(TvUi.dp(activity,14),TvUi.dp(activity,12),TvUi.dp(activity,14),TvUi.dp(activity,12));return b;}
}
