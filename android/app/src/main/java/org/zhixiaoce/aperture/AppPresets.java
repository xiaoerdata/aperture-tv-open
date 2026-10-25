package org.zhixiaoce.aperture;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Curated suggestions, never proof that an app is installed or usable in a region. */
final class AppPresets {
    static final class Entry {
        final String id,name;final List<String> packages=new ArrayList<>();
        Entry(JSONObject item)throws Exception{id=item.getString("id");name=item.getString("name");if(!id.matches("[a-z]{2,20}")||name.length()>40)throw new IllegalArgumentException();JSONArray values=item.getJSONArray("packages");for(int j=0;j<values.length();j++){String p=values.getString(j);if(!p.matches("[a-zA-Z0-9_.]{3,120}"))throw new IllegalArgumentException();packages.add(p);}}
    }
    static java.util.Map<String,String> initialize(java.util.Map<String,String> existing,List<String> candidates){
        java.util.Map<String,String> result=new java.util.LinkedHashMap<>(existing);java.util.Set<String> used=new java.util.HashSet<>();
        for(String component:existing.values())if(!component.isEmpty())used.add(component.split("/",2)[0]);
        for(String component:candidates){String pkg=component.split("/",2)[0];if(used.contains(pkg))continue;for(int i=1;i<=4;i++)if(!result.containsKey("quick"+i)){result.put("quick"+i,component);used.add(pkg);break;}}
        return result;
    }
    static List<Entry> read(String text)throws Exception{JSONArray items=new JSONArray(text);if(items.length()>24)throw new IllegalArgumentException();List<Entry> result=new ArrayList<>();java.util.Set<String> ids=new java.util.HashSet<>();for(int i=0;i<items.length();i++){Entry e=new Entry(items.getJSONObject(i));if(!ids.add(e.id))throw new IllegalArgumentException();result.add(e);}return result;}
}
