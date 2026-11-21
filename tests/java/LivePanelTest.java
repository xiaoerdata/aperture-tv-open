package org.zhixiaoce.aperture;
import android.app.Activity;import android.os.Handler;import android.view.*;import android.widget.*;import java.util.*;import java.util.concurrent.*;
public class LivePanelTest {
 static int checks;static long time;static int selected=-1,selections;static CinemaCatalog.Live chosen;static List<CinemaCatalog.Channel> chosenChannels;
 static Object get(Object o,String n)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static CinemaCatalog.Channel channel(String n,String group){return new CinemaCatalog.Channel(n,group,Arrays.asList("https://example.invalid/"+n));}
 static CinemaCatalog.Live live(String n,CinemaCatalog.Channel... c){return new CinemaCatalog.Live(n,"",Arrays.asList(c));}
 static CinemaLivePanel panel(List<CinemaCatalog.Live> sources,CinemaCatalog.Live current){return new CinemaLivePanel(new Activity(),new FrameLayout(new Activity()),sources,current,current.channels,new CinemaArtwork(),false,(s,c,i)->{chosen=s;chosenChannels=c;selected=i;selections++;},()->{});}
 static void key(CinemaLivePanel p,int code){long t=++time;p.key(new KeyEvent(t,0,code,0,false));p.key(new KeyEvent(t,1,code,0,false));}
 static int group(CinemaLivePanel p)throws Exception{return (int)get(p,"group");}
 static int source(CinemaLivePanel p)throws Exception{return (int)get(p,"sourceIndex");}
 static void await(java.util.function.BooleanSupplier b)throws Exception{long end=System.currentTimeMillis()+3000;while(!b.getAsBoolean()&&System.currentTimeMillis()<end){Thread.sleep(10);Handler.flush();}check(b.getAsBoolean(),"async fixture completed");}
 public static void main(String[] args)throws Exception{
  CinemaCatalog.Live a=live("来源A",channel("A1","A组"),channel("A2","A组")),b=live("来源B",channel("B1","B组")),c=live("来源C",channel("C1","C组"));
  CinemaLivePanel p=panel(Arrays.asList(a,b,c),a);
  try{
   p.showChannel(a.channels.get(0));key(p,22);check(source(p)==1,"RIGHT crosses a single-group source");check(selections==0,"browsing never changes playback");
   key(p,22);check(source(p)==2,"RIGHT reaches source C");key(p,22);check(source(p)==0,"RIGHT wraps sources");key(p,21);check(source(p)==2,"LEFT wraps sources");
   long t=++time;p.key(new KeyEvent(t,0,22,0,false));int at=source(p);p.key(new KeyEvent(t,0,22,0,false));p.key(new KeyEvent(t,0,22,1,false));p.key(new KeyEvent(t,0,22,4,false));check(source(p)==at,"duplicates and long-press repeats do not cycle again");p.key(new KeyEvent(t,1,22,0,false));
   key(p,22);key(p,23);check(chosen==b&&selected==0&&chosenChannels.get(0)==b.channels.get(0),"OK selects exact source and channel");
   p.showChannel(a.channels.get(0));key(p,20);check((int)get(p,"selected")==1,"DOWN selects next channel");View.flushPosts();check(View.focus==((LinearLayout)get(p,"rows")).getChildAt(1),"queued first-row focus cannot steal selection");
   Object focus=View.focus;p.guide(new CinemaEpg());check(View.focus==focus,"guide refresh preserves focus");
   long press=++time;p.key(new KeyEvent(press,0,23,0,false));int count=selections;key(p,22);p.key(new KeyEvent(press,1,23,0,false));check(selections==count,"group change invalidates old OK release");
   p.showChannel(a.channels.get(0));press=++time;p.key(new KeyEvent(press,0,23,0,false));p.key(new KeyEvent(press,1,23,0,true));check(selections==count,"canceled OK does not tune");
   key(p,22);View.flushPosts();check(View.focus==((LinearLayout)get(p,"rows")).getChildAt(0),"detached source rows cannot reclaim focus");
   p.hide();check(!p.open(),"BACK/hide closes picker");key(p,8);key(p,23);check(chosen==a&&selected==0,"closed numeric tuning uses playing source, not last browsed source");
   p.showChannel(a.channels.get(1));check(source(p)==0&&(int)get(p,"selected")==1,"reopen follows currently playing channel");key(p,4);check(!p.open(),"BACK closes only picker");
  }finally{p.close();}
  p=panel(Arrays.asList(a),a);try{p.showChannel(a.channels.get(0));key(p,21);check(group(p)==0,"one real group stays usable");key(p,23);check(chosen==a,"single-source confirmation works");}finally{p.close();}
  CinemaCatalog.Live multi=live("多分组",channel("N1","新闻"),channel("N2","影视"),channel("N3","体育"));
  p=panel(Arrays.asList(multi,b),multi);try{p.showChannel(multi.channels.get(0));key(p,22);check(source(p)==0&&group(p)==1,"RIGHT first changes group inside current source");key(p,22);check(group(p)==2,"internal final group reachable");key(p,22);check(source(p)==1,"then crosses source boundary");key(p,21);check(source(p)==0&&group(p)==2,"LEFT enters previous source at its last group");key(p,21);check(group(p)==1,"LEFT navigates internal groups");}finally{p.close();}
  CinemaCatalog.Live remote=new CinemaCatalog.Live("远程来源","https://example.invalid/list.m3u",new ArrayList<>());
  p=panel(Arrays.asList(a,remote,c),a);try{p.showChannel(a.channels.get(0));key(p,22);check(CinemaHttp.started.await(2,TimeUnit.SECONDS),"remote source fetched lazily");check((boolean)get(p,"loading"),"loading state retained");int count=selections;key(p,23);check(selections==count,"OK while loading does not play old channel");key(p,22);check(source(p)==2,"can leave loading source");CinemaHttp.release.countDown();Thread.sleep(80);Handler.flush();check(source(p)==2&&((List<?>)get(p,"channels")).get(0)==c.channels.get(0),"stale network result cannot replace current group");}finally{p.close();}
  CinemaHttp.started=new CountDownLatch(1);CinemaHttp.release=new CountDownLatch(1);CinemaHttp.fail=true;
  p=panel(Arrays.asList(a,remote,c),a);try{p.showChannel(a.channels.get(0));key(p,22);check(CinemaHttp.started.await(2,TimeUnit.SECONDS),"failure fetch started");CinemaHttp.release.countDown();CinemaLivePanel ref=p;await(()->{try{return !(boolean)get(ref,"loading");}catch(Exception e){throw new RuntimeException(e);}});check(((TextView)get(p,"preview")).text.contains("无法读取"),"failed group has feedback");key(p,22);check(source(p)==2,"failed group does not trap navigation");}finally{p.close();}
  p=panel(Arrays.asList(a,b),a);p.showChannel(a.channels.get(0));p.close();check(!p.key(new KeyEvent(++time,0,22,0,false)),"closed picker ignores input");
  System.out.println(checks+" production live-picker assertions passed with explicit JVM UI/HTTP doubles");
 }
}
