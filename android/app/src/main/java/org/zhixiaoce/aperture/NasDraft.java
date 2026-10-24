package org.zhixiaoce.aperture;

import org.json.JSONObject;
import java.util.*;

/** Shared native validation for remote configuration and the local form. Never trusts imported IDs/routes. */
final class NasDraft {
    static final String[] BRANDS = {"绿联 UGOS", "海康存储", "Xiaomi 智能存储 · 新款 NAS", "群晖 DSM", "TrueNAS", "其他 SMB / WebDAV"};
    final JSONObject connection;
    final String role;
    NasDraft(JSONObject input) throws Exception {
        Set<String> fields=new HashSet<>(Arrays.asList("name","brand","root","user","password","domain","role"));
        for(Iterator<String> keys=input.keys();keys.hasNext();)if(!fields.contains(keys.next()))throw new IllegalArgumentException("连接资料包含未知字段");
        String name=field(input,"name",40,true),brand=field(input,"brand",80,true),root=field(input,"root",2048,true),user=field(input,"user",128,true),password=field(input,"password",512,false),domain=field(input,"domain",128,false);
        if(!Arrays.asList(BRANDS).contains(brand))throw new IllegalArgumentException("请选择支持的NAS类型");
        NasLocation location=new NasLocation(root);
        if(brand.equals(BRANDS[2])&&!location.smb)throw new IllegalArgumentException("小米新品当前只按SMB路线核验");
        role=input.has("role")?field(input,"role",16,false):"";
        if(!role.isEmpty()&&!NasLibrary.role(role))throw new IllegalArgumentException("首页用途无效");
        connection=new JSONObject().put("name",name.trim()).put("brand",brand).put("root",root.trim()).put("user",user).put("password",password).put("domain",domain);
    }
    private static String field(JSONObject input,String key,int max,boolean required)throws Exception{
        Object value=input.get(key);if(!(value instanceof String))throw new IllegalArgumentException("连接字段需为文本");String text=(String)value;
        if(text.length()>max||required&&text.trim().isEmpty()||!key.equals("password")&&text.matches("(?s).*[\\p{Cntrl}].*"))throw new IllegalArgumentException("请检查连接字段长度和内容");return text;
    }
}
