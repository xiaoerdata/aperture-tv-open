package org.zhixiaoce.aperture;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.*;
import android.widget.*;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

/** Offline scenic home for old or unavailable WebView. Uses no renderer or GPU blur API. */
final class ScenicNativeHome extends android.view.ViewGroup {
    interface Actions { void open(String action); }
    private final Activity activity;
    private final DestinationApps apps;
    private final Actions actions;
    private final List<Item> items=new ArrayList<>();
    private final Handler clockHandler=new Handler(android.os.Looper.getMainLooper());
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private Bitmap wallpaper, softWallpaper;
    private float scale=1,offsetX,offsetY;
    private int page,entryPage;
    private boolean foreground=true;
    private HomeAlbumView album;private AppWallView appWall;private Button firstTool;
    private final Map<TextView,Integer> iconSizes=new IdentityHashMap<>();
    private TextView time,date,caption,messagePreview;
    private Button first,dock,firstShortcut;
    private final Runnable ticker=new Runnable(){public void run(){Date now=new Date();time.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(now));date.setText(new SimpleDateFormat("M月d日   EEEE",Locale.CHINA).format(now));clockHandler.postDelayed(this,1000*(60-now.getSeconds()));}};
    private static final String[] IDS={"carezza","namib","fuji","lofoten"};
    private static final String[] FILES={"carezza-4k.jpg","namib-4k.jpg","fuji-4k.jpg","lofoten-4k.jpg"};
    private static final String[] PLACES={"意大利 · 卡雷扎湖","纳米比亚 · 纳米布沙漠","日本 · 富士宫云海","挪威 · 罗弗敦极光"};
    private static final class Item {
        final View view;final int x,y,w,h,size,screen;
        Item(View v,int x,int y,int w,int h,int size,int screen){this.view=v;this.x=x;this.y=y;this.w=w;this.h=h;this.size=size;this.screen=screen;}
    }
    ScenicNativeHome(Activity activity,DestinationApps apps,Actions actions){
        super(activity);this.activity=activity;this.apps=apps;this.actions=actions;setWillNotDraw(false);setFocusable(false);setClipChildren(false);
        String saved=activity.getSharedPreferences("aperture",0).getString("native_wallpaper","carezza");int selected=Arrays.asList(IDS).indexOf(saved);loadWallpaper(Math.max(0,selected));
        ImageView logo=new ImageView(activity);try(InputStream in=activity.getAssets().open(BuildConfig.PARENTS_EDITION?"brand/kaishi-parents-v2.webp":"brand/kaishi-icon-v2.webp")){logo.setImageDrawable(Drawable.createFromStream(in,null));}catch(Exception ignored){}logo.setContentDescription("开视");logo.setTag("native-brand-logo");place(logo,100,BuildConfig.PARENTS_EDITION?62:44,BuildConfig.PARENTS_EDITION?52:88,BuildConfig.PARENTS_EDITION?52:88,0,-1);
        place(label(BuildConfig.PARENTS_EDITION?"开视 · 爸妈版":"开视"),BuildConfig.PARENTS_EDITION?168:210,59,400,58,BuildConfig.PARENTS_EDITION?36:44,-1);
        Button settings=control("设置",()->actions.open("settings"),false);place(settings,1692,58,128,64,26,-1);settings.setTag("native-settings");
        time=label("");time.setTypeface(Typeface.create("serif",Typeface.NORMAL));place(time,100,205,650,176,152,0);
        date=label("");place(date,108,399,650,58,32,0);
        Button weather=control("天气  ›",()->actions.open("weather"),!BuildConfig.PARENTS_EDITION);place(weather,BuildConfig.PARENTS_EDITION?762:1396,BuildConfig.PARENTS_EDITION?304:500,BuildConfig.PARENTS_EDITION?232:300,BuildConfig.PARENTS_EDITION?88:132,30,0);weather.setNextFocusUpId(settings.getId());
        String[] names={"看电视","看电视剧","看电影","看综艺"},routes={"live","series","movies","variety"};
        for(int i=0;i<names.length;i++){final String route=routes[i];Button b=control(names[i],()->actions.open(route),true);place(b,100+i*(BuildConfig.PARENTS_EDITION?404:324),500,BuildConfig.PARENTS_EDITION?380:304,132,34,0);if(i==3){b.setNextFocusRightId(weather.getId());weather.setNextFocusLeftId(b.getId());}b.setNextFocusUpId(settings.getId());if(i==0)first=b;b.setOnKeyListener((v,key,e)->{if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0)(firstShortcut!=null?firstShortcut:dock).requestFocus();return true;}return false;});}
        if(!BuildConfig.PARENTS_EDITION)weather.setOnKeyListener((v,key,e)->{if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0)(firstShortcut!=null?firstShortcut:dock).requestFocus();return true;}return false;});
        dock=control("应用与工具   ﹀",()->showPage(1),true);place(dock,100,870,300,88,27,0);dock.setOnKeyListener((v,key,e)->{if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0)showPage(1);return true;}if(key==KeyEvent.KEYCODE_DPAD_UP){if(e.getAction()==KeyEvent.ACTION_DOWN)first.requestFocus();return true;}return false;});
        caption=label(PLACES[Math.max(0,selected)]);caption.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);place(caption,1230,845,590,50,20,0);
        place(label("↓  应用与工具"),100,978,650,50,23,0);
        place(label("应用与工具"),100,195,1000,80,48,1);
        org.json.JSONObject appSnapshot=apps.homeApps();int count=0;Button firstApp=null;
        for(String slot:new String[]{"quick1","quick2","quick3","quick4"}){
            String name=apps.label(slot);if(name.isEmpty())continue;byte[] icon=apps.icon(slot);Drawable appIcon=icon==null?null:Drawable.createFromStream(new java.io.ByteArrayInputStream(icon),null);Button b=iconControl(appIcon,()->apps.launchShortcut(slot));b.setContentDescription(name);if(BuildConfig.PARENTS_EDITION)place(b,100+count*250,326,176,150,28,1);TextView appName=label(name);appName.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);appName.setMaxLines(2);if(BuildConfig.PARENTS_EDITION)place(appName,88+count*250,484,200,64,25,1);if(firstApp==null)firstApp=b;
            {Button shortcut=control(name,()->apps.launchShortcut(slot),true);if(icon!=null){Drawable d=Drawable.createFromStream(new java.io.ByteArrayInputStream(icon),null);if(d!=null){iconSizes.put(shortcut,48);d.setBounds(0,0,48,48);shortcut.setCompoundDrawables(d,null,null,null);shortcut.setCompoundDrawablePadding(12);}}place(shortcut,100+count*276,708,252,112,25,0);if(firstShortcut==null)firstShortcut=shortcut;shortcut.setOnKeyListener((v,key,e)->{if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(e.getAction()==KeyEvent.ACTION_DOWN)dock.requestFocus();return true;}return false;});}count++;
        }
        if(count==0){if(BuildConfig.PARENTS_EDITION)place(control("添加常用应用",apps::shortcuts,true),100,326,380,132,30,1);firstShortcut=control("添加常用应用",BuildConfig.PARENTS_EDITION?apps::shortcuts:apps::addShortcut,true);place(firstShortcut,100,708,300,112,25,0);Button emptyShortcut=firstShortcut;emptyShortcut.setOnKeyListener((v,key,e)->{if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(e.getAction()==KeyEvent.ACTION_DOWN)dock.requestFocus();return true;}return false;});}
        if(BuildConfig.PARENTS_EDITION){int manageX=100+Math.max(count,1)*250;Button manage=control("✎",apps::shortcuts,true);manage.setContentDescription("管理常用应用");place(manage,manageX,326,176,150,34,1);TextView manageName=label("管理应用");manageName.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);place(manageName,manageX-12,484,200,64,25,1);}
        if(!BuildConfig.PARENTS_EDITION){appWall=new AppWallView(activity,apps,appSnapshot,()->showPage(0),()->{if(firstTool!=null)firstTool.requestFocus();else showPage(2);});place(appWall,88,292,1744,520,0,1);}
        Button diagnostics=null;if(!BuildConfig.PARENTS_EDITION){diagnostics=control("本机诊断",()->actions.open("diagnostics"),true);place(diagnostics,1460,195,360,64,27,1);diagnostics.setTag("native-diagnostics");}
        final Button diagnosticEntry=diagnostics;
        String[] tools={"所有应用","NAS","原桌面","壁纸","天气","屏保"};String[] routes2={"apps","nas","system","wallpaper","weather","ambient"};
        for(int i=0;i<tools.length;i++){final String route=routes2[i];Button tool=control(tools[i],()->{if(route.equals("wallpaper"))chooseWallpaper();else actions.open(route);},true);place(tool,100+i*286,BuildConfig.PARENTS_EDITION?592:850,264,BuildConfig.PARENTS_EDITION?104:64,28,1);if(i==0)firstTool=tool;if(!BuildConfig.PARENTS_EDITION)tool.setOnKeyListener((v,key,e)->{if(key==KeyEvent.KEYCODE_DPAD_UP){if(e.getAction()==KeyEvent.ACTION_DOWN&&appWall!=null)appWall.last();return true;}return false;});if(BuildConfig.PARENTS_EDITION&&diagnosticEntry!=null&&i>=3){tool.setNextFocusDownId(diagnosticEntry.getId());if(i==tools.length-1)diagnosticEntry.setNextFocusUpId(tool.getId());}}
        place(control("返回首页  ↑",()->showPage(0),false),100,BuildConfig.PARENTS_EDITION?870:948,300,BuildConfig.PARENTS_EDITION?76:48,27,1);
        if(!BuildConfig.PARENTS_EDITION){
            album=new HomeAlbumView(activity,()->actions.open("gallery"));
            place(album,0,0,1920,1080,0,2);bringChildToFront(settings);
            place(label("留言"),100,195,1000,80,48,3);
            messagePreview=label("暂无留言");messagePreview.setGravity(Gravity.TOP);messagePreview.setMaxLines(7);messagePreview.setEllipsize(android.text.TextUtils.TruncateAt.END);place(messagePreview,100,330,1100,400,32,3);
            place(control("扫码编辑留言",()->actions.open("information"),true),100,790,400,88,28,3);
        }
        setContentDescription("风景首页 · 本机兼容模式");
    }
    private void loadWallpaper(int selected){
        Bitmap next=null;try(InputStream in=activity.getAssets().open(FILES[selected])){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;next=BitmapFactory.decodeStream(in,null,o);}catch(Exception ignored){}
        if(next==null)return;Bitmap old=wallpaper,soft=softWallpaper;wallpaper=next;Bitmap tiny=Bitmap.createScaledBitmap(next,96,54,true);softWallpaper=Bitmap.createScaledBitmap(tiny,384,216,true);tiny.recycle();if(old!=null)old.recycle();if(soft!=null)soft.recycle();if(caption!=null)caption.setText(PLACES[selected]);invalidate();
    }
    private void chooseWallpaper(){new TvDialogs.SideBuilder(activity).setTitle("壁纸").setItems(PLACES,(d,i)->{loadWallpaper(i);activity.getSharedPreferences("aperture",0).edit().putString("native_wallpaper",IDS[i]).apply();}).setNegativeButton("返回",null).show();}
    private TextView label(String text){TextView v=new TextView(activity);v.setText(text);v.setTextColor(TvUi.INK);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);v.setShadowLayer(10,0,2,0x66000000);return v;}
    void messages(byte[] bytes){if(messagePreview==null)return;try{org.json.JSONArray rows=new org.json.JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("messages");StringBuilder text=new StringBuilder();for(int i=0;i<Math.min(rows.length(),3);i++){if(i>0)text.append("\n\n");text.append(rows.getJSONObject(i).optString("text"));}messagePreview.setText(text.length()==0?"暂无留言":text.toString());}catch(Exception unavailable){messagePreview.setText("留言暂时无法读取");}}
    private Button iconControl(Drawable icon,Runnable run){
        Button b=new Button(activity){@Override protected void onDraw(Canvas c){super.onDraw(c);if(icon!=null){int edge=Math.max(1,Math.round(Math.min(getWidth(),getHeight())*.64f));int x=(getWidth()-edge)/2,y=(getHeight()-edge)/2;icon.setBounds(x,y,x+edge,y+edge);icon.draw(c);}}};
        b.setText(icon==null?"▦":"");b.setGravity(Gravity.CENTER);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(0,0,0,0);b.setBackground(new Pane(b,true));b.setOnClickListener(v->run.run());TvUi.touchClick(b);return b;
    }
    private Button control(String text,Runnable run,boolean glass){Button b=new Button(activity);b.setText(text);b.setTextColor(TvUi.INK);b.setAllCaps(false);b.setFocusableInTouchMode(true);b.setGravity(Gravity.CENTER);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(8,0,8,0);b.setBackground(new Pane(b,glass));b.setOnFocusChangeListener((v,f)->v.invalidate());b.setOnClickListener(v->run.run());TvUi.touchClick(b);return b;}
    private void place(View v,int x,int y,int w,int h,int size,int screen){v.setId(View.generateViewId());items.add(new Item(v,x,y,w,h,size,screen));addView(v,v instanceof HomeAlbumView?0:getChildCount());}
    private void showPage(int value){page=value;if(album!=null)album.active(foreground&&page==2);for(Item i:items)i.view.setVisibility((i.screen<0&&page!=2)||i.screen==page?VISIBLE:GONE);requestLayout();if(page==0){first.requestFocus();return;}if(page==1&&appWall!=null){appWall.first();return;}if(page==2&&album!=null){album.requestFocus();return;}for(Item i:items)if(i.screen==page&&i.view instanceof Button){i.view.requestFocus();break;}}
    void showApps(){entryPage=1;showPage(1);}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(page==1&&appWall!=null&&appWall.hasFocus())return super.dispatchKeyEvent(e);
        if(!BuildConfig.PARENTS_EDITION&&e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){
            if(page==1&&e.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN&&getFocusedChild()!=null){int id=getFocusedChild().getNextFocusDownId();View next=id==View.NO_ID?null:findViewById(id);if(next!=null&&next.isShown()){next.requestFocus();return true;}}
            if(page==2&&e.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP){showPage(1);return true;}
            if(page==2&&e.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN){showPage(3);return true;}
            if(page==3&&e.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP){showPage(2);return true;}
            if(page==1&&e.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN&&getFocusedChild()!=null&&getFocusedChild().getTop()>=getHeight()*.5f){showPage(2);return true;}
            if(page==1&&e.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP&&getFocusedChild()!=null&&getFocusedChild().getTop()<getHeight()*.5f){showPage(0);return true;}
        }return super.dispatchKeyEvent(e);
    }
    void active(boolean value){foreground=value;if(album!=null)album.active(value&&page==2);}
    boolean back(){if(page==0)return false;showPage(0);return true;}
    boolean scenicReady(){return wallpaper!=null&&wallpaper.getWidth()>=1280;}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();showPage(entryPage);clockHandler.post(ticker);}
    @Override protected void onDetachedFromWindow(){clockHandler.removeCallbacks(ticker);super.onDetachedFromWindow();}
    @Override protected void onMeasure(int w,int h){
        int width=MeasureSpec.getSize(w),height=MeasureSpec.getSize(h);setMeasuredDimension(width,height);
        scale=Math.min(width/1920f,height/1080f)*(BuildConfig.PARENTS_EDITION?1f:.86f);
        offsetX=(width-1920*scale)/2;offsetY=(height-1080*scale)/2;
        for(Item i:items){
            if(i.size>0)((TextView)i.view).setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,i.size*scale);
            if(i.view instanceof TextView&&iconSizes.containsKey(i.view)){
                TextView v=(TextView)i.view;int size=Math.max(1,Math.round(iconSizes.get(v)*scale));
                Drawable[] icons=v.getCompoundDrawables();for(Drawable icon:icons)if(icon!=null)icon.setBounds(0,0,size,size);
                v.setCompoundDrawables(icons[0],icons[1],icons[2],icons[3]);v.setCompoundDrawablePadding(Math.round(10*scale));
            }
            boolean full=i.view==album;
            i.view.measure(MeasureSpec.makeMeasureSpec(full?width:Math.round(i.w*scale),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(full?height:Math.round(i.h*scale),MeasureSpec.EXACTLY));
        }
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){for(Item i:items){int x=i.view==album?0:Math.round(offsetX+i.x*scale),y=i.view==album?0:Math.round(offsetY+i.y*scale);i.view.layout(x,y,x+i.view.getMeasuredWidth(),y+i.view.getMeasuredHeight());}}

    private void photo(Canvas c,Bitmap image){if(image==null||image.isRecycled())return;float s=Math.max(getWidth()/(float)image.getWidth(),getHeight()/(float)image.getHeight());float w=image.getWidth()*s,h=image.getHeight()*s;paint.setShader(null);paint.setColor(Color.WHITE);c.drawBitmap(image,null,new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2),paint);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(0xff0c1115);photo(c,wallpaper);paint.setShader(new LinearGradient(0,0,0,getHeight(),new int[]{0x44050a14,0x22050a14,0xa6050a14},new float[]{0,.55f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,getWidth(),getHeight(),paint);paint.setShader(null);}
    private final class Pane extends Drawable {
        private final View view;private final boolean glass;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Pane(View v,boolean glass){view=v;this.glass=glass;}
        @Override public void draw(Canvas c){RectF rect=new RectF(getBounds());rect.inset(2,2);float radius=18*scale;boolean focused=view.isFocused()||view.isPressed();if(glass){Path path=new Path();path.addRoundRect(rect,radius,radius,Path.Direction.CW);c.save();c.clipPath(path);c.translate(-view.getLeft(),-view.getTop());photo(c,softWallpaper);c.restore();p.setShader(new LinearGradient(0,0,rect.width(),rect.height(),new int[]{0xb52c3641,0xd20c141e},null,Shader.TileMode.CLAMP));c.drawRoundRect(rect,radius,radius,p);p.setShader(null);}else if(focused){p.setColor(0x33232d36);c.drawRoundRect(rect,radius,radius,p);}if(glass||focused){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth((focused?3:1)*scale);p.setColor(focused?0xfffff2da:0x60dce5ed);c.drawRoundRect(rect,radius,radius,p);p.setStyle(Paint.Style.FILL);}}
        @Override public void setAlpha(int a){}@Override public void setColorFilter(ColorFilter f){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        @Override public boolean isStateful(){return true;}@Override protected boolean onStateChange(int[] state){invalidateSelf();return true;}
    }
}
