package org.zhixiaoce.aperture;
import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import org.json.JSONObject;
/** Explicit preview/weather entry for the native recovery home. Never starts over playback. */
public final class AmbientActivity extends Activity {
    private AmbientWeb page;
    public void onCreate(Bundle state){super.onCreate(state);getWindow().getDecorView().setSystemUiVisibility(5894);page=new AmbientWeb(this,getIntent().getStringExtra("panel"),this::finish);setContentView(page.web);page.web.requestFocus();}
    protected void onResume(){super.onResume();if(page!=null)page.active(true);}
    protected void onPause(){if(page!=null)page.active(false);super.onPause();}
    public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(page!=null)page.active(focus&&hasWindowFocus()&&!isFinishing());}
    public void onBackPressed(){if(page==null){finish();return;}page.web.evaluateJavascript("!!(window.apertureBack&&window.apertureBack())",v->{if(!"true".equals(v))finish();});}
    public boolean onKeyDown(int code,KeyEvent event){if(VoiceAssistant.systemVoiceKey(code))return super.onKeyDown(code,event);if(code==KeyEvent.KEYCODE_BACK){if(event.getRepeatCount()==0)onBackPressed();return true;}String name=code==19?"ArrowUp":code==20?"ArrowDown":code==21?"ArrowLeft":code==22?"ArrowRight":code==23||code==66?"Enter":null;if(name!=null&&page!=null){if(event.getRepeatCount()==0||!"Enter".equals(name))page.web.evaluateJavascript("window.apertureRemoteKey&&window.apertureRemoteKey("+JSONObject.quote(name)+")",null);return true;}return super.onKeyDown(code,event);}
    public boolean onKeyUp(int code,KeyEvent event){if(VoiceAssistant.systemVoiceKey(code))return super.onKeyUp(code,event);if(page!=null)page.web.evaluateJavascript("window.KaishiAmbient&&window.KaishiAmbient.releaseKey()",null);if(code>=19&&code<=23||code==66||code==4)return true;return super.onKeyUp(code,event);}
    protected void onDestroy(){if(page!=null)page.close();super.onDestroy();}
}
