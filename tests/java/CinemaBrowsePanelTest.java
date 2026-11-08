package org.zhixiaoce.aperture;

import android.app.Activity;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Production panel with explicit JVM view-tree/layout/focus doubles. */
public final class CinemaBrowsePanelTest {
    private static int count;
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);count++;}
    private static Object field(Object object,String name)throws Exception{java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static List<CinemaCatalog.Item> page(int start,int size){List<CinemaCatalog.Item> out=new ArrayList<>();for(int i=start;i<start+size;i++)out.add(new CinemaCatalog.Item("id"+i,"影片"+i,"",Collections.emptyList()));return out;}
    private static void key(View view,int code,int repeat){view.sendKey(new KeyEvent(KeyEvent.ACTION_DOWN,code,repeat));view.sendKey(new KeyEvent(KeyEvent.ACTION_UP,code));}
    public static void main(String[] args)throws Exception{
        Activity owner=new Activity();LinearLayout root=new LinearLayout(owner),content=new LinearLayout(owner);ScrollView scroll=new ScrollView(owner);scroll.addView(content);root.addView(scroll);
        Map<String,View> made=new HashMap<>();AtomicInteger requests=new AtomicInteger(),left=new AtomicInteger();
        CinemaBrowsePanel panel=new CinemaBrowsePanel(owner,content,root,item->{View view=new View();made.put(item.id,view);return view;},requests::incrementAndGet,left::incrementAndGet);
        GridLayout grid=(GridLayout)field(panel,"grid");ProgressBar spinner=(ProgressBar)field(panel,"spinner");Button retry=(Button)field(panel,"retry");
        List<CinemaCatalog.Item> rows=page(0,8);panel.update(rows,false,"");panel.state(false,true,false,8);grid.frame();panel.focus("id4",300);View.flushPosts();
        View kept=made.get("id4"),sameGrid=grid;int keptId=kept.getId(),parents=root.getChildCount();
        key(kept,KeyEvent.KEYCODE_DPAD_DOWN,0);check(requests.get()==1&&kept.hasFocus(),"bottom Down requests loading without replacing current focus");
        panel.state(true,true,false,8);check(spinner.getVisibility()==View.VISIBLE&&grid.getChildCount()==8,"spinner coexists with existing posters");
        key(kept,KeyEvent.KEYCODE_DPAD_DOWN,1);key(kept,KeyEvent.KEYCODE_DPAD_DOWN,0);check(requests.get()==1,"in-flight loading suppresses repeated requests");
        rows.addAll(page(8,4));panel.update(rows,true,"");panel.state(false,true,false,12);grid.frame();
        check(grid==sameGrid&&root.getChildCount()==parents,"grid and page root never replaced by pagination");
        check(made.size()==12&&made.get("id4")==kept&&kept.getId()==keptId,"only new tiles created; old tile and poster identity retained");
        check(kept.hasFocus()&&scroll.getScrollY()==300,"append preserves focused movie and scroll position");
        check(spinner.getVisibility()==View.GONE,"success hides only loading indicator");
        key(kept,KeyEvent.KEYCODE_DPAD_DOWN,0);check(made.get("id8").hasFocus(),"next Down enters appended row");
        panel.state(true,true,false,12);panel.state(false,true,true,12);check(retry.getVisibility()==View.VISIBLE&&grid.getChildCount()==12,"failure keeps every movie and exposes footer retry");
        retry.requestFocus();retry.performClick();check(requests.get()>=2,"retry requests the next page again");panel.state(true,true,false,12);check(retry.getVisibility()==View.GONE&&grid.hasFocus(),"retry spinner restores grid focus before hiding retry button");

        made.get("id8").requestFocus();scroll.scrollTo(0,600);int offset=made.get("id8").getTop()-scroll.getScrollY();
        List<CinemaCatalog.Item> sorted=new ArrayList<>(page(20,4));sorted.addAll(rows);panel.update(sorted,true,"");grid.frame();
        check(made.get("id8").hasFocus()&&made.get("id8").getTop()-scroll.getScrollY()==offset,"global score/date insertion above view preserves visible anchor");
        check(made.size()==16&&grid.getChildCount()==16,"reordering does not recreate existing image views");
        panel.state(false,false,false,16);int done=requests.get();key(made.get("id11"),KeyEvent.KEYCODE_DPAD_DOWN,0);check(requests.get()==done,"last page stops additional requests");
        key(made.get("id20"),KeyEvent.KEYCODE_DPAD_LEFT,0);check(left.get()==1,"first-column Left still reaches source sidebar");

        int y=scroll.getScrollY();panel.update(sorted,true,"");panel.close();grid.frame();check(scroll.getScrollY()==y,"closed panel removes pending anchor callback");key(made.get("id11"),KeyEvent.KEYCODE_DPAD_DOWN,0);check(requests.get()==done,"detached old grid cannot trigger another load");

        AtomicInteger emptyRequests=new AtomicInteger();LinearLayout otherRoot=new LinearLayout(owner),otherContent=new LinearLayout(owner);ScrollView otherScroll=new ScrollView(owner);otherScroll.addView(otherContent);otherRoot.addView(otherScroll);
        CinemaBrowsePanel empty=new CinemaBrowsePanel(owner,otherContent,otherRoot,item->new View(),emptyRequests::incrementAndGet,()->{});empty.update(Collections.emptyList(),false,"已加载内容中没有匹配项");empty.state(false,true,false,12);empty.focus("",0);View.flushPosts();key(empty.first(),KeyEvent.KEYCODE_DPAD_DOWN,0);check(emptyRequests.get()==1,"filtered-empty page can continue Down without a Load More button");empty.close();
        System.out.println(count+" production browse-panel assertions passed with JVM UI doubles; no Android rendering or Xiaomi remote executed");
    }
}
