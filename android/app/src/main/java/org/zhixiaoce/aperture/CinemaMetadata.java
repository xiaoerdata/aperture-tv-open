package org.zhixiaoce.aperture;

import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Name matching favors an explicit year, then the newest plausible result. */
final class CinemaMetadata {
    interface Fetch {String read(String url,Map<String,String> headers)throws Exception;}
    static final class Name {
        final String title,year;
        Name(String raw,String explicit){
            String value=raw==null?"":raw;Matcher y=Pattern.compile("(?<![0-9])((?:19|20)[0-9]{2})(?![0-9])").matcher(value);year=explicit!=null&&explicit.matches("(?:19|20)[0-9]{2}")?explicit:y.find()?y.group(1):"";
            value=value.replaceFirst("(?i)\\.(mkv|mp4|m4v|webm|avi|mov|ts)$","").replaceAll("[. _]+"," ");
            value=value.replaceAll("[\\[【(（](?:[^\\]】)）]*?(?:19|20)[0-9]{2}|[^\\]】)）]*?(?i:1080|2160|720|bluray|webrip|web-dl|h264|h265|中字|国语))[^\\]】)）]*[\\]】)）]"," ");
            value=value.replaceFirst("(?i)\\b(?:s[0-9]{1,2}e[0-9]{1,3}|[0-9]{3,4}p|4k|bluray|webrip|web[ -]?dl|h[ .]?26[45]|x26[45])\\b.*$","");
            value=value.replaceFirst("第[一二三四五六七八九十百0-9]+[季集].*$","").replaceAll("(?<![0-9])(?:19|20)[0-9]{2}(?![0-9])","").replaceAll("[\\s._-]+$","").trim();title=value.length()>160?value.substring(0,160):value;
        }
    }
    static String comparable(String s){return java.text.Normalizer.normalize(s==null?"":s,java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]","");}
    private static double similarity(String a,String b){if(a.equals(b))return 1;if(a.length()<2||b.length()<2)return 0;Set<String> x=new HashSet<>(),y=new HashSet<>();for(int i=0;i<a.length()-1;i++)x.add(a.substring(i,i+2));for(int i=0;i<b.length()-1;i++)y.add(b.substring(i,i+2));int same=0;for(String v:x)if(y.contains(v))same++;return 2.0*same/(x.size()+y.size());}
    static JSONObject automatic(Name name,List<JSONObject> values){String title=comparable(name.title);if(title.isEmpty())return null;List<JSONObject> plausible=new ArrayList<>(),exact=new ArrayList<>(),dated=new ArrayList<>();for(JSONObject v:values){double score=Math.max(similarity(title,comparable(v.optString("title"))),similarity(title,comparable(v.optString("originalTitle"))));if(score>=.5)plausible.add(v);if(score==1)exact.add(v);}List<JSONObject> choices=exact.isEmpty()?plausible:exact;for(JSONObject v:choices)if(!name.year.isEmpty()&&name.year.equals(v.optString("year")))dated.add(v);if(!dated.isEmpty())choices=dated;choices.sort((a,b)->{int date=b.optString("year").compareTo(a.optString("year"));return date!=0?date:a.optString("key").compareTo(b.optString("key"));});return choices.isEmpty()?null:choices.get(0);}
    static List<JSONObject> search(Name name,String kind,String token,Fetch fetch)throws Exception{
        List<JSONObject> result=new ArrayList<>();if(name.title.isEmpty())return result;String term=java.net.URLEncoder.encode(name.title,"UTF-8");
        if(token!=null&&!token.isEmpty()){
            String type="movie".equals(kind)?"movie":"tv";JSONObject body=new JSONObject(fetch.read("https://api.themoviedb.org/3/search/"+type+"?language=zh-CN&include_adult=false&query="+term,Collections.singletonMap("Authorization","Bearer "+token)));JSONArray rows=body.optJSONArray("results");
            for(int i=0;rows!=null&&i<Math.min(8,rows.length());i++){JSONObject o=rows.getJSONObject(i);String pic=o.optString("poster_path"),back=o.optString("backdrop_path");result.add(new JSONObject().put("key","tmdb:"+type+":"+o.getLong("id")).put("title",o.optString("title",o.optString("name"))).put("originalTitle",o.optString("original_title",o.optString("original_name"))).put("year",year(o.optString("release_date",o.optString("first_air_date")))).put("summary",CinemaCatalog.Item.plain(o.optString("overview"))).put("poster",pic.matches("/[a-zA-Z0-9._-]+")?"https://image.tmdb.org/t/p/w500"+pic:"").put("backdrop",back.matches("/[a-zA-Z0-9._-]+")?"https://image.tmdb.org/t/p/w1280"+back:"").put("rating",o.optDouble("vote_average",0)>0?String.format(Locale.ROOT,"%.1f",o.optDouble("vote_average")):"").put("provider","TMDB").put("url","https://www.themoviedb.org/"+type+"/"+o.getLong("id")));}
        }else if(!"movie".equals(kind)){
            JSONArray rows=new JSONArray(fetch.read("https://api.tvmaze.com/search/shows?q="+term,Collections.emptyMap()));for(int i=0;i<Math.min(8,rows.length());i++){JSONObject o=rows.getJSONObject(i).getJSONObject("show"),image=o.optJSONObject("image");result.add(new JSONObject().put("key","tvmaze:"+o.getLong("id")).put("title",o.optString("name")).put("year",year(o.optString("premiered"))).put("summary",CinemaCatalog.Item.plain(o.optString("summary"))).put("poster",image==null?"":image.optString("medium")).put("genre",o.optJSONArray("genres")==null?"":join(o.getJSONArray("genres"))).put("provider","TVmaze · CC BY-SA").put("url",o.optString("url")));}
        }return result;
    }
    private static String year(String s){return s.length()>=4&&s.substring(0,4).matches("[0-9]{4}")?s.substring(0,4):"";}
    private static String join(JSONArray a){List<String> x=new ArrayList<>();for(int i=0;i<Math.min(8,a.length());i++)x.add(a.optString(i));return String.join(" / ",x);}
}
