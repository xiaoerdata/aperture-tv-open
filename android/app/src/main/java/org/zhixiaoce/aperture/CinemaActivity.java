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

/** Embedded TV catalogue/player with explicitly trusted plugin adapters. */
public final class CinemaActivity extends Activity {
    private static final int PICK=4601,FG=0xfff2f5f8,MUTED=0xffbac5d2;
    private final Handler main=new Handler();
    private final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
    private final CinemaHttp http=new CinemaHttp();private final DisplayLifecycle display=new DisplayLifecycle();
    private CinemaPlugins plugins;private final java.util.Map<String,CinemaPlugins.Candidate> candidates=new java.util.concurrent.ConcurrentHashMap<>();
    private CinemaPlaybackPlan vodPlan;private org.json.JSONObject vodReference;private CinemaCatalog.Site vodSite;private Runnable vodBack;private int failedTurn=-1;
    private static final class DetailSelection {int line,episode;}
    private CinemaCatalog.Site queueSite;private CinemaCatalog.Episode resolvedEpisode;
    private CinemaProgrammePanel programmePanel;private CinemaEpgLoader guideLoader;private CinemaEpg guide=new CinemaEpg();private CinemaLivePanel livePanel;private List<CinemaCatalog.Channel> liveAll=new ArrayList<>();private List<CinemaCatalog.Channel> playingChannels=new ArrayList<>();
    private final PlaybackEntryIntent entryIntent=new PlaybackEntryIntent();
    private PlayerTimeline timeline;private int bufferedPercent;private TextView playerProgramme;
    private final java.util.Map<CinemaCatalog.Channel,TextView> channelProgrammes=new java.util.IdentityHashMap<>();
    private final Runnable programmeClock=new Runnable(){public void run(){if(!destroyed){refreshGuideViews();if(guideLoader!=null&&config!=null&&display.active()&&((playingView&&liveQueue)||!channelProgrammes.isEmpty())){final CinemaCatalog.Live source=currentLive;guideLoader.load(config.epgs,config.allowHttp,(value,state)->{if(!destroyed&&source==currentLive)updateGuide(value);});}main.postDelayed(this,30000);}}};
    private CinemaArtwork artwork;private CinemaInfo info;private LinearLayout playerOverlay;private SurfaceView videoSurface;private Button playerToggle,playerMore;private FrameLayout playerStage;private View playerMenu;
    private CinemaVault vault;private CinemaCatalog.Config config;private LinearLayout root,content;private TextView status;
    private volatile int generation;private boolean destroyed,playingView,prepared;private String view="movies",health="尚未检查";
    private Runnable shelfRefresh;private Runnable back;private MediaPlayer player;private PlaybackSession session;private TextView playerStatus;
    private List<CinemaCatalog.Episode> queue=new ArrayList<>();private int playingIndex;private boolean liveQueue;private Runnable playerBack;
    private final ConfirmPress confirmPress=new ConfirmPress();private CinemaChoicePanel choicePanel;private Runnable playerConfirmAction;private boolean controlsConfirmHeld;private boolean fileAllowHttp;private int healthCursor;
    private CinemaShelf shelf;private String shelfWarning="";private CinemaCatalog.Live currentLive;
    private List<org.json.JSONObject> queueRefs=new ArrayList<>();private String queueLine="";private boolean hasPlayed;
    private final Runnable checkpoint=new Runnable(){public void run(){if(!destroyed){saveProgress(false);main.postDelayed(this,15000);}}};
    private final Runnable periodic=new Runnable(){public void run(){if(!destroyed){if(display.active()&&!playingView)checkSources(false);main.postDelayed(this,900000);}}};
    private static final class GridCursor { int selected,scrollY; boolean episodes; GridCursor(int selected){this.selected=selected;} GridCursor(int selected,boolean episodes){this.selected=selected;this.episodes=episodes;} }
    private static final class Action{final String name;final Runnable run;CinemaCatalog.Item item;String image="",base="";CinemaCatalog.Channel channel;Action(String name,Runnable run){this.name=name;this.run=run;}Action(CinemaCatalog.Item item,String base,Runnable run){this(item.name,run);this.item=item;this.image=item.poster;this.base=base;}Action(String name,String image,String base,Runnable run){this(name,run);this.image=image;this.base=base;}Action(CinemaCatalog.Channel channel,String base,Runnable run){this(channel.name,channel.logo,base,run);this.channel=channel;}}
    private boolean sectionFullscreen;private CinemaSectionView sectionLayout;private CinemaCatalog.Site sectionSite;private List<CinemaCatalog.Item> sectionItems=new ArrayList<>();private List<CinemaCatalog.Category> sectionCategories=new ArrayList<>();private List<CinemaCatalog.Channel> sectionChannels=new ArrayList<>();private String sectionRailMode="programmes",sectionCategory,sectionItemId="";private int sectionPage=1,sectionPages=1;
    private void showSectionContent(View body){
        if(sectionFullscreen){sectionLayout=null;setContentView(body);return;}
        sectionLayout=new CinemaSectionView(this,title(),body);setContentView(sectionLayout.stage);refreshSectionRail();
    }
    private boolean railCollapsed,sourcesExpanded,categoriesExpanded=true,filtersExpanded;
    private int browseScrollY;private String browseWord,browseName="片单",filterYear="",filterArea="";private boolean browseActive;
    private void refreshSectionRail(){
        if(sectionLayout==null)return;
        boolean live="live".equals(view);List<CinemaSectionView.Entry> rows=new ArrayList<>();
        rows.add(new CinemaSectionView.Entry((sourcesExpanded?"▾ ":"▸ ")+"来源",false,()->{sourcesExpanded=!sourcesExpanded;refreshSectionRail();}));
        if(sourcesExpanded){
            if(live){for(CinemaCatalog.Live source:CinemaLiveSources.visible(config.lives))rows.add(new CinemaSectionView.Entry(source.name,source==currentLive,()->sectionLive(source,0),1));}
            else for(CinemaCatalog.Site site:config.sites)if(site.supports(view))rows.add(new CinemaSectionView.Entry(site.name,site==sectionSite,()->{browseWord=null;filterYear="";filterArea="";sectionCategory=null;sectionLoad(site,null,1);},1));
        }
        if(live){
            LinkedHashSet<String> groups=new LinkedHashSet<>();for(CinemaCatalog.Channel c:sectionChannels)groups.add(c.group);
            for(String group:groups){rows.add(new CinemaSectionView.Entry(group,false,()->{sectionRailMode=group;refreshSectionRail();}));if(group.equals(sectionRailMode)||groups.size()==1)for(int i=0;i<sectionChannels.size();i++)if(group.equals(sectionChannels.get(i).group)){final int at=i;rows.add(new CinemaSectionView.Entry(sectionChannels.get(i).name,playingIndex==i,()->playChannels(sectionChannels,at,0,this::finish),1));}}
        }else{
            rows.add(new CinemaSectionView.Entry((categoriesExpanded?"▾ ":"▸ ")+"分类",false,()->{categoriesExpanded=!categoriesExpanded;refreshSectionRail();}));
            if(categoriesExpanded)for(CinemaCatalog.Category category:sectionCategories)rows.add(new CinemaSectionView.Entry(category.name,category.id.equals(sectionCategory)&&browseWord==null,()->{filterYear="";filterArea="";videos(sectionSite,category.id,category.name,1,null);},1));
            rows.add(new CinemaSectionView.Entry((filtersExpanded?"▾ ":"▸ ")+"筛选 · 已加载内容",false,()->{filtersExpanded=!filtersExpanded;refreshSectionRail();}));
            if(filtersExpanded){rows.add(new CinemaSectionView.Entry("年份"+(filterYear.isEmpty()?" · 全部":" · "+filterYear),false,()->filter(false),1));rows.add(new CinemaSectionView.Entry("地区"+(filterArea.isEmpty()?" · 全部":" · "+filterArea),false,()->filter(true),1));}
        }
        sectionLayout.render(rows,live?null:()->{if(sectionSite!=null)search(sectionSite);},live?null:()->{if(sectionSite!=null){browseActive=true;renderBrowse();}},this::settings,this::shelfHome);
        sectionLayout.collapsed(railCollapsed);sectionLayout.onCollapse=()->{railCollapsed=!railCollapsed;if(browseActive)renderBrowse();else sectionLayout.collapsed(railCollapsed);};
    }
    private void filter(boolean area){
        LinkedHashSet<String> values=new LinkedHashSet<>();values.add("全部");for(CinemaCatalog.Item item:sectionItems){String value=area?item.area:item.year;if(!value.isEmpty())values.add(value);}
        String[] choices=values.toArray(new String[0]);TvDialogs.menu(this,area?"地区 · 已加载内容":"年份 · 已加载内容",choices,(d,i)->{if(area)filterArea=i==0?"":choices[i];else filterYear=i==0?"":choices[i];renderBrowse();});
    }
    private static final class SectionPage {CinemaCatalog.Page page;List<CinemaCatalog.Category> categories;String category;}
    private void sectionLoad(CinemaCatalog.Site site,String category,int page){
        sectionFullscreen=false;sectionSite=site;sectionCategory=category;sectionPage=page;browseScrollY=0;browseActive=false;sectionRailMode="programmes";
        task(title(),this::finish,()->{
            SectionPage result=new SectionPage();CinemaCatalog.Page home=siteHome(site);result.categories=CinemaResourcePack.categories(site,home,view);
            if(result.categories.isEmpty()&&!"variety".equals(view)&&site.sections.isEmpty())result.categories=home.categories;
            boolean declared=site.sections.size()==1&&site.sections.contains(view);
            if("variety".equals(view)&&result.categories.isEmpty()&&!declared)throw new java.io.IOException("此来源未提供综艺分类");
            result.category=category!=null?category:result.categories.isEmpty()?null:result.categories.get(0).id;
            result.page=catalogue(site,result.category,page,null,null);return result;
        },result->{sectionCategories=result.categories;sectionCategory=result.category;sectionItems=new ArrayList<>(result.page.items);sectionPages=result.page.pages;
            if(sectionItems.isEmpty()){status.setText("此片单暂无影片");refreshSectionRail();return;}
            browseActive=true;renderBrowse();
        });
    }
    private void sectionLive(CinemaCatalog.Live source,int selected){
        sectionFullscreen=false;currentLive=source;sectionChannels=new ArrayList<>();sectionRailMode="programmes";
        task("看电视",this::finish,()->http.live(source,config.allowHttp),channels->{sectionChannels=channels;liveAll=channels;if(channels.isEmpty()){status.setText("此来源暂无频道");refreshSectionRail();return;}playChannels(channels,Math.max(0,Math.min(selected,channels.size()-1)),0,this::finish);});
    }
    private interface Work<T>{T run() throws Exception;}
    private interface Done<T>{void run(T value) throws Exception;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);guideLoader=new CinemaEpgLoader();artwork=new CinemaArtwork(this);info=new CinemaInfo(this);vault=new CinemaVault(this);plugins=new CinemaPlugins(this);String asked=getIntent().getStringExtra("view");if("live".equals(asked)||"series".equals(asked)||"variety".equals(asked)||"library".equals(asked))view=asked;
        session=new PlaybackSession(new PlaybackAudioFocus((AudioManager)getSystemService(AUDIO_SERVICE),main),new PlaybackSession.Output(){public void start(){if(player!=null)player.start();}public void pause(){if(player!=null&&prepared)player.pause();}public void changed(PlaybackSession.State s){playState(s);}});
        display.bind(session::active);
        try{config=vault.read();}catch(Exception invalid){config=new CinemaCatalog.Config(false);health="保存的来源无法读取，请重新导入";}
        try{List<String> guides=vault.guideSources(config.epgs);config.epgs.clear();config.epgs.addAll(guides);}catch(Exception unreadable){health="节目单设置无法读取";}
        try{shelf=vault.shelf();}catch(Exception unreadable){try{shelf=new CinemaShelf("[]");}catch(Exception impossible){throw new IllegalStateException(impossible);}shelfWarning="观看记录无法读取，旧记录未覆盖";}
        if("settings".equals(asked))settings();else home();main.postDelayed(periodic,900000);main.postDelayed(checkpoint,15000);main.post(programmeClock);
    }
    private AlertDialog settingsDialog;
    private void closeSettings(){if(settingsDialog!=null){settingsDialog.dismiss();settingsDialog=null;}}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String text,int size){TextView n=new TextView(this);n.setText(text);n.setTextColor(FG);n.setTextSize(TvUi.typeSize(size));n.setPadding(0,dp(3),0,dp(3));return n;}
    private Button button(LinearLayout parent,String text,Runnable action){
        Button b=TvUi.button(this,text,action);if(parent!=null){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(0,dp(6),0,dp(6));parent.addView(b,p);}return b;
    }
    private void release(){controlsConfirmHeld=false;playerConfirmAction=null;choicePanel=null;entryIntent.cancel();playerProgramme=null;programmePanel=null;if(livePanel!=null){livePanel.close();livePanel=null;}if(timeline!=null){timeline.close();timeline=null;}main.removeCallbacks(hidePlayerControls);playerOverlay=null;videoSurface=null;playerToggle=null;playerMore=null;playerMenu=null;playerStage=null;saveProgress(false);session.reset();prepared=false;if(player!=null){player.release();player=null;}getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);playerStatus=null;}
    private void invalidate(){generation++;if(artwork!=null)artwork.cancel();if(info!=null)info.cancel();http.cancel();http.deadline(0);if(plugins!=null)plugins.cancelPending();}
    private void screen(String title,Runnable previous){
        closeSettings();invalidate();release();playingView=false;channelProgrammes.clear();back=previous;shelfRefresh=null;
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackground(TvUi.background());
        int x=Math.round(getResources().getDisplayMetrics().widthPixels*.05f),y=Math.round(getResources().getDisplayMetrics().heightPixels*.035f);root.setPadding(x,y,x,y);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);root.addView(top);
        TextView heading=label(title,BuildConfig.PARENTS_EDITION?29:26);heading.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);heading.setSingleLine(true);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        Button mine=button(null,"我的",this::shelfHome);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(90),dp(44));mp.setMargins(Math.max(16,dp(8)),0,0,0);top.addView(mine,mp);
        Button sources=button(null,"来源",this::settings);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(100),dp(44));sp.setMargins(Math.max(16,dp(8)),0,0,0);top.addView(sources,sp);
        Button nas=button(null,"NAS",this::nas);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(dp(90),dp(44));np.setMargins(Math.max(16,dp(8)),0,0,0);top.addView(nas,np);
        status=label("",18);status.setTextColor(MUTED);root.addView(status);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        showSectionContent(root);
    }
    private void grid(String title,List<Action> actions,int page,Runnable previous){
        grid(title,actions,page,previous,new GridCursor(page*12));
    }
    private void grid(String title,List<Action> actions,int page,Runnable previous,GridCursor cursor){
        screen(title,previous);int pages=Math.max(1,(actions.size()+11)/12),at=Math.max(0,Math.min(page,pages-1));
        boolean posters=false;for(Action a:actions)if(a.item!=null){posters=true;break;}int columns=cursor.episodes?2:posters?5:3;GridLayout cells=new GridLayout(this);cells.setColumnCount(columns);cells.setClipChildren(false);content.addView(cells);
        for(int i=at*12;i<Math.min(actions.size(),at*12+12);i++){
            final int index=i;Action a=actions.get(i);View tile;
            Runnable activate=()->{cursor.selected=index;cursor.scrollY=((ScrollView)content.getParent()).getScrollY();a.run.run();};
            if(posters&&a.item!=null)tile=poster(a,activate);else if(a.channel!=null||!a.image.isEmpty())tile=channelTile(a,activate);else{Button b=button(null,a.name,activate);b.setMaxLines(cursor.episodes?Integer.MAX_VALUE:2);b.setPadding(dp(12),dp(4),dp(12),dp(4));b.setEllipsize(cursor.episodes?null:android.text.TextUtils.TruncateAt.END);if(cursor.episodes){b.setMinHeight(dp(64));b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setPadding(dp(14),dp(12),dp(14),dp(12));}tile=b;}
            GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.columnSpec=GridLayout.spec((i-at*12)%columns,1f);p.width=0;p.height=posters?dp(BuildConfig.PARENTS_EDITION?192:170):a.channel!=null?Math.max(dp(64),Math.round(getResources().getDisplayMetrics().heightPixels*.09f)):cursor.episodes?-2:dp(56);p.setMargins(dp(6),dp(6),dp(6),dp(6));cells.addView(tile,p);
        }
        status.setText(actions.isEmpty()?"暂无内容，请在来源中配置":actions.size()+" 项");
        LinearLayout controls=new LinearLayout(this);root.addView(controls);
        Button prev=button(null,"上一页",()->{cursor.selected=(at-1)*12;grid(title,actions,at-1,previous,cursor);}),next=button(null,"下一页",()->{cursor.selected=(at+1)*12;grid(title,actions,at+1,previous,cursor);});prev.setEnabled(at>0);next.setEnabled(at+1<pages);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(100),dp(44));cp.setMargins(0,dp(8),Math.max(16,dp(8)),0);controls.addView(prev,cp);
        TextView number=label((at+1)+" / "+pages,20);number.setGravity(Gravity.CENTER);LinearLayout.LayoutParams countParams=new LinearLayout.LayoutParams(dp(70),dp(44));countParams.setMargins(0,dp(8),Math.max(16,dp(8)),0);controls.addView(number,countParams);
        LinearLayout.LayoutParams nextParams=new LinearLayout.LayoutParams(dp(100),dp(44));nextParams.setMargins(0,dp(8),Math.max(16,dp(8)),0);controls.addView(next,nextParams);
        LinearLayout.LayoutParams backParams=new LinearLayout.LayoutParams(dp(90),dp(44));backParams.setMargins(0,dp(8),0,0);controls.addView(button(null,"返回",()->{if(back!=null)back.run();else finish();}),backParams);
        if(cells.getChildCount()>0)cells.getChildAt(Math.max(0,Math.min(cells.getChildCount()-1,cursor.selected-at*12))).requestFocus();else sourcesFocus();ScrollView listScroll=(ScrollView)content.getParent();listScroll.post(()->listScroll.scrollTo(0,cursor.scrollY));
    }
    private android.widget.ImageView art(String image,String base){android.widget.ImageView v=new android.widget.ImageView(this);v.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);v.setBackground(TvUi.glass(this,false));v.setClipToOutline(true);artwork.bind(v,image,base,config.allowHttp);return v;}
    private LinearLayout focusTile(String name,Runnable run){LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setPadding(dp(6),dp(6),dp(6),dp(6));tile.setBackground(TvUi.states(this));tile.setFocusable(true);tile.setFocusableInTouchMode(true);tile.setClickable(true);tile.setContentDescription(name);tile.setOnClickListener(v->run.run());tile.setOnFocusChangeListener((v,f)->{v.setScaleX(1);v.setScaleY(1);});return tile;}
    private View poster(Action action,Runnable run){
        LinearLayout tile=focusTile(action.name,run);CinemaPoster frame=new CinemaPoster(this,action.name);android.widget.ImageView image=frame.image;tile.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        TextView name=label(action.name,BuildConfig.PARENTS_EDITION?22:18);name.setMaxLines(2);name.setMinLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(name);TextView remark=label(action.item.remark.isEmpty()?action.item.year:action.item.remark,15);remark.setTextColor(MUTED);remark.setSingleLine();remark.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(remark,new LinearLayout.LayoutParams(-1,-2));
        artwork.bind(image,action.image,action.base,config.allowHttp,()->{int turn=generation;String kind="movies".equals(view)?"movie":"series";info.lookup(action.item.name,action.item.year,kind,false,(match,candidates,state)->{if(generation!=turn||match==null)return;artwork.bind(image,match.optString("poster"),"",false);});});return tile;
    }
    private View channelTile(Action action,Runnable run){
        LinearLayout tile=focusTile(action.name,run);tile.setOrientation(LinearLayout.HORIZONTAL);
        if(!action.image.isEmpty()){android.widget.ImageView icon=art(action.image,action.base);icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);tile.addView(icon,new LinearLayout.LayoutParams(dp(36),-1));}
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setGravity(Gravity.CENTER_VERTICAL);tile.addView(copy,new LinearLayout.LayoutParams(0,-1,1));
        TextView name=label(action.name,22);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,30*getResources().getDisplayMetrics().heightPixels/1080f);copy.addView(name,new LinearLayout.LayoutParams(-1,-2));
        if(action.channel!=null){TextView programme=label("",18);programme.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,22*getResources().getDisplayMetrics().heightPixels/1080f);programme.setTextColor(MUTED);programme.setMaxLines(2);programme.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(programme,new LinearLayout.LayoutParams(-1,-2));channelProgrammes.put(action.channel,programme);String current=guide.nowNext(action.channel,System.currentTimeMillis());programme.setText(current.isEmpty()?action.channel.urls.size()+" 条线路":current);}
        return tile;
    }
    private void refreshGuideViews(){
        long now=System.currentTimeMillis();for(java.util.Map.Entry<CinemaCatalog.Channel,TextView> entry:channelProgrammes.entrySet()){String value=guide.nowNext(entry.getKey(),now);entry.getValue().setText(value.isEmpty()?entry.getKey().urls.size()+" 条线路":value);}
        if(playerProgramme!=null&&liveQueue&&playingIndex>=0&&playingIndex<playingChannels.size()){String value=guide.nowNext(playingChannels.get(playingIndex),now);playerProgramme.setText(value);playerProgramme.setVisibility(value.isEmpty()?View.GONE:View.VISIBLE);}
    }
    private void updateGuide(CinemaEpg value){if(value==guide){refreshGuideViews();return;}guide=value;refreshGuideViews();if(livePanel!=null)livePanel.guide(value);if(programmePanel!=null)programmePanel.guide(value);}

    private void detailPanel(CinemaCatalog.Site site,CinemaCatalog.Item item,List<Action> actions,List<CinemaCatalog.Line> lines,org.json.JSONObject ref,Runnable previous,DetailSelection selection){
        screen("影片详情",previous);CinemaDetailView layout=new CinemaDetailView(this,item,previous,!sectionFullscreen);showSectionContent(layout.stage);content=layout.body;status=layout.credit;
        artwork.bind(layout.poster.image,item.poster,site.api,config.allowHttp);artwork.bind(layout.backdrop,item.backdrop.isEmpty()?item.poster:item.backdrop,site.api,config.allowHttp);
        String kind="movies".equals(view)?"movie":"series";int turn=generation;
        info.lookup(item.name,item.year,kind,false,(match,candidates,state)->{if(turn!=generation||match==null)return;if(item.poster.isEmpty())artwork.bind(layout.poster.image,match.optString("poster"),"",false);if(item.backdrop.isEmpty()&&item.poster.isEmpty())artwork.bind(layout.backdrop,match.optString("backdrop",match.optString("poster")),"",false);if(item.summary.isEmpty())layout.summary.setText(match.optString("summary","暂无简介"));List<String> facts=nonempty(item.year.isEmpty()?match.optString("year"):item.year,item.genre.isEmpty()?match.optString("genre"):item.genre,item.duration,item.area);layout.facts.setText(String.join(" · ",facts));layout.facts.setVisibility(facts.isEmpty()?View.GONE:View.VISIBLE);layout.credit.setText("资料："+match.optString("provider"));layout.credit.setContentDescription("资料："+match.optString("provider")+" "+match.optString("url"));});
        LinearLayout row=null;Button first=null;for(int i=0;i<actions.size();i++){if(i%3==0){row=new LinearLayout(this);layout.actions.addView(row);}Button b=layout.action(row,actions.get(i).name,actions.get(i).run,i==0);if(first==null)first=b;}
        if(!item.director.isEmpty()||!item.actors.isEmpty()){TextView people=label((item.director.isEmpty()?"":"导演  "+item.director+"  ")+item.actors,19);people.setMaxLines(2);layout.copy.addView(people);}
        final Button playAction=first;
        if(!lines.isEmpty()){layout.body.addView(label("选集",24));layout.body.addView(new CinemaEpisodeGrid(this,lines,selection.line,selection.episode,first,(line,at)->{selection.line=line;selection.episode=at;if(playAction!=null)playAction.setText("播放");},(line,at)->playEpisodes(site,ref,lines,line,at,()->details(site,item,previous))),new LinearLayout.LayoutParams(-1,-2));}
        button(layout.body,"更正资料",()->info.lookup(item.name,item.year,kind,true,(match,candidates,state)->{if(turn!=generation)return;if(!info.enabled()){metadataSettings();return;}if(candidates.isEmpty()){layout.credit.setText("暂无可选资料");return;}List<String> names=new ArrayList<>();for(org.json.JSONObject candidate:candidates)names.add(candidate.optString("title")+" · "+candidate.optString("year"));names.add("保留来源资料");TvDialogs.menu(this,"选择资料",names.toArray(new String[0]),(d,at)->{try{info.choose(item.name,item.year,kind,at<candidates.size()?candidates.get(at):null);detailPanel(site,item,actions,lines,ref,previous,selection);}catch(Exception error){layout.credit.setText("资料选择未保存");}});}));
        if(first!=null){if(sectionLayout!=null)sectionLayout.linkRight(first);first.requestFocus();}
    }
    private static List<String> nonempty(String... values){List<String> out=new ArrayList<>();for(String v:values)if(v!=null&&!v.isEmpty())out.add(v);return out;}
    private void metadataSettings(){
        closeSettings();LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);TextView note=label("优先使用来源封面。开启后，将片名、年份发送给资料服务；不会发送播放地址、NAS账号或来源凭证。",20);form.addView(note);
        CheckBox enabled=new CheckBox(this);enabled.setText("联网补全封面与简介");enabled.setTextColor(FG);enabled.setTextSize(18);enabled.setChecked(info.enabled());form.addView(enabled);
        form.addView(label("TMDB Read Access Token（可选）\n电影需要设置；未设置时电视剧使用 TVmaze。",19));EditText token=new EditText(this);token.setTextColor(FG);token.setTextSize(17);token.setSingleLine();token.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);token.setText(info.token());form.addView(token);
        button(form,"保存",()->{try{info.configure(enabled.isChecked(),token.getText().toString());closeSettings();home();}catch(Exception e){note.setText("资料设置保存失败");}});android.widget.ImageView tmdb=new android.widget.ImageView(this);tmdb.setImageResource(getResources().getIdentifier("tmdb_attribution","drawable",getPackageName()));tmdb.setScaleType(android.widget.ImageView.ScaleType.FIT_START);tmdb.setContentDescription("TMDB");form.addView(tmdb,new LinearLayout.LayoutParams(dp(150),dp(26)));form.addView(label("This product uses the TMDB API but is not endorsed or certified by TMDB.\nTVmaze 数据遵循 CC BY-SA；详情保留来源链接。联网不可用时仍可浏览和播放。",16));settingsDialog=TvDialogs.panel(this,"封面与资料",form);
    }
    private void sourcesFocus(){if(root!=null)root.getChildAt(0).requestFocus();}
    private <T> void task(String title,Runnable previous,Work<T> work,Done<T> done){
        screen(title,previous);status.setText("正在读取…");int turn=generation;
        content.addView(label("读取来源内容",22));button(content,"返回",previous);
        try{io.execute(()->{if(destroyed||generation!=turn)return;try{T value=work.run();main.post(()->{if(destroyed||generation!=turn)return;try{done.run(value);}catch(Exception error){taskFailure(error,()->task(title,previous,work,done));}});}catch(Exception error){main.post(()->{if(!destroyed&&generation==turn){if(error instanceof PluginTrust){confirmPlugin(((PluginTrust)error).candidate,()->task(title,previous,work,done));}else taskFailure(error,()->task(title,previous,work,done));}});}});}catch(java.util.concurrent.RejectedExecutionException busy){status.setText("操作较多，请返回后重试");}
    }
    private void taskFailure(Exception error,Runnable retry){
        String text=message(error);if(text==null||text.trim().isEmpty())text="来源暂时不可用";String lower=text.toLowerCase(java.util.Locale.ROOT);if(lower.contains("timeout")||lower.contains("timed out"))text="来源响应超时";else if(lower.contains("unable to resolve")||lower.contains("connect"))text="暂时无法连接来源";
        status.setText(text);content.removeAllViews();content.addView(label("内容暂时无法读取",24));button(content,"重试",retry).requestFocus();button(content,"切换来源",()->{railCollapsed=false;sourcesExpanded=true;refreshSectionRail();if(sectionLayout!=null)sectionLayout.focusFirst();});button(content,"来源设置",this::settings);
    }
    private static final class PluginTrust extends java.io.IOException {final CinemaPlugins.Candidate candidate;PluginTrust(CinemaPlugins.Candidate candidate){super("插件需要确认");this.candidate=candidate;}}
    private CinemaPlugins.Candidate plugin(CinemaCatalog.Site site)throws Exception {
        String key=source(site);CinemaPlugins.Candidate candidate=candidates.get(key);
        if(candidate==null){candidate=plugins.prepare(CinemaPlugins.Spec.from(site.api,site.ext,site.jar,site.spider,site.base,config.allowHttp));candidates.put(key,candidate);}
        if(!plugins.approved(candidate))throw new PluginTrust(candidate);return candidate;
    }
    private void confirmPlugin(CinemaPlugins.Candidate candidate,Runnable retry){
        String text="来源："+candidate.origin+"\n类型："+candidate.kind+"\nSHA-256："+candidate.digest+"\n\n插件会执行外部代码，可访问开视的应用数据和网络。请仅运行你信任的来源。";
        new AlertDialog.Builder(this).setTitle("运行此插件？").setMessage(text).setPositiveButton("信任并运行",(d,w)->{try{plugins.approve(candidate);retry.run();}catch(Exception invalid){status.setText("插件授权未保存");}}).setNegativeButton("返回",null).show();
    }
    private CinemaCatalog.Page siteHome(CinemaCatalog.Site site)throws Exception {return site.type==3?CinemaCatalog.page(plugins.call(plugin(site),"home",true),1):http.home(site,config.allowHttp);}
    private CinemaCatalog.Page catalogue(CinemaCatalog.Site site,String category,int page,String word,String id)throws Exception {
        if(site.type!=3)return http.catalogue(site,category,page,word,id,config.allowHttp);
        CinemaPlugins.Candidate candidate=plugin(site);String data;
        if(id!=null)data=plugins.call(candidate,"detail",java.util.Collections.singletonList(id));
        else if(word!=null)data=plugins.call(candidate,"search",word,false,String.valueOf(page));
        else if(category==null){try{data=plugins.call(candidate,"homeVod");if(CinemaCatalog.page(data,1).items.isEmpty())data=plugins.call(candidate,"home",true);}catch(java.io.IOException unsupported){data=plugins.call(candidate,"home",true);}}
        else data=plugins.call(candidate,"category",category,String.valueOf(page),false,new java.util.HashMap<String,String>());
        return CinemaCatalog.page(data,page);
    }
    private CinemaCatalog.Episode resolve(CinemaCatalog.Site site,String line,CinemaCatalog.Episode episode)throws Exception {
        if(site==null)return episode;
        return site.type==3?CinemaHttp.resolved(episode,plugins.call(plugin(site),"play",line,episode.url,java.util.Collections.emptyList()),config.allowHttp):http.resolve(site,line,episode,config.allowHttp);
    }
    private String message(Exception error){return error instanceof java.io.IOException?error.getMessage():"来源数据格式不支持，请更换来源或检查配置";}
    private String title(){return "live".equals(view)?"看电视":"series".equals(view)?"看电视剧":"variety".equals(view)?"看综艺":"library".equals(view)?"影视":"看电影";}
    private void nas(){Intent intent=new Intent(this,NasActivity.class);try{if(("movies".equals(view)||"series".equals(view))&&new NasVault(this).library().route(view)!=null)intent.putExtra("category",view);}catch(Exception invalid){status.setText("NAS配置无法读取，请在NAS设置中检查");return;}startActivity(intent);}
    private String source(CinemaCatalog.Site site){return site.identity.isEmpty()?site.key+"\n"+site.api:site.identity;}
    private org.json.JSONObject vodRef(CinemaCatalog.Site site,CinemaCatalog.Item item)throws Exception{return CinemaShelf.ref("vod",source(site),item.id,item.name,"");}
    private org.json.JSONObject liveRef(CinemaCatalog.Live live,CinemaCatalog.Channel channel)throws Exception{return CinemaShelf.ref("live",live.key(),channel.id.isEmpty()?channel.group+"\n"+channel.name:channel.id,channel.name,channel.group);}
    private interface ShelfEdit{void apply(CinemaShelf next)throws Exception;}
    private void editShelf(ShelfEdit edit){if(!shelfWarning.isEmpty()){if(status!=null)status.setText(shelfWarning);return;}try{shelf=vault.updateShelf(edit::apply);}catch(Exception failure){if(status!=null)status.setText("记录未保存，请检查本机存储");}}
    private void saveProgress(boolean complete){if(!playingView||!prepared||player==null||!hasPlayed||queueRefs.size()<=playingIndex)return;try{long position=liveQueue?0:player.getCurrentPosition(),duration=liveQueue?0:player.getDuration();org.json.JSONObject ref=queueRefs.get(playingIndex);String episode=queue.get(playingIndex).name;boolean finished=complete||session.state()==PlaybackSession.State.COMPLETED;editShelf(next->next.watch(ref,queueLine,episode,position,duration,finished,System.currentTimeMillis()));}catch(RuntimeException ignored){}}
    private void shelfHome(){List<Action> actions=new ArrayList<>();actions.add(new Action("最近观看",()->shelfList(null,"history")));actions.add(new Action("收藏频道",()->shelfList("live","favorite")));actions.add(new Action("收藏影片",()->shelfList("vod","favorite")));actions.add(new Action("收藏 NAS",()->shelfList("nas","favorite")));actions.add(new Action("追剧",()->shelfList("vod","follow")));grid("我的",actions,0,this::home);if(!shelfWarning.isEmpty())status.setText(shelfWarning);}
    private void shelfList(String kind,String flag){
        try{List<Action> actions=new ArrayList<>();for(org.json.JSONObject item:shelf.list(kind,flag)){String detail=flag.equals("follow")?(item.optInt("newEpisodes")>0?"新集数 +"+item.optInt("newEpisodes"):item.optString("remark",item.optString("latest"))):("nas".equals(item.optString("kind"))?"NAS":item.optString("episode"))+(item.optBoolean("complete")?" · 已看完":item.optLong("position")>0?" · "+item.optLong("position")/60000+"分钟":"");actions.add(new Action(item.optString("title")+(detail.isEmpty()?"":"\n"+detail),()->savedItem(item,()->shelfList(kind,flag))));}
            grid(flag.equals("follow")?"追剧":flag.equals("history")?"最近观看":"收藏",actions,0,this::shelfHome);shelfRefresh=()->shelfList(kind,flag);if(flag.equals("follow"))button(content,"检查更新",this::checkFollowing);
        }catch(Exception invalid){status.setText("观看记录无法读取");}
    }
    private void savedItem(org.json.JSONObject item,Runnable previous){
        TvDialogs.menu(this,item.optString("title"),new String[]{"继续观看","移除此记录"},(d,w)->{if(w==1){editShelf(next->next.remove(item));previous.run();return;}openSaved(item,previous);});
    }
    private void openSaved(org.json.JSONObject item,Runnable previous){
        if("nas".equals(item.optString("kind"))){try{NasActivity.openSaved(this,item);}catch(Exception invalid){status.setText(message(invalid));}return;}
        if("live".equals(item.optString("kind"))){for(CinemaCatalog.Live live:config.lives)if(live.key().equals(item.optString("source"))){currentLive=live;task(item.optString("title"),previous,()->http.live(live,config.allowHttp),channels->{for(int i=0;i<channels.size();i++)if(CinemaShelf.key(liveRef(live,channels.get(i))).equals(CinemaShelf.key(item))){playChannels(channels,i,0,previous);return;}throw new java.io.IOException("频道已移除，请在来源中重新选择");});return;}}
        else if("vod".equals(item.optString("kind"))){for(CinemaCatalog.Site site:config.sites)if(source(site).equals(item.optString("source"))){try{details(site,new CinemaCatalog.Item(new org.json.JSONObject().put("vod_id",item.getString("id")).put("vod_name",item.getString("title"))),previous);}catch(Exception invalid){status.setText("影片记录无法读取");}return;}}
        status.setText("来源已移除或更换，请重新配置来源");
    }
    private void checkFollowing(){
        final List<org.json.JSONObject> saved;try{saved=shelf.list("vod","follow");}catch(Exception invalid){status.setText("观看记录无法读取");return;}final CinemaCatalog.Config sourceSnapshot=config;final int expected=generation+1;
        task("检查追剧更新",()->shelfList("vod","follow"),()->{List<ShelfEdit> updates=new ArrayList<>();int checked=0;long budget=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);http.deadline(budget);try{for(org.json.JSONObject ref:saved){if(destroyed||generation!=expected||checked>=12||System.nanoTime()>=budget)break;for(CinemaCatalog.Site site:sourceSnapshot.sites)if(source(site).equals(ref.optString("source"))){checked++;try{CinemaCatalog.Page data=catalogue(site,null,1,null,ref.optString("id"));for(CinemaCatalog.Item item:data.items)if(item.id.equals(ref.optString("id")))updates.add(next->updateLatest(next,site,item));}catch(Exception unavailable){}break;}}}finally{http.deadline(0);}return updates;},updates->{editShelf(next->{for(ShelfEdit update:updates)update.apply(next);});shelfList("vod","follow");status.setText("已检查本轮可读取的来源；未检查项保留上次信息");});
    }
    private void updateLatest(CinemaShelf target,CinemaCatalog.Site site,CinemaCatalog.Item item)throws Exception{List<CinemaCatalog.Line> lines=CinemaCatalog.lines(item,config.allowHttp,site.headers,site.resolved());CinemaCatalog.Line longest=lines.get(0);for(CinemaCatalog.Line line:lines)if(line.episodes.size()>longest.episodes.size())longest=line;target.latest(vodRef(site,item),longest.episodes.size(),longest.episodes.get(longest.episodes.size()-1).name,item.remark,System.currentTimeMillis());}
    private void home(){
        sectionFullscreen=false;
        if("live".equals(view)){List<CinemaCatalog.Live> lives=CinemaLiveSources.visible(config.lives);if(!lives.isEmpty()){sectionLive(currentLive!=null&&lives.contains(currentLive)?currentLive:lives.get(0),0);return;}}
        else {for(CinemaCatalog.Site site:config.sites)if(site.supports(view)){sectionLoad(sectionSite!=null&&config.sites.contains(sectionSite)&&sectionSite.supports(view)?sectionSite:site,sectionCategory,sectionPage);return;}}
        screen(title(),this::finish);status.setText("尚未配置来源");button(content,"设置来源",this::settings).requestFocus();
    }
    private void liveSources(){
        List<Action> actions=new ArrayList<>();for(CinemaCatalog.Live live:CinemaLiveSources.visible(config.lives))actions.add(new Action(live.name,()->live(live)));grid("直播",actions,0,this::finish);if(actions.size()<config.lives.size())status.setText(actions.size()+" 个分组 · 相同配置已合并显示");
        if(actions.isEmpty()){button(content,"设置来源",this::settings).requestFocus();status.setText("尚未配置直播。可导入 M3U 或 TVBox 直播配置。");}
    }
    private void live(CinemaCatalog.Live source){
        currentLive=source;task(source.name,this::liveSources,()->http.live(source,config.allowHttp),channels->{liveAll=channels;groups(source.name,channels);guideLoader.load(config.epgs,config.allowHttp,(value,state)->{if(destroyed||currentLive!=source)return;updateGuide(value);});});
    }
    private void groups(String name,List<CinemaCatalog.Channel> channels){
        LinkedHashSet<String> names=new LinkedHashSet<>();for(CinemaCatalog.Channel c:channels)names.add(c.group);List<Action> actions=new ArrayList<>();
        if(names.size()==1){channels(name,names.iterator().next(),channels);return;}
        for(String group:names)actions.add(new Action(group,()->channels(name,group,channels)));grid(name,actions,0,this::liveSources);
    }
    private void channels(String name,String group,List<CinemaCatalog.Channel> all){
        channels(name,group,all,new GridCursor(0));
    }
    private void channels(String name,String group,List<CinemaCatalog.Channel> all,GridCursor cursor){
        List<CinemaCatalog.Channel> selected=new ArrayList<>();for(CinemaCatalog.Channel c:all)if(c.group.equals(group))selected.add(c);
        List<Action> actions=new ArrayList<>();for(int i=0;i<selected.size();i++){final int at=i;CinemaCatalog.Channel c=selected.get(i);actions.add(new Action(c,currentLive==null?"":currentLive.url,()->{
            if(c.urls.size()==1)playChannels(all,all.indexOf(c),0,()->{cursor.selected=Math.max(0,selected.indexOf(all.get(playingIndex)));channels(name,group,all,cursor);});else{String[] choices=new String[c.urls.size()];for(int j=0;j<choices.length;j++)choices[j]="线路 "+(j+1);new AlertDialog.Builder(this).setTitle(c.name).setItems(choices,(d,j)->playChannels(all,all.indexOf(c),j,()->{cursor.selected=Math.max(0,selected.indexOf(all.get(playingIndex)));channels(name,group,all,cursor);})).setNegativeButton("返回",null).show();}
        }));}grid(group,actions,cursor.selected/12,()->{boolean multiple=false;for(CinemaCatalog.Channel c:all)if(!c.group.equals(group)){multiple=true;break;}if(multiple)groups(name,all);else liveSources();},cursor);
    }
    private void playChannels(List<CinemaCatalog.Channel> channels,int at,int line,Runnable previous){try{playingChannels=new ArrayList<>(channels);List<CinemaCatalog.Episode> list=new ArrayList<>();List<org.json.JSONObject> refs=new ArrayList<>();for(int i=0;i<channels.size();i++){CinemaCatalog.Channel c=channels.get(i);list.add(new CinemaCatalog.Episode(c.name,c.urls.get(i==at?line:0),c.request(i==at?line:0)));refs.add(liveRef(currentLive,c));}play(list,at,true,previous,refs,"");}catch(Exception invalid){status.setText("频道无法打开");}}
    private void sites(){
        List<Action> actions=new ArrayList<>();for(CinemaCatalog.Site site:config.sites)if(site.supports(view))actions.add(new Action(site.name,()->categories(site)));grid(title(),actions,0,this::finish);
        if(actions.isEmpty()){button(content,"设置来源",this::settings).requestFocus();status.setText("尚未配置此分类。可导入开视资源包或 TVBox 配置。");}
    }
    private void categories(CinemaCatalog.Site site){sectionLoad(site,null,1);}
    private void search(CinemaCatalog.Site site){
        EditText input=new EditText(this);input.setSingleLine();input.setHint("片名");input.setTextColor(FG);input.setTextSize(22);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("搜索 · "+site.name).setView(input).setPositiveButton("搜索",null).setNegativeButton("返回",null).create();
        Runnable submit=()->{String word=input.getText().toString().trim();if(word.isEmpty()||word.length()>80){input.setError("请输入1至80字的片名");return;}((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);dialog.dismiss();filterYear="";filterArea="";videos(site,null,"搜索 · "+word,1,word);};
        dialog.setOnShowListener(d->{Button go=dialog.getButton(AlertDialog.BUTTON_POSITIVE);TvUi.styleButton(this,go);go.setOnClickListener(v->submit.run());input.setOnEditorActionListener((v,id,event)->{if(id==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH||id==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){submit.run();return true;}return false;});input.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getAction()==KeyEvent.ACTION_DOWN){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);go.requestFocus();}return true;}return false;});});dialog.show();settingsDialog=dialog;
    }
    private void videos(CinemaCatalog.Site site,String category,String name,int page,String word){
        sectionFullscreen=false;sectionSite=site;sectionCategory=category;if(page==1)browseScrollY=0;browseName=name;browseWord=word;browseActive=true;
        task(name,this::home,()->catalogue(site,category,page,word,null),data->{
            if(page==1)sectionItems=new ArrayList<>();java.util.Set<String> seen=new java.util.HashSet<>();for(CinemaCatalog.Item item:sectionItems)seen.add(item.id);for(CinemaCatalog.Item item:data.items)if(seen.add(item.id)&&sectionItems.size()<400)sectionItems.add(item);
            sectionPage=page;sectionPages=data.pages;renderBrowse();
        });
    }
    private void renderBrowse(){
        sectionFullscreen=false;browseActive=true;screen(browseName,this::finish);
        List<CinemaCatalog.Item> shown=CinemaBrowse.items(sectionItems,browseWord,filterYear,filterArea);
        status.setText("已加载 "+sectionItems.size()+" 部"+(filterYear.isEmpty()&&filterArea.isEmpty()?"":" · 筛选后 "+shown.size()+" 部")+(browseWord==null?"":" · 当前来源"));
        LinearLayout filters=new LinearLayout(this);content.addView(filters);button(filters,filterYear.isEmpty()?"年份 · 全部":filterYear,()->filter(false));button(filters,filterArea.isEmpty()?"地区 · 全部":filterArea,()->filter(true));
        GridLayout cells=new GridLayout(this);int columns=5;cells.setColumnCount(columns);content.addView(cells);
        for(int i=0;i<shown.size();i++){CinemaCatalog.Item item=shown.get(i);View tile=poster(new Action(item,sectionSite.api,()->{}),()->{browseScrollY=((ScrollView)content.getParent()).getScrollY();details(sectionSite,item,this::renderBrowse);});GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.columnSpec=GridLayout.spec(i%columns,1f);p.width=0;p.height=Math.round(getResources().getDisplayMetrics().heightPixels*.37f);p.setMargins(dp(5),dp(6),dp(5),dp(8));cells.addView(tile,p);if(i%columns==0&&sectionLayout!=null)sectionLayout.linkLeft(tile);}
        if(shown.isEmpty())content.addView(label(sectionItems.isEmpty()?"暂无影片，可搜索或切换来源":"已加载内容中没有匹配项，可清除筛选或加载更多",22));
        if(sectionPage<sectionPages&&sectionItems.size()<400)button(content,"加载更多",()->videos(sectionSite,sectionCategory,browseName,sectionPage+1,browseWord));
        else if(sectionItems.size()>=400)content.addView(label("已加载400部，请按分类或关键词缩小范围",18));
        if(cells.getChildCount()>0){int selected=0;for(int i=0;i<shown.size();i++)if(shown.get(i).id.equals(sectionItemId))selected=i;View chosen=cells.getChildAt(selected);sectionLayout.linkRight(chosen);chosen.requestFocus();ScrollView list=(ScrollView)content.getParent();list.post(()->list.scrollTo(0,browseScrollY));}
    }
    private void details(CinemaCatalog.Site site,CinemaCatalog.Item item,Runnable previous){
        sectionFullscreen=false;sectionItemId=item.id;
        task(item.name,previous,()->catalogue(site,null,1,null,item.id),data->{CinemaCatalog.Item found=null;for(CinemaCatalog.Item candidate:data.items)if(candidate.id.equals(item.id)){found=candidate;break;}if(found==null)throw new java.io.IOException("来源没有返回所选影片详情");final CinemaCatalog.Item detail=found;List<CinemaCatalog.Line> lines=CinemaCatalog.lines(detail,config.allowHttp,site.headers,site.resolved());org.json.JSONObject ref=vodRef(site,detail),saved=shelf.get(ref);List<Action> actions=new ArrayList<>();
            DetailSelection selection=new DetailSelection();boolean continuing=false;
            if(saved!=null&&saved.optLong("watchedAt")>0&&!saved.optBoolean("complete"))for(int l=0;l<lines.size();l++)if(lines.get(l).name.equals(saved.optString("line")))for(int i=0;i<lines.get(l).episodes.size();i++)if(lines.get(l).episodes.get(i).name.equals(saved.optString("episode"))){selection.line=l;selection.episode=i;continuing=true;}
            if(!lines.isEmpty())actions.add(new Action(continuing?"继续播放":"播放",()->playEpisodes(site,ref,lines,selection.line,selection.episode,()->details(site,item,previous))));
            actions.add(new Action(saved!=null&&saved.optBoolean("favorite")?"取消收藏":"收藏影片",()->{editShelf(next->next.toggle(ref,"favorite"));details(site,item,previous);}));
            if(!"movies".equals(view))actions.add(new Action(saved!=null&&saved.optBoolean("follow")?"取消追剧":"追剧",()->{editShelf(next->{next.toggle(ref,"follow");updateLatest(next,site,detail);});details(site,item,previous);}));
            editShelf(next->{updateLatest(next,site,detail);next.seen(ref);});detailPanel(site,detail,actions,lines,ref,previous,selection);
        });
    }
    private void playEpisodes(CinemaCatalog.Site site,org.json.JSONObject ref,CinemaCatalog.Line line,int at,Runnable previous){playEpisodes(site,ref,java.util.Collections.singletonList(line),0,at,previous);}
    private void playEpisodes(CinemaCatalog.Site site,org.json.JSONObject ref,List<CinemaCatalog.Line> lines,int line,int at,Runnable previous){
        long resume=0;try{resume=shelf.resume(ref,lines.get(line).name,lines.get(line).episodes.get(at).name);}catch(Exception ignored){}
        vodPlan=new CinemaPlaybackPlan(lines,line,at,resume);vodReference=ref;vodSite=site;vodBack=previous;attemptVod();
    }
    private void attemptVod(){
        final CinemaPlaybackPlan plan=vodPlan;final CinemaCatalog.Site site=vodSite;final org.json.JSONObject ref=vodReference;final Runnable previous=vodBack;
        task(ref.optString("title")+" · "+plan.label(),previous,()->{
            final int workTurn=generation;
            do{if(destroyed||workTurn!=generation)throw new java.io.IOException("读取已取消");try{return http.media(resolve(site,plan.current().name,plan.current().episodes.get(plan.episode)),config.allowHttp);}catch(PluginTrust trust){throw trust;}catch(Exception unavailable){if(Thread.currentThread().isInterrupted())throw unavailable;}}while(plan.next());return null;
        },media->{
            if(plan!=vodPlan)return;
            if(media==null){status.setText("没有可播放的线路");content.removeAllViews();button(content,"重试",()->{vodPlan=new CinemaPlaybackPlan(plan.lines,plan.line,plan.episode,plan.resume);attemptVod();}).requestFocus();button(content,"选择线路",this::failedVodLines);button(content,"返回详情",previous);return;}
            List<org.json.JSONObject> refs=new ArrayList<>();for(CinemaCatalog.Episode episode:plan.current().episodes)refs.add(ref);
            startPlayer(plan.current().episodes,plan.episode,false,previous,refs,plan.current().name,site,media);
            if(playerStatus!=null)playerStatus.setText("正在准备 · "+plan.label());if(plan.fallbackUsed)android.widget.Toast.makeText(this,"已切换至"+plan.label(),android.widget.Toast.LENGTH_SHORT).show();
        });
    }
    private void failedVodLines(){if(vodPlan==null)return;List<String> names=new ArrayList<>();for(int i=0;i<vodPlan.lines.size();i++)names.add("线路"+(i+1));TvDialogs.menu(this,"选择线路",names.toArray(new String[0]),(d,at)->switchVodLine(at));}
    private void switchVodLine(int at){
        if(vodPlan==null)return;rememberVodPosition();if(!vodPlan.choose(at)){android.widget.Toast.makeText(this,"此线路没有对应集数",android.widget.Toast.LENGTH_SHORT).show();return;}attemptVod();
    }
    private void playerLines(){if(vodPlan==null)return;List<Action> actions=new ArrayList<>();for(int i=0;i<vodPlan.lines.size();i++){final int at=i;actions.add(new Action("线路"+(i+1)+(i==vodPlan.line?" · 当前":""),()->switchVodLine(at)));}showPlayerMenu("线路",actions,vodPlan.line,false);}
    private void rememberVodPosition(){if(!liveQueue&&vodPlan!=null&&player!=null&&prepared)try{vodPlan.resume=player.getCurrentPosition();}catch(RuntimeException ignored){}}
    private void replayVod(int at,boolean sameEpisode){
        if(vodPlan==null)return;if(sameEpisode)rememberVodPosition();vodPlan=new CinemaPlaybackPlan(vodPlan.lines,vodPlan.line,at,sameEpisode?vodPlan.resume:0);attemptVod();
    }
    private void playerFailure(){
        if(!liveQueue&&vodPlan!=null&&display.active()&&failedTurn!=generation){rememberVodPosition();failedTurn=generation;final int turn=generation;
            if(vodPlan.next()){if(playerStatus!=null){playerStatus.setText("正在切换 · "+vodPlan.label());playerStatus.setVisibility(View.VISIBLE);}main.post(()->{if(!destroyed&&turn==generation&&display.active())attemptVod();});return;}}
        prepared=false;session.error();
    }
    private void episodes(CinemaCatalog.Site site,org.json.JSONObject ref,CinemaCatalog.Line line,Runnable previous,GridCursor cursor){
        List<Action> actions=new ArrayList<>();for(int i=0;i<line.episodes.size();i++){final int at=i;actions.add(new Action(line.episodes.get(i).name,()->playEpisodes(site,ref,line,at,()->{cursor.selected=playingIndex;episodes(site,ref,line,previous,cursor);})));}grid(ref.optString("title")+" · "+line.name,actions,cursor.selected/12,previous,cursor);
    }
    private void play(List<CinemaCatalog.Episode> list,int at,boolean live,Runnable previous,List<org.json.JSONObject> refs,String line){play(list,at,live,previous,refs,line,null);}
    private void play(List<CinemaCatalog.Episode> list,int at,boolean live,Runnable previous,List<org.json.JSONObject> refs,String line,CinemaCatalog.Site site){
        if(live)vodPlan=null;
        if(site!=null&&site.resolved())task(list.get(at).name,previous,()->resolve(site,line,list.get(at)),media->startPlayer(list,at,live,previous,refs,line,site,media));
        else startPlayer(list,at,live,previous,refs,line,site,list.get(at));
    }
    private void startPlayer(List<CinemaCatalog.Episode> list,int at,boolean live,Runnable previous,List<org.json.JSONObject> refs,String line,CinemaCatalog.Site site,CinemaCatalog.Episode media){
        sectionFullscreen=true;screen(list.get(at).name,previous);root.removeAllViews();root.setPadding(0,0,0,0);root.setBackgroundColor(Color.BLACK);queueSite=site;resolvedEpisode=media;hasPlayed=false;playingView=true;queueRefs=refs;queueLine=line;queue=list;playingIndex=at;liveQueue=live;playerBack=previous;entryIntent.begin(!live||display.active());session.begin();
        FrameLayout stage=new FrameLayout(this);playerStage=stage;stage.setBackgroundColor(Color.BLACK);SurfaceView surface=new SurfaceView(this);stage.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));videoSurface=surface;surface.setFocusable(true);surface.setFocusableInTouchMode(true);
        playerOverlay=new LinearLayout(this);playerOverlay.setOrientation(LinearLayout.VERTICAL);playerOverlay.setPadding(dp(40),dp(22),dp(40),dp(20));
        GradientDrawable fade=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0x90000000,0xed000000});playerOverlay.setBackground(fade);stage.addView(playerOverlay,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        org.json.JSONObject ref=refs.get(at);String name=ref.optString("title",list.get(at).name);
        TextView title=label(name+(!live&&list.size()>1?" · 第 "+(at+1)+" 集":""),24);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);playerOverlay.addView(title);
        playerStatus=status;playerOverlay.addView(status);playerStatus.setText("正在准备…");
        if(!live){timeline=new PlayerTimeline(this,new PlayerTimeline.Media(){public long position(){return player!=null&&prepared?player.getCurrentPosition():0;}public long duration(){return player!=null&&prepared?player.getDuration():0;}public int buffered(){return bufferedPercent;}public void seek(long position){if(player!=null&&prepared)player.seekTo((int)position);}},true);}
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);playerOverlay.addView(controls);
        playerToggle=PlayerChrome.control(this,"",this::togglePlayback);PlayerChrome.transport(this,playerToggle,false);controls.addView(playerToggle,new LinearLayout.LayoutParams(dp(56),dp(52)));
        if(!live)controls.addView(timeline,new LinearLayout.LayoutParams(0,dp(52),1));else{TextView hint=label("直播",18);hint.setTextColor(TvUi.MUTED);controls.addView(hint,new LinearLayout.LayoutParams(0,-2,1));}
        if(!live){controls.addView(PlayerChrome.control(this,"选集",this::playerEpisodes),new LinearLayout.LayoutParams(dp(100),dp(52)));if(vodPlan!=null&&vodPlan.lines.size()>1)controls.addView(PlayerChrome.control(this,"线路",this::playerLines),new LinearLayout.LayoutParams(dp(100),dp(52)));}
        if(live){programmePanel=new CinemaProgrammePanel(this,stage,playingChannels,guide,()->{if(videoSurface!=null)videoSurface.requestFocus();});livePanel=new CinemaLivePanel(this,stage,playingChannels,artwork,config.allowHttp,index->playChannels(playingChannels,index,0,playerBack),()->{if(videoSurface!=null)videoSurface.requestFocus();});livePanel.guide(guide);playerProgramme=label("",18);playerProgramme.setMaxLines(1);playerProgramme.setEllipsize(android.text.TextUtils.TruncateAt.END);playerOverlay.addView(playerProgramme);refreshGuideViews();guideLoader.load(config.epgs,config.allowHttp,(value,state)->{if(destroyed)return;updateGuide(value);});
            controls.addView(PlayerChrome.control(this,"频道",()->{playerOverlay.setVisibility(View.GONE);livePanel.show(playingIndex);}),new LinearLayout.LayoutParams(dp(100),dp(52)));}
        playerMore=PlayerChrome.control(this,"更多",this::playerOptions);controls.addView(playerMore,new LinearLayout.LayoutParams(dp(100),dp(52)));
        playerToggle.setNextFocusDownId(View.NO_ID);playerToggle.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_DOWN)playerMore.requestFocus();return true;}return false;});
        playerMore.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_DPAD_UP){if(event.getAction()==KeyEvent.ACTION_DOWN)playerToggle.requestFocus();return true;}return false;});
        if(sectionLayout!=null)sectionLayout.linkRight(surface);surface.requestFocus();showPlayerControls();int turn=generation;main.postDelayed(()->{if(!destroyed&&turn==generation&&session.state()==PlaybackSession.State.PREPARING){playerFailure();}},25000);
        surface.getHolder().addCallback(new SurfaceHolder.Callback(){
            public void surfaceCreated(SurfaceHolder holder){if(destroyed||turn!=generation)return;session.surface(true);prepare(holder,surface,stage,media.url,turn);}
            public void surfaceChanged(SurfaceHolder h,int f,int w,int t){}
            public void surfaceDestroyed(SurfaceHolder holder){if(turn!=generation)return;session.surface(false);if(player!=null)player.setDisplay(null);}
        });
    }
    private void togglePlayback(){entryIntent.cancel();if(session.state()==PlaybackSession.State.PLAYING||session.state()==PlaybackSession.State.PREPARING)session.pause();else session.play();showPlayerControls();}
    private void playerOptions(){
        if(timeline!=null)timeline.cancelEdit();
        List<Action> actions=new ArrayList<>();org.json.JSONObject ref=queueRefs.get(playingIndex);boolean favorite=false;try{org.json.JSONObject saved=shelf.get(ref);favorite=saved!=null&&saved.optBoolean("favorite");}catch(Exception ignored){}
        if(liveQueue&&!sectionFullscreen)actions.add(new Action("全屏播放",()->{int selected=playingIndex;sectionFullscreen=true;playChannels(playingChannels,selected,0,()->sectionLive(currentLive,selected));}));
        actions.add(new Action(favorite?"取消收藏":"收藏",()->editShelf(next->next.toggle(ref,"favorite"))));
        if(!liveQueue&&playingIndex>0)actions.add(new Action("上一集",()->adjacent(-1)));
        if(!liveQueue&&playingIndex+1<queue.size())actions.add(new Action("下一集",()->adjacent(1)));
        if(!liveQueue&&queue.size()>1)actions.add(new Action("选集",this::playerEpisodes));
        if(liveQueue&&programmePanel!=null)actions.add(new Action("节目单",()->{playerOverlay.setVisibility(View.GONE);programmePanel.show();}));
        if(!liveQueue)actions.add(new Action("从头播放",()->{if(player!=null&&prepared){player.seekTo(0);saveProgress(false);}}));
        if(!liveQueue)actions.add(new Action("播放速度",this::playbackSpeeds));
        if(liveQueue)actions.add(new Action("直播来源",()->{List<Action> sources=new ArrayList<>();for(CinemaCatalog.Live source:CinemaLiveSources.visible(config.lives))sources.add(new Action(source.name,()->sectionLive(source,0)));showPlayerMenu("直播来源",sources);}));
        actions.add(new Action("音轨",this::audioTracks));
        if(!liveQueue&&vodPlan!=null&&vodPlan.lines.size()>1)actions.add(new Action("切换线路",this::playerLines));
        actions.add(new Action("重试",()->{if(liveQueue)playChannels(playingChannels,playingIndex,0,playerBack);else replayVod(playingIndex,true);}));
        actions.add(new Action("退出播放",()->{if(playerBack!=null)playerBack.run();}));
        String[] labels=new String[actions.size()];for(int i=0;i<labels.length;i++)labels[i]=actions.get(i).name;
        showPlayerMenu("播放选项",actions);
    }
    private void playbackSpeeds(){List<Action> choices=new ArrayList<>();String[] labels={"0.75倍","正常","1.25倍","1.5倍","2倍"};float[] speeds={.75f,1,1.25f,1.5f,2};for(int i=0;i<labels.length;i++){final float speed=speeds[i];choices.add(new Action(labels[i],()->{if(player!=null&&prepared){boolean paused=!player.isPlaying();try{player.setPlaybackParams(player.getPlaybackParams().setSpeed(speed));if(paused)player.pause();}catch(RuntimeException unsupported){playerStatus.setText("当前视频不支持变速");playerStatus.setVisibility(View.VISIBLE);}}}));}showPlayerMenu("播放速度",choices);}
    private void audioTracks(){if(player==null||!prepared)return;android.media.MediaPlayer.TrackInfo[] tracks=player.getTrackInfo();List<Action> choices=new ArrayList<>();for(int i=0;i<tracks.length;i++)if(tracks[i].getTrackType()==android.media.MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO){final int id=i;choices.add(new Action("音轨 "+(choices.size()+1)+" · "+tracks[i].getLanguage(),()->{if(player!=null&&prepared)try{player.selectTrack(id);}catch(RuntimeException ignored){playerStatus.setText("音轨切换失败");playerStatus.setVisibility(View.VISIBLE);}}));}if(choices.isEmpty())choices.add(new Action("当前视频未提供可选音轨",()->{}));showPlayerMenu("音轨",choices);}
    private void playerEpisodes(){if(timeline!=null)timeline.cancelEdit();List<Action> actions=new ArrayList<>();for(int i=0;i<queue.size();i++){final int at=i;actions.add(new Action(queue.get(i).name,()->replayVod(at,false)));}showPlayerMenu("选集",actions,playingIndex,true);}
    private void closePlayerMenu(){if(playerMenu!=null&&playerStage!=null)playerStage.removeView(playerMenu);playerMenu=null;choicePanel=null;if(videoSurface!=null)videoSurface.requestFocus();showPlayerControls();}
    private void showPlayerMenu(String title,List<Action> actions){showPlayerMenu(title,actions,0,false);}
    private void showPlayerMenu(String title,List<Action> actions,int selected,boolean episodes){
        closePlayerMenu();if(playerStage==null||actions.isEmpty())return;main.removeCallbacks(hidePlayerControls);if(playerOverlay!=null)playerOverlay.setVisibility(View.GONE);
        List<String> names=new ArrayList<>();for(Action action:actions)names.add(action.name);
        choicePanel=new CinemaChoicePanel(this,playerStage,title,names,selected,episodes,at->{closePlayerMenu();actions.get(at).run.run();},this::closePlayerMenu);playerMenu=choicePanel.panel;
    }
    private final Runnable hidePlayerControls=()->{if(playingView&&!controlsConfirmHeld&&playerMenu==null&&session.state()==PlaybackSession.State.PLAYING&&(timeline==null||!timeline.interacting())&&(livePanel==null||!livePanel.open())&&(programmePanel==null||!programmePanel.open())&&playerOverlay!=null){playerOverlay.setVisibility(View.GONE);if(videoSurface!=null)videoSurface.requestFocus();}};
    private void showPlayerControls(){if(playerOverlay==null)return;playerOverlay.setVisibility(View.VISIBLE);main.removeCallbacks(hidePlayerControls);main.postDelayed(hidePlayerControls,5500);}
    private void prepare(SurfaceHolder holder,SurfaceView surface,FrameLayout stage,String url,int turn){
        if(player!=null){player.setDisplay(holder);return;}
        try{CinemaCatalog.url(url,null,config.allowHttp);player=new MediaPlayer();bufferedPercent=0;MediaPlayer p=player;p.setOnBufferingUpdateListener((m,percent)->{if(player==m)bufferedPercent=percent;});p.setAudioAttributes(PlaybackAudioFocus.attributes());p.setDisplay(holder);p.setDataSource(this,Uri.parse(url),resolvedEpisode.headers);
            p.setOnVideoSizeChangedListener((m,w,h)->{if(player!=m||w<=0||h<=0)return;stage.post(()->{if(player!=m)return;double scale=Math.min((double)stage.getWidth()/w,(double)stage.getHeight()/h);surface.setLayoutParams(new FrameLayout.LayoutParams(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)),Gravity.CENTER));});});
            p.setOnPreparedListener(m->{if(player==m&&turn==generation){prepared=true;long resume=0;try{if(!liveQueue)resume=vodPlan!=null?vodPlan.resume:shelf.resume(queueRefs.get(playingIndex),queueLine,queue.get(playingIndex).name);}catch(Exception ignored){}resume=Math.min(resume,Math.max(0,m.getDuration()-1000));if(resume>0){m.setOnSeekCompleteListener(done->{if(player==done&&turn==generation){done.setOnSeekCompleteListener(null);session.prepared();}});m.seekTo(resume,MediaPlayer.SEEK_CLOSEST);}else session.prepared();if(entryIntent.take(display.active()))session.play();}});p.setOnCompletionListener(m->{if(player==m&&turn==generation){saveProgress(true);session.completed();}});p.setOnErrorListener((m,a,b)->{if(player==m&&turn==generation){playerFailure();}return true;});p.prepareAsync();
        }catch(Exception invalid){playerFailure();}
    }
    private void playState(PlaybackSession.State state){
        if(state==PlaybackSession.State.ERROR||state==PlaybackSession.State.PAUSED||state==PlaybackSession.State.COMPLETED)entryIntent.cancel();
        if(playerStatus==null)return;String value;
        switch(state){case PLAYING:showPlayerControls();hasPlayed=true;saveProgress(false);value="正在播放";getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);break;case ERROR:showPlayerControls();value="播放失败，可切换线路或重试";break;case INTERRUPTED:value=entryIntent.pending()?"正在准备…":"播放已中断，按播放继续";break;case PAUSED:value="已暂停，按播放继续";break;case PREPARING:value="正在准备…";break;case COMPLETED:value="已播完";break;case FOCUS_DENIED:value="声音正忙，稍后按播放继续";break;default:value="已暂停，按播放继续";}
        if(state!=PlaybackSession.State.PLAYING){saveProgress(state==PlaybackSession.State.COMPLETED);}
        if(state!=PlaybackSession.State.PLAYING)getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);playerStatus.setText(value);playerStatus.setVisibility(state==PlaybackSession.State.PLAYING||state==PlaybackSession.State.PAUSED?View.GONE:View.VISIBLE);if(playerToggle!=null)PlayerChrome.transport(this,playerToggle,state==PlaybackSession.State.PLAYING);
    }
    private void adjacent(int delta){int next=playingIndex+delta;if(next>=0&&next<queue.size()){if(liveQueue)playChannels(playingChannels,next,0,playerBack);else replayVod(next,false);}}
    private void seek(int delta){if(!liveQueue&&prepared&&player!=null)try{player.seekTo(Math.max(0,Math.min(player.getDuration(),player.getCurrentPosition()+delta)));}catch(RuntimeException ignored){}}
    private void settings(){
        closeSettings();if(root==null)home();
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);
        int catalogues=0,collections=0;for(CinemaCatalog.Site site:config.sites){if(site.type==5)collections++;else catalogues++;}TextView description=label(catalogues+" 个影视站点"+(collections==0?"":" · "+collections+" 个片单")+" · "+CinemaLiveSources.visible(config.lives).size()+" 个直播来源\n"+health,18);form.addView(description);
        EditText address=new EditText(this);address.setTextSize(18);address.setTextColor(FG);address.setSingleLine();address.setHint("开视资源包 / TVBox / M3U 地址");address.setHintTextColor(MUTED);form.addView(address);
        CheckBox allow=new CheckBox(this);allow.setText("允许本次导入 HTTP 来源");allow.setTextColor(FG);allow.setTextSize(18);allow.setChecked(config.allowHttp);form.addView(allow);
        button(form,"读取地址",()->{String value=address.getText().toString().trim();boolean policy=allow.isChecked();task("读取来源",this::settings,()->http.importUrl(value,policy),next->preview(next,0));});
        button(form,"导入本机文件",()->{fileAllowHttp=allow.isChecked();try{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,PICK);}catch(RuntimeException missing){status.setText("当前系统没有文件选择器，请使用配置地址");}});
        if(!config.packId.isEmpty())button(form,"当前资源包",()->new AlertDialog.Builder(this).setTitle(config.packTitle).setMessage("ID："+config.packId+"\n修订："+config.packRevision+"\nSHA-256："+config.packSha256).setPositiveButton("返回",null).show());
        button(form,"WorkBuddy 导入资源包",()->startActivityForResult(new Intent(this,ResourcePackAgentActivity.class),4603));
        button(form,"封面与资料",this::metadataSettings);
        button(form,"检查来源",()->checkSources(true));
        button(form,"节目单地址",this::guideSettings);
        button(form,"观看记录与收藏",this::shelfHome);
        button(form,"检查插件更新",()->{candidates.clear();plugins.clear();home();});
        button(form,"撤销插件授权",()->new AlertDialog.Builder(this).setTitle("撤销所有插件授权？").setMessage("下次打开插件站点时需要重新确认。").setPositiveButton("撤销",(d,w)->{plugins.revokeAll();candidates.clear();settings();}).setNegativeButton("返回",null).show());
        button(form,"清空观看记录",()->new AlertDialog.Builder(this).setTitle("清空观看记录？").setMessage("保留收藏和追剧。").setPositiveButton("清空",(d,w)->{editShelf(CinemaShelf::clearHistory);settings();}).setNegativeButton("返回",null).show());
        button(form,"家庭服务片库",()->{setResult(RESULT_OK,new Intent().putExtra("cinemaServer",view));finish();});
        button(form,"清除本机来源",()->new AlertDialog.Builder(this).setTitle("清除直播和影视来源？").setMessage("不删除NAS文件或第三方应用资料。").setPositiveButton("清除",(d,w)->{vault.clear();config=new CinemaCatalog.Config(false);health="尚未检查";settings();}).setNegativeButton("返回",null).show());
        settingsDialog=TvDialogs.panel(this,"来源设置",form);
    }
    private void guideSettings(){
        EditText input=new EditText(this);input.setTextColor(FG);input.setTextSize(20);input.setHint("XMLTV 或 XMLTV.gz 地址，每行一个，最多4个");input.setText(String.join("\n",config.epgs));LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.addView(input);TextView error=label("使用自己的节目单来源；频道按tvg-id或名称匹配",18);form.addView(error);
        button(form,"保存节目单",()->{try{List<String> urls=new ArrayList<>();for(String line:input.getText().toString().split("\n"))if(!line.trim().isEmpty())urls.add(CinemaCatalog.url(line.trim(),null,config.allowHttp));if(urls.size()>4)throw new java.io.IOException("最多4个节目单地址");vault.guideSources(urls,true);config.epgs.clear();config.epgs.addAll(urls);closeSettings();if(playingView&&programmePanel!=null)guideLoader.load(config.epgs,config.allowHttp,(value,state)->updateGuide(value));else home();}catch(Exception bad){error.setText(message(bad));}});closeSettings();settingsDialog=TvDialogs.panel(this,"节目单地址",form);
    }
    private void preview(CinemaCatalog.Config next){preview(next,0);}
    private void preview(CinemaCatalog.Config next,int depth){
        if(!next.warehouses.isEmpty()){if(depth>=3){status.setText("仓库嵌套超过3层，请直接使用配置地址");return;}List<Action> choices=new ArrayList<>();for(CinemaCatalog.Site feed:next.warehouses)choices.add(new Action(feed.name,()->task(feed.name,this::settings,()->http.importUrl(feed.api,next.allowHttp),child->preview(child,depth+1))));grid("选择配置",choices,0,this::settings);return;}
        try{if(!CinemaResourcePack.shouldApply(config,next)){status.setText("该资源包已导入");return;}}catch(java.io.IOException conflict){status.setText(conflict.getMessage());return;}
        String note=next.sites.size()+" 个影视站点，"+CinemaLiveSources.visible(next.lives).size()+" 个直播分组。\n替换本机现有来源。";
        if(CinemaLiveSources.visible(next.lives).size()<next.lives.size())note+="\n输入文件有重复配置，列表合并显示，历史ID保留。";
        if(!next.packId.isEmpty())note=next.packTitle+" · 修订 "+next.packRevision+"\n"+note+"\nSHA-256："+next.packSha256;
        for(int i=0;i<Math.min(8,next.skipped.size());i++)note+="\n"+next.skipped.get(i);
        new AlertDialog.Builder(this).setTitle("导入来源").setMessage(note).setPositiveButton("保存",(d,w)->{try{vault.save(next);candidates.clear();plugins.clear();config=next;health="等待检查";home();checkSources(false);}catch(Exception failed){status.setText("无法保存来源，请重试");}}).setNegativeButton("返回",(d,w)->settings()).show();
    }
    private static final class Probe {final String name;final Work<String> read;Probe(String name,Work<String> read){this.name=name;this.read=read;}}
    private void checkSources(boolean interactive){
        if(interactive)screen("来源检测",this::settings);int turn=generation;CinemaCatalog.Config snapshot=config;
        if(interactive)status.setText("正在检查，最多25秒…");
        final int start=healthCursor;
        try{io.execute(()->{if(destroyed||turn!=generation)return;List<Probe> probes=new ArrayList<>();
            for(CinemaCatalog.Site site:snapshot.sites)probes.add(new Probe(site.name,()->{if(site.type==3)return "插件按需检查";CinemaCatalog.Page page=http.home(site,snapshot.allowHttp);return !page.categories.isEmpty()||!page.items.isEmpty()?"目录可读取":"目录为空";}));
            for(CinemaCatalog.Live live:snapshot.lives)probes.add(new Probe(live.name,()->{List<CinemaCatalog.Channel> channels=live.url.isEmpty()?live.channels:http.live(live,snapshot.allowHttp);return http.probe(channels.get(0).urls.get(0),snapshot.allowHttp,channels.get(0).request(0));}));
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
        super.onActivityResult(request,result,data);if(request==4603){try{config=vault.read();candidates.clear();plugins.clear();home();}catch(Exception failure){status.setText("来源无法重新读取");}return;}if(request!=PICK||result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(!"content".equals(uri.getScheme()))return;boolean policy=fileAllowHttp;
        task("读取配置文件",this::settings,()->{try(InputStream stream=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(stream==null)throw new java.io.IOException("文件不可读取");byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1){if(out.size()+n>CinemaCatalog.MAX_BYTES)throw new java.io.IOException("配置超过512KB");out.write(buffer,0,n);}return CinemaCatalog.config(new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8),null,policy);}},this::preview);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();boolean down=event.getAction()==KeyEvent.ACTION_DOWN;
        if(VoiceAssistant.systemVoiceKey(key))return super.dispatchKeyEvent(event);
        boolean confirm=key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER;
        if(confirm){if(down&&event.getRepeatCount()==0)confirmPress.down(key,event.getDownTime(),generation);else if(!down&&!confirmPress.up(key,event.getDownTime(),generation,event.isCanceled())){controlsConfirmHeld=false;playerConfirmAction=null;if(livePanel!=null)livePanel.cancelPress();if(choicePanel!=null)choicePanel.cancelPress();return true;}}
        if(playingView&&choicePanel!=null){if(choicePanel.key(event))return true;return super.dispatchKeyEvent(event);}
        if(playingView&&programmePanel!=null&&programmePanel.key(event))return true;
        if(playingView&&programmePanel!=null&&programmePanel.open())return super.dispatchKeyEvent(event);
        if(playingView&&liveQueue&&livePanel!=null&&livePanel.key(event))return true;
        if(playingView&&confirm){
            if(down&&event.getRepeatCount()==0){controlsConfirmHeld=true;main.removeCallbacks(hidePlayerControls);View focus=getCurrentFocus();
                if(focus!=null&&focus!=videoSurface&&playerOverlay!=null&&playerOverlay.getVisibility()==View.VISIBLE&&focus instanceof Button)playerConfirmAction=()->focus.performClick();
                else playerConfirmAction=()->{if(liveQueue&&livePanel!=null){playerOverlay.setVisibility(View.GONE);livePanel.show(playingIndex);}else playerEpisodes();};
            }else if(!down){controlsConfirmHeld=false;Runnable action=playerConfirmAction;playerConfirmAction=null;if(action!=null)action.run();}return true;
        }
        boolean videoFocus=getCurrentFocus()==videoSurface||playerOverlay==null||playerOverlay.getVisibility()!=View.VISIBLE;
        if(playingView&&!liveQueue&&timeline!=null&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)&&videoFocus){showPlayerControls();return timeline.remoteKey(event);}
        if(playingView&&liveQueue&&videoFocus&&(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN)){if(down&&event.getRepeatCount()==0)adjacent(key==KeyEvent.KEYCODE_DPAD_DOWN?1:-1);return true;}
        if(playingView&&key==KeyEvent.KEYCODE_BACK){if(!down){if(timeline!=null)timeline.cancelEdit();onBackPressed();}return true;}
        if(playingView&&(key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN)&&videoFocus){if(down){showPlayerControls();if(playerToggle!=null)playerToggle.requestFocus();}return true;}
        if(playingView&&key==KeyEvent.KEYCODE_MENU){if(down&&event.getRepeatCount()==0)playerOptions();return true;}
        if(playingView&&(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||key==KeyEvent.KEYCODE_MEDIA_PLAY||key==KeyEvent.KEYCODE_MEDIA_PAUSE||key==KeyEvent.KEYCODE_MEDIA_REWIND||key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD||key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN)){
            if(down&&event.getRepeatCount()==0)switch(key){case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:togglePlayback();break;case KeyEvent.KEYCODE_MEDIA_PLAY:session.play();break;case KeyEvent.KEYCODE_MEDIA_PAUSE:entryIntent.cancel();session.pause();break;case KeyEvent.KEYCODE_MEDIA_REWIND:seek(-30000);break;case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:seek(30000);break;case KeyEvent.KEYCODE_CHANNEL_UP:adjacent(1);break;case KeyEvent.KEYCODE_CHANNEL_DOWN:adjacent(-1);break;}return true;
        }
        if(playingView&&down&&key>=KeyEvent.KEYCODE_DPAD_UP&&key<=KeyEvent.KEYCODE_DPAD_RIGHT)showPlayerControls();
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed(){if(back!=null)back.run();else finish();}
    @Override protected void onResume(){super.onResume();display.resumed(true);if(vault!=null)try{shelf=vault.shelf();shelfWarning="";if(shelfRefresh!=null)shelfRefresh.run();}catch(Exception invalid){shelfWarning="观看记录无法读取，旧记录未覆盖";}}
    @Override protected void onPause(){confirmPress.cancel();controlsConfirmHeld=false;playerConfirmAction=null;entryIntent.cancel();display.resumed(false);if(plugins!=null)plugins.cancelPending();super.onPause();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus){if(livePanel!=null)livePanel.cancelPress();if(choicePanel!=null)choicePanel.cancelPress();confirmPress.cancel();controlsConfirmHeld=false;playerConfirmAction=null;entryIntent.cancel();}display.focused(focus);if(focus&&prepared&&entryIntent.take(display.active()))session.play();}
    @Override protected void onDestroy(){closeSettings();destroyed=true;invalidate();main.removeCallbacksAndMessages(null);release();io.shutdownNow();if(artwork!=null)artwork.close();if(info!=null)info.close();if(guideLoader!=null)guideLoader.close();if(plugins!=null)plugins.clear();super.onDestroy();}
}
