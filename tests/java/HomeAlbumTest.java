package org.zhixiaoce.aperture;
import android.os.Handler;import android.widget.ImageView;import android.graphics.Bitmap;import java.util.concurrent.ExecutorService;
public class HomeAlbumTest {
 static int n;static void check(boolean b,String s){n++;if(!b)throw new AssertionError(s);}
 static Object field(Object o,String n)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static void flush(HomeAlbumView h)throws Exception{ExecutorService e=(ExecutorService)field(h,"worker");for(int i=0;i<3;i++){e.submit(()->{}).get();Handler.flush();}}
 public static void main(String[] a)throws Exception{HomeAlbumView h=new HomeAlbumView(new android.app.Activity(),()->{});try{
 h.active(true);flush(h);ImageView[] p=(ImageView[])field(h,"pictures");check(p[0].image==null&&p[1].image==null,"empty initial album");
 GalleryStore.rows="[{\"id\":\"11111111111111111111111111111111\"}]";((Runnable)field(h,"poll")).run();flush(h);ImageView first=p[0].image!=null?p[0]:p[1];check(first.image!=null&&first.alpha==1,"upload becomes visible without focus change or animator execution");Bitmap image=first.image;
 h.active(true);flush(h);check(first.image==image,"same active notification does not restart first frame");
 GalleryStore.rows="[{\"id\":\"11111111111111111111111111111111\"},{\"id\":\"22222222222222222222222222222222\"}]";((Runnable)field(h,"poll")).run();flush(h);check(((org.json.JSONArray)field(h,"photos")).length()==2,"in-place upload refreshes list");
 GalleryStore.bad=true;((Runnable)field(h,"next")).run();flush(h);check(p[0].image!=null||p[1].image!=null,"failed decode preserves previous image");GalleryStore.bad=false;
 h.active(false);check(!image.recycled,"submitted bitmap never recycled while render thread may reference it");check(p[0].image==null&&p[1].image==null,"inactive album releases drawable references");h.active(true);flush(h);check((p[0].image!=null&&p[0].alpha==1)||(p[1].image!=null&&p[1].alpha==1),"return from settings restores visible frame");
 GalleryStore.rows="[]";((Runnable)field(h,"poll")).run();flush(h);check(p[0].image==null&&p[1].image==null,"deleting last photo clears prior frame");
 }finally{h.onDetachedFromWindow();}h.active(true);check((boolean)field(h,"closed"),"late lifecycle cannot restart detached executor");System.out.println(n+" native album assertions passed with stalled animations and store/UI doubles");}
}
