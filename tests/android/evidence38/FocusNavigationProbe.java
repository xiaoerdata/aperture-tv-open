package org.zhixiaoce.aperture;
import android.app.*;import android.os.Bundle;import android.view.*;import android.widget.*;
/** Generic widgets only; no production controls, upload factory, files or network permission. */
public final class FocusNavigationProbe extends Activity {
    Root row;SearchButton first;Button second;boolean consume,openDialog;AlertDialog popup;
    @Override public void onCreate(Bundle state){super.onCreate(state);row=new Root();row.setPadding(40,50,40,50);first=new SearchButton();first.setText("A");second=new Button(this);second.setText("B");for(Button b:new Button[]{first,second}){b.setId(View.generateViewId());b.setFocusableInTouchMode(true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(240,120);p.setMargins(0,0,80,0);row.addView(b,p);}setContentView(row);first.requestFocus();}
    @Override public boolean onKeyDown(int code,KeyEvent event){if(code==KeyEvent.KEYCODE_DPAD_RIGHT){if(openDialog){popup=new AlertDialog.Builder(this).setTitle("测试窗口").setNegativeButton("返回",null).show();ProbeKeys.track(this,popup);return true;}if(consume)return true;}if(code==KeyEvent.KEYCODE_BACK){finish();return true;}return super.onKeyDown(code,event);}
    final class SearchButton extends Button {boolean overrideSearch;View forced;int searches;SearchButton(){super(FocusNavigationProbe.this);}@Override public View focusSearch(int direction){searches++;return overrideSearch?forced:super.focusSearch(direction);}}
    final class Root extends LinearLayout {boolean handleMove;int moves;Root(){super(FocusNavigationProbe.this);setOrientation(HORIZONTAL);}@Override public boolean dispatchUnhandledMove(View focused,int direction){moves++;return handleMove||super.dispatchUnhandledMove(focused,direction);}}
}
