package org.zhixiaoce.aperture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Embedded TV catalogue/live/player. No external player, WebView, plugin or remote intent. */
public final class CinemaActivity extends Activity {
    private static final int PICK=4601,FG=0xfff2f5f8,MUTED=0xffbac5d2;
    private final Handler main=new Handler();
    private final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
    private final CinemaHttp http=new CinemaHttp();private final DisplayLifecycle display=new DisplayLifecycle();
    private CinemaVault vault;private CinemaCatalog.Config config;private LinearLayout root,content;private TextView status;
    private volatile int generation;private boolean destroyed,playingView,prepared;private String view="movies",health="尚未检查";
    private Runnable back;private MediaPlayer player;private PlaybackSession session;private TextView playerStatus;
    private List<CinemaCatalog.Episode> queue=new ArrayList<>();private int playingIndex;private boolean liveQueue;private Runnable playerBack;
    private boolean fileAllowHttp;private int healthCursor;
    private final Runnable periodic=new Runnable(){public void run(){if(!destroyed){if(display.active()&&!playingView)checkSources(false);main.postDelayed(this,900000);}}};
    private static final class GridCursor { int selected; GridCursor(int selected){this.selected=selected;} }
    private static final class Action{final String name;final Runnable run;Action(String name,Runnable run){this.name=name;this.run=run;}}
    private interface Work<T>{T run() throws Exception;}
    private interface Done<T>{void run(T value) throws Exception;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);vault=new CinemaVault(this);String asked=getIntent().getStringExtra("view");if("live".equals(asked)||"series".equals(asked)||"library".equals(asked))view=asked;
        session=new PlaybackSession(new PlaybackAudioFocus((AudioManager)getSystemService(AUDIO_SERVICE),main),new PlaybackSession.Output(){public void start(){if(player!=null)player.start();}public void pause(){if(player!=null&&prepared)player.pause();}public void changed(PlaybackSession.State s){playState(s);}});
        display.bind(session::active);
        try{config=vault.read();}catch(Exception invalid){config=new CinemaCatalog.Config(false);health="保存的来源无法读取，请重新导入";}
        if("settings".equals(asked))settings();else home();main.postDelayed(periodic,900000);
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String text,int size){TextView n=new TextView(this);n.setText(text);n.setTextColor(FG);n.setTextSize(size);n.setPadding(0,dp(5),0,dp(5));return n;}
    private Button button(LinearLayout parent,String text,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextColor(FG);b.setTextSize(BuildConfig.PARENTS_EDITION?23:21);b.setMinHeight(dp(60));b.setPadding(dp(16),dp(8),dp(16),dp(8));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff202c3a);bg.setCornerRadius(dp(9));bg.setStroke(dp(1),0xff718194);b.setBackground(bg);
        b.setOnFocusChangeListener((v,focused)->{GradientDrawable next=new GradientDrawable();next.setColor(focused?0xff3e4c5c:0xff202c3a);next.setCornerRadius(dp(9));next.setStroke(dp(focused?3:1),focused?0xfff1f5fa:0xff718194);b.setBackground(next);});
        b.setOnClickListener(v->action.run());if(parent!=null){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(4));parent.addView(b,p);}return b;
    }
    private void release(){session.reset();prepared=false;if(player!=null){player.release();player=null;}getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);playerStatus=null;}
    private void invalidate(){generation++;http.cancel();http.deadline(0);}
    private void screen(String title,Runnable previous){
        invalidate();playingView=false;release();back=previous;
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff0e141e);
        int x=Math.round(getResources().getDisplayMetrics().widthPixels*.05f),y=Math.round(getResources().getDisplayMetrics().heightPixels*.05f);root.setPadding(x,y,x,y);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);root.addView(top);
        TextView heading=label(title,BuildConfig.PARENTS_EDITION?29:26);heading.setSingleLine(true);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        Button sources=button(null,"来源",this::settings);top.addView(sources,new LinearLayout.LayoutParams(dp(100),dp(56)));
        Button nas=button(null,"NAS",this::nas);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(dp(90),dp(56));np.setMargins(dp(8),0,0,0);top.addView(nas,np);
        status=label("",18);status.setTextColor(MUTED);root.addView(status);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }
    private void grid(String title,List<Action> actions,int page,Runnable previous){
        grid(title,actions,page,previous,new GridCursor(page*12));
    }
    private void grid(String title,List<Action> actions,int page,Runnable previous,GridCursor cursor){
        screen(title,previous);int pages=Math.max(1,(actions.size()+11)/12),at=Math.max(0,Math.min(page,pages-1));
        GridLayout cells=new GridLayout(this);cells.setColumnCount(4);content.addView(cells);
        for(int i=at*12;i<Math.min(actions.size(),at*12+12);i++){
            final int index=i;Action a=actions.get(i);Button b=button(null,a.name,()->{cursor.selected=index;a.run.run();});b.setMaxLines(2);b.setEllipsize(android.text.TextUtils.TruncateAt.END);
            GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.columnSpec=GridLayout.spec((i-at*12)%4,1f);p.width=0;p.height=dp(84);p.setMargins(dp(4),dp(5),dp(4),dp(5));cells.addView(b,p);
        }
        if(actions.isEmpty())status.setText("暂无内容，请在来源中配置");
        LinearLayout controls=new LinearLayout(this);root.addView(controls);
        Button prev=button(null,"上一页",()->{cursor.selected=(at-1)*12;grid(title,actions,at-1,previous,cursor);}),next=button(null,"下一页",()->{cursor.selected=(at+1)*12;grid(title,actions,at+1,previous,cursor);});prev.setEnabled(at>0);next.setEnabled(at+1<pages);
        controls.addView(prev,new LinearLayout.LayoutParams(0,dp(56),1));TextView number=label((at+1)+" / "+pages,20);number.setGravity(Gravity.CENTER);controls.addView(number,new LinearLayout.LayoutParams(0,dp(56),1));controls.addView(next,new LinearLayout.LayoutParams(0,dp(56),1));controls.addView(button(null,"返回",()->{if(back!=null)back.run();else finish();}),new LinearLayout.LayoutParams(0,dp(56),1));
        if(cells.getChildCount()>0)cells.getChildAt(Math.max(0,Math.min(cells.getChildCount()-1,cursor.selected-at*12))).requestFocus();else sourcesFocus();
    }
    private void sourcesFocus(){if(root!=null)root.getChildAt(0).requestFocus();}
    private <T> void task(String title,Runnable previous,Work<T> work,Done<T> done){
        screen(title,previous);status.setText("正在读取…");int turn=generation;
        button(content,"返回",previous);
        try{io.execute(()->{if(destroyed||generation!=turn)return;try{T value=work.run();main.post(()->{if(destroyed||generation!=turn)return;try{done.run(value);}catch(Exception error){status.setText(message(error));}});}catch(Exception error){main.post(()->{if(!destroyed&&generation==turn)status.setText(message(error));});}});}catch(java.util.concurrent.RejectedExecutionException busy){status.setText("操作较多，请返回后重试");}
    }
    private String message(Exception error){return error instanceof java.io.IOException?error.getMessage():"来源数据格式不支持，请更换来源或检查配置";}
    private String title(){return "live".equals(view)?"看电视":"series".equals(view)?"看电视剧":"library".equals(view)?"影视":"看电影";}
    private void nas(){Intent intent=new Intent(this,NasActivity.class);try{if(("movies".equals(view)||"series".equals(view))&&new NasVault(this).library().route(view)!=null)intent.putExtra("category",view);}catch(Exception invalid){status.setText("NAS配置无法读取，请在NAS设置中检查");return;}startActivity(intent);}
    private void home(){
        if("live".equals(view))liveSources();else sites();
    }
    private void liveSources(){
        List<Action> actions=new ArrayList<>();for(CinemaCatalog.Live live:config.lives)actions.add(new Action(live.name,()->live(live)));grid("直播",actions,0,this::finish);
        if(actions.isEmpty()){button(content,"设置来源",this::settings).requestFocus();status.setText("尚未配置直播。可导入 M3U 或 TVBox 直播配置。");}
    }
    private void live(CinemaCatalog.Live source){
        if(source.url.isEmpty())groups(source.name,source.channels);else task(source.name,this::liveSources,()->CinemaCatalog.m3u(http.text(source.url,config.allowHttp),source.url,config.allowHttp),channels->groups(source.name,channels));
    }
    private void groups(String name,List<CinemaCatalog.Channel> channels){
        LinkedHashSet<String> names=new LinkedHashSet<>();for(CinemaCatalog.Channel c:channels)names.add(c.group);List<Action> actions=new ArrayList<>();
        for(String group:names)actions.add(new Action(group,()->channels(name,group,channels)));grid(name,actions,0,this::liveSources);
    }
    private void channels(String name,String group,List<CinemaCatalog.Channel> all){
        channels(name,group,all,new GridCursor(0));
    }
    private void channels(String name,String group,List<CinemaCatalog.Channel> all,GridCursor cursor){
        List<CinemaCatalog.Channel> selected=new ArrayList<>();for(CinemaCatalog.Channel c:all)if(c.group.equals(group))selected.add(c);
        List<Action> actions=new ArrayList<>();for(int i=0;i<selected.size();i++){final int at=i;CinemaCatalog.Channel c=selected.get(i);actions.add(new Action(c.name,()->{
            if(c.urls.size()==1)playChannels(selected,at,0,()->{cursor.selected=playingIndex;channels(name,group,all,cursor);});else{String[] choices=new String[c.urls.size()];for(int j=0;j<choices.length;j++)choices[j]="线路 "+(j+1);new AlertDialog.Builder(this).setTitle(c.name).setItems(choices,(d,j)->playChannels(selected,at,j,()->{cursor.selected=playingIndex;channels(name,group,all,cursor);})).setNegativeButton("返回",null).show();}
        }));}grid(group,actions,cursor.selected/12,()->groups(name,all),cursor);
    }
    private void playChannels(List<CinemaCatalog.Channel> channels,int at,int line,Runnable previous){List<CinemaCatalog.Episode> list=new ArrayList<>();for(int i=0;i<channels.size();i++){CinemaCatalog.Channel c=channels.get(i);list.add(new CinemaCatalog.Episode(c.name,c.urls.get(i==at?line:0)));}play(list,at,true,previous);}
    private void sites(){
        List<Action> actions=new ArrayList<>();for(CinemaCatalog.Site site:config.sites)actions.add(new Action(site.name,()->categories(site)));grid(title(),actions,0,this::finish);
        if(actions.isEmpty()){button(content,"设置来源",this::settings).requestFocus();status.setText("尚未配置影视。支持 TVBox type=1 JSON 站点。");}
    }
    private void categories(CinemaCatalog.Site site){
        task(site.name,this::sites,()->http.home(site,config.allowHttp),data->{
            List<Action> actions=new ArrayList<>();actions.add(new Action("搜索",()->search(site)));
            for(CinemaCatalog.Category category:data.categories)actions.add(new Action(category.name,()->videos(site,category.id,category.name,1,null)));
            actions.add(new Action("全部",()->videos(site,null,"全部",1,null)));grid(site.name,actions,0,this::sites);
        });
    }
    private void search(CinemaCatalog.Site site){
        EditText input=new EditText(this);input.setSingleLine();input.setHint("片名");input.setTextSize(24);
        new AlertDialog.Builder(this).setTitle("搜索 · "+site.name).setView(input).setPositiveButton("搜索",(d,w)->{String word=input.getText().toString().trim();if(!word.isEmpty()&&word.length()<=80)videos(site,null,"搜索",1,word);}).setNegativeButton("返回",null).show();
    }
    private void videos(CinemaCatalog.Site site,String category,String name,int page,String word){
        task(name,()->categories(site),()->http.catalogue(site,category,page,word,null,config.allowHttp),data->{
            List<Action> actions=new ArrayList<>();for(CinemaCatalog.Item item:data.items)actions.add(new Action(item.name+(item.remark.isEmpty()?"":"\n"+item.remark),()->details(site,item,()->videos(site,category,name,page,word))));
            if(page>1)actions.add(new Action("上一批",()->videos(site,category,name,page-1,word)));if(page<data.pages)actions.add(new Action("下一批",()->videos(site,category,name,page+1,word)));grid(name+" · "+page+" / "+data.pages,actions,0,()->categories(site));
        });
    }
    private void details(CinemaCatalog.Site site,CinemaCatalog.Item item,Runnable previous){
        task(item.name,previous,()->http.catalogue(site,null,1,null,item.id,config.allowHttp),data->{CinemaCatalog.Item found=null;for(CinemaCatalog.Item candidate:data.items)if(candidate.id.equals(item.id)){found=candidate;break;}if(found==null)throw new java.io.IOException("来源没有返回所选影片详情");final CinemaCatalog.Item detail=found;List<CinemaCatalog.Line> lines=CinemaCatalog.lines(detail,config.allowHttp);List<Action> actions=new ArrayList<>();for(CinemaCatalog.Line line:lines)actions.add(new Action(line.name,()->episodes(detail.name,line,()->details(site,item,previous))));grid(detail.name,actions,0,previous);});
    }
    private void episodes(String name,CinemaCatalog.Line line,Runnable previous){
        episodes(name,line,previous,new GridCursor(0));
    }
    private void episodes(String name,CinemaCatalog.Line line,Runnable previous,GridCursor cursor){
        List<Action> actions=new ArrayList<>();for(int i=0;i<line.episodes.size();i++){final int at=i;actions.add(new Action(line.episodes.get(i).name,()->play(line.episodes,at,false,()->{cursor.selected=playingIndex;episodes(name,line,previous,cursor);})));}grid(name+" · "+line.name,actions,cursor.selected/12,previous,cursor);
    }
    private void play(List<CinemaCatalog.Episode> list,int at,boolean live,Runnable previous){
        screen(list.get(at).name,previous);playingView=true;queue=list;playingIndex=at;liveQueue=live;playerBack=previous;session.begin();
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(Color.BLACK);SurfaceView surface=new SurfaceView(this);stage.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        root.removeViewAt(root.getChildCount()-1);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));playerStatus=status;playerStatus.setText("正在准备…");
        LinearLayout controls=new LinearLayout(this);root.addView(controls);
        List<Action> playback=new ArrayList<>();playback.add(new Action(live?"上一台":"上一集",()->adjacent(-1)));playback.add(new Action("播放 / 暂停",()->{if(session.state()==PlaybackSession.State.PLAYING||session.state()==PlaybackSession.State.PREPARING)session.pause();else session.play();}));playback.add(new Action(live?"下一台":"下一集",()->adjacent(1)));if(!live){playback.add(new Action("后退30秒",()->seek(-30000)));playback.add(new Action("前进30秒",()->seek(30000)));}playback.add(new Action("返回",previous));
        for(Action a:playback)controls.addView(button(null,a.name,a.run),new LinearLayout.LayoutParams(0,dp(60),1));
        controls.getChildAt(1).requestFocus();int turn=generation;
        surface.getHolder().addCallback(new SurfaceHolder.Callback(){
            public void surfaceCreated(SurfaceHolder holder){if(destroyed||turn!=generation)return;session.surface(true);prepare(holder,surface,stage,list.get(at).url,turn);}
            public void surfaceChanged(SurfaceHolder h,int f,int w,int t){}
            public void surfaceDestroyed(SurfaceHolder holder){if(turn!=generation)return;session.surface(false);if(player!=null)player.setDisplay(null);}
        });
    }
    private void prepare(SurfaceHolder holder,SurfaceView surface,FrameLayout stage,String url,int turn){
        if(player!=null){player.setDisplay(holder);return;}
        try{CinemaCatalog.url(url,null,config.allowHttp);player=new MediaPlayer();MediaPlayer p=player;p.setAudioAttributes(PlaybackAudioFocus.attributes());p.setDisplay(holder);p.setDataSource(this,Uri.parse(url));
            p.setOnVideoSizeChangedListener((m,w,h)->{if(player!=m||w<=0||h<=0)return;stage.post(()->{if(player!=m)return;double scale=Math.min((double)stage.getWidth()/w,(double)stage.getHeight()/h);surface.setLayoutParams(new FrameLayout.LayoutParams(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)),Gravity.CENTER));});});
            p.setOnPreparedListener(m->{if(player==m&&turn==generation){prepared=true;session.prepared();}});p.setOnCompletionListener(m->{if(player==m&&turn==generation)session.completed();});p.setOnErrorListener((m,a,b)->{if(player==m&&turn==generation){prepared=false;session.error();}return true;});p.prepareAsync();
        }catch(Exception invalid){prepared=false;session.error();}
    }
    private void playState(PlaybackSession.State state){
        if(playerStatus==null)return;String value;
        switch(state){case PLAYING:value="正在播放";getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);break;case ERROR:value="播放失败，请返回换一条线路或检查来源";break;case PREPARING:value="正在准备…";break;case COMPLETED:value="已播完";break;case FOCUS_DENIED:value="声音正忙，稍后按播放继续";break;default:value="已暂停，按播放继续";}
        if(state!=PlaybackSession.State.PLAYING)getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);playerStatus.setText(value);
    }
    private void adjacent(int delta){int next=playingIndex+delta;if(next>=0&&next<queue.size())play(queue,next,liveQueue,playerBack);}
    private void seek(int delta){if(!liveQueue&&prepared&&player!=null)try{player.seekTo(Math.max(0,Math.min(player.getDuration(),player.getCurrentPosition()+delta)));}catch(RuntimeException ignored){}}
    private void settings(){
        screen("来源设置",this::home);status.setText(config.sites.size()+" 个影视站点 · "+config.lives.size()+" 个直播来源\n"+health);
        EditText address=new EditText(this);address.setTextSize(22);address.setTextColor(FG);address.setSingleLine();address.setHint("JSON / M3U 配置地址");address.setHintTextColor(MUTED);content.addView(address);
        CheckBox allow=new CheckBox(this);allow.setText("允许本次导入 HTTP 来源");allow.setTextColor(FG);allow.setTextSize(21);allow.setChecked(config.allowHttp);content.addView(allow);
        button(content,"读取地址",()->{String value=address.getText().toString().trim();boolean policy=allow.isChecked();task("读取来源",this::settings,()->CinemaCatalog.config(http.text(value,policy),value,policy),this::preview);});
        button(content,"导入本机文件",()->{fileAllowHttp=allow.isChecked();try{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,PICK);}catch(RuntimeException missing){status.setText("当前系统没有文件选择器，请使用配置地址");}});
        button(content,"检查来源",()->checkSources(true));
        button(content,"家庭服务片库",()->{setResult(RESULT_OK,new Intent().putExtra("cinemaServer",view));finish();});
        button(content,"清除本机来源",()->new AlertDialog.Builder(this).setTitle("清除直播和影视来源？").setMessage("不删除NAS文件或第三方应用资料。").setPositiveButton("清除",(d,w)->{vault.clear();config=new CinemaCatalog.Config(false);health="尚未检查";settings();}).setNegativeButton("返回",null).show());
        button(content,"返回",this::home);
    }
    private void preview(CinemaCatalog.Config next){
        String note=next.sites.size()+" 个影视站点，"+next.lives.size()+" 个直播来源。\n替换本机现有来源。";
        for(int i=0;i<Math.min(8,next.skipped.size());i++)note+="\n"+next.skipped.get(i);
        new AlertDialog.Builder(this).setTitle("导入来源").setMessage(note).setPositiveButton("保存",(d,w)->{try{vault.save(next);config=next;health="等待检查";home();checkSources(false);}catch(Exception failed){status.setText("无法保存来源，请重试");}}).setNegativeButton("返回",(d,w)->settings()).show();
    }
    private static final class Probe {final String name;final Work<String> read;Probe(String name,Work<String> read){this.name=name;this.read=read;}}
    private void checkSources(boolean interactive){
        if(interactive)screen("来源检测",this::settings);int turn=generation;CinemaCatalog.Config snapshot=config;
        if(interactive)status.setText("正在检查，最多25秒…");
        final int start=healthCursor;
        try{io.execute(()->{if(destroyed||turn!=generation)return;List<Probe> probes=new ArrayList<>();
            for(CinemaCatalog.Site site:snapshot.sites)probes.add(new Probe(site.name,()->{CinemaCatalog.Page page=http.home(site,snapshot.allowHttp);return !page.categories.isEmpty()||!page.items.isEmpty()?"目录可读取":"目录为空";}));
            for(CinemaCatalog.Live live:snapshot.lives)probes.add(new Probe(live.name,()->{List<CinemaCatalog.Channel> channels=live.url.isEmpty()?live.channels:CinemaCatalog.m3u(http.text(live.url,snapshot.allowHttp),live.url,snapshot.allowHttp);return http.probe(channels.get(0).urls.get(0),snapshot.allowHttp);}));
            List<String> rows=new ArrayList<>();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);http.deadline(deadline);int done=0;
            for(int i=0;i<probes.size();i++){
                if(destroyed||turn!=generation)break;Probe probe=probes.get((start+i)%probes.size());
                if(System.nanoTime()>deadline||done>=12){rows.add(probe.name+"：本轮未检查");continue;}
                done++;try{rows.add(probe.name+"："+probe.read.run());}catch(Exception unavailable){rows.add(probe.name+"：不可读取");}
            }
            http.deadline(0);final int next=probes.isEmpty()?0:(start+done)%probes.size();
            main.post(()->{if(destroyed||turn!=generation)return;healthCursor=next;health="上次检查："+new java.text.SimpleDateFormat("HH:mm",java.util.Locale.CHINA).format(new java.util.Date());if(interactive){status.setText(health+" · 频道仅检查首项样本，解码待验证");for(String row:rows)content.addView(label(row,21));button(content,"返回来源设置",this::settings).requestFocus();}});
        });}catch(java.util.concurrent.RejectedExecutionException busy){if(interactive)status.setText("正在处理其他请求，请稍后再试");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=PICK||result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(!"content".equals(uri.getScheme()))return;boolean policy=fileAllowHttp;
        task("读取配置文件",this::settings,()->{try(InputStream stream=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(stream==null)throw new java.io.IOException("文件不可读取");byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1){if(out.size()+n>CinemaCatalog.MAX_BYTES)throw new java.io.IOException("配置超过512KB");out.write(buffer,0,n);}return CinemaCatalog.config(new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8),null,policy);}},this::preview);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(VoiceAssistant.systemVoiceKey(event.getKeyCode()))return super.dispatchKeyEvent(event);
        if(playingView&&event.getAction()==KeyEvent.ACTION_DOWN){switch(event.getKeyCode()){case KeyEvent.KEYCODE_MEDIA_REWIND:seek(-30000);return true;case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:seek(30000);return true;case KeyEvent.KEYCODE_CHANNEL_UP:adjacent(1);return true;case KeyEvent.KEYCODE_CHANNEL_DOWN:adjacent(-1);return true;}}
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed(){if(back!=null)back.run();else finish();}
    @Override protected void onResume(){super.onResume();display.resumed(true);}
    @Override protected void onPause(){display.resumed(false);super.onPause();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);display.focused(focus);}
    @Override protected void onDestroy(){destroyed=true;invalidate();main.removeCallbacksAndMessages(null);release();io.shutdownNow();super.onDestroy();}
}
