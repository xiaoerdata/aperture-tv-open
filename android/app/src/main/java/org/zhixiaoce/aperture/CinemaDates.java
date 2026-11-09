package org.zhixiaoce.aperture;

import java.time.*;
import java.util.regex.*;
import org.json.JSONObject;

/** Source dates, with no invented current time when metadata is absent. */
final class CinemaDates {
    static final long UNKNOWN=Long.MIN_VALUE;
    private static final ZoneId CHINA=ZoneId.of("Asia/Shanghai");
    private static final Pattern DAY=Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})(.*)$");
    private static long bounded(long value){int year=Instant.ofEpochMilli(value).atZone(CHINA).getYear();return year>=1800&&year<=2200?value:UNKNOWN;}
    static long parse(String raw){
        if(raw==null)return UNKNOWN;String text=raw.trim();if(text.isEmpty()||text.equals("0")||text.equalsIgnoreCase("null"))return UNKNOWN;
        try{
            if(text.matches("[0-9]{10}(?:\\.[0-9]{1,9})?"))return bounded(new java.math.BigDecimal(text).multiply(java.math.BigDecimal.valueOf(1000)).longValue());
            if(text.matches("[0-9]{13}(?:\\.[0-9]{1,9})?"))return bounded(new java.math.BigDecimal(text).longValue());
            if(text.matches("[0-9]{4}年?")){int year=Integer.parseInt(text.substring(0,4));return year>=1800&&year<=2200?LocalDate.of(year,1,1).atStartOfDay(CHINA).toInstant().toEpochMilli():UNKNOWN;}
            if(text.matches("[0-9]{8}"))text=text.substring(0,4)+"-"+text.substring(4,6)+"-"+text.substring(6);
            text=text.replace('年','-').replace('月','-').replace("日","").replace('/','-').replaceFirst("^(\\d{4})\\.(\\d{1,2})\\.(\\d{1,2})","$1-$2-$3");
            if(text.matches("\\d{4}-\\d{1,2}-?")){String[] parts=text.split("-");return bounded(YearMonth.of(Integer.parseInt(parts[0]),Integer.parseInt(parts[1])).atDay(1).atStartOfDay(CHINA).toInstant().toEpochMilli());}
            Matcher day=DAY.matcher(text);if(!day.matches())return UNKNOWN;
            LocalDate date=LocalDate.of(Integer.parseInt(day.group(1)),Integer.parseInt(day.group(2)),Integer.parseInt(day.group(3)));String rest=day.group(4).trim();
            if(rest.isEmpty())return bounded(date.atStartOfDay(CHINA).toInstant().toEpochMilli());
            if(rest.startsWith("T"))rest=rest.substring(1);rest=rest.replaceFirst("\\s+([+-]\\d{4})$","$1").replaceFirst("([+-]\\d{2})(\\d{2})$","$1:$2").replace(" UTC","Z");
            String value=date+"T"+rest;
            try{return bounded(OffsetDateTime.parse(value).toInstant().toEpochMilli());}catch(Exception local){return bounded(LocalDateTime.parse(value).atZone(CHINA).toInstant().toEpochMilli());}
        }catch(Exception invalid){return UNKNOWN;}
    }
    static String first(JSONObject value,String... names){for(String name:names){String text=value.optString(name,"");if(parse(text)!=UNKNOWN)return text;}return "";}
    static long release(CinemaCatalog.Item item){long date=parse(item.releaseDate);if(date!=UNKNOWN)return date;date=parse(item.year);return date!=UNKNOWN?date:parse(item.updatedAt);}
    static String label(CinemaCatalog.Item item){
        long released=parse(item.releaseDate);if(released!=UNKNOWN){String value=item.releaseDate.trim();if(value.matches("[0-9]{4}年?"))return value.substring(0,4);return Instant.ofEpochMilli(released).atZone(CHINA).toLocalDate().toString();}
        if(parse(item.year)!=UNKNOWN)return item.year;
        long updated=parse(item.updatedAt);return updated==UNKNOWN?item.year:"更新 "+Instant.ofEpochMilli(updated).atZone(CHINA).toLocalDate();
    }
}
