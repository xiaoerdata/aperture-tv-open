package org.zhixiaoce.aperture;

import android.app.Activity;
import android.view.*;
import android.widget.*;
import java.util.List;

/** Source/category navigation stays on the left; movie titles belong to the content pane. */
final class CinemaSectionView {
    static final class Entry {final String title;final boolean selected;final Runnable open;final int depth;Entry(String title,boolean selected,Runnable open){this(title,selected,open,0);}Entry(String title,boolean selected,Runnable open,int depth){this.title=title;this.selected=selected;this.open=open;this.depth=depth;}}
    final LinearLayout stage,rail;final ScrollView list;final LinearLayout entries;private final LinearLayout footer;private final TextView heading;private final Button toggle;
    private final Activity activity;private View selection;Runnable onCollapse;
    CinemaSectionView(Activity a,String title,View body){
        activity=a;stage=new LinearLayout(a);stage.setBackground(TvUi.background());stage.setOrientation(LinearLayout.HORIZONTAL);
        rail=new LinearLayout(a);rail.setOrientation(LinearLayout.VERTICAL);rail.setPadding(px(14),px(24),px(14),px(22));rail.setBackgroundColor(0xe80e1014);stage.addView(rail,new LinearLayout.LayoutParams(Math.round(a.getResources().getDisplayMetrics().widthPixels*.225f),-1));
        LinearLayout top=new LinearLayout(a);top.setGravity(Gravity.CENTER_VERTICAL);rail.addView(top);heading=TvUi.text(a,title,30,false);heading.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));toggle=PlayerChrome.control(a,"‹",()->{if(onCollapse!=null)onCollapse.run();});toggle.setContentDescription("折叠侧栏");top.addView(toggle,new LinearLayout.LayoutParams(px(56),px(62)));
        list=new ScrollView(a);list.setFillViewport(false);list.setClipToPadding(false);list.setPadding(0,px(12),0,px(12));entries=new LinearLayout(a);entries.setOrientation(LinearLayout.VERTICAL);list.addView(entries);rail.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        footer=new LinearLayout(a);footer.setOrientation(LinearLayout.VERTICAL);rail.addView(footer);stage.addView(body,new LinearLayout.LayoutParams(0,-1,1));
    }
    private int px(float value){return Math.round(value*activity.getResources().getDisplayMetrics().heightPixels/1080f);}
    void render(List<Entry> rows,Runnable search,Runnable browse,Runnable settings,Runnable saved){
        entries.removeAllViews();footer.removeAllViews();selection=null;
        for(Entry row:rows)add(entries,row.title,row.open,row.selected,row.depth);
        if(search!=null)add(footer,"搜索",search,false,0);if(browse!=null)add(footer,"片单",browse,false,0);
        add(footer,"我的收藏",saved,false,0);add(footer,"来源设置",settings,false,0);
    }
    private void add(LinearLayout parent,String title,Runnable open,boolean selected,int depth){
        Button b=PlayerChrome.control(activity,title,open);b.setId(View.generateViewId());b.setFocusableInTouchMode(true);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(BuildConfig.PARENTS_EDITION?30:26));b.setMinHeight(px(60));b.setMinimumHeight(px(60));b.setPadding(px(12+depth*28),0,px(8),0);b.setSingleLine(true);b.setEllipsize(android.text.TextUtils.TruncateAt.END);b.setContentDescription(title);b.setSelected(selected);if(selected){b.setTextColor(0xffe8d4ae);selection=b;}
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,px(62));lp.setMargins(0,px(2),0,px(2));parent.addView(b,lp);
    }
    // Keep a stable five-column content viewport. Sources never disappear with focus changes.
    void collapsed(boolean ignored){toggle.setVisibility(View.GONE);}
    void linkLeft(View target){View left=selection!=null?selection:entries.getChildCount()>0?entries.getChildAt(0):null;if(left!=null){target.setNextFocusLeftId(left.getId());target.setOnKeyListener((v,k,e)->{if(k==KeyEvent.KEYCODE_DPAD_LEFT){if(e.getAction()==KeyEvent.ACTION_DOWN)left.requestFocus();return true;}return false;});}}
    void focusFirst(){View v=entries.getChildCount()>0?entries.getChildAt(0):toggle;v.requestFocus();}
    void linkRight(View target){if(target==null)return;if(target.getId()==View.NO_ID)target.setId(View.generateViewId());for(LinearLayout group:new LinearLayout[]{entries,footer})for(int i=0;i<group.getChildCount();i++)if(group.getChildAt(i).isFocusable())group.getChildAt(i).setNextFocusRightId(target.getId());View left=selection!=null?selection:entries.getChildCount()>0?entries.getChildAt(0):toggle;target.setNextFocusLeftId(left.getId());}
}
