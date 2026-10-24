package org.zhixiaoce.aperture;

import android.app.Activity;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.webkit.CookieManager;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Foreground-only native receiving flow. Credentials never enter the TV WebView or public intents. */
public final class NasSetupActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private volatile NasSetupCrypto crypto;
    private NasSetupClient client;
    private TextView status;
    private LinearLayout layout;
    private Bitmap qr;
    private volatile boolean closed;
    private boolean terminal;
    private int revision;
    private long expiresAt;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(48,28,48,28);layout.setBackgroundColor(Color.rgb(12,23,32));
        label("让家人帮电视连上 NAS",30);status=label("正在准备本次加密交接…",20);
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);setContentView(scroll);
        button("取消并返回",this::finish);
        String origin=getSharedPreferences("aperture",MODE_PRIVATE).getString("origin","");
        try{client=new NasSetupClient(origin,origin.isEmpty()?"":CookieManager.getInstance().getCookie(origin),BuildConfig.DEBUG);}
        catch(Exception unavailable){status.setText(unavailable.getMessage());return;}
        io.execute(()->{
            try{
                NasSetupCrypto next=new NasSetupCrypto();crypto=next;
                if(closed){next.close();return;}
                // Keep the v13 wire label so renamed APKs still work with older household servers.
                JSONObject request=client.start(next,BuildConfig.PARENTS_EDITION?"开窗 · 爸妈版":"开窗 · 标准版");
                String fingerprint=request.getString("fingerprint");
                if(!next.fingerprint.equals(fingerprint)||!next.id.equals(request.getString("id")))throw new IllegalStateException();
                expiresAt=request.getLong("expiresAt");String encoded=request.getString("qr");
                if(encoded.length()>65536)throw new IllegalArgumentException();
                byte[] png=Base64.getDecoder().decode(encoded);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(png,0,png.length,bounds);
                if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>600||bounds.outHeight>600)throw new IllegalArgumentException();
                Bitmap image=BitmapFactory.decodeByteArray(png,0,png.length);if(image==null)throw new IllegalArgumentException();
                main.post(()->{if(closed){image.recycle();return;}qr=image;ImageView picture=new ImageView(this);picture.setImageBitmap(image);picture.setAdjustViewBounds(true);layout.addView(picture,1,new LinearLayout.LayoutParams(300,300));label("校验码  "+fingerprint.substring(0,4)+" "+fingerprint.substring(4,8)+" "+fingerprint.substring(8,12),24);label("家人用手机扫码并验证管理员身份。也可由已授权的助手交接加密资料。\n这次请求10分钟有效；离开此页即取消。不会共享原系统或小米账号。",18);status.setText("等待家人填写；接收后先测试目录，再保存在本机。无法扫码时，可让已授权助手查找这次请求，并核对校验码。");schedule();});
            }catch(Exception error){main.post(()->failed("暂时无法准备交接，请检查家庭连接，并确认服务已更新到本版源码，再重新打开。"));}
        });
    }
    private TextView label(String text,int size){TextView view=new TextView(this);view.setText(text);view.setTextColor(Color.rgb(233,241,245));view.setTextSize(size);view.setPadding(0,12,0,12);layout.addView(view);return view;}
    private void button(String text,Runnable action){Button button=new Button(this);button.setText(text);button.setTextSize(26);button.setMinHeight(Math.round(72*getResources().getDisplayMetrics().density));button.setAllCaps(false);button.setTextColor(Color.WHITE);
        StateListDrawable states=new StateListDrawable();for(boolean focus:new boolean[]{true,false}){GradientDrawable shape=new GradientDrawable();shape.setColor(focus?Color.rgb(57,78,91):Color.rgb(27,43,55));shape.setCornerRadius(14);shape.setStroke(focus?3:1,focus?Color.rgb(222,236,244):Color.rgb(90,112,128));states.addState(focus?new int[]{android.R.attr.state_focused}:new int[]{},shape);}button.setBackground(states);layout.addView(button,new LinearLayout.LayoutParams(-1,-2));button.setOnClickListener(v->action.run());}
    private void failed(String text){if(!closed)status.setText(text);}
    private void schedule(){if(!closed&&!terminal)main.postDelayed(this::poll,2500);}
    private void poll(){
        if(closed||terminal)return;
        if(System.currentTimeMillis()>=expiresAt){failed("这次交接已过期，请返回后重新发起。");return;}
        io.execute(()->{
            try{
                JSONObject response=client.poll(crypto);String state=response.getString("state");revision=response.getInt("revision");
                if(closed)return;
                if(state.equals("waiting")){main.post(this::schedule);return;}
                if(!state.equals("delivered")){main.post(()->failed("这次交接已结束，请返回后重新发起。"));return;}
                main.post(()->{if(!closed)status.setText("收到加密资料，正在测试选定目录…");});
                NasDraft draft=NasSetupImport.verify(crypto,client,response,()->!closed);
                main.post(()->{
                    if(closed)return;
                    try{new NasVault(this).addDraft(draft);terminal=true;status.setText("已经连上并保存在这台电视。返回后即可使用"+(draft.role.isEmpty()?"这个NAS连接":"「"+NasLibrary.title(draft.role)+"」入口")+"。正在发送回执…");ack("saved");}
                    catch(Exception failed){terminal=true;status.setText("目录可读，但本机未能保存。请返回检查已有连接数量或本机加密记录。");ack("connection_failed");}
                });
            }catch(Exception error){main.post(()->{if(closed)return;terminal=true;failed("连接或资料验证未通过，没有新增连接。请家人检查NAS服务、只读账号与目录后重新发起。");ack(revision==0?"cancelled":"connection_failed");});}
        });
    }
    private void ack(String state){io.execute(()->{try{client.finish(crypto,state,revision);main.post(()->{if(!closed&&state.equals("saved"))status.setText("电视已保存，家人手机也能查到回执。返回后即可使用。");});}catch(Exception error){main.post(()->{if(!closed&&state.equals("saved"))status.setText("电视已保存；回执暂未送达。请保留本机连接，不要重复添加。");});}finally{crypto.close();}});}
    private void stopTransfer(){
        if(closed)return;
        closed=true;main.removeCallbacksAndMessages(null);if(client!=null)client.interrupt();
        if(!terminal&&client!=null&&crypto!=null){io.execute(()->{try{client.finish(crypto,"cancelled",revision);}catch(Exception ignored){}finally{crypto.close();}});}
        io.shutdown();
    }
    @Override protected void onPause(){stopTransfer();super.onPause();finish();}
    @Override protected void onStop(){stopTransfer();super.onStop();}
    @Override protected void onDestroy(){if(qr!=null){qr.recycle();qr=null;}super.onDestroy();}
}
