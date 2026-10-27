package org.zhixiaoce.aperture;

import java.util.*;
import org.json.*;

public final class CinemaRelatedTest {
    private static int checks;
    private static void check(boolean ok,String why) { if(!ok)throw new AssertionError(why);checks++; }
    private static CinemaCatalog.Item item(String id,String genre,String director,String actors) throws Exception {
        return new CinemaCatalog.Item(new JSONObject().put("vod_id",id).put("vod_name","合成影片 "+id).put("type_name",genre).put("vod_director",director).put("vod_actor",actors));
    }
    public static void main(String[] args) throws Exception {
        CinemaCatalog.Item seed=item("seed","剧情 / 动作","导演甲","演员甲、演员乙");
        CinemaCatalog.Item unrelated=item("unrelated","纪录片","导演丁","演员丁");
        unrelated.year="2026";unrelated.area="中国";seed.year="2026";seed.area="中国";
        CinemaCatalog.Item genre=item("genre","动作","导演乙","");
        CinemaCatalog.Item actor=item("actor","","","演员乙,演员丙");
        CinemaCatalog.Item director=item("director","","导演甲","");
        CinemaCatalog.Item strongest=item("strongest","剧情/动作","导演甲","演员甲、演员乙");
        List<CinemaCatalog.Item> candidates=Arrays.asList(seed,unrelated,genre,actor,director,strongest,genre,null);
        CinemaRelated.Selection related=CinemaRelated.select(seed,candidates);
        check(related.associated,"metadata relationships are distinguished from source fallback");
        check(related.items.size()==4,"unrelated, duplicate and current IDs are excluded from association results");
        check(related.items.get(0)==strongest&&related.items.get(1)==director,"stronger shared metadata sorts first");
        check(related.items.contains(genre)&&related.items.contains(actor),"genre and exact performer identity each form an association");
        check(candidates.get(0)==seed&&candidates.size()==8,"source list is never mutated");
        CinemaCatalog.Item near=item("near","","","演员甲乙");
        check(!CinemaRelated.select(seed,Arrays.asList(near)).associated,"performer substring is not an identity match");
        CinemaCatalog.Item empty=item("empty","未知","内详","暂无");
        check(!CinemaRelated.select(empty,Arrays.asList(item("other","未知","内详","暂无"))).associated,"placeholder metadata cannot invent an association");
        check(!CinemaRelated.select(item("na","N/A","", ""),Arrays.asList(item("na2","n/a","", ""))).associated,"unavailable metadata remains unknown rather than matching split letters");
        CinemaCatalog.Item western=item("western","","JANE  DOE","ALICE Smith");
        check(CinemaRelated.select(western,Arrays.asList(item("cast","","Jane Doe",""))).associated,"case and whitespace normalize without splitting a person's name");
        CinemaRelated.Selection fallback=CinemaRelated.select(seed,Arrays.asList(unrelated,near));
        check(!fallback.associated&&fallback.items.equals(Arrays.asList(unrelated,near)),"insufficient metadata retains honest source-order fallback");
        List<CinemaCatalog.Item> many=new ArrayList<>();for(int i=0;i<45;i++)many.add(item("genre"+i,"剧情","",""));
        CinemaRelated.Selection bounded=CinemaRelated.select(seed,many);
        check(bounded.items.size()==30&&bounded.items.get(0)==many.get(0)&&bounded.items.get(29)==many.get(29),"rail is bounded and equal evidence preserves source order");
        check(!CinemaRelated.select(seed,Collections.emptyList()).associated&&CinemaRelated.select(seed,Collections.emptyList()).items.isEmpty(),"empty source does not create titles");
        try{bounded.items.clear();throw new AssertionError("mutable output");}catch(UnsupportedOperationException expected){checks++;}
        List<CinemaCatalog.Item> six=new ArrayList<>();for(int i=0;i<6;i++)six.add(item("six"+i,"动作","",""));
        check(CinemaRelated.select(seed,six).items.size()==6,"six genuinely related available films satisfy the six-card requirement");
        System.out.println(checks+" production related-film assertions passed");
    }
}
