package org.zhixiaoce.aperture;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.content.res.ColorStateList;

/** Shared cinema/NAS timeline. Seeking preserves playback intent; no media ownership here. */
final class PlayerTimeline extends LinearLayout implements AutoCloseable {
    interface Media {long position();long duration();int buffered();void seek(long position);}
    private final Media media;private final Handler clock=new Handler(Looper.getMainLooper());
    private final SeekBar bar;private final TextView current,total,target;private boolean editing,closed;private boolean compact;private long preview;
    private final Runnable refresh=new Runnable(){public void run(){if(closed)return;update();clock.postDelayed(this,500);}};
    PlayerTimeline(Activity activity,Media media){
        this(activity,media,false);
    }
    PlayerTimeline(Activity activity,Media media,boolean compact){
        super(activity);this.media=media;this.compact=compact;setOrientation(VERTICAL);setPadding(0,TvUi.dp(activity,4),0,TvUi.dp(activity,4));
        target=TvUi.text(activity,"",20,false);target.setVisibility(GONE);addView(target);
        LinearLayout times=new LinearLayout(activity);current=TvUi.text(activity,"00:00",20,false);total=TvUi.text(activity,"--:--",20,true);times.addView(current,new LayoutParams(0,-2,1));times.addView(total);addView(times);
        bar=new SeekBar(activity);bar.setContentDescription("播放进度，左右键选择时间，确定跳转");bar.setMax(10000);bar.setPadding(TvUi.dp(activity,8),0,TvUi.dp(activity,8),0);bar.setProgressTintList(ColorStateList.valueOf(0xfff0dfc0));bar.setSecondaryProgressTintList(ColorStateList.valueOf(0xff85888d));bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xff34373d));bar.setThumbTintList(ColorStateList.valueOf(0xfffff0d8));addView(bar,new LayoutParams(-1,TvUi.dp(activity,44)));
        if(compact){bar.setFocusable(false);bar.setFocusableInTouchMode(false);bar.setContentDescription("播放进度，左右键快退快进，松开跳转");}
        bar.setOnFocusChangeListener((v,focus)->{v.setBackground(focus?TvUi.glass(activity,true):null);if(!focus&&editing)commit();});
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){editing=true;preview=safePosition();}public void onProgressChanged(SeekBar b,int value,boolean user){if(user){editing=true;preview=duration()*value/10000;showTarget();}}public void onStopTrackingTouch(SeekBar b){commit();}});
        bar.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getAction()==KeyEvent.ACTION_DOWN){if(!editing)preview=safePosition();editing=true;preview=PlaybackTimeline.step(preview,duration(),key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1,event.getRepeatCount());showTarget();}return true;}if(editing&&(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)){if(event.getAction()==KeyEvent.ACTION_UP)commit();return true;}return false;});
        if(compact){removeView(target);removeView(times);removeView(bar);times.removeView(current);times.removeView(total);setOrientation(HORIZONTAL);setGravity(android.view.Gravity.CENTER_VERTICAL);current.setGravity(android.view.Gravity.CENTER);total.setGravity(android.view.Gravity.CENTER);addView(current,new LayoutParams(TvUi.dp(activity,64),-2));addView(bar,new LayoutParams(0,TvUi.dp(activity,52),1));addView(total,new LayoutParams(TvUi.dp(activity,64),-2));}
        clock.post(refresh);
    }
    private long duration(){try{return Math.max(0,media.duration());}catch(RuntimeException e){return 0;}}
    private long safePosition(){try{return Math.max(0,media.position());}catch(RuntimeException e){return 0;}}
    private void showTarget(){target.setText(compact?PlaybackTimeline.time(preview):"跳转至 "+PlaybackTimeline.time(preview)+" · 确定跳转");target.setVisibility(VISIBLE);if(compact)current.setText(PlaybackTimeline.time(preview));long duration=duration();if(duration>0)bar.setProgress((int)(preview*10000/duration));}
    private void commit(){if(!editing)return;editing=false;target.setVisibility(GONE);if(duration()>0)try{media.seek(Math.min(preview,duration()));}catch(RuntimeException ignored){}update();}
    private void update(){if(getWindowVisibility()!=VISIBLE)return;long duration=duration();bar.setEnabled(duration>0);total.setText(duration>0?PlaybackTimeline.time(duration):"--:--");if(editing)return;long position=safePosition();current.setText(PlaybackTimeline.time(position));bar.setProgress(duration>0?(int)Math.min(10000,position*10000/duration):0);try{bar.setSecondaryProgress(Math.max(0,Math.min(100,media.buffered()))*100);}catch(RuntimeException ignored){}}
    boolean remoteKey(KeyEvent event){
        int key=event.getKeyCode();if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;
        if(duration()<=0)return true;
        if(event.getAction()==KeyEvent.ACTION_DOWN){if(!editing)preview=safePosition();editing=true;preview=PlaybackTimeline.step(preview,duration(),key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1,event.getRepeatCount());showTarget();}
        else if(event.getAction()==KeyEvent.ACTION_UP){if(event.isCanceled())cancelEdit();else commit();}
        return true;
    }
    boolean interacting(){return editing||bar.hasFocus();}
    void cancelEdit(){editing=false;target.setVisibility(GONE);update();}
    public void close(){closed=true;editing=false;clock.removeCallbacksAndMessages(null);}
}
