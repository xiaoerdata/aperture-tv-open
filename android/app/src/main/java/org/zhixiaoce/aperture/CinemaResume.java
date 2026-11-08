package org.zhixiaoce.aperture;

import java.util.List;
import org.json.JSONObject;

/** Restore by source/item identity plus line/episode names, never a signed media URL or an index. */
final class CinemaResume {
    static final class Selection {int line,episode;boolean continuing;}
    static int episode(CinemaCatalog.Line line,String name){
        if(name.isEmpty())return -1;int exact=-1;
        for(int i=0;i<line.episodes.size();i++)if(line.episodes.get(i).name.equals(name)){if(exact>=0)return -1;exact=i;}
        if(exact>=0)return exact;
        String key=CinemaPlaybackPlan.key(name);int found=-1;
        for(int i=0;i<line.episodes.size();i++)if(CinemaPlaybackPlan.key(line.episodes.get(i).name).equals(key)){if(found>=0)return -1;found=i;}
        if(found>=0)return found;
        return line.episodes.size()==1&&CinemaPlaybackPlan.filmLabel(key)&&CinemaPlaybackPlan.filmLabel(CinemaPlaybackPlan.key(line.episodes.get(0).name))?0:-1;
    }
    static Selection select(List<CinemaCatalog.Line> lines,JSONObject saved){
        Selection result=new Selection();if(lines.isEmpty()||saved==null||saved.optLong("watchedAt")<=0)return result;
        int preferred=-1;
        for(int i=0;i<lines.size();i++)if(lines.get(i).name.equals(saved.optString("line"))){if(preferred>=0){preferred=-1;break;}preferred=i;}
        if(preferred>=0)result.line=preferred;
        int selected=preferred<0?-1:episode(lines.get(preferred),saved.optString("episode"));
        if(selected>=0){result.episode=selected;result.continuing=CinemaShelf.resumePosition(saved)>0;return result;}
        // A removed line may fall back only to the same unambiguously matched episode.
        for(int i=0;i<lines.size();i++){int at=episode(lines.get(i),saved.optString("episode"));if(at>=0){result.line=i;result.episode=at;result.continuing=CinemaShelf.resumePosition(saved)>0;return result;}}
        return result;
    }
    static long position(List<CinemaCatalog.Line> lines,int line,int at,JSONObject saved){
        if(saved==null||line<0||line>=lines.size()||at<0||at>=lines.get(line).episodes.size())return 0;
        return episode(lines.get(line),saved.optString("episode"))==at?CinemaShelf.resumePosition(saved):0;
    }
    static int liveLine(JSONObject saved,int count){
        if(saved==null||count<=0)return 0;String name=saved.optString("line");
        if(!name.matches("线路[1-9][0-9]*"))return 0;
        try{int line=Integer.parseInt(name.substring(2))-1;return line>=0&&line<count?line:0;}catch(NumberFormatException invalid){return 0;}
    }
}
