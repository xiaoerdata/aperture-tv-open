package org.zhixiaoce.aperture;
import java.util.*;
public class PlaybackLineMatchTest {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}static CinemaCatalog.Line line(String n,String... titles){CinemaCatalog.Line l=new CinemaCatalog.Line(n);for(String t:titles)l.episodes.add(new CinemaCatalog.Episode(t,"https://example.invalid/"+l.episodes.size()));return l;}
 public static void main(String[] args){
  List<CinemaCatalog.Line> lines=Arrays.asList(line("A","第1期（上）","第1期（下）","第2期"),line("B","第1期（下）","第2期","第1期（上）"));
  CinemaPlaybackPlan p=new CinemaPlaybackPlan(lines,0,0,42000);check(p.choose(1)&&p.episode==2&&p.resume==42000,"same full title beats duplicate normalized episode number; preserve position");check(p.choose(0)&&p.episode==0,"switch back finds same full title");
  p=new CinemaPlaybackPlan(lines,0,1,90000);check(p.next()&&p.episode==0&&p.resume==90000,"automatic fallback respects lower half");check(CinemaResume.episode(lines.get(1),"第1期（上）")==2,"resume uses same matching contract");
  p=new CinemaPlaybackPlan(Arrays.asList(line("A","第１期（上）\u200b"),line("B","第1期(下)","第1期(上)")),0,0,12345);check(p.choose(1)&&p.episode==1&&p.resume==12345,"full-width punctuation/digits and invisible whitespace normalized without losing upper/lower");
  p=new CinemaPlaybackPlan(Arrays.asList(line("A","第01集"),line("B","第1集","第01集")),0,0,100);check(p.choose(1)&&p.episode==1,"unique exact title has priority over numeric ambiguity");
  p=new CinemaPlaybackPlan(Arrays.asList(line("A","第001期"),line("B","第1期（上）","第01期（下）")),0,0,100);check(!p.choose(1)&&p.line==0&&p.episode==0&&p.resume==100,"ambiguous number-only match neither guesses nor mutates playback");
  p=new CinemaPlaybackPlan(Arrays.asList(line("A","第1期（上）"),line("B","第1期（上）","第1期（上）")),0,0,100);check(!p.choose(1),"duplicate exact names remain ambiguous");
  p=new CinemaPlaybackPlan(Arrays.asList(line("A","20261121 第1期：上"),line("B","20261121 第1期：下","20261121 第1期：上")),0,0,5000);check(p.choose(1)&&p.episode==1,"dated variety title remains addressable");
  p=new CinemaPlaybackPlan(Arrays.asList(line("A","正片"),line("B","HD")),0,0,8000);check(p.choose(1)&&p.episode==0&&p.resume==8000,"single movie label fallback preserved");check(!p.choose(99)&&!p.choose(-1),"invalid target line rejected");
  System.out.println(checks+" production full-title line matching assertions passed");
 }
}
