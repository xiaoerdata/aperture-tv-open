package org.zhixiaoce.aperture;

import java.util.*;
import java.util.regex.*;

/** One episode across sibling lines. Each line is tried once, serially, without guessing a different episode. */
final class CinemaPlaybackPlan {
    final List<CinemaCatalog.Line> lines;
    private final Set<Integer> tried=new HashSet<>();
    private final String selectedKey,selectedName;
    private final boolean singleFilm;
    int line,episode;long resume;boolean fallbackUsed;
    CinemaPlaybackPlan(List<CinemaCatalog.Line> lines,int line,int episode,long resume){
        this.lines=new ArrayList<>(lines);this.line=line;this.episode=episode;this.resume=resume;tried.add(line);
        selectedName=lines.get(line).episodes.get(episode).name;selectedKey=key(selectedName);
        singleFilm=lines.get(line).episodes.size()==1&&filmLabel(selectedKey);
    }
    static String title(String name){return java.text.Normalizer.normalize(name,java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}\\u200B\\uFEFF]+","");}
    static String key(String name){
        String s=title(name);
        Matcher m=Pattern.compile("^(?:第)?0*([0-9]+)(?:集|期|话|回)$").matcher(s);
        if(m.matches())return "episode:"+m.group(1);
        if(s.matches("[0-9]+"))return "episode:"+s.replaceFirst("^0+(?!$)","");
        m=Pattern.compile("第0*([0-9]+)(?:集|期|话|回)(?:$|[:：·（(])").matcher(s);
        return m.find()?"episode:"+m.group(1):s;
    }
    static boolean filmLabel(String key){return key.matches("(?:正片|播放|完整版|高清|蓝光|中字|hd|sd|fhd|uhd|4k|720p|1080p|2160p)(?:中字|国语|粤语)?");}
    static int matchEpisode(CinemaCatalog.Line to,String name,boolean allowFilm){
        if(name.isEmpty())return -1;
        int found=-1;for(int i=0;i<to.episodes.size();i++)if(to.episodes.get(i).name.equals(name)){if(found>=0)return -1;found=i;}if(found>=0)return found;
        String full=title(name);for(int i=0;i<to.episodes.size();i++)if(title(to.episodes.get(i).name).equals(full)){if(found>=0)return -1;found=i;}if(found>=0)return found;
        String key=key(name);for(int i=0;i<to.episodes.size();i++)if(key(to.episodes.get(i).name).equals(key)){if(found>=0)return -1;found=i;}if(found>=0)return found;
        return allowFilm&&filmLabel(key)&&to.episodes.size()==1&&filmLabel(key(to.episodes.get(0).name))?0:-1;
    }
    int match(int target){return target<0||target>=lines.size()?-1:matchEpisode(lines.get(target),selectedName,singleFilm);}
    boolean next(){for(int step=1;step<lines.size();step++){int next=(line+step)%lines.size();if(tried.contains(next))continue;int at=match(next);tried.add(next);if(at>=0){line=next;episode=at;fallbackUsed=true;return true;}}return false;}
    boolean choose(int target){int at=match(target);if(at<0)return false;line=target;episode=at;tried.clear();tried.add(line);return true;}
    CinemaCatalog.Line current(){return lines.get(line);}
    String label(){return "线路"+(line+1);}
}
