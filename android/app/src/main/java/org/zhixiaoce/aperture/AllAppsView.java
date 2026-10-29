package org.zhixiaoce.aperture;
import android.app.*;
import android.content.pm.ResolveInfo;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Local PackageManager icons and a remote-first, vertically scrollable app wall. */
final class AllAppsView {
    interface Open {void run(ResolveInfo app);}
    static Dialog show(Activity a,List<ResolveInfo> apps,String recent,Open launch,Open options){
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(px(a,40),px(a,24),px(a,40),px(a,30));body.setBackground(TvUi.background());
        TextView title=TvUi.text(a,"所有应用",32,false);body.addView(title);
        ScrollView scroll=new ScrollView(a);scroll.setClipToPadding(false);scroll.setPadding(px(a,6),px(a,12),px(a,6),px(a,12));body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout groups=new LinearLayout(a);groups.setOrientation(LinearLayout.VERTICAL);scroll.addView(groups);
        Dialog dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setContentView(body,new ViewGroup.LayoutParams(-1,-1));
        List<ResolveInfo> last=new ArrayList<>();for(String key:recent.split("\\n"))for(ResolveInfo app:apps)if((app.activityInfo.packageName+"/"+app.activityInfo.name).equals(key)&&!last.contains(app))last.add(app);
        if(!last.isEmpty())group(a,groups,"最近使用",last,dialog,launch,options);
        group(a,groups,"全部应用 · "+apps.size(),apps,dialog,launch,options);
        Button back=PlayerChrome.control(a,"返回",dialog::dismiss);body.addView(back);
        dialog.setOnShowListener(d->{if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout(Math.round(a.getResources().getDisplayMetrics().widthPixels*.92f),Math.round(a.getResources().getDisplayMetrics().heightPixels*.90f));}View first=findTile(groups);if(first!=null)first.requestFocus();else back.requestFocus();});dialog.show();return dialog;
    }
    private static View findTile(View view){if(view.isFocusable()&&view.isClickable())return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findTile(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    private static int px(Activity a,float value){return Math.round(value*a.getResources().getDisplayMetrics().heightPixels/1080f);}
    private static void group(Activity a,LinearLayout parent,String name,List<ResolveInfo> apps,Dialog dialog,Open launch,Open options){
        parent.addView(TvUi.text(a,name,25,true));GridLayout grid=new GridLayout(a);grid.setColumnCount(5);parent.addView(grid);
        for(int i=0;i<apps.size();i++){ResolveInfo app=apps.get(i);String label=app.loadLabel(a.getPackageManager()).toString();LinearLayout tile=new LinearLayout(a);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);tile.setPadding(px(a,12),px(a,16),px(a,12),px(a,16));tile.setFocusable(true);tile.setClickable(true);tile.setBackground(TvUi.states(a));tile.setContentDescription(label);
            ImageView icon=new ImageView(a);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);try{icon.setImageDrawable(app.loadIcon(a.getPackageManager()));}catch(RuntimeException ignored){}tile.addView(icon,new LinearLayout.LayoutParams(px(a,96),px(a,96)));
            TextView text=TvUi.text(a,label,26,false);text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(a,26));text.setGravity(Gravity.CENTER);text.setMaxLines(2);text.setMinLines(2);text.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(text,new LinearLayout.LayoutParams(-1,-2));
            tile.setOnClickListener(v->{dialog.dismiss();launch.run(app);});tile.setOnLongClickListener(v->{options.run(app);return true;});GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.columnSpec=GridLayout.spec(i%5);p.width=Math.max(1,(Math.round(a.getResources().getDisplayMetrics().widthPixels*.92f)-px(a,92))/5-px(a,28));p.height=px(a,216);p.setMargins(px(a,14),px(a,12),px(a,14),px(a,12));grid.addView(tile,p);
        }
    }
}
