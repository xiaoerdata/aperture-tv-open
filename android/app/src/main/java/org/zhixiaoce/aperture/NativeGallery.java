package org.zhixiaoce.aperture;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A real local album which does not construct a WebView or bind a network port. */
final class NativeGallery extends FrameLayout {
    interface Actions {void upload();void stop();void back();void stages();void snapshot(boolean success);}
    private final Activity activity;private final GalleryStore store;private final Actions actions;
    private final Handler ui=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final LinearLayout body;private final ImageView picture;private final TextView heading,status,count,empty,address;private final Button stop;
    private JSONArray photos=new JSONArray();private int index,seconds=12;private long epoch;private boolean closed,active,full,paused;private Bitmap bitmap;
    private final Runnable tick=()->{if(!closed&&active&&full&&!paused&&photos.length()>1){move(1);schedule();}};
    NativeGallery(Activity a,GalleryStore store,Actions actions,boolean recovery){
        super(a);activity=a;this.store=store;this.actions=actions;setTag(recovery?"kaishi-gallery-error":"kaishi-gallery-native");setBackground(TvUi.background());
        body=new LinearLayout(a);body.setOrientation(1);int pad=TvUi.dp(a,24);body.setPadding(pad,pad,pad,pad);addView(body,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(a);heading=TvUi.text(a,"相册",30,false);heading.setTag("native-gallery-heading");heading.setFocusableInTouchMode(true);heading.setId(View.generateViewId());header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));Button back=TvUi.button(a,"返回首页",actions::back);header.addView(back);body.addView(header);
        status=TvUi.text(a,recovery?"网页暂不可用，已切到本机相册。":"本机相册",18,true);body.addView(status);
        FrameLayout scene=new FrameLayout(a);scene.setBackgroundColor(0xff05090c);picture=new ImageView(a);picture.setTag("native-gallery-picture");picture.setScaleType(ImageView.ScaleType.FIT_CENTER);scene.addView(picture,new FrameLayout.LayoutParams(-1,-1));empty=TvUi.text(a,"暂无照片\n手机上传后即可在电视查看",25,true);empty.setTag("native-gallery-empty");empty.setGravity(Gravity.CENTER);scene.addView(empty,new FrameLayout.LayoutParams(-1,-1));body.addView(scene,new LinearLayout.LayoutParams(-1,0,1));
        count=TvUi.text(a,"正在读取本机照片…",18,true);count.setTag("native-gallery-count");body.addView(count);
        LinearLayout controls=new LinearLayout(a);Button previous=TvUi.button(a,"上一张",()->move(-1)),next=TvUi.button(a,"下一张",()->move(1));Button preview=TvUi.button(a,"全屏轮播",this::fullscreen),upload=TvUi.button(a,"手机上传",actions::upload),stages=TvUi.button(a,"加载记录",actions::stages);
        upload.setTag("native-gallery-upload");preview.setTag("native-gallery-preview");upload.setId(View.generateViewId());heading.setNextFocusDownId(upload.getId());
        for(Button b:new Button[]{previous,next,preview,upload,stages})TvUi.addAction(a,controls,b);body.addView(controls);
        TextView note=TvUi.text(a,"本机模式逐张轮播；扫码、模板和照片管理需网页相册。手机上传可手动输入临时地址。",16,true);body.addView(note);
        address=TvUi.text(a,"",17,false);address.setMaxLines(3);address.setVisibility(GONE);body.addView(address);stop=TvUi.button(a,"关闭上传",actions::stop);stop.setVisibility(GONE);body.addView(stop);
        if(recovery)back.requestFocus();else heading.requestFocus();refresh();
    }
    void refresh(){
        if(closed)return;final long expected=++epoch;
        worker.execute(()->{try{JSONObject snapshot=store.snapshot();ui.post(()->{if(closed||epoch!=expected)return;photos=snapshot.optJSONArray("photos");if(photos==null)photos=new JSONArray();seconds=snapshot.optInt("seconds",12);index=photos.length()==0?0:Math.min(index,photos.length()-1);actions.snapshot(true);display();});}catch(Exception error){ui.post(()->{if(closed||epoch!=expected)return;actions.snapshot(false);status("本机相册暂时无法读取，可返回后重试");empty.setVisibility(VISIBLE);empty.setText("无法读取本机照片");});}});
    }
    private void display(){
        if(closed)return;count.setText(photos.length()==0?"0 / 300 张":(index+1)+" / "+photos.length()+" 张");empty.setVisibility(photos.length()==0?VISIBLE:GONE);
        if(photos.length()==0){empty.setText("暂无照片\n手机上传后即可在电视查看");picture.setImageDrawable(null);recycle();schedule();return;}
        String id=photos.optJSONObject(index).optString("id");final long expected=++epoch;
        worker.execute(()->{Bitmap decoded=null;try{decoded=store.image(id);}catch(Exception ignored){}final Bitmap image=decoded;ui.post(()->{if(closed||epoch!=expected){if(image!=null)image.recycle();return;}picture.setImageBitmap(image);recycle();bitmap=image;if(image==null){empty.setText("这张照片暂时无法读取，可切换其他照片");empty.setVisibility(VISIBLE);}schedule();});});
    }
    private void recycle(){if(bitmap!=null){bitmap.recycle();bitmap=null;}}
    private void move(int delta){if(photos.length()==0)return;index=(index+delta+photos.length())%photos.length();display();}
    private void schedule(){ui.removeCallbacks(tick);if(active&&full&&!paused&&photos.length()>1)ui.postDelayed(tick,Math.max(8,Math.min(20,seconds))*1000L);}
    private void fullscreen(){if(photos.length()==0){status("暂无照片");return;}full=true;paused=false;body.setPadding(0,0,0,0);for(int i=0;i<body.getChildCount();i++)if(i!=2)body.getChildAt(i).setVisibility(GONE);schedule();}
    boolean back(){if(!full)return false;full=false;paused=false;int pad=TvUi.dp(activity,24);body.setPadding(pad,pad,pad,pad);for(int i=0;i<body.getChildCount();i++)if(body.getChildAt(i)!=address&&body.getChildAt(i)!=stop)body.getChildAt(i).setVisibility(VISIBLE);heading.requestFocus();schedule();return true;}
    boolean key(KeyEvent event){if(!full)return false;int code=event.getKeyCode();if(code==KeyEvent.KEYCODE_BACK){if(event.getAction()==0&&event.getRepeatCount()==0)back();return true;}if(code==21||code==22||code==23||code==66){if(event.getAction()==0&&event.getRepeatCount()==0){if(code==21||code==22)move(code==21?-1:1);else{paused=!paused;schedule();}}return true;}return false;}
    void active(boolean value){active=value;schedule();}
    void status(String text){if(!closed)status.setText(text);}
    void upload(String url){if(closed)return;address.setText("手机浏览器输入此临时地址（10分钟）：\n"+url);address.setVisibility(VISIBLE);stop.setVisibility(VISIBLE);stop.requestFocus();}
    void uploadClosed(){address.setText("");address.setVisibility(GONE);stop.setVisibility(GONE);}
    void close(){closed=true;++epoch;ui.removeCallbacks(tick);worker.shutdownNow();picture.setImageDrawable(null);recycle();}
}
