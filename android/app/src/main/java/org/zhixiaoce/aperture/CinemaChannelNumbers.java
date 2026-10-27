package org.zhixiaoce.aperture;

import java.util.*;

final class CinemaChannelNumbers {
    static int[] assign(List<CinemaCatalog.Channel> channels){Set<Integer> used=new HashSet<>();for(CinemaCatalog.Channel c:channels)if(c.number>0)used.add(c.number);int[] values=new int[channels.size()];int next=1;for(int i=0;i<values.length;i++){if(channels.get(i).number>0)values[i]=channels.get(i).number;else{while(used.contains(next))next++;values[i]=next;used.add(next++);}}return values;}
}
