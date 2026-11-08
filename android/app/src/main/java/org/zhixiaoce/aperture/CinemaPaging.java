package org.zhixiaoce.aperture;

import java.util.*;

/** One page in flight. A cancelled generation can never append into a new catalogue. */
final class CinemaPaging {
    static final int LIMIT=2000;
    static final class Ticket {final int page;private Ticket(int page){this.page=page;}}
    private volatile Ticket pending;
    static boolean more(int page,int pages,int count){return page<pages&&count<LIMIT;}
    Ticket start(int page,int pages,int count){if(pending!=null||!more(page,pages,count))return null;return pending=new Ticket(page+1);}
    boolean waiting(Ticket ticket){return ticket!=null&&pending==ticket;}
    boolean finish(Ticket ticket){if(!waiting(ticket))return false;pending=null;return true;}
    boolean loading(){return pending!=null;}
    void cancel(){pending=null;}
    static List<CinemaCatalog.Item> merge(List<CinemaCatalog.Item> before,List<CinemaCatalog.Item> incoming){
        List<CinemaCatalog.Item> result=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(List<CinemaCatalog.Item> page:Arrays.asList(before,incoming))for(CinemaCatalog.Item item:page)if(result.size()<LIMIT&&ids.add(item.id))result.add(item);
        return result;
    }
}
