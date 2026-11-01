"""Build an isolated Android WebView diagnostic APK; not a product or release artifact.

Captures the exact launcher source under artifacts, omitting large bitmap backgrounds
and fonts to keep emulator installation small. Does not modify device display settings.
"""
from pathlib import Path
import argparse, hashlib, json, os, shutil, subprocess, zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--variant', choices=['baseline', 'fixed'], default='baseline')
parser.add_argument('--source-apk', type=Path, help='Baseline APK whose packaged launcher assets should be reproduced')
args = parser.parse_args()

root = Path(__file__).resolve().parents[1]
out = root / ('artifacts/s90-webview-harness' if args.variant == 'baseline' else 'artifacts/s90-webview-fixed')
runtime = root / '.android-local'
sdk, jdk = runtime / 'android-sdk', runtime / 'jdk17'
build = sdk / 'build-tools/35.0.0'
android = sdk / 'platforms/android-35/android.jar'
for directory in ['classes', 'dex', 'assets']:
    (out / directory).mkdir(parents=True, exist_ok=True)
sources = {}
def keep_asset(relative, data):
    if relative.suffix not in ['.html', '.css', '.js', '.json', '.svg']:
        return
    target = out / 'assets' / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    sources[str(relative)] = hashlib.sha256(data).hexdigest()
if args.variant == 'baseline':
    source_apk = args.source_apk or root / 'artifacts/s89-v33-final-standard/kaishi-v33-standard.apk'
    with zipfile.ZipFile(source_apk) as apk:
        for name in apk.namelist():
            if name.startswith('assets/') and not name.endswith('/'):
                keep_asset(Path(name[7:]), apk.read(name))
else:
    for path in (root / 'public/launcher').rglob('*'):
        if path.is_file():
            keep_asset(path.relative_to(root / 'public/launcher'), path.read_bytes())
