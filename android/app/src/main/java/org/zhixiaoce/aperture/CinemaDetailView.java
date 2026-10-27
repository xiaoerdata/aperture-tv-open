package org.zhixiaoce.aperture;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Native detail composition; domain actions and async ownership stay with the Activity. */
final class CinemaDetailView {
    final FrameLayout stage;final LinearLayout body,copy,actions;final ImageView backdrop;final CinemaPoster poster;final TextView facts,summary,credit,rating;final Activity activity;
    private int dp(float n){return TvUi.dp(activity,n);}private TextView text(String value,int size,boolean muted){TextView v=TvUi.text(activity,value,size,muted);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size*activity.getResources().getDisplayMetrics().heightPixels/1080f*activity.getResources().getConfiguration().fontScale);return v;}
    CinemaDetailView(Activity a,CinemaCatalog.Item item,Runnable back){
        activity=a;stage=new FrameLayout(a);stage.setBackground(TvUi.background());backdrop=new ImageView(a){@Override public void setImageBitmap(android.graphics.Bitmap bitmap){if(bitmap==null){super.setImageBitmap(null);return;}int width=Math.min(160,bitmap.getWidth()),height=Math.max(1,bitmap.getHeight()*width/bitmap.getWidth());super.setImageBitmap(android.graphics.Bitmap.createScaledBitmap(bitmap,width,height,true));if(android.animation.ValueAnimator.areAnimatorsEnabled()){setAlpha(0);animate().alpha(.42f).setDuration(300).start();}else setAlpha(.42f);}};backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);backdrop.setAlpha(.42f);stage.addView(backdrop,new FrameLayout.LayoutParams(-1,Math.round(a.getResources().getDisplayMetrics().heightPixels*.74f)));
        android.view.View gradient=new android.view.View(a);gradient.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x33090c10,0xb9090c10,0xff090c10}));stage.addView(gradient,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(a);scroll.setClipChildren(false);stage.addView(scroll,new FrameLayout.LayoutParams(-1,-1));body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);int x=Math.round(a.getResources().getDisplayMetrics().widthPixels*.055f);body.setPadding(x,dp(18),x,dp(28));scroll.addView(body);
        Button returned=TvUi.button(a,"返回片单",back);returned.setContentDescription("返回片单并恢复原位置");body.addView(returned,new LinearLayout.LayoutParams(-2,dp(44)));
        LinearLayout hero=new LinearLayout(a);hero.setPadding(0,Math.round(a.getResources().getDisplayMetrics().heightPixels*.04f),0,dp(20));body.addView(hero);poster=new CinemaPoster(a,item.name);int height=Math.round(a.getResources().getDisplayMetrics().heightPixels*.57f);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(height*2/3,height);pp.setMargins(0,0,Math.round(a.getResources().getDisplayMetrics().widthPixels*.035f),0);hero.addView(poster,pp);
        copy=new LinearLayout(a);copy.setOrientation(LinearLayout.VERTICAL);hero.addView(copy,new LinearLayout.LayoutParams(0,-2,1));TextView title=text(item.name,BuildConfig.PARENTS_EDITION?72:68,false);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(title);
        facts=text("",30,true);facts.setMaxLines(2);copy.addView(facts);rating=text("",34,false);rating.setTextColor(0xffb7c99a);copy.addView(rating);
        summary=text("",30,false);summary.setMaxLines(3);summary.setEllipsize(android.text.TextUtils.TruncateAt.END);summary.setLineSpacing(dp(3),1);copy.addView(summary);summary.setFocusable(true);summary.setContentDescription("影片简介");summary.setOnFocusChangeListener((v,f)->v.setBackground(f?TvUi.glass(a,true):null));summary.setOnClickListener(v->{TextView full=text(summary.getText().toString(),23,false);full.setLineSpacing(dp(4),1);TvDialogs.panel(a,"简介",full);});
        actions=new LinearLayout(a);actions.setOrientation(LinearLayout.VERTICAL);copy.addView(actions);
        credit=text("来源资料",19,true);copy.addView(credit);fill(item);
    }
    void fill(CinemaCatalog.Item item){List<String> values=new ArrayList<>();for(String value:new String[]{item.year,item.genre,item.duration,item.area})if(value!=null&&!value.isEmpty())values.add(value);facts.setText(String.join(" · ",values));facts.setVisibility(values.isEmpty()?View.GONE:View.VISIBLE);rating.setText(item.rating.isEmpty()?"":(item.ratingSource.isEmpty()?"来源评分":item.ratingSource)+"  "+item.rating);rating.setVisibility(item.rating.isEmpty()?View.GONE:View.VISIBLE);summary.setText(item.summary.isEmpty()?"暂无简介":item.summary);}
    Button action(LinearLayout row,String title,Runnable run,boolean primary){Button b=TvUi.button(activity,title,run);if(primary){GradientDrawable shape=new GradientDrawable();shape.setColor(0xffe9dfca);shape.setCornerRadius(dp(10));b.setBackground(shape);b.setTextColor(0xff11151b);b.setOnFocusChangeListener((v,f)->{shape.setStroke(dp(f?3:1),f?0xfffff7e6:0xffbcb3a2);v.setScaleX(f?1.025f:1);v.setScaleY(f?1.025f:1);});}TvUi.addAction(activity,row,b);LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)b.getLayoutParams();p.height=Math.max(dp(44),Math.round(activity.getResources().getDisplayMetrics().heightPixels*.075f));p.weight=primary?2:1;b.setLayoutParams(p);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,30*activity.getResources().getDisplayMetrics().heightPixels/1080f);return b;}
}
