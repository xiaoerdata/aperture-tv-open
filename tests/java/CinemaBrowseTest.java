package org.zhixiaoce.aperture;
import java.util.*;
final class CinemaBrowseTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        List<CinemaCatalog.Item> items=new ArrayList<>();String[] names={"其他片名","电影续集","电影","我的电影","电影"};
        for(int i=0;i<names.length;i++){CinemaCatalog.Item item=new CinemaCatalog.Item("id"+i,names[i],"",new ArrayList<>());item.year=i%2==0?"2026":"2025";item.area=i%2==0?"中国大陆":"";items.add(item);}
        List<CinemaCatalog.Item> ranked=CinemaBrowse.items(items,"电影","","");check(ranked.get(0)==items.get(2)&&ranked.get(1)==items.get(4));check(ranked.get(2)==items.get(1));check(ranked.get(3)==items.get(3));check(items.get(0).name.equals("其他片名"));
        check(CinemaBrowse.items(items,null,"2026","").size()==3);check(CinemaBrowse.items(items,null,"2025","中国大陆").isEmpty());check(CinemaBrowse.items(items,null,"","中国大陆").size()==3);check(CinemaBrowse.items(items,null,"2023","").isEmpty());check(CinemaBrowse.items(items,null,"","").size()==5);
        System.out.println("9 production browse assertions passed");
    }
}
