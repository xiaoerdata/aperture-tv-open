package org.zhixiaoce.aperture;

import java.util.*;
import java.util.regex.*;

/** One episode across sibling lines. Each line is tried once, serially, without guessing a different episode. */
final class CinemaPlaybackPlan {
    final List<CinemaCatalog.Line> lines;
    private final Set<Integer> tried=new HashSet<>();
    int line,episode;long resume;boolean fallbackUsed;
    CinemaPlaybackPlan(List<CinemaCatalog.Line> lines,int line,int episode,long resume){
        this.lines=new ArrayList<>(lines);this.line=line;this.episode=episode;this.resume=resume;tried.add(line);
    }
    static String key(String name){
        String s=name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+","");
        Matcher m=Pattern.compile("^(?:第)?0*([0-9]+)(?:集|期|话|回)$").matcher(s);
        if(m.matches())return "episode:"+m.group(1);
        if(s.matches("[0-9]+"))return "episode:"+s.replaceFirst("^0+(?!$)","");
        m=Pattern.compile("第0*([0-9]+)(?:集|期|话|回)(?:$|[:：·（(])").matcher(s);
        return m.find()?"episode:"+m.group(1):s;
    }
    int match(int target){
        CinemaCatalog.Line from=lines.get(line),to=lines.get(target);String name=key(from.episodes.get(episode).name);int found=-1;
        for(int i=0;i<to.episodes.size();i++)if(key(to.episodes.get(i).name).equals(name)){if(found>=0)return -1;found=i;}
        if(found>=0)return found;
        return from.episodes.size()==1&&to.episodes.size()==1?0:-1;
    }
    boolean next(){for(int step=1;step<lines.size();step++){int next=(line+step)%lines.size();if(tried.contains(next))continue;int at=match(next);tried.add(next);if(at>=0){line=next;episode=at;fallbackUsed=true;return true;}}return false;}
    boolean choose(int target){int at=match(target);if(at<0)return false;line=target;episode=at;tried.clear();tried.add(line);return true;}
    CinemaCatalog.Line current(){return lines.get(line);}
    String label(){return "线路"+(line+1);}
}
