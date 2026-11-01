package org.zhixiaoce.aperture;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;

/** Physical confirmation stays outside the remotely controllable activities. */
public final class LocalControlActivity extends Activity {
    private volatile boolean closed,starting;private TextView state;private final Handler main=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(32,24,32,24);form.addView(TvUi.text(this,"局域网控制",32,false));form.addView(TvUi.text(this,"在同一家庭网络中连接电脑或WorkBuddy。\n六位码、核对证书并在电视确认后，允许控制开视前台30分钟。\n可读取当前页面、截图、遥控和导入资源包；不能操作原系统、账号或静默安装。",21,true));state=TvUi.text(this,"默认关闭。连接不经过公网。",22,false);state.setTextIsSelectable(true);form.addView(state);Button start=TvUi.button(this,"开启配对",this::begin);form.addView(start);form.addView(TvUi.button(this,"关闭所有连接",()->{TvLocalControl.get().close();state.setText("已关闭，原连接立即失效。");}));form.addView(TvUi.button(this,"返回开视",this::finish));TvUi.showForm(this,form);start.requestFocus();}
    private void begin(){if(starting||closed)return;starting=true;state.setText("正在准备本机证书…");new Thread(()->{try{JSONObject info=TvLocalControl.get().start(this);if(closed){TvLocalControl.get().close();return;}main.post(()->{if(isFinishing())return;state.setText("配对码  "+info.optString("pin")+"  · 2分钟有效\nHTTPS端口  "+info.optInt("port")+"\n证书 SHA-256（电脑须核对）\n"+info.optString("fingerprint")+"\n连接助手：node scripts/tv-control.mjs discover");});}catch(Exception failure){main.post(()->{TvLocalControl.get().close();state.setText("无法开启本机HTTPS，请检查电视证书组件后重试。");});}finally{starting=false;}},"Kaishi-pairing").start();}
    @Override protected void onDestroy(){closed=true;main.removeCallbacksAndMessages(null);super.onDestroy();}
    void confirm(String peer,String ticket,Runnable allow,Runnable deny){if(isFinishing()||!hasWindowFocus()){deny.run();return;}new AlertDialog.Builder(this).setTitle("允许这台电脑连接？").setMessage("局域网地址  "+peer+"\n核对码  "+ticket.substring(ticket.length()-6)+"\n允许读取和控制开视前台30分钟。安装、卸载仍需原有单次授权。" ).setPositiveButton("允许30分钟",(d,w)->{allow.run();state.setText("已连接，30分钟后自动失效。\n返回开视首页或片单后即可操作；随时可在这里关闭连接。");}).setNegativeButton("拒绝",(d,w)->deny.run()).setOnCancelListener(d->deny.run()).show();}
}
