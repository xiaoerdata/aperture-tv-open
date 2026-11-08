package org.zhixiaoce.aperture;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only third home screen. Never starts an upload listener or a WebView. */
final class HomeAlbumView extends FrameLayout {
    private final GalleryStore store;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ImageView[] pictures=new ImageView[2];
    private final Bitmap[] bitmaps=new Bitmap[2];
    private final TextView empty,hint;
    private JSONArray photos=new JSONArray();
    private int index,seconds=12,current;
    private long generation;
    private boolean active,closed;
    private final Runnable next=()->move(1);
    private final Runnable hideHint=()->hintVisible(false);
    private void hintVisible(boolean visible){if(hint!=null)hint.setVisibility(visible?VISIBLE:INVISIBLE);}
    private void revealHint(){ui.removeCallbacks(hideHint);if(active&&!closed){hintVisible(true);ui.postDelayed(hideHint,10000);}}

    HomeAlbumView(Activity activity,Runnable settings){
        super(activity);store=new GalleryStore(activity);setTag("native-home-album");
        setBackgroundColor(0xff05090c);setFocusableInTouchMode(true);setClickable(true);
        setContentDescription("相册，确定键打开相册设置");setOnClickListener(v->settings.run());TvUi.touchClick(this);
        for(int i=0;i<2;i++){
            pictures[i]=new ImageView(activity);pictures[i].setScaleType(ImageView.ScaleType.FIT_CENTER);
            pictures[i].setAlpha(0);addView(pictures[i],new FrameLayout.LayoutParams(-1,-1));
        }
        empty=TvUi.text(activity,"暂无照片\n确定键打开相册设置",24,true);empty.setGravity(Gravity.CENTER);
        addView(empty,new FrameLayout.LayoutParams(-1,-1));
        hint=TvUi.text(activity,"确定 · 相册设置",16,true);hint.setTag("native-home-album-hint");hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams label=new FrameLayout.LayoutParams(-1,TvUi.dp(activity,48),Gravity.BOTTOM);
        label.bottomMargin=TvUi.dp(activity,28);addView(hint,label);
    }
    void active(boolean value){
        if(closed)return;
        if(active==value)return;
        active=value;++generation;ui.removeCallbacks(next);cancelAnimations();
        if(value){revealHint();refresh();}else{ui.removeCallbacks(hideHint);hintVisible(false);releasePictures();}
    }
    private void refresh(){
        final long expected=++generation;
        worker.execute(()->{
            try{
                JSONObject state=store.snapshot();
                ui.post(()->{
                    if(!valid(expected))return;
                    photos=state.optJSONArray("photos");if(photos==null)photos=new JSONArray();
                    seconds=Math.max(8,Math.min(20,state.optInt("seconds",12)));
                    index=photos.length()==0?0:index%photos.length();display();
                });
            }catch(Exception unavailable){ui.post(()->{if(valid(expected)){empty.setText("相册暂时无法读取\n确定键打开相册设置");empty.setVisibility(VISIBLE);}});}
        });
    }
    private void display(){
        if(!active||closed)return;
        ui.removeCallbacks(next);final long expected=++generation;
        if(photos.length()==0){releasePictures();empty.setText("暂无照片\n确定键打开相册设置");empty.setVisibility(VISIBLE);return;}
        String id=photos.optJSONObject(index).optString("id");
        worker.execute(()->{
            Bitmap decoded=null;try{decoded=store.image(id);}catch(Exception unavailable){}
            final Bitmap image=decoded;
            ui.post(()->{
                if(!valid(expected)){if(image!=null)image.recycle();return;}
                cancelAnimations();int target=1-current;
                pictures[target].setImageDrawable(null);if(bitmaps[target]!=null)bitmaps[target].recycle();
                bitmaps[target]=image;pictures[target].setImageBitmap(image);
                empty.setVisibility(image==null?VISIBLE:GONE);
                if(image==null)empty.setText("这张照片暂时无法读取");
                pictures[target].setAlpha(0);pictures[target].animate().alpha(1).setDuration(900).start();
                pictures[current].animate().alpha(0).setDuration(900).start();current=target;
                if(photos.length()>1)ui.postDelayed(next,seconds*1000L);
            });
        });
    }
    private boolean valid(long expected){return !closed&&active&&expected==generation;}
    private void move(int delta){if(!active||photos.length()==0)return;index=(index+delta+photos.length())%photos.length();display();}
    @Override public boolean onKeyDown(int code,KeyEvent event){
        if(event.getRepeatCount()==0&&(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT||code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER))revealHint();
        if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getRepeatCount()==0)move(code==KeyEvent.KEYCODE_DPAD_LEFT?-1:1);return true;}
        return super.onKeyDown(code,event);
    }
    @Override public boolean onKeyUp(int code,KeyEvent event){return code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT||super.onKeyUp(code,event);}
    private void cancelAnimations(){for(ImageView v:pictures)v.animate().cancel();}
    private void releasePictures(){for(int i=0;i<2;i++){pictures[i].setImageDrawable(null);pictures[i].setAlpha(0);if(bitmaps[i]!=null){bitmaps[i].recycle();bitmaps[i]=null;}}}
    @Override protected void onDetachedFromWindow(){closed=true;active=false;++generation;ui.removeCallbacks(next);ui.removeCallbacks(hideHint);cancelAnimations();releasePictures();worker.shutdownNow();super.onDetachedFromWindow();}
}
