package org.zhixiaoce.aperture;

import android.content.Intent;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.MediaDataSource;
import android.media.MediaPlayer;
import android.media.AudioManager;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native direct NAS entry. Works without a computer, WebView or household service. */
public final class NasActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(android.os.Looper.getMainLooper());
    private CinemaArtwork artwork;private CinemaInfo info;
    private NasVault vault;
    private CinemaVault viewing;
    private JSONObject watching;
    private NasReader.Entry playingEntry;
    private boolean hasPlayed, savedLaunch;
    private long resumePosition;
    private String pendingVideoPath="", progressWarning="";
    private Button favorite;
    private final Runnable checkpoint=new Runnable(){public void run(){if(!destroyed){saveProgress(false);updatePlayback();main.postDelayed(this,15000);}}};

    /** Called only by the native library. Never passes NAS credentials or URLs through an Intent. */
    static void openSaved(Activity owner,JSONObject item)throws Exception {
        NasWatchRoute route=NasWatchRoute.resolve(item,new NasVault(owner).library());
        owner.startActivity(new Intent(owner,NasActivity.class).putExtra("watch-id",route.connection.getString("id")).putExtra("watch-path",route.path).putExtra("watch-root",route.root));
    }
    private ApkInstaller installer;
    private final DisplayLifecycle display = new DisplayLifecycle();
    private final NasNavigation navigation = new NasNavigation();
    private NasReader reader;
    private NasReader.Resource resource;
    private JSONObject connection;
    private LinearLayout content;
    private String folder = "", filter = "all", mode = "connections";
    private String category = "", focusedPath = "", resumeId = "";
    private List<NasReader.Entry> entries = new ArrayList<>();
    private volatile int generation;
    private int page;
    private boolean busy;
    private volatile boolean destroyed;
    private MediaPlayer player;
    private boolean prepared;
    private PlaybackSession session;
    private TextView playback;private LinearLayout videoOverlay;private SurfaceView videoSurface;private Button videoToggle;
    private final Runnable hideControls=()->{if(mode.equals("video")&&session!=null&&session.state()==PlaybackSession.State.PLAYING&&videoOverlay!=null){videoOverlay.setVisibility(View.GONE);if(videoSurface!=null)videoSurface.requestFocus();}};
    private void showControls(){if(videoOverlay==null)return;videoOverlay.setVisibility(View.VISIBLE);main.removeCallbacks(hideControls);main.postDelayed(hideControls,5500);}
    private Bitmap shownPhoto;
    private static final int INK = Color.rgb(241,244,248), MUTED = Color.rgb(177,190,205);
    private static final String[] BRANDS = NasDraft.BRANDS;
    private static final String[] HELP = {
        "控制面板 → 文件服务 → SMB；也可启用 HTTPS WebDAV。请创建只能读取家庭文件夹的账号。",
        "优先 SMB。R1 官方资料列出 SMB；不同型号需确认版本及签名支持，不默认提供 WebDAV。",
        "仅适配最新发布的 Xiaomi 智能存储，按 SMB 路线核验。若设备提供 SMB 共享，请使用其地址及专用账号；不预设支持 WebDAV，不包含旧路由器存储。该机型尚待实测。",
        "控制面板 → 文件服务 → SMB，允许 SMB2.1/3；WebDAV 需安装 WebDAV Server，使用 HTTPS 最终地址。",
        "创建 Dataset 和 SMB 共享，给专用账号读取权限；使用共享名，不填写管理后台地址。",
        "同一家庭网络内，输入共享地址和专用只读账号。WebDAV 必须 HTTPS，保留证书校验。"
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); artwork=new CinemaArtwork(this);info=new CinemaInfo(this);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); vault = new NasVault(this); viewing = new CinemaVault(this);
        installer = new ApkInstaller(this, () -> new DestinationApps(this, getSharedPreferences("aperture", MODE_PRIVATE), slot -> {}).shortcuts());
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        session = new PlaybackSession(new PlaybackAudioFocus((AudioManager)getSystemService(AUDIO_SERVICE),main), new PlaybackSession.Output() {
            public void start() { if (player == null || !prepared) throw new IllegalStateException(); player.start(); }
            public void pause() { if (player != null && prepared && player.isPlaying()) player.pause(); }
            public void changed(PlaybackSession.State state) { showControls();if(state==PlaybackSession.State.PLAYING)hasPlayed=true;saveProgress(state==PlaybackSession.State.COMPLETED);updatePlayback(); }
        });
        display.bind(session::active);
        filter = "photo".equals(getIntent().getStringExtra("filter")) ? "photo" : "all";
        String requested = getIntent().getStringExtra("category"); category = NasLibrary.role(requested) ? requested : "";
        main.postDelayed(checkpoint,15000);
        if(getIntent().hasExtra("watch-id"))launchSaved();else if (category.isEmpty()) connections(); else launchCategory();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size, int color) { TextView view = new TextView(this); view.setText(text); view.setTextSize(TvUi.typeSize(size)); view.setTextColor(color); view.setPadding(0, dp(3), 0, dp(4)); view.setTextIsSelectable(false); return view; }
    private AlertDialog connectionDialog;
    private void closeConnectionDialog(){if(connectionDialog!=null){connectionDialog.dismiss();connectionDialog=null;}}
    private void screen(String title, String subtitle) {
        closeConnectionDialog();main.removeCallbacks(hideControls);videoOverlay=null;videoSurface=null;videoToggle=null;if(artwork!=null)artwork.cancel();if(info!=null)info.cancel();
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(38), dp(22), dp(38), dp(30));
        content.setBackground(TvUi.background());
        TextView heading = label(title, 30, INK); heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD); content.addView(heading); content.addView(label(subtitle, 17, MUTED));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(content); setContentView(scroll);
        if (shownPhoto != null) { shownPhoto.recycle(); shownPhoto = null; }
    }
    private Button button(LinearLayout parent, String title, Runnable action) {
        Button button = new Button(this); button.setText(title); button.setTextSize(BuildConfig.PARENTS_EDITION?28:24); button.setMaxLines(2); button.setEllipsize(android.text.TextUtils.TruncateAt.END); button.setTextColor(INK); button.setAllCaps(false); button.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL); button.setPadding(dp(18),dp(8),dp(18),dp(8));
        StateListDrawable states = new StateListDrawable();
        for (boolean focus : new boolean[]{true,false}) { GradientDrawable shape = new GradientDrawable(); shape.setColor(focus ? Color.rgb(57,78,91) : Color.argb(180,29,43,56)); shape.setCornerRadius(dp(14)); shape.setStroke(dp(focus?3:1),focus?Color.rgb(222,236,244):Color.argb(90,161,185,204)); states.addState(focus ? new int[]{android.R.attr.state_focused} : new int[]{},shape); }
        TvUi.styleButton(this,button);button.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL); LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0,dp(5),0,dp(5)); button.setMinHeight(dp(44)); parent.addView(button,params); button.setOnClickListener(v -> action.run()); return button;
    }
    private Button button(String title, Runnable action) { return button(content,title,action); }
    private void message(String title, String text) { if (!destroyed) new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("知道了",null).show(); }
    private void connections() {
        category=""; savedLaunch=false; pendingVideoPath=""; navigation.reset(""); focusedPath=""; resumeId="";
        mode="connections"; invalidate(); release(); disconnect(); connection=null;
        screen("NAS", "SMB 2/3 · HTTPS WebDAV");
        try {
            JSONArray all = vault.read();
            for(int i=0;i<all.length();i++) { JSONObject item=all.getJSONObject(i); button(item.getString("name")+"  ·  "+item.getString("brand"),()-> choose(item)); }
            if(all.length()<8) button("添加连接",this::brandPicker);
            if(all.length()<8) button("用手机 / 助手配置 NAS",()->startActivity(new android.content.Intent(this,NasSetupActivity.class)));
            button("首页观看入口 · 查看 / 恢复家庭服务",this::homeRoutes);
        } catch(Exception invalid) {
            content.addView(label("本机连接记录无法解密，请重新添加。",20,INK));
            button("清除无法读取的记录",()->new AlertDialog.Builder(this).setMessage("清除本机 NAS 连接记录？不会修改 NAS 文件。").setPositiveButton("清除",(d,w)->{vault.reset();connections();}).setNegativeButton("返回",null).show());
        }
        button("返回开视",this::finish);
    }
    private void choose(JSONObject item) {
        new AlertDialog.Builder(this).setTitle(item.optString("name")).setItems(new String[]{"浏览这个文件夹","忘记本机连接"},(d,w)->{
            if(w==0){connection=item;folder="";page=0;focusedPath="";navigation.reset("");connect(false);}
            else new AlertDialog.Builder(this).setMessage("忘记后，对应的首页观看绑定也会移除。NAS 文件不受影响。").setPositiveButton("忘记",(a,b)->{try{vault.remove(item.getString("id"));connections();}catch(Exception error){message("未能移除","请重试。");}}).setNegativeButton("返回",null).show();
        }).setNegativeButton("返回",null).show();
    }
    private void launchCategory() {
        try {
            NasLibrary.Route route = vault.library().route(category);
            if (route == null) throw new IllegalStateException();
            boolean same = route.id.equals(resumeId) && navigation.root().equals(route.path);
            connection = route.connection;
            if (!same) { navigation.reset(route.path); folder = route.path; page = 0; focusedPath = ""; }
            filter = "photos".equals(category) ? "photo" : "video"; connect(false);
        } catch (Exception unavailable) {
            invalidate(); release(); disconnect(); mode="error";
            screen("这个片库还没准备好", "请家人检查NAS连接或重新选择首页片库。不会自动打开另一个NAS目录。"); button("返回开视",this::finish);
        }
    }
    private void launchSaved() {
        savedLaunch=true;
        try {
            JSONObject ref=NasWatchRoute.ref(getIntent().getStringExtra("watch-id"),getIntent().getStringExtra("watch-path"),getIntent().getStringExtra("watch-root"));
            NasWatchRoute route=NasWatchRoute.resolve(ref,vault.library());
            connection=route.connection;navigation.reset(route.root);folder=route.folder;filter="video";page=0;focusedPath=route.path;pendingVideoPath=route.path;connect(false);
        } catch(Exception missing) { savedUnavailable(missing); }
    }
    private void savedUnavailable(Exception reason) {
        invalidate();release();disconnect();mode="error";
        screen("无法打开这条记录",reason instanceof IOException?reason.getMessage():"原 NAS 连接或文件路径无效，请从片库重新选择。");button("返回观看记录",this::finish);
    }
    private void connectedListing() {
        String requested=pendingVideoPath;pendingVideoPath="";
        if(!requested.isEmpty()) {
            int visibleIndex=0;
            for(NasReader.Entry entry:entries){
                boolean visible=entry.directory||NasLocation.kind(entry.name).equals("video");
                if(!entry.directory&&entry.path.equals(requested)&&visible){focusedPath=entry.path;page=visibleIndex/20;navigation.save(folder,page,focusedPath);video(entry);return;}
                if(visible)visibleIndex++;
            }
            listing();message("影片已移动或无法读取","原目录里没有找到这部影片，请重新选择。观看记录仍保留。");
        } else listing();
    }
    private void exitLibrary() { if(savedLaunch)finish();else if (category.isEmpty()) connections(); else finish(); }
    private String exitLabel(){return savedLaunch?"返回观看记录":category.isEmpty()?"返回连接列表":"返回开视";}
    private void homeRoutes() {
        try {
            NasLibrary library=vault.library(); String[] labels=new String[NasLibrary.ROLES.length];
            for(int i=0;i<labels.length;i++){String role=NasLibrary.ROLES[i];NasLibrary.Route route=library.route(role);labels[i]=NasLibrary.title(role)+" · "+(route==null?(role.equals("photos")?"选择NAS时再打开":"家庭服务"):route.connection.optString("name")+(route.path.isEmpty()?"":" / "+route.path));}
            new AlertDialog.Builder(this).setTitle("首页入口用哪个片库").setItems(labels,(d,index)->{
                String role=NasLibrary.ROLES[index];new AlertDialog.Builder(this).setMessage("恢复后，观看入口使用家庭服务；NAS相册返回连接列表。NAS文件不受影响。")
                    .setPositiveButton("恢复默认",(a,b)->{try{vault.clear(role);homeRoutes();}catch(Exception error){message("未能保存","请返回后重试。");}}).setNegativeButton("保留",null).show();
            }).setNegativeButton("返回",null).show();
        }catch(Exception invalid){message("入口记录不可读取","请检查本机NAS连接记录。");}
    }
    private void assignHome() {
        final JSONObject selected=connection; final String selectedPath=folder;
        new AlertDialog.Builder(this).setTitle("把这个目录放到首页入口").setItems(new String[]{"看电影","看电视剧","NAS相册"},(d,index)->{
            String role=NasLibrary.ROLES[index];
            new AlertDialog.Builder(this).setMessage("将「"+selected.optString("name")+(selectedPath.isEmpty()?"":" / "+selectedPath)+"」用于「"+NasLibrary.title(role)+"」？只更改电视本机入口，不移动文件。")
                .setPositiveButton("使用这个目录",(a,b)->{try{vault.assign(role,selected.getString("id"),selectedPath);message("已放到入口", "下次从「"+(BuildConfig.PARENTS_EDITION||role.equals("photos")?NasLibrary.title(role):"影视仓 → 我的家庭片库")+"」打开这个目录。家人设置中可恢复原来的家庭服务。");}catch(Exception error){message("未能保存入口","连接可能已经变化，请返回后重试。");}}).setNegativeButton("返回",null).show();
        }).setNegativeButton("返回",null).show();
    }
    private void brandPicker(){ new AlertDialog.Builder(this).setTitle("选择家里的 NAS").setItems(BRANDS,(d,w)->form(w)).setNegativeButton("返回",null).show(); }
    private EditText input(String title,String hint,boolean password) {
        content.addView(label(title,18,INK)); EditText field=new EditText(this);field.setSingleLine(true);field.setTextSize(17);field.setTextColor(INK);field.setHintTextColor(MUTED);field.setHint(hint);field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|(password?android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD:android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));content.addView(field);return field;
    }
    private void form(int brand) {
        closeConnectionDialog();
        LinearLayout previousContent=content,formContent=new LinearLayout(this);formContent.setOrientation(LinearLayout.VERTICAL);formContent.addView(label(HELP[brand],17,MUTED));content=formContent;
        EditText name=input("在电视上叫什么","例如：周末电影 / 一家人的照片",false),address=input("共享地址","smb://192.168.1.20/Family 或 https://nas.example/Family/",false),user=input("NAS 只读账号","不使用管理后台账号",false),password=input("密码","只保存在本机加密记录里",true),domain=input("域（通常留空）","仅 SMB 企业域需要",false);
        Button save=button("测试目录并保存",()->{
            if(busy)return;
            try {
                NasDraft draft=new NasDraft(new JSONObject().put("name",name.getText().toString()).put("brand",BRANDS[brand]).put("root",address.getText().toString()).put("user",user.getText().toString()).put("password",password.getText().toString()).put("domain",domain.getText().toString()));
                connection=draft.connection;folder="";page=0;connect(true);
            }catch(Exception error){message("检查连接资料",error instanceof IllegalArgumentException?error.getMessage():"请检查输入。");}
        });
        content=previousContent;connectionDialog=TvDialogs.panel(this,"添加 · "+BRANDS[brand],formContent);name.requestFocus();
    }
    private void connect(boolean save) {
        invalidate();release();disconnect();mode="loading";int turn=generation;JSONObject config=connection;
        screen("正在连接…","只测试选定目录的只读访问。不会上传、删除或修改原文件。");button("取消连接",this::exitLibrary);busy=true;
        io.execute(()->{
            NasReader next=null;char[] password=config.optString("password").toCharArray();
            try {
                next=NasReader.connect(new NasLocation(config.getString("root")),config.getString("user"),password,config.optString("domain"));List<NasReader.Entry> found=next.list(folder);NasReader connected=next;
                main.post(()->{if(destroyed||generation!=turn){closeLater(connected);return;}busy=false;
                    try{if(save)connection=vault.add(config);reader=connected;entries=found;connectedListing();}catch(Exception error){closeLater(connected);message("未保存连接","本机加密记录不可用或已满，请返回后重试。");exitLibrary();}
                });
            }catch(Exception error){quietClose(next);String reason=error instanceof IOException?error.getMessage():"NAS 地址或服务不可读取";main.post(()->{if(destroyed||generation!=turn)return;busy=false;mode="error";screen("暂时连不上",reason);button("重试",()->connect(save));button(exitLabel(),this::exitLibrary);});}
            finally{Arrays.fill(password,'\0');}
        });
    }
    private void listing() {
        mode="listing";screen(category.isEmpty()?connection.optString("name"):NasLibrary.title(category),"已连接 · "+connection.optString("name")+(folder.isEmpty()?"":" / "+folder));
        LinearLayout tools=new LinearLayout(this);content.addView(tools);
        if(category.isEmpty())for(String type:new String[]{"all","photo","video"}){String title=type.equals("all")?"全部":type.equals("photo")?"相册":"视频";Button tab=button(tools,(filter.equals(type)?"● ":"")+title,()->{filter=type;page=0;focusedPath="";listing();});LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(dp(3),0,dp(3),0);tab.setLayoutParams(params);}
        List<NasReader.Entry> visible=new ArrayList<>();for(NasReader.Entry e:entries)if(e.directory||filter.equals("all")||NasLocation.kind(e.name).equals(filter))visible.add(e);
        int pages=Math.max(1,(visible.size()+19)/20);page=Math.max(0,Math.min(page,pages-1));
        content.addView(label("第 "+(page+1)+" / "+pages+" 页 · "+visible.size()+" 项",16,MUTED));
        if(visible.isEmpty())content.addView(label("这个分区还没有可显示的内容。",22,INK));
        View restore=null,first=null;
        GridLayout files=new GridLayout(this);files.setColumnCount(1);content.addView(files);
        for(int i=page*20;i<Math.min(visible.size(),page*20+20);i++){
            NasReader.Entry item=visible.get(i);String type=item.directory?"文件夹":NasLocation.kind(item.name).equals("photo")?"照片":NasLocation.kind(item.name).equals("video")?"影片":InstallCache.apkName(item.name)?"安装包":"文件";
            Runnable open=()->{focusedPath=item.path;navigation.save(folder,page,focusedPath);if(item.directory)load(item.path);else if(type.equals("照片"))photo(item);else if(type.equals("影片"))video(item);else if(type.equals("安装包"))installer.fromNas(reader,item);else message("暂不打开此格式","当前只读照片和视频，不执行文件中的程序或配置。");};
            LinearLayout tile=new LinearLayout(this);tile.setGravity(android.view.Gravity.CENTER_VERTICAL);tile.setPadding(dp(12),dp(8),dp(12),dp(8));tile.setBackground(TvUi.states(this));tile.setFocusable(true);tile.setClickable(true);tile.setContentDescription(type+" · "+item.name);tile.setOnClickListener(v->open.run());
            FrameLayout iconFrame=new FrameLayout(this);TextView symbol=label(item.directory?"▰":type.equals("照片")?"▧":type.equals("影片")?"▷":"▱",30,MUTED);symbol.setGravity(android.view.Gravity.CENTER);iconFrame.addView(symbol,new FrameLayout.LayoutParams(-1,-1));ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);iconFrame.addView(cover,new FrameLayout.LayoutParams(-1,-1));tile.addView(iconFrame,new LinearLayout.LayoutParams(dp(32),dp(40)));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(14),0,0,0);tile.addView(copy,new LinearLayout.LayoutParams(0,-2,1));TextView name=label(item.name,BuildConfig.PARENTS_EDITION?24:21,INK);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(name);TextView kind=label(type,17,MUTED);tile.addView(kind,new LinearLayout.LayoutParams(dp(90),-2));
            GridLayout.LayoutParams params=new GridLayout.LayoutParams();params.columnSpec=GridLayout.spec(0,1f);params.width=0;params.height=dp(56);params.setMargins(dp(2),dp(3),dp(2),dp(3));files.addView(tile,params);
            if(type.equals("影片")&&i<page*20+12){String mediaKind="series".equals(category)||"variety".equals(category)?"series":"movie";int turn=generation;info.lookup(item.name,"",mediaKind,false,(match,candidates,state)->{if(destroyed||generation!=turn||match==null)return;artwork.bind(cover,match.optString("poster"),"",false);kind.setText(match.optString("year")+" · "+match.optString("provider"));});}
            if(first==null)first=tile;if(item.path.equals(focusedPath))restore=tile;
        }
        if(page>0)button("上一页",()->{page--;listing();});if(page+1<pages)button("下一页",()->{page++;listing();});
        button(navigation.parent(folder)==null?exitLabel():"返回上一级",()->{String parent=navigation.parent(folder);if(parent==null)exitLibrary();else load(parent);});button("重新连接 / 检测",()->connect(false));
        if(category.isEmpty())button("家人设置 · 将当前目录用于首页入口",this::assignHome);
        if(restore!=null)restore.requestFocus();else if(first!=null)first.requestFocus();
    }
    private void load(String path){
        if(busy||!navigation.contains(path))return;navigation.save(folder,page,focusedPath);invalidate();release();int turn=generation;busy=true;mode="loading-folder";NasReader source=reader;screen("读取文件夹…","只浏览这个文件夹，不递归扫描整台 NAS。");button("返回原目录",()->{invalidate();listing();});
        io.execute(()->{try{List<NasReader.Entry> list=source.list(path);main.post(()->{if(destroyed||generation!=turn)return;busy=false;folder=path;NasNavigation.Position position=navigation.position(path);page=position.page;focusedPath=position.focus;entries=list;listing();});}catch(Exception error){main.post(()->{if(destroyed||generation!=turn)return;busy=false;listing();message("文件夹不可读取",error instanceof IOException?error.getMessage():"请检查文件夹与账号权限。");});}});
    }
    private void photo(NasReader.Entry entry){
        if(busy)return;invalidate();int turn=generation;busy=true;mode="photo";NasReader source=reader;screen(entry.name,"正在从家里的 NAS 读取照片…");button("返回文件夹",()->{invalidate();listing();});
        io.execute(()->{Bitmap image=null;try(NasReader.Resource file=source.open(entry.path)){
            if(file.size()<=0||file.size()>12000000)throw new IOException("照片需小于12MB");byte[] data=new byte[(int)file.size()];int position=0;while(position<data.length){if(destroyed||generation!=turn)return;int n=file.readAt(position,data,position,Math.min(65536,data.length-position));if(n<=0)throw new IOException("照片未读取完整");position+=n;}
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,options);if(options.outWidth<1||options.outHeight<1||(long)options.outWidth*options.outHeight>50000000)throw new IOException("照片尺寸不支持");options.inSampleSize=1;while(options.outWidth/options.inSampleSize>1920||options.outHeight/options.inSampleSize>1080)options.inSampleSize*=2;options.inJustDecodeBounds=false;image=BitmapFactory.decodeByteArray(data,0,data.length,options);if(image==null)throw new IOException("照片编码不支持");Bitmap result=orient(image,data);
            main.post(()->{if(destroyed||generation!=turn){result.recycle();return;}busy=false;mode="photo";screen(entry.name,"照片仅在本机显示，不自动上传到家庭服务。");shownPhoto=result;ImageView view=new ImageView(this);view.setImageBitmap(result);view.setAdjustViewBounds(true);view.setMaxHeight(dp(420));content.addView(view,new LinearLayout.LayoutParams(-1,dp(350)));button("返回文件夹",this::listing);});
        }catch(Exception error){main.post(()->{if(destroyed||generation!=turn)return;busy=false;listing();message("无法显示照片",error instanceof IOException?error.getMessage():"请换用 JPEG / PNG / WebP 照片。");});}});
    }
    private Bitmap orient(Bitmap image,byte[] bytes){
        try{int orientation=new android.media.ExifInterface(new java.io.ByteArrayInputStream(bytes)).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);android.graphics.Matrix matrix=new android.graphics.Matrix();
            switch(orientation){case 2:matrix.setScale(-1,1);break;case 3:matrix.setRotate(180);break;case 4:matrix.setRotate(180);matrix.postScale(-1,1);break;case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;case 6:matrix.setRotate(90);break;case 7:matrix.setRotate(-90);matrix.postScale(-1,1);break;case 8:matrix.setRotate(-90);break;default:return image;}
            Bitmap rotated=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),matrix,true);if(rotated!=image)image.recycle();return rotated;
        }catch(IOException invalid){return image;}
    }
    private void video(NasReader.Entry entry){
        if(busy)return;invalidate();release();
        try {
            watching=NasWatchRoute.ref(connection.getString("id"),entry.path,navigation.root());
            playingEntry=entry;resumePosition=viewing.shelf().resume(watching,"NAS",watching.optString("title"));progressWarning="";
        }catch(Exception unavailable){resumePosition=0;progressWarning="观看记录暂不可读取";}
        session.begin();int turn=generation;busy=true;mode="video";NasReader source=reader;screen(entry.name,"正在打开影片…");button("返回文件夹",()->{invalidate();release();listing();});
        io.execute(()->{try{NasReader.Resource opened=source.open(entry.path);main.post(()->{if(destroyed||generation!=turn){closeLater(opened);return;}busy=false;resource=opened;mode="video";screen(entry.name,"NAS 原文件直放 · 解码取决于电视，当前不转码。");
            FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(Color.BLACK);SurfaceView surface=new SurfaceView(this);stage.addView(surface,new FrameLayout.LayoutParams(-1,-1,android.view.Gravity.CENTER));videoSurface=surface;surface.setFocusable(true);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(8),dp(24),dp(10));content.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x550b0d10,0xf70b0d10}));videoOverlay=content;stage.addView(content,new FrameLayout.LayoutParams(-1,-2,android.view.Gravity.BOTTOM));setContentView(stage);content.addView(label(entry.name,28,INK));playback=label("准备中，请稍候…",18,MUTED);content.addView(playback);
            LinearLayout controls=new LinearLayout(this);content.addView(controls);
            Button playButton=button(controls,"播放 / 暂停",()->{if(session.state()==PlaybackSession.State.PLAYING||session.state()==PlaybackSession.State.PREPARING)session.pause();else session.play();});button(controls,"后退30秒",()->seek(-30000));button(controls,"前进30秒",()->seek(30000));for(int i=0;i<controls.getChildCount();i++){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(dp(3),0,dp(3),0);controls.getChildAt(i).setLayoutParams(params);}
            LinearLayout options=new LinearLayout(this);content.addView(options);
            favorite=button(options,"收藏",this::toggleFavorite);updateFavorite();
            button(options,"从头播放",this::startOver);
            button(options,"重试",()->video(entry));
            for(int i=0;i<options.getChildCount();i++){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(dp(3),0,dp(3),0);options.getChildAt(i).setLayoutParams(params);}
            button("返回文件夹",()->{invalidate();release();listing();});videoToggle=playButton;playButton.requestFocus();showControls();
            surface.getHolder().addCallback(new SurfaceHolder.Callback(){public void surfaceCreated(SurfaceHolder holder){if(generation!=turn||destroyed)return;session.surface(true);prepare(holder,opened,turn,surface,stage);}public void surfaceChanged(SurfaceHolder h,int f,int w,int t){}public void surfaceDestroyed(SurfaceHolder holder){if(generation!=turn||destroyed)return;session.surface(false);if(player!=null)player.setDisplay(null);}});
        });}catch(Exception error){main.post(()->{if(destroyed||generation!=turn)return;busy=false;release();listing();message("影片不可读取",error instanceof IOException?error.getMessage():"请检查文件与权限。");});}});
    }
    private void prepare(SurfaceHolder surface,NasReader.Resource file,int turn,SurfaceView view,FrameLayout stage){
        if(generation!=turn||destroyed)return;
        if(player!=null){player.setDisplay(surface);return;}
        try{player=new MediaPlayer();MediaPlayer current=player;current.setAudioAttributes(PlaybackAudioFocus.attributes());current.setDisplay(surface);current.setDataSource(new MediaDataSource(){public long getSize(){return file.size();}public int readAt(long p,byte[] b,int o,int n)throws IOException{return file.readAt(p,b,o,n);}public void close(){closeLater(file);}});
            current.setOnVideoSizeChangedListener((p,w,h)->{if(player!=p||w<1||h<1)return;stage.post(()->{if(player!=p)return;int width=stage.getWidth(),height=stage.getHeight();double scale=Math.min((double)width/w,(double)height/h);view.setLayoutParams(new FrameLayout.LayoutParams(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)),android.view.Gravity.CENTER));});});
            current.setOnPreparedListener(p->{
                if(player!=p||generation!=turn)return;prepared=true;
                long position=resumePosition;resumePosition=0;
                if(position>0&&p.getDuration()>position+3000) {
                    p.setOnSeekCompleteListener(seek->{if(player==seek&&generation==turn){seek.setOnSeekCompleteListener(null);session.prepared();}});
                    p.seekTo((int)Math.min(Integer.MAX_VALUE,position));
                }else session.prepared();
            });
            current.setOnCompletionListener(p->{if(player==p&&generation==turn){saveProgress(true);session.completed();}});
            current.setOnErrorListener((p,a,b)->{if(player==p&&generation==turn){saveProgress(false);prepared=false;session.error();}return true;});current.prepareAsync();
            main.postDelayed(()->{if(player==current&&generation==turn&&!prepared)session.error();},25000);
        }catch(Exception error){prepared=false;session.error();}
    }
    private void toggleFavorite(){
        if(watching==null)return;
        try{viewing.updateShelf(next->next.toggle(watching,"favorite"));progressWarning="";updateFavorite();}
        catch(Exception unavailable){progressWarning="收藏未保存，请检查本机记录";updatePlayback();}
    }
    private void updateFavorite(){
        if(favorite==null||watching==null)return;
        try{JSONObject record=viewing.shelf().get(watching);favorite.setText(record!=null&&record.optBoolean("favorite")?"取消收藏":"收藏");}
        catch(Exception unavailable){favorite.setText("收藏");}
    }
    private void startOver(){
        if(player==null||!prepared)return;
        try{final MediaPlayer selected=player;session.pause();selected.setOnSeekCompleteListener(p->{if(player==p){p.setOnSeekCompleteListener(null);hasPlayed=true;saveAtStart();session.play();}});selected.seekTo(0);}
        catch(RuntimeException ignored){}
    }
    private void saveAtStart(){
        if(watching==null||playingEntry==null)return;
        try{long duration=Math.max(0,player.getDuration());viewing.updateShelf(next->next.watch(watching,"NAS",watching.optString("title"),0,duration,false,System.currentTimeMillis()));progressWarning="";}
        catch(Exception unavailable){progressWarning="进度未保存，请检查本机记录";}
    }
    private void saveProgress(boolean complete){
        if(watching==null||playingEntry==null||player==null||!prepared||!hasPlayed)return;
        try{
            long position=player.getCurrentPosition(),duration=player.getDuration();boolean finished=complete||session.state()==PlaybackSession.State.COMPLETED;
            viewing.updateShelf(next->next.watch(watching,"NAS",watching.optString("title"),position,duration,finished,System.currentTimeMillis()));progressWarning="";
        }catch(Exception unavailable){progressWarning="进度未保存，请检查本机记录";}
    }
    private void updatePlayback(){
        if(playback==null||session==null)return;
        String title;
        switch(session.state()){
            case PLAYING:title="正在播放";break;
            case INTERRUPTED:title="已暂停，按播放继续";break;
            case FOCUS_DENIED:title="声音正忙，稍后按播放继续";break;
            case COMPLETED:title="已播完，按播放重看";break;
            case ERROR:playback.setText("电视无法播放或读取中断，请返回后重试。可换用 H.264/AAC MP4。");return;
            case PREPARING:title="准备中，请稍候…";break;
            case IDLE:return;
            default:title="已暂停，按播放继续";
        }
        if(player!=null&&prepared)try{title+=" · "+player.getCurrentPosition()/1000+" / "+player.getDuration()/1000+" 秒";}catch(RuntimeException ignored){}
        playback.setText(title+(progressWarning.isEmpty()?"":" · "+progressWarning));
    }
    private void seek(int delta){if(player!=null&&prepared)try{player.seekTo(Math.max(0,Math.min(player.getDuration(),player.getCurrentPosition()+delta)));updatePlayback();}catch(RuntimeException ignored){}}
    private void invalidate(){generation++;busy=false;}
    private void closeLater(java.io.Closeable object){if(object==null)return;try{io.execute(()->quietClose(object));}catch(RejectedExecutionException stopped){Thread closer=new Thread(()->quietClose(object),"nas-close");closer.setDaemon(true);closer.start();}}
    private static void quietClose(java.io.Closeable object){try{if(object!=null)object.close();}catch(Exception ignored){}}
    private void disconnect(){NasReader old=reader;reader=null;if(old!=null)closeLater(old);}
    private void release(){main.removeCallbacks(hideControls);videoOverlay=null;videoSurface=null;videoToggle=null;saveProgress(false);if(session!=null)session.reset();prepared=false;hasPlayed=false;watching=null;playingEntry=null;resumePosition=0;favorite=null;if(player!=null){player.release();player=null;}playback=null;NasReader.Resource old=resource;resource=null;if(old!=null)closeLater(old);}
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event){if(VoiceAssistant.systemVoiceKey(event.getKeyCode()))return super.dispatchKeyEvent(event);if(mode.equals("video")&&event.getAction()==android.view.KeyEvent.ACTION_DOWN){int key=event.getKeyCode();if(key>=19&&key<=23){boolean hidden=videoOverlay!=null&&videoOverlay.getVisibility()!=View.VISIBLE;showControls();if(hidden){if(videoToggle!=null)videoToggle.requestFocus();return true;}}if(key==android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){if(session.state()==PlaybackSession.State.PLAYING)session.pause();else session.play();return true;}if(key==android.view.KeyEvent.KEYCODE_MEDIA_REWIND){seek(-30000);showControls();return true;}if(key==android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD){seek(30000);showControls();return true;}}return super.dispatchKeyEvent(event);}
    @Override public void onBackPressed(){if(mode.equals("video")||mode.equals("photo")||mode.equals("loading-folder")){invalidate();release();listing();}else if(mode.equals("listing")){String parent=navigation.parent(folder);if(parent==null)exitLibrary();else load(parent);}else if(mode.equals("connections"))finish();else exitLibrary();}
    @Override protected void onResume(){super.onResume();display.resumed(true);}
    @Override protected void onPause(){display.resumed(false);saveProgress(false);super.onPause();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);display.focused(focus);}
    @Override protected void onStop(){resumeId=connection==null?"":connection.optString("id");if(connection!=null)navigation.save(folder,page,focusedPath);invalidate();release();disconnect();connection=null;super.onStop();}
    @Override protected void onActivityResult(int request,int result,Intent data){if(installer!=null&&installer.result(request,result,data))return;super.onActivityResult(request,result,data);}
    @Override protected void onRestart(){super.onRestart();if(!category.isEmpty()){launchCategory();return;}try{JSONObject saved=vault.library().find(resumeId);if(saved!=null){connection=saved;connect(false);return;}if(savedLaunch){savedUnavailable(new IOException("原 NAS 连接已移除，请重新添加并从片库打开影片"));return;}}catch(Exception invalid){if(savedLaunch){savedUnavailable(invalid);return;}}connections();}
    @Override protected void onDestroy(){closeConnectionDialog();if(installer!=null)installer.destroy();destroyed=true;display.bind(null);invalidate();release();disconnect();io.shutdown();if(artwork!=null)artwork.close();if(info!=null)info.close();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
