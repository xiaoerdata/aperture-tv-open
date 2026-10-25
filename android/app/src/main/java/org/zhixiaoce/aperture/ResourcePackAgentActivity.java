package org.zhixiaoce.aperture;

import android.app.Activity;
import android.os.*;
import android.graphics.Color;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.widget.*;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** One user-authorized import. Domain parsing/saving is shared with CinemaActivity. */
public final class ResourcePackAgentActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final CinemaHttp http=new CinemaHttp();private final Object guard=new Object();
    private final String id=UUID.randomUUID().toString(),secret=secret();
    private HouseholdCommandClient client;private TextView status;private CheckBox allow;private Button begin;
    private volatile boolean closed,resumed,started,processing,terminal,saved;private volatile int revision;private volatile long expiresAt;
    private boolean scheduled,polling,allowHttp;private CinemaCatalog.Config next;
    private static String secret(){byte[] value=new byte[32];new SecureRandom().nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
    private JSONObject identity()throws Exception{return new JSONObject().put("id",id).put("secret",secret);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(48,32,48,32);layout.setBackgroundColor(Color.rgb(12,23,32));
        label(layout,"WorkBuddy · 导入资源包",32);label(layout,"本次允许WorkBuddy用一个开视资源包替换直播与影视来源。\n仅接收一个HTTPS文件及其SHA-256，10分钟有效。插件仍需另外确认。",22);
        allow=new CheckBox(this);allow.setText("允许本次资源包包含HTTP媒体地址");allow.setTextColor(Color.WHITE);allow.setTextSize(22);layout.addView(allow);
        status=label(layout,"先开启本次导入，再核对电视校验码。",24);begin=button(layout,"允许一次导入",this::start);button(layout,"取消并返回",this::finish);ScrollView scroll=new ScrollView(this);scroll.addView(layout);setContentView(scroll);
    }
    private TextView label(LinearLayout layout,String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(Color.WHITE);v.setPadding(0,12,0,18);layout.addView(v);return v;}
    private Button button(LinearLayout layout,String title,Runnable action){Button v=new Button(this);v.setText(title);v.setTextSize(25);v.setMinHeight(Math.round(68*getResources().getDisplayMetrics().density));v.setOnClickListener(n->action.run());layout.addView(v);return v;}
    private void start(){
        if(started||processing||closed)return;begin.setEnabled(false);allow.setEnabled(false);allowHttp=allow.isChecked();
        try{
            String origin=getSharedPreferences("aperture",MODE_PRIVATE).getString("origin","");client=new HouseholdCommandClient(origin,origin.isEmpty()?"":CookieManager.getInstance().getCookie(origin),BuildConfig.DEBUG);String hash=CinemaResourcePack.sha256(secret);
            JSONArray targets=new JSONArray().put(new JSONObject().put("id","kaishi.resource-pack.v1").put("name",allowHttp?"开视资源包 v1 · 允许HTTP媒体":"开视资源包 v1 · HTTPS媒体"));
            JSONObject hello=new JSONObject().put("id",id).put("secretHash",hash).put("label",BuildConfig.PARENTS_EDITION?"开视 · 爸妈版":"开视 · 标准版").put("kind","resources").put("targets",targets);
            status.setText("正在开启本次导入…");io.execute(()->{try{JSONObject r=client.call("app-operations","start",hello);if(!id.equals(r.getString("id"))||!hash.equals(r.getString("fingerprint")))throw new IOException();expiresAt=r.getLong("expiresAt");started=true;if(closed){client.call("app-operations","finish",identity().put("state","cancelled").put("revision",0));return;}main.post(()->{if(closed)return;status.setText("校验码  "+hash.substring(0,4)+" "+hash.substring(4,8)+" "+hash.substring(8,12)+"\n等待已授权的WorkBuddy提交资源包。");schedule();});}catch(Exception failure){main.post(()->fail("无法开启导入，请检查家庭HTTPS连接和电视配对。"));}});
        }catch(Exception failure){fail("请先连接并配对可信HTTPS家庭服务，再开启WorkBuddy导入。");}
    }
    private void schedule(){if(closed||terminal||processing||scheduled||polling||!started||!resumed)return;scheduled=true;main.postDelayed(()->{scheduled=false;poll();},2500);}
    private void poll(){
        if(closed||terminal||processing||!resumed)return;if(System.currentTimeMillis()>=expiresAt){fail("本次窗口已过期，请返回后重新开启。");return;}polling=true;
        io.execute(()->{try{JSONObject r=client.call("app-operations","poll",identity());revision=r.getInt("revision");String state=r.getString("state");
            if(state.equals("waiting")){main.post(()->{polling=false;schedule();});return;}if(!state.equals("delivered"))throw new IOException("本次操作已结束或结果未知，请先查询原请求。");
            if(closed||!resumed)throw new IOException("窗口已关闭");JSONObject command=r.getJSONObject("command");revision=client.call("app-operations","stage",identity().put("state","staging").put("revision",revision)).getInt("revision");processing=true;main.post(()->{polling=false;if(!closed)status.setText("正在核对资源包…");});receive(command);
        }catch(Exception failure){main.post(()->{polling=false;if(processing)report(saved?"unknown":"failed");else fail("操作连接或授权已失效，请先查询原请求状态。");});}});
    }
    private void receive(JSONObject command)throws Exception{
        if(closed||!resumed)throw new IOException("窗口已关闭");String address=CinemaCatalog.url(command.getString("url"),null,false),expected=command.getString("sha256");if(!expected.matches("[a-f0-9]{64}"))throw new IOException();CinemaHttp.Document document=http.document(address,false,Collections.emptyMap());
        if(!CinemaResourcePack.sha256(document.text).equals(expected))throw new IOException("文件摘要不一致");next=CinemaResourcePack.read(new JSONObject(CinemaFormats.json(document.text.replaceFirst("^\\uFEFF",""))),allowHttp,document.text);
        CinemaVault vault=new CinemaVault(this);
        if(closed||!resumed||System.currentTimeMillis()>=expiresAt)throw new IOException("导入窗口已关闭");revision=client.call("app-operations","stage",identity().put("state","awaiting_system").put("revision",revision)).getInt("revision");
        final boolean changed;synchronized(guard){if(closed||!resumed||System.currentTimeMillis()>=expiresAt)throw new IOException("导入窗口已关闭");changed=vault.importConfig(next);saved=true;}
        JSONObject result=new JSONObject().put("packId",next.packId).put("revision",next.packRevision).put("sha256",next.packSha256).put("changed",changed);
        main.post(()->{if(!closed){setResult(RESULT_OK);status.setText(next.packTitle+" · 修订 "+next.packRevision+"\n"+(changed?"已保存。":"该文件已导入。")+"正在发送回执…");report("succeeded",result);}});
    }
    private void report(String state){report(state,null);}
    private void report(String state,JSONObject result){
        if(closed||terminal)return;terminal=true;io.execute(()->{try{JSONObject input=identity().put("state",state).put("revision",revision);if(result!=null)input.put("result",result);client.call("app-operations","finish",input);main.post(()->{if(!closed)status.setText(state.equals("succeeded")?next.packTitle+" · 修订 "+next.packRevision+"\n已导入，WorkBuddy可查询本次回执。":"本次导入未完成，请让WorkBuddy查询原请求。");});}catch(Exception failure){main.post(()->{if(!closed)status.setText(saved?"资源包已保存，回执未确认。请查询原请求，勿重复提交。":"导入回执未确认，请先查询原请求。");});}});
    }
    private void fail(String message){if(!closed){terminal=true;status.setText(message);}}
    @Override protected void onResume(){super.onResume();resumed=true;schedule();}
    @Override protected void onPause(){synchronized(guard){resumed=false;}super.onPause();}
    @Override protected void onDestroy(){
        synchronized(guard){closed=true;}main.removeCallbacksAndMessages(null);http.cancel();
        if(started&&!terminal&&client!=null)io.execute(()->{try{client.call("app-operations","finish",identity().put("state",processing?"unknown":"cancelled").put("revision",revision));}catch(Exception ignored){}});
        io.shutdown();super.onDestroy();
    }
}
