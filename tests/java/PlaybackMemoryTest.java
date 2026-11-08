package org.zhixiaoce.aperture;

import java.nio.file.*;
import java.util.*;
import org.json.*;

public final class PlaybackMemoryTest {
    private static int checks;
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    private static CinemaCatalog.Line line(String name,String... episodes){CinemaCatalog.Line line=new CinemaCatalog.Line(name);for(String episode:episodes)line.episodes.add(new CinemaCatalog.Episode(episode,"https://example.invalid/expired.mp4?token=synthetic"));return line;}
    public static void main(String[] args)throws Exception{
        JSONObject ref=CinemaShelf.ref("vod","stable-source","movie-1","影片","").put("duration","96分钟");
        CinemaShelf shelf=new CinemaShelf("[]");shelf.watch(ref,"备用线路","第02集",42000,120000,false,1000);
        check(shelf.get(ref).getString("duration").equals("96分钟"),"watch checkpoint preserves display duration");
        check(shelf.get(ref).getLong("playDuration")==120000,"play duration has its own field");
        shelf.enrich(new JSONObject(ref.toString()).put("duration","98分钟"));
        check(shelf.resume(ref,"备用线路","第02集")==42000,"refreshing movie metadata cannot lose resume position");
        shelf.toggle(ref,"favorite");check(shelf.resume(ref,"备用线路","第02集")==42000,"favorite edit cannot overwrite playback duration");
        check(!shelf.json().contains("expired.mp4")&&!shelf.json().contains("token="),"history contains no expiring episode URL");

        Path file=Path.of(args[0]);Files.writeString(file,shelf.json());Object lock=new Object();
        CinemaShelfStore.Storage storage=new CinemaShelfStore.Storage(){public String read()throws Exception{return Files.readString(file);}public void write(String value)throws Exception{Files.writeString(file,value);}};
        CinemaShelfStore reopened=new CinemaShelfStore(lock,storage);JSONObject saved=reopened.read().get(ref);
        List<CinemaCatalog.Line> reordered=Arrays.asList(line("备用线路","第01集","第02集"),line("默认线路","第01集","第02集"));
        CinemaResume.Selection selected=CinemaResume.select(reordered,saved);
        check(selected.line==0&&selected.episode==1&&selected.continuing,"disk reopen restores line by name after reordering");
        check(CinemaResume.position(reordered,selected.line,selected.episode,saved)==42000,"disk reopen restores actual time");
        check(CinemaResume.position(reordered,0,0,saved)==0,"different episode never inherits last episode progress");
        CinemaPlaybackPlan plan=new CinemaPlaybackPlan(reordered,selected.line,selected.episode,CinemaResume.position(reordered,selected.line,selected.episode,saved));
        check(plan.next()&&plan.line==1&&plan.episode==1&&plan.resume==42000,"automatic fallback preserves remembered episode and position");
        check(plan.choose(0)&&plan.line==0&&plan.episode==1&&plan.resume==42000,"manual line switch preserves position");
        check(reopened.read().get(CinemaShelf.ref("vod","different-source","movie-1","影片",""))==null,"different sources keep independent progress");
        CinemaShelfStore otherWriter=new CinemaShelfStore(lock,storage);otherWriter.update(s->s.enrich(new JSONObject(ref.toString()).put("duration","100分钟")));
        check(reopened.read().resume(ref,"备用线路","第02集")==42000,"another writer's metadata refresh retains progress");

        List<CinemaCatalog.Line> renamed=Arrays.asList(line("默认线路","第1集","第2集：一段很长的节目标题"));
        selected=CinemaResume.select(renamed,saved);check(selected.line==0&&selected.episode==1&&selected.continuing,"removed line and normalized long episode name recover same episode");
        check(CinemaResume.position(renamed,0,1,saved)==42000,"matched episode carries progress to fallback line");
        List<CinemaCatalog.Line> missing=Arrays.asList(line("默认线路","第01集"),line("备用线路","第03集"));
        selected=CinemaResume.select(missing,saved);check(selected.line==1&&selected.episode==0&&!selected.continuing,"missing episode keeps preferred line without claiming resume");
        check(CinemaResume.position(missing,1,0,saved)==0,"unrelated replacement episode starts from beginning");
        check(CinemaResume.episode(line("x","第2集（上）","第02集（下）"),"第002集")==-1,"ambiguous normalized episodes not guessed");
        check(CinemaResume.episode(line("x","第2集（上）","第02集（下）"),"第2集（上）")==0,"exact long title remains addressable");

        CinemaShelf completed=new CinemaShelf(shelf.json());completed.watch(ref,"备用线路","第02集",120000,120000,true,2000);JSONObject done=completed.get(ref);
        selected=CinemaResume.select(Arrays.asList(reordered.get(1),reordered.get(0)),done);
        check(selected.line==1&&selected.episode==1&&!selected.continuing,"completed title still remembers chosen line and episode");
        check(CinemaResume.position(reordered,0,1,done)==0,"completed title starts from beginning rather than end");
        completed.watch(ref,"备用线路","第02集",0,120000,false,2100);check(!completed.get(ref).optBoolean("complete")&&completed.resume(ref,"备用线路","第02集")==0,"start-over clears completion and old time");
        completed.watch(ref,"备用线路","第02集",119000,120000,false,2200);check(completed.resume(ref,"备用线路","第02集")==0,"near-end position does not resume at credits end");

        JSONObject legacy=new JSONObject(saved.toString());legacy.remove("playDuration");legacy.put("duration",120000);
        CinemaShelf migrated=new CinemaShelf(new JSONArray().put(legacy).toString());check(migrated.resume(ref,"备用线路","第02集")==42000,"numeric legacy duration migrates without losing progress");
        check(migrated.get(ref).getLong("playDuration")==120000&&!migrated.get(ref).has("duration"),"legacy milliseconds no longer appear as display duration");
        migrated.enrich(ref);check(migrated.resume(ref,"备用线路","第02集")==42000,"metadata can be enriched after migration");
        JSONObject previouslyOverwritten=new JSONObject(saved.toString());previouslyOverwritten.remove("playDuration");previouslyOverwritten.put("duration","96分钟");
        CinemaShelf recovered=new CinemaShelf(new JSONArray().put(previouslyOverwritten).toString());check(recovered.resume(ref,"备用线路","第02集")==42000,"recover old position even when legacy duration was overwritten by metadata");
        JSONObject film=new JSONObject(saved.toString()).put("episode","正片");check(CinemaResume.position(Collections.singletonList(line("电影","HD")),0,0,film)==42000,"single-movie label change retains film position");
        check(CinemaResume.position(Collections.singletonList(line("电影","预告片")),0,0,film)==0,"movie resume never applied to trailer");

        CinemaShelf live=new CinemaShelf("[]");JSONObject channel=CinemaShelf.ref("live","live-source","c1","频道","分组");live.watch(channel,"线路2","频道",0,0,false,3000);live=new CinemaShelf(live.json());
        check(CinemaResume.liveLine(live.get(channel),3)==1,"live channel restores selected line after serialization");
        check(CinemaResume.liveLine(live.get(channel),1)==0,"removed live line falls back within valid bounds");
        check(CinemaResume.liveLine(new JSONObject().put("line","线路9999999999999999"),3)==0,"invalid oversized live index rejected");
        check(CinemaResume.liveLine(new JSONObject().put("line",""),3)==0,"old live records default to first line");
        check(CinemaShelf.resumePosition(live.get(channel))==0,"live channel stores line, not fake seek position");

        migrated.toggle(ref,"follow");migrated.clearProgress(ref);JSONObject cleared=migrated.get(ref);
        check(cleared.optBoolean("favorite")&&cleared.optBoolean("follow")&&cleared.getString("duration").equals("96分钟"),"clear history keeps favorite, follow and display metadata");
        check(!cleared.has("playDuration")&&!cleared.has("position")&&!cleared.has("line"),"clear history removes both progress and line memory");
        recovered.clearHistory();check(!recovered.get(ref).has("playDuration")&&!recovered.get(ref).has("line"),"bulk history clear removes playback state");
        System.out.println(checks+" production playback-memory assertions passed; real disk reopen, line reorder/fallback, metadata isolation, legacy migration and live-line memory; Android decoder not executed");
    }
}
