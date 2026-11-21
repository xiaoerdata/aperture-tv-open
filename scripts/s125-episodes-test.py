"""Production detail grid and playback drawer with explicit JVM view/focus doubles."""
from pathlib import Path
import hashlib,json,subprocess,tempfile
r=Path(__file__).resolve().parents[1]
# Reuse only the UI double definitions; do not execute the other test's runners.
ns={'__file__':str(r/'scripts/s123-live-test.py')};text=(r/'scripts/s123-live-test.py').read_text();exec(compile(text[:text.index("base=r/'android/app/")],'live-ui-double-definitions','exec'),ns);stubs=ns['stubs'];jdk=ns['jdk'];lib=ns['lib']
stubs['android/view/Gravity.java']=stubs['android/view/Gravity.java'].replace('CENTER=17','START=8388611,END=8388613,CENTER=17')
stubs['android/view/View.java']=stubs['android/view/View.java'].replace('public interface OnKeyListener','public void setScaleX(float n){}public void setScaleY(float n){}public void setSelected(boolean b){}public void setEnabled(boolean b){}public void setAlpha(float a){}public boolean requestRectangleOnScreen(android.graphics.Rect r,boolean now){return true;}public interface OnKeyListener')
stubs['android/view/ViewGroup.java']=stubs['android/view/ViewGroup.java'].replace('public int getChildCount()','public int indexOfChild(View v){return children.indexOf(v);}public void addView(View v,int at,LayoutParams p){v.setLayoutParams(p);children.add(at,v);v.parent=this;}public int getChildCount()')
stubs['android/widget/TextView.java']=stubs['android/widget/TextView.java'].replace('public String text;','public String text;public int maxLines;public Object ellipsize;public void setIncludeFontPadding(boolean b){}public void setMinHeight(int n){}public void setMinimumHeight(int n){}public void setMinWidth(int n){}public void setMinimumWidth(int n){}').replace('public void setMaxLines(int n){}','public void setMaxLines(int n){maxLines=n;}').replace('public void setEllipsize(android.text.TextUtils.TruncateAt t){}','public void setEllipsize(android.text.TextUtils.TruncateAt t){ellipsize=t;}')
stubs['android/widget/HorizontalScrollView.java']='package android.widget;public class HorizontalScrollView extends ScrollView {public HorizontalScrollView(android.app.Activity a){super(a);}public void setHorizontalScrollBarEnabled(boolean b){}}'
stubs['android/widget/ScrollView.java']=stubs['android/widget/ScrollView.java'].replace('private int y;','public static class LayoutParams extends android.view.ViewGroup.LayoutParams{public LayoutParams(int w,int h){super(w,h);}}public void setClipToPadding(boolean b){}public void setPadding(int a,int b,int c,int d){}public void setFadingEdgeLength(int n){}private int y;')
stubs['android/widget/GridLayout.java']=stubs['android/widget/GridLayout.java'].replace('public GridLayout','public static final int ALIGN_BOUNDS=1,FILL=2;public void setAlignmentMode(int n){}public GridLayout').replace('public static Spec spec(int at){','public static Spec spec(int at,int alignment){return new Spec(at);}public static Spec spec(int at,int alignment,float weight){return new Spec(at);}public static Spec spec(int at){')
stubs['org/zhixiaoce/aperture/TvUi.java']=stubs['org/zhixiaoce/aperture/TvUi.java'].replace('static final int MUTED=0;','static final int MUTED=0,INK=0;')
stubs['org/zhixiaoce/aperture/BuildConfig.java']='package org.zhixiaoce.aperture;class BuildConfig {static final boolean PARENTS_EDITION=false;}'
base=r/'android/app/src/main/java/org/zhixiaoce/aperture';sources=[base/(n+'.java') for n in ['CinemaCatalog','CinemaDates','CinemaFormats','CinemaResourcePack','CinemaChoicePanel','CinemaEpisodeGrid']];test=r/'tests/java/EpisodeAppendTest.java'
with tempfile.TemporaryDirectory(prefix='kaishi-s125-episodes-') as d:
 temp=Path(d);files=[]
 for name,body in stubs.items():
  p=temp/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body);files.append(p)
 classes=temp/'classes';subprocess.run([str(jdk/'javac'),'-encoding','UTF-8','-cp',str(lib),'-d',str(classes),*map(str,[*files,*sources,test])],check=True)
 q=subprocess.run([str(jdk/'java'),'-cp',str(classes)+':'+str(lib),'org.zhixiaoce.aperture.EpisodeAppendTest'],capture_output=True,text=True);assert q.returncode==0,q.stdout+q.stderr;print(q.stdout.strip())
 receipt={'passed':True,'output':q.stdout.strip(),'productionEpisodeGridAndChoicePanelExecuted':True,'jvmFocusViewDoubles':True,'realAndroidRendering':False,'realXiaomi':False,'sources':{str(p.relative_to(r)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [*sources,test,Path(__file__).resolve()]}};(r/'docs/verification/s125-episode-append.json').write_text(json.dumps(receipt,indent=2)+'\n')
