package org.zhixiaoce.aperture;
import android.app.Activity;import android.os.Bundle;import android.view.View;import android.widget.TextView;
/** Public only in an isolated test APK, with no product data or network permission. */
public final class R2FrameProbe extends Activity {
    TextView target;
    @Override public void onCreate(Bundle state){super.onCreate(state);replace("first");}
    void replace(String name){target=new TextView(this);target.setTag(name);target.setText("截图同步测试 · "+name);target.setTextSize(32);target.setTextColor(-1);target.setBackgroundColor(0xff102535);target.setFocusableInTouchMode(true);setContentView(target);target.requestFocus();}
}
