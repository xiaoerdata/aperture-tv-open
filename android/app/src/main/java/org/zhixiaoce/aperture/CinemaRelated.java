package org.zhixiaoce.aperture;

import java.text.Normalizer;
import java.util.*;

/** Associations within an available source, using declared metadata, never fabricated titles or scores. */
final class CinemaRelated {
    static final class Selection {
        final List<CinemaCatalog.Item> items;
        final boolean associated;
        Selection(List<CinemaCatalog.Item> items, boolean associated) {
            this.items = Collections.unmodifiableList(items);
            this.associated = associated;
        }
    }
    private static final Set<String> UNKNOWN = new HashSet<>(Arrays.asList(
        "未知", "不详", "内详", "暂无", "待更新", "其他", "其它", "unknown", "n/a", "na", "null", "-"));
    private static Set<String> tokens(String text, boolean genre) {
        Set<String> result = new HashSet<>();
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("\\bn\\s*/\\s*a\\b", "");
        for (String token : normalized.split(genre ? "[、,，/|;；\\s]+" : "[、,，/|;；\\r\\n]+")) {
            String value = token.trim().replaceAll("\\s+", " ");
            if (!value.isEmpty() && !UNKNOWN.contains(value)) result.add(value);
        }
        return result;
    }
    private static int overlap(Set<String> left, Set<String> right) {
        int count = 0;
        for (String value : left) if (right.contains(value)) count++;
        return count;
    }
    private static final class Ranked {
        final CinemaCatalog.Item item; final int score;
        Ranked(CinemaCatalog.Item item, int score) { this.item=item; this.score=score; }
    }
    static Selection select(CinemaCatalog.Item current, List<CinemaCatalog.Item> available) {
        Set<String> genres=tokens(current.genre,true), directors=tokens(current.director,false), actors=tokens(current.actors,false);
        List<CinemaCatalog.Item> fallback=new ArrayList<>(); List<Ranked> ranked=new ArrayList<>(); Set<String> seen=new HashSet<>();
        for (CinemaCatalog.Item item : available) {
            if (item == null || item.id.equals(current.id) || !seen.add(item.id)) continue;
            fallback.add(item);
            int score=Math.min(3,overlap(genres,tokens(item.genre,true)))*3
                + Math.min(2,overlap(directors,tokens(item.director,false)))*6
                + Math.min(3,overlap(actors,tokens(item.actors,false)))*2;
            if (score>0) ranked.add(new Ranked(item,score));
        }
        // Collections.sort is stable: equal evidence keeps the source's own order.
        ranked.sort((a,b)->Integer.compare(b.score,a.score));
        List<CinemaCatalog.Item> chosen=new ArrayList<>();
        if (!ranked.isEmpty()) for (int i=0;i<Math.min(30,ranked.size());i++) chosen.add(ranked.get(i).item);
        else chosen.addAll(fallback.subList(0,Math.min(30,fallback.size())));
        return new Selection(chosen,!ranked.isEmpty());
    }
}
