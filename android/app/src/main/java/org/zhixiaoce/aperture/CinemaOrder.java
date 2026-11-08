package org.zhixiaoce.aperture;

import java.time.*;
import java.util.*;

/** Stable sorting of loaded records; unknown dates/scores remain last. */
final class CinemaOrder {
    static double douban(CinemaCatalog.Item item){String score=item.doubanRating;if(score.isEmpty()&&(item.ratingSource.contains("豆瓣")||item.ratingSource.toLowerCase(Locale.ROOT).contains("douban")))score=item.rating;try{double value=Double.parseDouble(score.trim());return Double.isFinite(value)&&value>0&&value<=10?value:-1;}catch(Exception absent){return -1;}}
    static long time(CinemaCatalog.Item item){String text=item.updatedAt.trim();if(text.isEmpty())return Long.MIN_VALUE;try{
        if(text.matches("[0-9]{10}"))return Long.parseLong(text)*1000;
        if(text.matches("[0-9]{13}"))return Long.parseLong(text);
        try{return Instant.parse(text).toEpochMilli();}catch(Exception local){}
        try{return OffsetDateTime.parse(text).toInstant().toEpochMilli();}catch(Exception local){}
        if(text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))return LocalDate.parse(text).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        return LocalDateTime.parse(text.replace(' ','T')).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
    }catch(Exception missing){return Long.MIN_VALUE;}}
    static List<CinemaCatalog.Item> sorted(List<CinemaCatalog.Item> items,String mode){
        List<CinemaCatalog.Item> result=new ArrayList<>(items);
        // Parse once per record instead of repeating date/zone parsing on every comparison.
        Map<CinemaCatalog.Item,Long> times=new IdentityHashMap<>();
        Map<CinemaCatalog.Item,Double> scores=new IdentityHashMap<>();boolean rated="douban".equals(mode);
        for(CinemaCatalog.Item item:items){times.put(item,time(item));if(rated)scores.put(item,douban(item));}
        result.sort((a,b)->{if(rated){int score=Double.compare(scores.get(b),scores.get(a));if(score!=0)return score;}return Long.compare(times.get(b),times.get(a));});
        return result;
    }
}
