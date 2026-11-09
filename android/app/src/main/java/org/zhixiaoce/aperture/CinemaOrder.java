package org.zhixiaoce.aperture;

import java.util.*;

/** Stable sorting of loaded records; unknown dates/scores remain last. */
final class CinemaOrder {
    static double douban(CinemaCatalog.Item item){String score=item.doubanRating;if(score.isEmpty()&&(item.ratingSource.contains("豆瓣")||item.ratingSource.toLowerCase(Locale.ROOT).contains("douban")))score=item.rating;try{double value=Double.parseDouble(score.trim());return Double.isFinite(value)&&value>0&&value<=10?value:-1;}catch(Exception absent){return -1;}}
    static long time(CinemaCatalog.Item item){return CinemaDates.release(item);}
    static List<CinemaCatalog.Item> sorted(List<CinemaCatalog.Item> items,String mode){
        List<CinemaCatalog.Item> result=new ArrayList<>(items);
        // Parse once per record instead of repeating date/zone parsing on every comparison.
        Map<CinemaCatalog.Item,Long> times=new IdentityHashMap<>();
        Map<CinemaCatalog.Item,Long> updates=new IdentityHashMap<>();Map<CinemaCatalog.Item,Double> scores=new IdentityHashMap<>();boolean rated="douban".equals(mode);
        for(CinemaCatalog.Item item:items){times.put(item,time(item));updates.put(item,CinemaDates.parse(item.updatedAt));if(rated)scores.put(item,douban(item));}
        result.sort((a,b)->{if(rated){int score=Double.compare(scores.get(b),scores.get(a));if(score!=0)return score;}int date=Long.compare(times.get(b),times.get(a));return date!=0?date:Long.compare(updates.get(b),updates.get(a));});
        return result;
    }
}
