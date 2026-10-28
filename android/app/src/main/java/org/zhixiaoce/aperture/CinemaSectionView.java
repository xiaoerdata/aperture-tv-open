package org.zhixiaoce.aperture;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.List;

/** One persistent left selector and a right content pane, never a stack of source pages. */
final class CinemaSectionView {
    static final class Entry {final String title;final boolean selected;final Runnable open;Entry(String title,boolean selected,Runnable open){this.title=title;this.selected=selected;this.open=open;}}
    final LinearLayout stage,rail;final ScrollView list;final LinearLayout entries;
    private final Activity activity;private View selection;
    CinemaSectionView(Activity a,String title,View body){
        activity=a;stage=new LinearLayout(a);stage.setBackground(TvUi.background());stage.setOrientation(LinearLayout.HORIZONTAL);
        rail=new LinearLayout(a);rail.setOrientation(LinearLayout.VERTICAL);rail.setPadding(TvUi.dp(a,16),TvUi.dp(a,22),TvUi.dp(a,12),TvUi.dp(a,16));rail.setBackgroundColor(0xe80e1014);stage.addView(rail,new LinearLayout.LayoutParams(Math.round(a.getResources().getDisplayMetrics().widthPixels*.225f),-1));
        TextView heading=TvUi.text(a,title,30,false);heading.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);rail.addView(heading);
        list=new ScrollView(a);list.setFillViewport(false);entries=new LinearLayout(a);entries.setOrientation(LinearLayout.VERTICAL);list.addView(entries);rail.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        stage.addView(body,new LinearLayout.LayoutParams(0,-1,1));
    }
    void render(String source,Runnable sources,String category,Runnable categories,String group,List<Entry> rows,Runnable settings,Runnable saved){
        entries.removeAllViews();selection=null;add(source.isEmpty()?"选择来源":source+" ▾",sources,false);
        if(categories!=null)add(category+" ▾",categories,false);
        TextView label=TvUi.text(activity,group,19,true);label.setPadding(TvUi.dp(activity,10),TvUi.dp(activity,14),0,TvUi.dp(activity,6));entries.addView(label);
        for(Entry row:rows)add(row.title,row.open,row.selected);
        add("来源设置",settings,false);add("我的收藏",saved,false);
    }
    private void add(String title,Runnable open,boolean selected){
        Button b=PlayerChrome.control(activity,title,open);b.setId(View.generateViewId());b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setTextSize(BuildConfig.PARENTS_EDITION?20:17);b.setSingleLine(true);b.setEllipsize(android.text.TextUtils.TruncateAt.END);b.setContentDescription(title);b.setSelected(selected);if(selected){b.setTextColor(0xffe8d4ae);selection=b;}
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,TvUi.dp(activity,52));lp.setMargins(0,TvUi.dp(activity,2),0,TvUi.dp(activity,2));entries.addView(b,lp);
    }
    void linkRight(View target){if(target==null)return;if(target.getId()==View.NO_ID)target.setId(View.generateViewId());for(int i=0;i<entries.getChildCount();i++)if(entries.getChildAt(i).isFocusable())entries.getChildAt(i).setNextFocusRightId(target.getId());View left=selection!=null?selection:entries.getChildAt(0);if(left!=null)target.setNextFocusLeftId(left.getId());}
}
