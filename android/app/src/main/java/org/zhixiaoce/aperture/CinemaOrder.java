package org.zhixiaoce.aperture;

import java.util.*;

/** Stable year-first sorting of all loaded records; unknown years/scores remain last. */
final class CinemaOrder {
    static double douban(CinemaCatalog.Item item){String score=item.doubanRating;if(score.isEmpty()&&(item.ratingSource.contains("豆瓣")||item.ratingSource.toLowerCase(Locale.ROOT).contains("douban")))score=item.rating;try{double value=Double.parseDouble(score.trim());return Double.isFinite(value)&&value>0&&value<=10?value:-1;}catch(Exception absent){return -1;}}
    static long time(CinemaCatalog.Item item){return CinemaDates.release(item);}
    private static final java.util.regex.Pattern YEAR=java.util.regex.Pattern.compile("(?<!\\d)(?:18|19|20|21|22)\\d{2}(?!\\d)");
    static int year(CinemaCatalog.Item item){java.util.regex.Matcher match=YEAR.matcher(item.year);if(match.find()){int year=Integer.parseInt(match.group());if(year<=2200)return year;}long released=CinemaDates.parse(item.releaseDate);return released==CinemaDates.UNKNOWN?-1:java.time.Instant.ofEpochMilli(released).atZone(java.time.ZoneId.of("Asia/Shanghai")).getYear();}
    private static double rating(CinemaCatalog.Item item){double known=douban(item);if(known>=0)return known;try{double value=Double.parseDouble(item.rating.trim());return Double.isFinite(value)&&value>0&&value<=10?value:-1;}catch(Exception absent){return -1;}}
    static List<CinemaCatalog.Item> sorted(List<CinemaCatalog.Item> items,String mode){
        List<CinemaCatalog.Item> result=new ArrayList<>(items);Map<CinemaCatalog.Item,Integer> years=new IdentityHashMap<>();Map<CinemaCatalog.Item,Long> updates=new IdentityHashMap<>();Map<CinemaCatalog.Item,Double> scores=new IdentityHashMap<>();boolean rated="douban".equals(mode);
        for(CinemaCatalog.Item item:items){years.put(item,year(item));updates.put(item,CinemaDates.parse(item.updatedAt));scores.put(item,rated?douban(item):rating(item));}
        result.sort((a,b)->{int year=Integer.compare(years.get(b),years.get(a));if(year!=0)return year;int score=Double.compare(scores.get(b),scores.get(a));return score!=0?score:Long.compare(updates.get(b),updates.get(a));});return result;
    }
}
