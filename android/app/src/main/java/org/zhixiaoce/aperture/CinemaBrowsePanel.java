package org.zhixiaoce.aperture;

import android.app.Activity;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Function;

/** Keeps the same scroll/grid/poster instances while a next-page request completes. */
final class CinemaBrowsePanel {
    private static final int COLUMNS=4;
    private final Activity owner;
    private final ScrollView scroll;
    private final GridLayout grid;
    private final LinearLayout footer;
    private final ProgressBar spinner;
    private final TextView message,empty;
    private final Button retry;
    private final Function<CinemaCatalog.Item,View> factory;
    private final Runnable load,left;
    private final Map<String,View> tiles=new LinkedHashMap<>();
    private final Map<String,Integer> positions=new HashMap<>();
    private List<CinemaCatalog.Item> shown=new ArrayList<>();
    private boolean closed,loading,more;
    private ViewTreeObserver.OnGlobalLayoutListener anchorTask;

    CinemaBrowsePanel(Activity owner,LinearLayout content,LinearLayout root,Function<CinemaCatalog.Item,View> factory,Runnable load,Runnable left){
        this.owner=owner;this.factory=factory;this.load=load;this.left=left;scroll=(ScrollView)content.getParent();
        grid=new GridLayout(owner);grid.setColumnCount(COLUMNS);grid.setClipChildren(false);content.addView(grid);
        empty=TvUi.text(owner,"",22,true);content.addView(empty);
        empty.setFocusable(true);empty.setFocusableInTouchMode(true);empty.setBackground(TvUi.states(owner));empty.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(event.getAction()==KeyEvent.ACTION_DOWN)left.run();return true;}if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0&&more&&!loading)load.run();return true;}return false;});
        // Reserve a fixed footer so loading/error visibility never changes the scroll viewport.
        footer=new LinearLayout(owner);footer.setGravity(Gravity.CENTER);root.addView(footer,new LinearLayout.LayoutParams(-1,px(48)));
        spinner=new ProgressBar(owner);spinner.setIndeterminate(true);spinner.setContentDescription("加载中");footer.addView(spinner,new LinearLayout.LayoutParams(px(26),px(26)));
        message=TvUi.text(owner,"",18,true);message.setPadding(px(10),0,px(10),0);footer.addView(message);
        retry=TvUi.button(owner,"重试",load);footer.addView(retry,new LinearLayout.LayoutParams(px(90),px(44)));
        state(false,false,false,0);
    }
    private int px(float value){return TvUi.dp(owner,value);}
    private void cancelAnchor(){if(anchorTask!=null){if(grid.getViewTreeObserver().isAlive())grid.getViewTreeObserver().removeOnGlobalLayoutListener(anchorTask);anchorTask=null;}}
    private View anchor(){for(CinemaCatalog.Item item:shown){View tile=tiles.get(item.id);if(tile.hasFocus())return tile;}for(CinemaCatalog.Item item:shown){View tile=tiles.get(item.id);if(tile.getBottom()+grid.getTop()>scroll.getScrollY())return tile;}return null;}
    void update(List<CinemaCatalog.Item> rows,boolean preserve,String emptyText){
        if(closed)return;cancelAnchor();View anchor=preserve?anchor():null;int offset=anchor==null?0:anchor.getTop()+grid.getTop()-scroll.getScrollY();
        shown=new ArrayList<>(rows);
        for(int i=0;i<shown.size();i++){
            CinemaCatalog.Item item=shown.get(i);View tile=tiles.get(item.id);
            if(tile==null){tile=factory.apply(item);if(tile.getId()==View.NO_ID)tile.setId(View.generateViewId());tiles.put(item.id,tile);final String id=item.id;tile.setOnKeyListener((v,key,event)->key(id,key,event));grid.addView(tile);}
            if(!positions.containsKey(item.id)||positions.get(item.id)!=i){GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.rowSpec=GridLayout.spec(i/COLUMNS);p.columnSpec=GridLayout.spec(i%COLUMNS,1f);p.width=0;p.height=Math.round(owner.getResources().getDisplayMetrics().heightPixels*.51f);p.setMargins(px(5),px(6),px(5),px(8));tile.setLayoutParams(p);positions.put(item.id,i);}
        }
        empty.setText(emptyText);empty.setVisibility(shown.isEmpty()?View.VISIBLE:View.GONE);
        if(anchor!=null){final View kept=anchor;anchorTask=()->{cancelAnchor();if(!closed&&kept.getParent()==grid)scroll.scrollTo(0,kept.getTop()+grid.getTop()-offset);};grid.getViewTreeObserver().addOnGlobalLayoutListener(anchorTask);}
    }
    private boolean key(String id,int code,KeyEvent event){
        if(closed)return true;
        int at=-1;for(int i=0;i<shown.size();i++)if(shown.get(i).id.equals(id)){at=i;break;}if(at<0)return false;
        if(code==KeyEvent.KEYCODE_DPAD_LEFT&&at%COLUMNS==0){if(event.getAction()==KeyEvent.ACTION_DOWN)left.run();return true;}
        int target=-1;
        if(code==KeyEvent.KEYCODE_DPAD_DOWN){
            if(at/COLUMNS<(shown.size()-1)/COLUMNS)target=Math.min(at+COLUMNS,shown.size()-1);
            else{if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0&&more&&!loading)load.run();return true;}
        }else if(code==KeyEvent.KEYCODE_DPAD_UP&&at>=COLUMNS)target=at-COLUMNS;
        else if(code==KeyEvent.KEYCODE_DPAD_RIGHT){if(at%COLUMNS<COLUMNS-1&&at+1<shown.size())target=at+1;else return true;}
        else if(code==KeyEvent.KEYCODE_DPAD_LEFT)target=at-1;
        if(target<0)return false;
        if(event.getAction()==KeyEvent.ACTION_DOWN){tiles.get(shown.get(target).id).requestFocus();if(code==KeyEvent.KEYCODE_DPAD_DOWN&&target/COLUMNS==(shown.size()-1)/COLUMNS&&more&&!loading)load.run();}
        return true;
    }
    void state(boolean loading,boolean more,boolean failed,int count){
        if(closed)return;if(!failed&&retry.hasFocus()){View kept=anchor();if(kept!=null)kept.requestFocus();else empty.requestFocus();}this.loading=loading;this.more=more;spinner.setVisibility(loading?View.VISIBLE:View.GONE);
        retry.setVisibility(failed?View.VISIBLE:View.GONE);message.setText(failed?"加载失败":count>=CinemaPaging.LIMIT?"已加载2000部":more||loading?"":"已到底");
    }
    View first(){return shown.isEmpty()?empty:tiles.get(shown.get(0).id);}
    void focus(String id,int y){View target=tiles.get(id);if(target==null)target=first();if(target!=null)target.requestFocus();scroll.post(()->{if(!closed)scroll.scrollTo(0,y);});}
    void close(){closed=true;cancelAnchor();}
}
