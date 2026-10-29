package org.zhixiaoce.aperture;
import java.util.*;
final class CinemaBrowse {
    static List<CinemaCatalog.Item> items(List<CinemaCatalog.Item> loaded,String word,String year,String area){
        List<CinemaCatalog.Item> out=new ArrayList<>();for(CinemaCatalog.Item i:loaded)if((year.isEmpty()||year.equals(i.year))&&(area.isEmpty()||area.equals(i.area)))out.add(i);
        if(word!=null&&!word.isEmpty()){final String query=word.toLowerCase(Locale.ROOT);out.sort((a,b)->Integer.compare(rank(a.name,query),rank(b.name,query)));}return out;
    }
    private static int rank(String name,String query){String n=name.toLowerCase(Locale.ROOT);return n.equals(query)?0:n.startsWith(query)?1:n.contains(query)?2:3;}
}
