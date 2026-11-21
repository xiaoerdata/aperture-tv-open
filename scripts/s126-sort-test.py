"""Year-first ranking plus production append/anchor tests; no APK or TV rendering."""
from pathlib import Path
import ast,hashlib,json,subprocess,tempfile
r=Path(__file__).resolve().parents[1];jdk=r/'.android-local/jdk17/bin';lib=next((r/'.android-local/gradle-user/caches/modules-2/files-2.1/org.json/json/20160810').rglob('*.jar'))
module=ast.parse((r/'scripts/s111-browse-append-test.py').read_text());stubs=next(ast.literal_eval(n.value) for n in module.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='stubs' for t in n.targets));base=r/'android/app/src/main/java/org/zhixiaoce/aperture'
sources=[base/(n+'.java') for n in ['CinemaCatalog','CinemaDates','CinemaFormats','CinemaResourcePack','CinemaOrder','CinemaBrowse','CinemaPaging','CinemaBrowsePanel','AppWall']];tests=[r/'tests/java'/(n+'.java') for n in ['YearScoreAppendTest','CinemaDatesTest','HomeUpdatesTest','CinemaBrowsePanelTest']];outputs=[]
with tempfile.TemporaryDirectory(prefix='kaishi-s126-sort-') as d:
 temp=Path(d);files=[]
 for name,body in stubs.items():
  p=temp/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body);files.append(p)
 classes=temp/'classes';subprocess.run([str(jdk/'javac'),'-encoding','UTF-8','-cp',str(lib),'-d',str(classes),*map(str,[*files,*sources,*tests])],check=True)
 for t in tests:
  q=subprocess.run([str(jdk/'java'),'-cp',str(classes)+':'+str(lib),'org.zhixiaoce.aperture.'+t.stem],capture_output=True,text=True);assert q.returncode==0,q.stdout+q.stderr;outputs.append(q.stdout.strip());print(q.stdout.strip())
activity=base/'CinemaActivity.java';text=activity.read_text();load=text[text.index('    private void loadMoreBrowse()'):text.index('    private void browseMoreFailed')];assert load.index('CinemaPaging.merge')<load.index('rows=browseItems()')<load.index('panel.update(rows,true,')
tracked=[*sources,*tests,activity,base/'CinemaSectionView.java',Path(__file__).resolve()];(r/'docs/verification/s126-year-score-sort.json').write_text(json.dumps({'passed':True,'outputs':outputs,'sourceAppendResortsAllLoadedItems':True,'productionUiWithJvmDoubles':True,'actualAndroidRendering':False,'realXiaomi':False,'sources':{str(p.relative_to(r)):hashlib.sha256(p.read_bytes()).hexdigest() for p in tracked}},indent=2)+'\n')
