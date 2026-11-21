"""Run production settings speed/scroll with explicit focus and geometry doubles."""
from pathlib import Path
import hashlib,json,subprocess,tempfile
r=Path(__file__).resolve().parents[1];base=r/'android/app/src/main/java/org/zhixiaoce/aperture';jdk=r/'.android-local/jdk17/bin'
stubs={
'android/content/Context.java':'package android.content;public class Context {}',
'android/graphics/Rect.java':'package android.graphics;public class Rect{public int top,bottom;public Rect(int l,int t,int r,int b){top=t;bottom=b;}}',
'android/view/KeyEvent.java':'package android.view;public class KeyEvent{public static final int KEYCODE_DPAD_UP=19,KEYCODE_DPAD_DOWN=20,ACTION_DOWN=0,ACTION_UP=1;final int key,action,repeat;final long time;public KeyEvent(int key,int action,int repeat,long time){this.key=key;this.action=action;this.repeat=repeat;this.time=time;}public int getKeyCode(){return key;}public int getAction(){return action;}public int getRepeatCount(){return repeat;}public long getEventTime(){return time;}public long getDownTime(){return 0;}}',
'android/view/ViewParent.java':'package android.view;public interface ViewParent{ViewParent getParent();}',
'android/view/View.java':'package android.view;public class View implements ViewParent{public static final int FOCUS_DOWN=130;public static View focused;public ViewParent parent;public View next;public ViewParent getParent(){return parent;}public int getWidth(){return 100;}public int getHeight(){return 50;}public boolean isEnabled(){return true;}public View findFocus(){return focused;}public View focusSearch(int d){return next;}public boolean requestFocus(){focused=this;return true;}public boolean requestRectangleOnScreen(android.graphics.Rect r,boolean n){return true;}}',
'android/widget/ScrollView.java':'package android.widget;public class ScrollView extends android.view.View{public int defaults,scroll;public ScrollView(android.content.Context c){}public int getHeight(){return 500;}public int getPaddingTop(){return 0;}public int getPaddingBottom(){return 0;}public int getScrollY(){return scroll;}public void scrollBy(int x,int y){scroll+=y;}public void offsetDescendantRectToMyCoords(android.view.View f,android.graphics.Rect r){}public boolean dispatchKeyEvent(android.view.KeyEvent e){defaults++;return false;}}'
}
sources=[base/'SettingsScroll.java',base/'RemoteScrollView.java',r/'tests/java/SettingsScrollTest.java']
with tempfile.TemporaryDirectory(prefix='kaishi-settings-') as d:
 files=[]
 for n,b in stubs.items():p=Path(d)/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(b);files.append(p)
 subprocess.run([str(jdk/'javac'),'-d',d,*map(str,[*files,*sources])],check=True)
 output=subprocess.check_output([str(jdk/'java'),'-cp',d,'org.zhixiaoce.aperture.SettingsScrollTest'],text=True);print(output.strip())
 files=sources+[base/'TvDialogs.java',base/'MainActivity.java',r/'public/launcher/launcher.js',Path(__file__).resolve()]
 assert 'new RemoteScrollView(a,true)' in (base/'TvDialogs.java').read_text()
 (r/'artifacts/s124-release/settings-native.json').write_text(json.dumps({'passed':True,'output':output.strip(),'productionPolicyAndScrollExecuted':True,'explicitFocusGeometryDoubles':True,'realTV':False,'sources':{str(p.relative_to(r)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files}},indent=2)+'\n')
