package org.zhixiaoce.aperture;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

/** Third home page in a native window, independent of WebView photo compositing. */
public final class HomeAlbumActivity extends Activity {
    private HomeAlbumView album;private boolean resumed,leaving,closed;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().getDecorView().setSystemUiVisibility(5894);
        album=new HomeAlbumView(this,()->{if(!closed&&!leaving)startActivity(new Intent(this,GalleryActivity.class));});
        setContentView(album);album.requestFocus();
    }
    private void leave(int page){
        if(closed||leaving)return;leaving=true;if(album!=null)album.active(false);
        setResult(RESULT_OK,new Intent().putExtra("homePage",page));
        if(isTaskRoot())startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();
        if(VoiceAssistant.systemVoiceKey(key))return super.dispatchKeyEvent(event);
        if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_BACK){
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)leave(key==KeyEvent.KEYCODE_DPAD_UP?1:key==KeyEvent.KEYCODE_DPAD_DOWN?3:0);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed(){leave(0);}
    @Override protected void onResume(){super.onResume();resumed=true;sync();}
    @Override protected void onPause(){resumed=false;sync();super.onPause();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);sync();}
    private void sync(){if(album!=null&&!closed)album.active(resumed&&hasWindowFocus()&&!leaving);}
    @Override protected void onDestroy(){closed=true;resumed=false;if(album!=null)album.active(false);super.onDestroy();}
}
