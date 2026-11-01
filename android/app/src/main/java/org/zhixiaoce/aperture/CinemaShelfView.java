package org.zhixiaoce.aperture;
import android.app.Activity;import android.view.*;import android.widget.*;import org.json.*;import java.util.*;
/** A stable collection rail and immediate movie overview, sharing the native detail composition. */
final class CinemaShelfView {
    interface Select{void select(JSONObject record);}final LinearLayout stage,rail;final FrameLayout preview;private final Activity activity;private final Map<String,Button> rows=new HashMap<>();private final TextView count;
    CinemaShelfView(Activity a,List<JSONObject> records,String selected,Select focus,Runnable back,java.util.function.BiConsumer<String,String> category,Runnable checkUpdates){
        activity=a;stage=new LinearLayout(a);stage.setBackground(TvUi.background());rail=new LinearLayout(a);rail.setOrientation(1);rail.setPadding(px(20),px(30),px(16),px(20));rail.setBackgroundColor(0xf00c1118);stage.addView(rail,new LinearLayout.LayoutParams(Math.round(a.getResources().getDisplayMetrics().widthPixels*.23f),-1));rail.addView(TvUi.text(a,"我的收藏",30,false));count=TvUi.text(a,records.size()+" 项",18,true);rail.addView(count);
        ScrollView scroll=new ScrollView(a);LinearLayout list=new LinearLayout(a);list.setOrientation(1);scroll.addView(list);rail.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        for(JSONObject record:records){Button b=TvUi.button(a,record.optString("title"),()->focus.select(record));b.setId(View.generateViewId());b.setSingleLine(false);b.setMaxLines(2);b.setContentDescription(record.optString("title"));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,Math.max(px(76),Math.round(a.getResources().getDisplayMetrics().heightPixels*.09f)));p.setMargins(0,px(4),0,px(4));list.addView(b,p);b.setOnFocusChangeListener((v,f)->{if(f)focus.select(record);});rows.put(key(record),b);}
        String[] labels={"全部收藏","收藏影片","收藏频道","收藏NAS","最近观看","追剧"},kinds={null,"vod","live","nas",null,"vod"},flags={"favorite","favorite","favorite","favorite","history","follow"};
        Button filter=TvUi.button(a,selected+"  ⌄",()->TvDialogs.menu(a,"收藏分区",labels,(d,i)->category.accept(kinds[i],flags[i])));rail.addView(filter,new LinearLayout.LayoutParams(-1,px(52)));rail.addView(TvUi.button(a,"检查追剧更新",checkUpdates),new LinearLayout.LayoutParams(-1,px(48)));rail.addView(TvUi.button(a,"返回",back));
        preview=new FrameLayout(a);stage.addView(preview,new LinearLayout.LayoutParams(0,-1,1));
        if(records.isEmpty()){TextView empty=TvUi.text(a,"这里还没有内容\n在影片详情中选择收藏，随后可在这里继续观看。",25,true);empty.setPadding(px(55),px(90),px(55),px(40));preview.addView(empty);}
    }
    private String key(JSONObject ref){return ref.optString("kind")+"\0"+ref.optString("source")+"\0"+ref.optString("id");}
    private int px(float value){return TvUi.dp(activity,value);}
    void focus(JSONObject ref){Button row=rows.get(key(ref));if(row!=null)row.requestFocus();}
    CinemaDetailView show(CinemaCatalog.Item item,JSONObject saved,String source,CinemaArtwork artwork,boolean allowHttp,Runnable play,Runnable remove,Runnable back){
        boolean wasFocused=preview.hasFocus();CinemaDetailView detail=new CinemaDetailView(activity,item,back,true);preview.removeAllViews();preview.addView(detail.stage,new FrameLayout.LayoutParams(-1,-1));artwork.bind(detail.poster.image,item.poster,source,allowHttp);artwork.bind(detail.backdrop,item.backdrop.isEmpty()?item.poster:item.backdrop,source,allowHttp);
        String progress=saved.optBoolean("complete")?"已看完":saved.optLong("position")>0?"上次看到 "+saved.optLong("position")/60000+" 分钟":"";detail.credit.setText(String.join(" · ",Arrays.asList(saved.optString("episode"),progress)).replaceAll("^ · | · $",""));
        LinearLayout actions=new LinearLayout(activity);detail.actions.addView(actions);Button start=detail.action(actions,saved.optLong("position")>0&&!saved.optBoolean("complete")?"继续播放":"播放",play,true);detail.action(actions,"移除",remove,false);Button row=rows.get(key(saved));if(row!=null){start.setNextFocusLeftId(row.getId());row.setNextFocusRightId(start.getId());}if(wasFocused)start.requestFocus();return detail;
    }
}
