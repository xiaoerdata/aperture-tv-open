"""Production native photo Activity and Main return methods, explicit lifecycle doubles."""
from pathlib import Path
import hashlib,json,subprocess,tempfile
r=Path(__file__).resolve().parents[1];base=r/'android/app/src/main/java/org/zhixiaoce/aperture';jdk=r/'.android-local/jdk17/bin'
stubs={
'android/os/Bundle.java':'package android.os;public class Bundle{}',
'android/view/View.java':'package android.view;public class View {public void setSystemUiVisibility(int n){}public boolean requestFocus(){return true;}}',
'android/view/Window.java':'package android.view;public class Window{public View getDecorView(){return new View();}}',
'android/view/KeyEvent.java':'package android.view;public class KeyEvent{public static final int ACTION_DOWN=0,ACTION_UP=1,KEYCODE_DPAD_UP=19,KEYCODE_DPAD_DOWN=20,KEYCODE_BACK=4;final int k,a,r;public KeyEvent(int k,int a,int r){this.k=k;this.a=a;this.r=r;}public int getKeyCode(){return k;}public int getAction(){return a;}public int getRepeatCount(){return r;}}',
'android/content/Intent.java':'package android.content;public class Intent{public static final int FLAG_ACTIVITY_CLEAR_TOP=1,FLAG_ACTIVITY_SINGLE_TOP=2;public Class<?> target;public int page=-1,flags;public Intent(){}public Intent(Object c,Class<?> t){target=t;}public Intent putExtra(String n,int v){page=v;return this;}public Intent addFlags(int f){flags|=f;return this;}}',
'android/app/Activity.java':'package android.app;import android.view.*;import android.content.*;public class Activity{public static final int RESULT_OK=-1;public boolean root,focused=true;public int finishes,keys;public Intent started,result;public View content;public void onCreate(android.os.Bundle b){}public Window getWindow(){return new Window();}public void setContentView(View v){content=v;}public void startActivity(Intent i){started=i;}public void setResult(int r,Intent i){result=i;}public boolean isTaskRoot(){return root;}public boolean hasWindowFocus(){return focused;}public void finish(){finishes++;}public boolean dispatchKeyEvent(KeyEvent e){keys++;return false;}public void onBackPressed(){}protected void onResume(){}protected void onPause(){}protected void onDestroy(){}public void onWindowFocusChanged(boolean f){focused=f;}}',
'org/zhixiaoce/aperture/HomeAlbumView.java':'package org.zhixiaoce.aperture;class HomeAlbumView extends android.view.View{final Runnable settings;boolean active;HomeAlbumView(android.app.Activity a,Runnable s){settings=s;}void active(boolean v){active=v;}}',
'org/zhixiaoce/aperture/GalleryActivity.java':'package org.zhixiaoce.aperture;class GalleryActivity extends android.app.Activity{}',
'org/zhixiaoce/aperture/MainActivity.java':'package org.zhixiaoce.aperture;class MainActivity extends android.app.Activity{}',
'org/zhixiaoce/aperture/VoiceAssistant.java':'package org.zhixiaoce.aperture;class VoiceAssistant{static boolean systemVoiceKey(int k){return k==231;}}'}
sources=[base/'HomeAlbumActivity.java',r/'tests/java/HomeAlbumActivityTest.java']
with tempfile.TemporaryDirectory(prefix='kaishi-s136-native-') as d:
 files=[]
 for n,b in stubs.items():p=Path(d)/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(b);files.append(p)
 subprocess.run([str(jdk/'javac'),'-d',d,*map(str,[*files,*sources])],check=True)
 q=subprocess.check_output([str(jdk/'java'),'-cp',d,'org.zhixiaoce.aperture.HomeAlbumActivityTest'],text=True);print(q.strip())
 # Execute actual queued return method; only its WebView and lifecycle dependencies differ.
 main=(base/'MainActivity.java').read_text();a=main.index('    private void applyAlbumReturn()');b=main.index('    private void action(',a);method=main[a:b]
 harness='''class AlbumReturnTest {int albumReturnPage=-1;boolean ready,lobbyMode=true;static class Display{boolean value;boolean active(){return value;}}Display display=new Display();static class Web{String script;int calls;void evaluateJavascript(String s,Object c){script=s;calls++;}}Web web=new Web();'''+method+'''public static void main(String[] a){AlbumReturnTest h=new AlbumReturnTest();h.albumReturnPage=3;h.applyAlbumReturn();if(h.web.calls!=0||h.albumReturnPage!=3)throw new AssertionError("defer until ready");h.ready=true;h.applyAlbumReturn();if(h.web.calls!=0||h.albumReturnPage!=3)throw new AssertionError("defer until active");h.display.value=true;h.applyAlbumReturn();if(h.web.calls!=1||h.albumReturnPage!=-1||!h.web.script.contains("(3)"))throw new AssertionError("exact destination");h.applyAlbumReturn();if(h.web.calls!=1)throw new AssertionError("no repeat");System.out.println("4 actual Main queued-return assertions passed");}}'''
 p=Path(d)/'AlbumReturnTest.java';p.write_text(harness);subprocess.run([str(jdk/'javac'),'-d',d,str(p)],check=True);back=subprocess.check_output([str(jdk/'java'),'-cp',d,'AlbumReturnTest'],text=True);print(back.strip())
 tracked=sources+[base/'MainActivity.java',base/'HomeAlbumView.java',base/'TvLocalControl.java',r/'android/app/src/main/AndroidManifest.xml',Path(__file__).resolve()]
 (r/'docs/verification/s136-home-album-native.json').write_text(json.dumps({'passed':True,'outputs':[q.strip(),back.strip()],'productionActivityExecutedWithLifecycleDoubles':True,'actualMainReturnMethodExtracted':True,'realAndroidRenderer':False,'hisenseHardware':False,'sources':{str(p.relative_to(r)):hashlib.sha256(p.read_bytes()).hexdigest() for p in tracked}},indent=2)+'\n')
