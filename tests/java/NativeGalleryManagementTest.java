package org.zhixiaoce.aperture;
import android.app.*;import android.view.*;import java.lang.reflect.*;import java.util.concurrent.*;
public final class NativeGalleryManagementTest {
 static int checks;static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
 static View tagged(View v,String tag){if(tag.equals(v.getTag()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=tagged(((ViewGroup)v).getChildAt(i),tag);if(found!=null)return found;}return null;}
 static void flush(NativeGallery v)throws Exception{Field f=NativeGallery.class.getDeclaredField("worker");f.setAccessible(true);ExecutorService worker=(ExecutorService)f.get(v);for(int i=0;i<3;i++){worker.submit(()->{}).get(2,TimeUnit.SECONDS);android.os.Handler.flush();}}
 public static void main(String[] args)throws Exception{
  GalleryStore store=new GalleryStore();NativeGallery view=new NativeGallery(new Activity(),store,new NativeGallery.Actions(){public void upload(){}public void stop(){}public void back(){}public void stages(){}public void snapshot(boolean ok){}},false);
  try{view.active(true);flush(view);View manage=tagged(view,"native-gallery-manage");check(manage!=null,"management entry exists");manage.performClick();AlertDialog menu=AlertDialog.last;check(menu.items.length==4,"photo menu includes delete and intervals");menu.choose(0);AlertDialog confirm=AlertDialog.last;check(confirm!=menu,"delete has separate confirmation");check(View.focus==confirm.getButton(AlertDialog.BUTTON_NEGATIVE),"keep is default focus");confirm.click(false);check(store.deletes==0,"keep never deletes");
   manage.performClick();AlertDialog.last.choose(0);AlertDialog.last.click(true);flush(view);check(store.deletes==1,"explicit confirmation deletes once");check("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".equals(store.deleted),"deletes captured photo id");check(store.ids.size()==1,"remaining photo retained");
   for(int i=1;i<=3;i++){manage.performClick();AlertDialog.last.choose(i);check(store.seconds==new int[]{8,12,20}[i-1],"interval persisted");}
   manage.performClick();AlertDialog.last.choose(0);AlertDialog late=AlertDialog.last;view.active(false);check(!late.isShowing(),"pause dismisses confirmation");view.active(true);flush(view);late.click(true);check(store.deletes==1,"old confirmation after resume cannot delete");
   manage.performClick();AlertDialog oldMenu=AlertDialog.last;view.active(false);view.active(true);flush(view);oldMenu.choose(1);check(store.seconds==20,"old menu after resume cannot change interval");
   store.ids.clear();view.refresh();flush(view);manage.performClick();check(AlertDialog.last.items.length==3,"empty album has no delete choice");AlertDialog.last.choose(0);check(store.seconds==8,"empty album interval can be saved");
   manage.performClick();AlertDialog closed=AlertDialog.last;view.close();check(!closed.isShowing(),"close dismisses management");closed.choose(2);check(store.seconds==8&&store.deletes==1,"late closed callback has no writes");
   System.out.println(checks+" production native gallery menu, cancellation and stale-callback assertions passed with JVM UI/store doubles");
  }finally{view.close();}
 }
}
