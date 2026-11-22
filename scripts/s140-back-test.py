"""Run production Back dispatch and cached-detail callbacks with JVM UI/player/network doubles."""
from pathlib import Path
import hashlib,json,re,subprocess,tempfile
r=Path(__file__).resolve().parents[1];base=r/'android/app/src/main/java/org/zhixiaoce/aperture';jdk=r/'.android-local/jdk17/bin';lib=next((r/'.android-local/gradle-user/caches/modules-2/files-2.1/org.json/json/20160810').rglob('*.jar'))
ns={'__file__':str(r/'scripts/s138-player-keys-test.py')};raw=(r/'scripts/s138-player-keys-test.py').read_text();exec(compile(raw[:raw.index("cinema=(base/")],'routing-double-definitions','exec'),ns);stubs=ns['stubs'];common=ns['common'];text=(base/'CinemaActivity.java').read_text()
header=common.replace('class RoutingHarness','class BackHarness').replace('Object sectionSite;','CinemaCatalog.Site sectionSite;').replace('public void onBackPressed(){backs++;}','')
header=header.replace('static class CinemaCatalog{static class Channel{String group="组";}}','')
header=header.replace('void renderBrowse(){browses++;}','void renderBrowse(){browses++;generation++;playingView=false;screen="browse";back=()->exits++;}')
header=header.replace('void rememberVodPosition(){}','void rememberVodPosition(){if(vodPlan!=null)vodPlan.resume=watched;}')
header=header.replace('void saveProgress(boolean complete){}','void saveProgress(boolean complete){try{if(playingView&&vodPlan!=null)shelf.watch(vodReference,vodPlan.current().name,vodPlan.current().episodes.get(vodPlan.episode).name,watched,300000,false,1000);}catch(Exception e){throw new RuntimeException(e);}}')
extra='''
 String screen="browse",sectionItemId="",filterYear="2026",filterArea="中国",browseWord="合成检索";int sectionPage=7,browseScrollY=963,loadedItems=80,exits,networkReads,detailRenders;boolean online=true;long watched=83000;
 CinemaShelf shelf;CinemaPlaybackPlan vodPlan;org.json.JSONObject vodReference;CinemaCatalog.Site vodSite;Runnable vodBack;final CinemaCatalog.Config config=new CinemaCatalog.Config(false);CinemaCatalog.Item returned;CinemaResume.Selection shownSelection;DetailCursor shownCursor;final List<Action> shownActions=new ArrayList<>();LinearLayout content=new LinearLayout(this);final ScrollView detailScroll=new ScrollView(this);
 BackHarness(){try{shelf=new CinemaShelf("[]");}catch(Exception e){throw new RuntimeException(e);}detailScroll.addView(content);}
 static final class DetailCursor{int scrollY;boolean episodes,fullscreen;}
 static final class Action{final String name;final Runnable run;Action(String n,Runnable r){name=n;run=r;}}
 interface Work<T>{T run()throws Exception;}interface Done<T>{void run(T value)throws Exception;}interface ShelfEdit{void apply(CinemaShelf s)throws Exception;}
 void editShelf(ShelfEdit e){try{e.apply(shelf);}catch(Exception x){throw new RuntimeException(x);}}
 final TextView status=new TextView();CinemaCatalog.Item data;
 <T> void task(String title,Runnable previous,Work<T> work,Done<T> done){try{generation++;playingView=false;back=previous;done.run(work.run());}catch(Exception e){throw new RuntimeException(e);}}
 CinemaCatalog.Page catalogue(CinemaCatalog.Site s,String c,int p,String w,String id)throws Exception{networkReads++;if(!online)throw new java.io.IOException("synthetic offline");CinemaCatalog.Page out=new CinemaCatalog.Page();out.items.add(data);return out;}
 org.json.JSONObject vodRef(CinemaCatalog.Site s,CinemaCatalog.Item i)throws Exception{return CinemaShelf.ref("vod",s.key,i.id,i.name,"");}
 void updateLatest(CinemaShelf s,CinemaCatalog.Site site,CinemaCatalog.Item item){}
 void detailPanel(CinemaCatalog.Site s,CinemaCatalog.Item i,List<Action> a,List<CinemaCatalog.Line> l,org.json.JSONObject ref,Runnable previous,CinemaResume.Selection selection,DetailCursor cursor){generation++;playingView=false;screen="detail";returned=i;shownSelection=selection;shownCursor=cursor;sectionFullscreen=cursor.fullscreen;back=previous;detailRenders++;shownActions.clear();shownActions.addAll(a);}
 void attemptVod(){playingView=true;sectionFullscreen=true;screen="player";generation++;playerBack=vodBack;back=vodBack;}
 void openDetail(CinemaCatalog.Site site,CinemaCatalog.Item item,Runnable previous){details(site,item,previous);}
 void openOverview(org.json.JSONObject ref,CinemaCatalog.Site site,CinemaCatalog.Item item,Runnable previous){playSavedOverview(ref,site,item,previous);}
 void openSaved(org.json.JSONObject ref,Runnable previous){throw new AssertionError("unexpected non-VOD branch");}
'''
methods=text[text.index('    private void details('):text.index('    private void playEpisodes(')]
play=text[text.index('    private void playEpisodes(CinemaCatalog.Site site,org.json.JSONObject ref,List<'):text.index('    private void attemptVod()')]
back=text[text.index('    private boolean backKey('):text.index('    @Override protected void onResume()')]
saved=text[text.index('    private void playSavedOverview('):text.index('    private void savedItem(')]
with tempfile.TemporaryDirectory(prefix='kaishi-s140-back-') as d:
 files=[]
 for n,b in stubs.items():p=Path(d)/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(b);files.append(p)
 h=Path(d)/'BackHarness.java';h.write_text(header+extra+methods+play+saved+back+'}\n');test=r/'tests/java/BackNavigationTest.java'
 production=[base/(n+'.java') for n in ['CinemaCatalog','CinemaDates','CinemaFormats','CinemaResourcePack','CinemaShelf','CinemaResume','CinemaPlaybackPlan','PlayerTimeline','PlaybackTimeline','PlaybackEntryIntent','ConfirmPress']]
 subprocess.run([str(jdk/'javac'),'-encoding','UTF-8','-cp',str(lib),'-d',d,*map(str,[*files,*production,h,test])],check=True)
 output=subprocess.check_output([str(jdk/'java'),'-cp',d+':'+str(lib),'org.zhixiaoce.aperture.BackNavigationTest'],text=True);print(output.strip())
 assert '()->details(site,item,previous)' not in text
 assert '()->restoreDetails(site,item,lines,ref,previous,selection,cursor)' in text and 'episodeGrid.restoreFocus()' in text
 assert 'backPress.cancel();' in text[text.index('    @Override protected void onPause()'):]
 tracked=[base/'CinemaActivity.java',base/'CinemaEpisodeGrid.java',test,Path(__file__).resolve(),r/'scripts/s138-player-keys-test.py',base/'ConfirmPress.java']
 (r/'docs/verification/s140-back-navigation.json').write_text(json.dumps({'passed':True,'output':output.strip(),'productionDispatchDetailsRestoreCallbacksExtractedVerbatim':True,'realCataloguePlanResumeAndShelfClasses':True,'uiNetworkPlaybackStartDoubles':True,'androidBackDispatcherExecuted':False,'physicalTV':False,'sources':{str(p.relative_to(r)):hashlib.sha256(p.read_bytes()).hexdigest() for p in tracked}},indent=2)+'\n')
