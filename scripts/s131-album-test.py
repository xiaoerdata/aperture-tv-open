"""Production native album with stalled animation/UI/store doubles, not an OEM renderer."""
from pathlib import Path
import hashlib,json,subprocess,tempfile
r=Path(__file__).resolve().parents[1];ns={'__file__':str(r/'scripts/s123-live-test.py')};t=(r/'scripts/s123-live-test.py').read_text();exec(compile(t[:t.index("base=r/'android/app/")],'ui-doubles','exec'),ns);stubs=ns['stubs'];jdk=ns['jdk'];lib=ns['lib']
stubs['android/view/Gravity.java']=stubs['android/view/Gravity.java'].replace('CENTER=17','BOTTOM=80,CENTER=17')
stubs['android/view/View.java']=stubs['android/view/View.java'].replace('public interface OnKeyListener','public static final int INVISIBLE=4;public float alpha=1;public void setAlpha(float n){alpha=n;}public void setBackgroundColor(int c){}public void setClickable(boolean b){}public boolean onKeyDown(int k,KeyEvent e){return false;}public boolean onKeyUp(int k,KeyEvent e){return false;}protected void onDetachedFromWindow(){}public android.view.ViewPropertyAnimator animate(){return new android.view.ViewPropertyAnimator();}public interface OnKeyListener')
stubs['android/view/ViewPropertyAnimator.java']='package android.view;public class ViewPropertyAnimator {public ViewPropertyAnimator alpha(float n){return this;}public ViewPropertyAnimator setDuration(long n){return this;}public void start(){}public void cancel(){}}'
stubs['android/widget/FrameLayout.java']=stubs['android/widget/FrameLayout.java'].replace('public LayoutParams(int w,int h,int gravity)','public LayoutParams(int w,int h){super(w,h);}public LayoutParams(int w,int h,int gravity)')
stubs['android/graphics/Bitmap.java']='package android.graphics;public class Bitmap {public boolean recycled;public void recycle(){recycled=true;}}'
stubs['android/widget/ImageView.java']='package android.widget;public class ImageView extends android.view.View {public enum ScaleType{FIT_CENTER}public android.graphics.Bitmap image;public ImageView(android.app.Activity a){}public void setScaleType(ScaleType s){}public void setImageBitmap(android.graphics.Bitmap b){image=b;}public void setImageDrawable(Object d){image=null;}}'
stubs['org/zhixiaoce/aperture/GalleryStore.java']='''package org.zhixiaoce.aperture;class GalleryStore {static String rows="[]";static boolean bad;GalleryStore(android.app.Activity a){}org.json.JSONObject snapshot()throws Exception{return new org.json.JSONObject().put("photos",new org.json.JSONArray(rows)).put("seconds",12);}android.graphics.Bitmap image(String id){return bad?null:new android.graphics.Bitmap();}}'''
stubs['org/zhixiaoce/aperture/TvUi.java']=stubs['org/zhixiaoce/aperture/TvUi.java'].replace('static int dp','static void touchClick(android.view.View v){}static int dp')
for name in ['CinemaHttp','CinemaEpg','CinemaArtwork']:stubs.pop('org/zhixiaoce/aperture/'+name+'.java',None)
source=r/'android/app/src/main/java/org/zhixiaoce/aperture/HomeAlbumView.java';test=r/'tests/java/HomeAlbumTest.java'
with tempfile.TemporaryDirectory(prefix='kaishi-s131-') as d:
 p=Path(d);files=[]
 for n,b in stubs.items():f=p/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(b);files.append(f)
 subprocess.run([str(jdk/'javac'),'-encoding','UTF-8','-cp',str(lib),'-d',d,*map(str,[*files,source,test])],check=True)
 q=subprocess.run([str(jdk/'java'),'-cp',d+':'+str(lib),'org.zhixiaoce.aperture.HomeAlbumTest'],capture_output=True,text=True,timeout=30);assert q.returncode==0,q.stdout+q.stderr;print(q.stdout.strip())
 (r/'docs/verification/s131-album-native.json').write_text(json.dumps({'passed':True,'output':q.stdout.strip(),'actualHomeAlbumView':True,'stalledAnimationDouble':True,'galleryStoreDouble':True,'realAndroidRenderer':False,'hisenseTested':False,'sources':{str(f.relative_to(r)):hashlib.sha256(f.read_bytes()).hexdigest() for f in [source,test,Path(__file__).resolve()]}},indent=2)+'\n')
