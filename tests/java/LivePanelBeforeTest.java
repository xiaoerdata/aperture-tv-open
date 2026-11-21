package org.zhixiaoce.aperture;
import android.app.Activity;import android.view.*;import android.widget.*;import java.util.*;
public class LivePanelBeforeTest {
 public static void main(String[] args)throws Exception{
  List<CinemaCatalog.Channel> a=Arrays.asList(new CinemaCatalog.Channel("A1","来源A",Arrays.asList("https://example.invalid/a")));
  CinemaLivePanel p=new CinemaLivePanel(new Activity(),new FrameLayout(new Activity()),a,new CinemaArtwork(),false,index->{},()->{});p.show(0);
  p.key(new KeyEvent(1,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT,0,false));p.key(new KeyEvent(1,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT,0,false));
  java.lang.reflect.Field f=p.getClass().getDeclaredField("group");f.setAccessible(true);if((int)f.get(p)!=0)throw new AssertionError("baseline mismatch");p.close();
  System.out.println("V47 reproduced: picker sees only current source; RIGHT remains in its sole group");
 }
}
