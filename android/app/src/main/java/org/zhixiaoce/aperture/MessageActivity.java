package org.zhixiaoce.aperture;

import android.app.Activity;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** Explicit phone editing session, entirely local to the television. */
public final class MessageActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private volatile boolean resumed,closed;private volatile MessagePhoneSession session;
    private TextView status,expiry;private LinearLayout content;private FrameLayout code;private Button open,stop;
    private volatile long generation;private long ends;
    private final Runnable tick=new Runnable(){public void run(){if(session==null)return;long remaining=Math.max(0,(ends-SystemClock.elapsedRealtime()+999)/1000);expiry.setText("剩余 "+remaining/60+"分"+remaining%60+"秒");if(remaining==0||session.isClosed()){stopSession();status.setText("扫码编辑已关闭");}else ui.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().getDecorView().setSystemUiVisibility(5894);
        FrameLayout stage=new FrameLayout(this);stage.setBackground(TvUi.background());content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);int pad=TvUi.dp(this,18);content.setPadding(pad,pad,pad,pad);content.setBackground(TvUi.glass(this,false));
        FrameLayout.LayoutParams sheet=new FrameLayout.LayoutParams(Math.round(TvUi.viewportWidth(this)*.38f),Math.round(TvUi.viewportHeight(this)*.9f),Gravity.RIGHT|Gravity.CENTER_VERTICAL);sheet.rightMargin=Math.round(TvUi.viewportWidth(this)*.025f);stage.addView(content,sheet);
        content.addView(TvUi.text(this,"家庭留言",28,false));content.addView(TvUi.text(this,"手机与电视连接同一网络\n扫码添加、编辑或删除留言",18,true));
        code=new FrameLayout(this);content.addView(code,new LinearLayout.LayoutParams(-1,0,1));expiry=TvUi.text(this,"",16,true);content.addView(expiry);status=TvUi.text(this,"留言保存在电视本机，离开此页即关闭编辑",16,true);status.setMaxLines(3);content.addView(status);
        open=TvUi.button(this,"生成二维码",this::startSession);open.setTag("messages-open-qr");content.addView(open);stop=TvUi.button(this,"关闭扫码编辑",()->{stopSession();status.setText("扫码编辑已关闭");});stop.setVisibility(View.GONE);content.addView(stop);content.addView(TvUi.button(this,"返回",this::finish));setContentView(stage);open.requestFocus();
    }
    private void startSession(){
        if(!resumed||closed||session!=null)return;open.setEnabled(false);status.setText("正在连接家庭网络…");final long turn=++generation;
        io.execute(()->{MessagePhoneSession next=null;try{
            byte[] page;try(InputStream in=getAssets().open("messages-edit.html");ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] block=new byte[8192];int count;while((count=in.read(block))!=-1){if(bytes.size()+count>131072)throw new IOException();bytes.write(block,0,count);}page=bytes.toByteArray();}
            next=new MessagePhoneSession(MessagePhoneSession.lanAddress(),new LocalMessageStore(this),page,()->resumed&&!closed&&turn==generation,()->ui.post(()->{if(resumed&&!closed&&turn==generation)status.setText("留言已更新，返回首页即可查看");}));
            final MessagePhoneSession created=next;String url=created.start();ui.post(()->{
                if(!resumed||closed||turn!=generation){created.close();return;}
                session=created;code.removeAllViews();PhoneQrView qr=new PhoneQrView(this,url);qr.setTag("messages-phone-qr");int size=Math.max(1,Math.min(code.getWidth(),code.getHeight())-TvUi.dp(this,12));code.addView(qr,new FrameLayout.LayoutParams(size,size,Gravity.CENTER));
                ends=SystemClock.elapsedRealtime()+600000;open.setVisibility(View.GONE);stop.setVisibility(View.VISIBLE);stop.requestFocus();status.setText("二维码10分钟有效，离开此页即关闭");ui.post(tick);
            });
        }catch(Exception unavailable){if(next!=null)next.close();ui.post(()->{if(!closed&&resumed&&turn==generation){open.setEnabled(true);status.setText("无法连接家庭网络，请检查电视网络后重试");}});}});
    }
    private void stopSession(){++generation;MessagePhoneSession old=session;session=null;if(old!=null)old.close();ui.removeCallbacks(tick);code.removeAllViews();expiry.setText("");open.setVisibility(View.VISIBLE);open.setEnabled(true);stop.setVisibility(View.GONE);if(resumed&&!closed)open.requestFocus();}
    @Override protected void onResume(){super.onResume();resumed=true;}
    @Override protected void onPause(){resumed=false;stopSession();super.onPause();}
    @Override protected void onDestroy(){closed=true;stopSession();io.shutdownNow();ui.removeCallbacksAndMessages(null);super.onDestroy();}
}
