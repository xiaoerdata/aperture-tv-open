package org.zhixiaoce.aperture;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import org.json.JSONObject;

/** Weather/preview is a child screen. Returning must not exit the home task. */
public final class AmbientActivity extends Activity {
    private AmbientWeb page;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean weatherPanel,resumed,closed,leaving,backPending;
    private int backGeneration;
    private final Runnable backTimeout=()->finishScreen();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().getDecorView().setSystemUiVisibility(5894);
        String panel=getIntent().getStringExtra("panel");weatherPanel="weather".equals(panel);
        page=new AmbientWeb(this,panel,this::finishScreen);setContentView(page.web);page.web.requestFocus();
    }
    private void cancelBack(){main.removeCallbacks(backTimeout);backPending=false;backGeneration++;}
    private void finishScreen(){
        if(closed||leaving)return;leaving=true;cancelBack();
        // An OEM task restore can leave this private child as the task root.
        // Rebuild the app home in that case instead of revealing the Xiaomi launcher.
        if(isTaskRoot())startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
    @Override protected void onResume(){super.onResume();resumed=true;if(page!=null&&!leaving)page.active(hasWindowFocus());}
    @Override protected void onPause(){resumed=false;cancelBack();if(page!=null)page.active(false);super.onPause();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(page!=null&&!leaving)page.active(resumed&&focus&&!isFinishing());}

    @Override public void onBackPressed(){
        if(closed||leaving)return;
        // Back from weather has a fixed native destination, even before JS is ready.
        if(weatherPanel||page==null||!page.available()||backPending){finishScreen();return;}
        backPending=true;final int turn=++backGeneration;main.postDelayed(backTimeout,600);
        if(!page.evaluate("!!(window.apertureBack&&window.apertureBack())",value->{
            if(closed||leaving||!backPending||turn!=backGeneration)return;
            cancelBack();if(!"true".equals(value))finishScreen();
        }))finishScreen();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        // Consume both edges before WebView/native history can also handle BACK.
        if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)onBackPressed();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean onKeyDown(int code,KeyEvent event){
        if(VoiceAssistant.systemVoiceKey(code))return super.onKeyDown(code,event);
        if(code==KeyEvent.KEYCODE_BACK){if(event.getRepeatCount()==0)onBackPressed();return true;}
        String name=code==19?"ArrowUp":code==20?"ArrowDown":code==21?"ArrowLeft":code==22?"ArrowRight":code==23||code==66?"Enter":null;
        if(name!=null&&page!=null){if(!closed&&!leaving&&(event.getRepeatCount()==0||!"Enter".equals(name)))page.evaluate("window.apertureRemoteKey&&window.apertureRemoteKey("+JSONObject.quote(name)+")",null);return true;}
        return super.onKeyDown(code,event);
    }
    @Override public boolean onKeyUp(int code,KeyEvent event){
        if(VoiceAssistant.systemVoiceKey(code))return super.onKeyUp(code,event);
        if(!closed&&!leaving&&page!=null)page.evaluate("window.KaishiAmbient&&window.KaishiAmbient.releaseKey()",null);
        if(code>=19&&code<=23||code==66||code==4)return true;
        return super.onKeyUp(code,event);
    }
    @Override protected void onDestroy(){closed=true;resumed=false;cancelBack();AmbientWeb old=page;page=null;if(old!=null)old.close();super.onDestroy();}
}
