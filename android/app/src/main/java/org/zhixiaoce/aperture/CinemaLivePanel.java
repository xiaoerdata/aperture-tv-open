package org.zhixiaoce.aperture;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Same-window channel picker; browsing sources never changes the playing decoder. */
final class CinemaLivePanel implements AutoCloseable {
    interface Select {void select(CinemaCatalog.Live source,List<CinemaCatalog.Channel> channels,int index);}
    private final Activity activity;private final List<CinemaCatalog.Live> sources;private final CinemaCatalog.Live playingSource;private final List<CinemaCatalog.Channel> playingChannels;
    private List<CinemaCatalog.Channel> channels=new ArrayList<>();private final List<String> groups=new ArrayList<>();private final List<Integer> visible=new ArrayList<>();private int[] numbers=new int[0];
    private final Select select;private final Runnable dismissed;private final LinearLayout panel,rows;private final TextView heading,preview,digits;private final ImageView logo;private final CinemaArtwork artwork;private final Handler main=new Handler(Looper.getMainLooper());
    private final CinemaHttp http=new CinemaHttp();private final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>(1));
    private final boolean allowHttp;private CinemaEpg guide=new CinemaEpg();private volatile int loadEpoch;private volatile boolean closed;private int sourceIndex,group,selected,renderEpoch;private String entered="";private boolean rendering,confirmHeld,loading;private int confirmSelection,confirmEpoch;
    private int groupKey=-1;private long groupDownTime=-1;
    private final Runnable tune=()->tune();
    CinemaLivePanel(Activity a,FrameLayout stage,List<CinemaCatalog.Live> sources,CinemaCatalog.Live current,List<CinemaCatalog.Channel> channels,CinemaArtwork artwork,boolean allowHttp,Select select,Runnable dismissed){
        activity=a;this.allowHttp=allowHttp;this.sources=new ArrayList<>(sources);if(!this.sources.contains(current))this.sources.add(current);playingSource=current;playingChannels=new ArrayList<>(channels);this.artwork=artwork;this.select=select;this.dismissed=dismissed;
        panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setFocusableInTouchMode(true);panel.setPadding(TvUi.dp(a,16),TvUi.dp(a,18),TvUi.dp(a,16),TvUi.dp(a,18));android.graphics.drawable.GradientDrawable glass=TvUi.glass(a,false);glass.setAlpha(235);panel.setBackground(glass);stage.addView(panel,new FrameLayout.LayoutParams(Math.round(a.getResources().getDisplayMetrics().widthPixels*.34f),-1,Gravity.LEFT));panel.setVisibility(View.GONE);
        heading=TvUi.text(a,"",24,false);heading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,34*a.getResources().getDisplayMetrics().heightPixels/1080f);heading.setMaxLines(2);panel.addView(heading);preview=TvUi.text(a,"",19,true);preview.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,26*a.getResources().getDisplayMetrics().heightPixels/1080f);preview.setMaxLines(3);logo=new ImageView(a);logo.setScaleType(ImageView.ScaleType.FIT_START);panel.addView(logo,new LinearLayout.LayoutParams(TvUi.dp(a,80),TvUi.dp(a,36)));panel.addView(preview);
        ScrollView scroll=new ScrollView(a);rows=new LinearLayout(a);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));panel.addView(TvUi.text(a,"左右分组 · 上下选择 · 确定换台",16,true));
        digits=TvUi.text(a,"",32,false);digits.setPadding(TvUi.dp(a,12),TvUi.dp(a,8),TvUi.dp(a,12),TvUi.dp(a,8));digits.setBackground(TvUi.glass(a,false));digits.setVisibility(View.GONE);FrameLayout.LayoutParams dp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);dp.setMargins(TvUi.dp(a,30),TvUi.dp(a,25),0,0);stage.addView(digits,dp);
    }
    void guide(CinemaEpg value){guide=value;if(open()&&!loading&&selected>=0)updatePreview();}
    void cancelPress(){confirmHeld=false;groupKey=-1;groupDownTime=-1;}
    boolean open(){return !closed&&panel.getVisibility()==View.VISIBLE;}
    void showChannel(CinemaCatalog.Channel current){
        if(closed||playingChannels.isEmpty())return;cancelLoad();cancelPress();clearDigits();sourceIndex=sources.indexOf(playingSource);panel.setVisibility(View.VISIBLE);accept(playingChannels,false,Math.max(0,playingChannels.indexOf(current)));
    }
    void hide(){if(confirmHeld)return;cancelLoad();cancelPress();clearDigits();if(panel.getVisibility()!=View.VISIBLE)return;panel.setVisibility(View.GONE);dismissed.run();}
    private void clearDigits(){entered="";digits.setVisibility(View.GONE);main.removeCallbacks(tune);}
    private void cancelLoad(){loadEpoch++;loading=false;http.cancel();io.getQueue().clear();}
    private void accept(List<CinemaCatalog.Channel> values,boolean last,int wanted){
        channels=new ArrayList<>(values);groups.clear();for(CinemaCatalog.Channel c:channels)if(!groups.contains(c.group))groups.add(c.group);numbers=CinemaChannelNumbers.assign(channels);loading=false;
        if(channels.isEmpty()){selected=-1;message("此组暂无频道");return;}
        selected=Math.max(0,Math.min(wanted,channels.size()-1));group=last?groups.size()-1:groups.indexOf(channels.get(selected).group);render();
    }
    private String sourceName(){return sources.get(sourceIndex).name;}
    private void message(String text){renderEpoch++;rows.removeAllViews();heading.setText("‹  "+sourceName()+"  ›");preview.setText(text+"\n左右切换分组");logo.setImageDrawable(null);panel.requestFocus();}
    private void source(int at,boolean last){
        cancelLoad();sourceIndex=at;group=0;groups.clear();visible.clear();channels=new ArrayList<>();numbers=new int[0];selected=-1;loading=true;message("正在读取频道…");final int turn=loadEpoch;CinemaCatalog.Live source=sources.get(at);
        if(source==playingSource){accept(playingChannels,last,0);return;}
        if(!source.channels.isEmpty()){accept(source.channels,last,0);return;}
        try{io.execute(()->{try{if(closed||turn!=loadEpoch)return;List<CinemaCatalog.Channel> values=http.live(source,allowHttp);main.post(()->{if(!closed&&open()&&turn==loadEpoch)accept(values,last,0);});}catch(Exception unavailable){main.post(()->{if(!closed&&open()&&turn==loadEpoch){loading=false;message("此组暂时无法读取");}});}});}catch(RejectedExecutionException closedOrBusy){if(!closed){loading=false;message("此组暂时无法读取");}}
    }
    private void moveGroup(int direction){
        confirmHeld=false;clearDigits();int next=group+direction;
        if(!loading&&!groups.isEmpty()&&next>=0&&next<groups.size()){group=next;render();return;}
        if(sources.size()>1){source((sourceIndex+direction+sources.size())%sources.size(),direction<0);return;}
        if(!groups.isEmpty()){group=(next+groups.size())%groups.size();render();}
    }
    private void render(){
        rendering=true;final int turn=++renderEpoch;String name=groups.get(group);heading.setText("‹  "+(sources.size()>1&&!sourceName().equals(name)?sourceName()+" · ":"")+name+"  ›");visible.clear();rows.removeAllViews();
        for(int i=0;i<channels.size();i++)if(channels.get(i).group.equals(name))visible.add(i);if(!visible.contains(selected))selected=visible.get(0);
        for(int index:visible){Button row=TvUi.button(activity,numbers[index]+"  "+channels.get(index).name,()->{if(turn==renderEpoch)choose(index);});row.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,28*activity.getResources().getDisplayMetrics().heightPixels/1080f);row.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);row.setFocusableInTouchMode(true);row.setSingleLine(true);row.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,Math.max(TvUi.dp(activity,44),Math.round(activity.getResources().getDisplayMetrics().heightPixels*.065f)));p.setMargins(0,0,0,TvUi.dp(activity,5));rows.addView(row,p);row.setOnFocusChangeListener((v,f)->{if(f&&!rendering&&turn==renderEpoch){selected=index;paint();updatePreview();}});}
        rendering=false;final int focusIndex=selected;View focus=rows.getChildAt(visible.indexOf(selected));focus.post(()->focusRow(focus,focusIndex,turn));paint();updatePreview();
    }
    private void choose(int index){if(!open()||loading||index<0||index>=channels.size() )return;clearDigits();select.select(sources.get(sourceIndex),new ArrayList<>(channels),index);}
    private void paint(){for(int i=0;i<rows.getChildCount();i++)rows.getChildAt(i).setBackground(TvUi.glass(activity,visible.get(i)==selected));}
    private void focusRow(View row,int index,int turn){if(!closed&&open()&&turn==renderEpoch&&selected==index&&row.getParent()==rows)row.requestFocus();}
    private void updatePreview(){if(selected<0||selected>=channels.size())return;CinemaCatalog.Channel c=channels.get(selected);String programme=guide.nowNext(c,System.currentTimeMillis());preview.setText(c.name+(programme.isEmpty()?"\n暂无节目单":"\n"+programme));logo.setImageDrawable(null);if(!c.logo.isEmpty())artwork.bind(logo,c.logo,"",allowHttp);}
    boolean key(KeyEvent event){
        if(closed)return false;int code=event.getKeyCode();boolean down=event.getAction()==KeyEvent.ACTION_DOWN,confirm=code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_NUMPAD_ENTER||code==KeyEvent.KEYCODE_BUTTON_A;
        if(open()&&(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT)){
            if(down&&event.getRepeatCount()==0&&(groupKey!=code||groupDownTime!=event.getDownTime())){groupKey=code;groupDownTime=event.getDownTime();moveGroup(code==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1);}else if(event.getAction()==KeyEvent.ACTION_UP){groupKey=-1;groupDownTime=-1;}return true;
        }
        if(confirmHeld&&confirm){if(event.getAction()==KeyEvent.ACTION_UP){confirmHeld=false;if(!event.isCanceled()&&confirmEpoch==renderEpoch)choose(confirmSelection);}return true;}
        if(open()&&entered.isEmpty()&&confirm){if(down&&event.getRepeatCount()==0&&!loading&&selected>=0){confirmHeld=true;confirmSelection=selected;confirmEpoch=renderEpoch;}return true;}
        if(code>=KeyEvent.KEYCODE_0&&code<=KeyEvent.KEYCODE_9){if(down&&!loading){entered+=String.valueOf(code-KeyEvent.KEYCODE_0);if(entered.length()>4)entered=entered.substring(entered.length()-4);digits.setText(entered);digits.setVisibility(View.VISIBLE);main.removeCallbacks(tune);main.postDelayed(tune,1200);}return true;}
        if(!entered.isEmpty()&&confirm){if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())tune();return true;}
        if(!open())return false;
        if(code==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP){confirmHeld=false;hide();}return true;}
        if(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN){if(down&&!loading&&!visible.isEmpty()){int at=visible.indexOf(selected),next=at+(code==KeyEvent.KEYCODE_DPAD_DOWN?1:-1);if(next>=0&&next<visible.size()){selected=visible.get(next);paint();updatePreview();final int chosenIndex=selected,turn=renderEpoch;View chosen=rows.getChildAt(next);chosen.requestFocus();chosen.post(()->focusRow(chosen,chosenIndex,turn));chosen.requestRectangleOnScreen(new android.graphics.Rect(0,0,chosen.getWidth(),chosen.getHeight()));}}return true;}
        return false;
    }
    private void tune(){main.removeCallbacks(tune);int value;try{value=Integer.parseInt(entered);}catch(Exception e){return;}entered="";int[] candidates=open()?numbers:CinemaChannelNumbers.assign(playingChannels);for(int i=0;i<candidates.length;i++)if(candidates[i]==value){digits.setVisibility(View.GONE);if(open())choose(i);else select.select(playingSource,new ArrayList<>(playingChannels),i);return;}digits.setText("没有这个频道");main.postDelayed(()->digits.setVisibility(View.GONE),1800);}
    public void close(){if(closed)return;closed=true;cancelLoad();cancelPress();main.removeCallbacksAndMessages(null);io.shutdownNow();}
}
