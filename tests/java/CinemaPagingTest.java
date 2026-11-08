package org.zhixiaoce.aperture;
import java.util.*;

public final class CinemaPagingTest {
    private static int count;
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);count++;}
    private static CinemaCatalog.Item item(String id){return new CinemaCatalog.Item(id,id,"",Collections.emptyList());}
    public static void main(String[] args){
        CinemaPaging gate=new CinemaPaging();CinemaPaging.Ticket first=gate.start(1,4,8);
        check(first!=null&&first.page==2&&gate.loading(),"requests next page");
        for(int i=0;i<10;i++)check(gate.start(1,4,8)==null,"held remote and prefetch cannot duplicate request");
        check(gate.finish(first)&&!gate.loading(),"success clears pending request");check(!gate.finish(first),"duplicate callback ignored");
        CinemaPaging.Ticket failed=gate.start(2,4,16);check(gate.finish(failed),"failure can end request without advancing caller's page");
        CinemaPaging.Ticket retry=gate.start(2,4,16);check(retry!=null&&retry.page==3,"retry asks for same failed page");
        gate.cancel();check(!gate.finish(retry),"late response after screen change or background cannot apply");
        CinemaPaging.Ticket fresh=gate.start(1,2,4);check(!gate.finish(retry)&&gate.waiting(fresh),"old response cannot consume a newer request");gate.finish(fresh);
        check(gate.start(2,2,8)==null,"last page has no further request");check(gate.start(1,8,2000)==null,"existing item cap retained");
        CinemaCatalog.Item kept=item("same");List<CinemaCatalog.Item> before=Arrays.asList(item("1"),kept),incoming=Arrays.asList(item("same"),item("2"),item("2"));
        List<CinemaCatalog.Item> merged=CinemaPaging.merge(before,incoming);check(merged.size()==3&&merged.get(1)==kept&&merged.get(2).id.equals("2"),"deduplicates repeated pages without replacing old objects");
        check(before.size()==2&&incoming.size()==3,"source pages not mutated");
        List<CinemaCatalog.Item> large=new ArrayList<>();for(int i=0;i<1999;i++)large.add(item("id"+i));check(CinemaPaging.merge(large,Arrays.asList(item("extra"),item("overflow"))).size()==2000,"append enforces item budget");
        System.out.println(count+" production pagination assertions passed; duplicate requests, retry, late response and bounded merge");
    }
}