(out / 'source-hashes.json').write_text(json.dumps(sources, indent=2) + '\n')
(out / 'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.zhixiaoce.test.viewport">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/>
<uses-permission android:name="android.permission.INTERNET"/>
<application android:debuggable="true" android:theme="@android:style/Theme.Material.NoActionBar" android:label="Viewport diagnostic">
<activity android:name=".ProbeActivity" android:exported="true" android:screenOrientation="landscape"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity>
</application></manifest>''')
(out / 'ProbeActivity.java').write_text(r'''
package org.zhixiaoce.test.viewport;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.webkit.*;
import android.net.Uri;
import android.util.Log;
import org.json.JSONObject;
import java.io.*;
import java.util.Collections;
public class ProbeActivity extends Activity {
  WebView web; boolean ready; int tick;
  void record(String kind,String data) {
    try { JSONObject j=new JSONObject();j.put("event",kind);j.put("data",data);j.put("tick",tick);Log.i("KaishiViewport",j.toString());
      try(FileOutputStream f=openFileOutput("metrics.jsonl",MODE_APPEND)){f.write((j.toString()+"\n").getBytes("UTF-8"));}
    }catch(Exception e){Log.e("KaishiViewport","record",e);}
  }
  void sync(){
    if(!ready||web.getWidth()<=0||web.getHeight()<=0)return;
    float d=getResources().getDisplayMetrics().density;
    double w=web.getWidth()/d,h=web.getHeight()/d;
    web.evaluateJavascript("window.apertureSetViewport&&window.apertureSetViewport("+w+","+h+")",result->record("sync",result));
  }
  void snapshot(){
    record("native","w="+web.getWidth()+",h="+web.getHeight()+",density="+getResources().getDisplayMetrics().density+",pageScale="+web.getScale()+",display="+getResources().getDisplayMetrics());
    web.evaluateJavascript("(function(){var ids=['lobby','brand-home','home-pages','home-live','home-series','home-movies','home-variety'],v={innerWidth:innerWidth,innerHeight:innerHeight,clientWidth:document.documentElement.clientWidth,clientHeight:document.documentElement.clientHeight,dpr:devicePixelRatio,visual:window.visualViewport?{width:visualViewport.width,height:visualViewport.height,scale:visualViewport.scale}:null,cssScale:getComputedStyle(document.documentElement).getPropertyValue('--tv-scale'),api:typeof apertureSetViewport,rects:{}};ids.forEach(function(id){var el=document.getElementById(id);if(el){var r=el.getBoundingClientRect();v.rects[id]={x:r.left,y:r.top,w:r.width,h:r.height,transform:getComputedStyle(el).transform}}});return JSON.stringify(v)})()",result->record("dom",result));
  }
  @Override public void onCreate(Bundle state){super.onCreate(state);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    web=new WebView(this);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setAllowFileAccessFromFileURLs(false);s.setAllowUniversalAccessFromFileURLs(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setMediaPlaybackRequiresUserGesture(true);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setSupportMultipleWindows(false);s.setTextZoom(100);s.setSupportZoom(false);s.setBuiltInZoomControls(false);s.setDisplayZoomControls(false);s.setUseWideViewPort(true);s.setLoadWithOverviewMode(false);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);WebView.setWebContentsDebuggingEnabled(true);
    record("provider",WebView.getCurrentWebViewPackage().versionName);
    web.setWebChromeClient(new WebChromeClient(){public boolean onConsoleMessage(ConsoleMessage m){record("console",m.messageLevel()+" "+m.sourceId()+":"+m.lineNumber()+" "+m.message());return true;}});
    web.setWebViewClient(new WebViewClient(){
      @Override public void onPageStarted(WebView v,String u,android.graphics.Bitmap i){ready=false;record("start",u);}
      @Override public void onScaleChanged(WebView v,float old,float now){record("scale",old+"->"+now);}
      @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){record("action",r.getUrl().toString());return true;}
      @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
        Uri u=r.getUrl();String p=u.getPath();
        try{if("appassets.androidplatform.net".equals(u.getHost())&&p.startsWith("/assets/")){
          String f=p.substring(8),mime=f.endsWith(".js")?"text/javascript":f.endsWith(".css")?"text/css":f.endsWith(".html")?"text/html":f.endsWith(".json")?"application/json":"image/svg+xml";
          return new WebResourceResponse(mime,"UTF-8",200,"OK",Collections.singletonMap("Cache-Control","no-store"),getAssets().open(f));
        }}catch(IOException ignored){}
        return new WebResourceResponse("text/plain","UTF-8",404,"Unavailable",Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(new byte[0]));
      }
      @Override public void onPageFinished(WebView v,String u){ready=true;record("finish",u);sync();snapshot();}
    });
    web.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->sync());web.setOverScrollMode(View.OVER_SCROLL_NEVER);web.setHorizontalScrollBarEnabled(false);web.setVerticalScrollBarEnabled(false);web.setOnScrollChangeListener((v,x,y,ox,oy)->{if(x!=0||y!=0)v.scrollTo(0,0);});
    setContentView(web);web.requestFocus();web.loadUrl("https://appassets.androidplatform.net/assets/index.html?edition=standard&photos=0");
    web.postDelayed(new Runnable(){public void run(){tick++;snapshot();if(tick<12)web.postDelayed(this,10000);}},10000);
  }
}
''')
if args.variant == 'fixed':
    source = (out / 'ProbeActivity.java').read_text()
    source = source.replace('float d=getResources().getDisplayMetrics().density;', 'float d=web.getScale();')
    source = source.replace('s.setUseWideViewPort(true);', 's.setUseWideViewPort(false);')
    source = source.replace('record("scale",old+"->"+now);', 'record("scale",old+"->"+now);sync();')
    source = source.replace('sync();snapshot();}', 'sync();web.evaluateJavascript("window.apertureNativeFocus&&window.apertureNativeFocus(true)",null);snapshot();}')
    (out / 'ProbeActivity.java').write_text(source)
env = dict(os.environ, JAVA_HOME=str(jdk))
def run(args):
    subprocess.run([str(x) for x in args], cwd=root, env=env, check=True)
run([jdk/'bin/javac', '-source', '8', '-target', '8', '-cp', android, '-d', out/'classes', out/'ProbeActivity.java'])
run([build/'d8', '--lib', android, '--min-api', '26', '--output', out/'dex', *sorted((out/'classes').rglob('*.class'))])
run([build/'aapt','package','-f','-M',out/'AndroidManifest.xml','-A',out/'assets','-I',android,'-F',out/'resources.apk'])
with zipfile.ZipFile(out/'resources.apk') as source, zipfile.ZipFile(out/'unsigned.apk','w') as apk:
    for name in source.namelist(): apk.writestr(name,source.read(name))
    for dex in (out/'dex').glob('*.dex'): apk.write(dex,dex.name)
run([build/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk'])
run([build/'apksigner','sign','--ks',runtime/'android-user/debug.keystore','--ks-pass','pass:android','--key-pass','pass:android','--out',out/'probe.apk',out/'aligned.apk'])
print(json.dumps({'apk':str(out/'probe.apk'),'bytes':(out/'probe.apk').stat().st_size,'omitted':'Bitmap artwork and fonts; DOM JS CSS are exact snapshots'}))
