package org.zhixiaoce.aperture;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Eight-column remote grid. Scrolls internally; its edges return to the home screens. */
final class AppWallView extends ScrollView {
    private final Activity owner;private final GridLayout grid;private final List<LinearLayout> tiles=new ArrayList<>();
    private final List<FrameLayout> stages=new ArrayList<>();private final List<TextView> labels=new ArrayList<>();private final Runnable up,down;
    AppWallView(Activity a,DestinationApps apps,JSONObject snapshot,Runnable up,Runnable down){
        super(a);owner=a;this.up=up;this.down=down;setTag("native-app-wall");setFillViewport(false);setClipToPadding(false);setFocusable(false);
        grid=new GridLayout(a);grid.setColumnCount(8);addView(grid);
        tile("添加应用",null,"＋",apps::editWall);
        JSONArray rows=snapshot.optJSONArray("wall");for(int i=0;rows!=null&&i<Math.min(AppWall.MAX,rows.length());i++){
            JSONObject row=rows.optJSONObject(i);if(row==null)continue;String key=row.optString("key");byte[] bytes=apps.icon(key);Drawable icon=bytes==null?null:Drawable.createFromStream(new java.io.ByteArrayInputStream(bytes),null);
            tile(row.optString("name"),icon,"▦",()->apps.launchWall(key));
        }
        tile("所有应用",null,"▦",apps::all);tile("管理应用",null,"✎",apps::editWall);
    }
    private void tile(String name,Drawable icon,String fallback,Runnable action){
        LinearLayout tile=new LinearLayout(owner);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);tile.setFocusable(true);tile.setClickable(true);tile.setContentDescription(name);tile.setId(View.generateViewId());
        FrameLayout stage=new FrameLayout(owner);stage.setBackground(TvUi.glass(owner,false));stage.setFocusable(false);
        if(icon!=null){ImageView image=new ImageView(owner);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImageDrawable(icon);stage.addView(image,new FrameLayout.LayoutParams(80,80,Gravity.CENTER));}
        else{TextView glyph=TvUi.text(owner,fallback,28,false);glyph.setGravity(Gravity.CENTER);stage.addView(glyph,new FrameLayout.LayoutParams(-1,-1));}
        tile.addView(stage,new LinearLayout.LayoutParams(100,100));TextView label=TvUi.text(owner,name,24,false);label.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(label,new LinearLayout.LayoutParams(-1,-2));
        tile.setOnClickListener(v->action.run());tile.setOnFocusChangeListener((v,focused)->{stage.setBackground(TvUi.glass(owner,focused));if(focused)v.requestRectangleOnScreen(new android.graphics.Rect(0,0,v.getWidth(),v.getHeight()),true);});TvUi.touchClick(tile);
        tiles.add(tile);stages.add(stage);labels.add(label);GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.columnSpec=GridLayout.spec((tiles.size()-1)%8);p.width=128;p.height=174;grid.addView(tile,p);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(w<=0||w==oldw)return;int cell=Math.max(1,w/8),edge=Math.round(cell*.8f);for(int i=0;i<tiles.size();i++){
        LinearLayout tile=tiles.get(i);GridLayout.LayoutParams p=(GridLayout.LayoutParams)tile.getLayoutParams();p.width=cell;p.height=Math.round(cell*1.34f);tile.setLayoutParams(p);tile.setPadding(0,Math.round(cell*.04f),0,0);
        FrameLayout stage=stages.get(i);stage.setLayoutParams(new LinearLayout.LayoutParams(edge,edge));View image=stage.getChildAt(0);if(image instanceof ImageView)image.setLayoutParams(new FrameLayout.LayoutParams(Math.round(edge*.67f),Math.round(edge*.67f),Gravity.CENTER));else ((TextView)image).setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,cell*.23f);
        TextView label=labels.get(i);label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,cell*.145f);label.setPadding(0,Math.round(cell*.04f),0,0);
    }}
    void first(){if(!tiles.isEmpty()){scrollTo(0,0);tiles.get(0).requestFocus();}}
    void last(){if(!tiles.isEmpty())tiles.get(tiles.size()-1).requestFocus();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(e.getAction()==KeyEvent.ACTION_DOWN){int at=-1;for(int i=0;i<tiles.size();i++)if(tiles.get(i).hasFocus()){at=i;break;}
            if(at>=0){int code=e.getKeyCode(),target=at;if(code==KeyEvent.KEYCODE_DPAD_DOWN){if(at+8>=tiles.size()){down.run();return true;}target=at+8;}
                else if(code==KeyEvent.KEYCODE_DPAD_UP){if(at<8){up.run();return true;}target=at-8;}
                else if(code==KeyEvent.KEYCODE_DPAD_LEFT){if(at%8==0)return true;target=at-1;}
                else if(code==KeyEvent.KEYCODE_DPAD_RIGHT){if(at%8==7||at+1>=tiles.size())return true;target=at+1;}
                if(target!=at){tiles.get(target).requestFocus();return true;}
            }
        }return super.dispatchKeyEvent(e);
    }
}
