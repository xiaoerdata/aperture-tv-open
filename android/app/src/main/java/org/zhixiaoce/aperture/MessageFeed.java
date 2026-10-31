package org.zhixiaoce.aperture;

import java.io.IOException;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.json.*;

/** Read-only message file shared by display and scheduling. No author or editor model. */
final class MessageFeed {
    final ZoneId zone;final List<JSONObject> messages;
    MessageFeed(String json)throws Exception {
        if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>131072)throw new IOException("留言文件超过128KiB");
        JSONObject root=new JSONObject(json);if(root.optInt("version")!=1)throw new IOException("留言文件版本无效");zone=ZoneId.of(root.optString("timezone","Asia/Shanghai"));JSONArray values=root.getJSONArray("messages");if(values.length()>100)throw new IOException("最多100条留言");messages=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(int i=0;i<values.length();i++){JSONObject v=values.getJSONObject(i);for(String field:new String[]{"time","repeat","date","expireAt"})if(v.has(field)&&!(v.get(field) instanceof String))throw new IOException("留言字段类型无效");if(v.has("tts")&&!(v.get("tts") instanceof Boolean))throw new IOException("播报开关需为布尔值");String id=v.getString("id"),text=v.getString("text"),time=v.optString("time",""),repeat=v.optString("repeat","once"),date=v.optString("date","");
            if(!id.matches("[A-Za-z0-9._-]{1,96}")||!ids.add(id)||text.trim().isEmpty()||text.length()>500||text.indexOf('\0')>=0||!Arrays.asList("daily","once").contains(repeat))throw new IOException("留言内容无效");
            if(!time.isEmpty()){if(!time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9](?:-(?:[01][0-9]|2[0-3]):[0-5][0-9])?"))throw new IOException("留言时刻无效");if("once".equals(repeat))LocalDate.parse(date);if(time.length()>5&&!LocalTime.parse(time.substring(6)).isAfter(LocalTime.parse(time.substring(0,5))))throw new IOException("留言时间范围无效");}
            String expires=v.optString("expireAt","");if(!expires.isEmpty())Instant.parse(expires);
            messages.add(new JSONObject().put("id",id).put("text",text).put("time",time).put("repeat",repeat).put("date",date).put("tts",v.optBoolean("tts",false)).put("expireAt",expires));
        }
    }
    static String source(String input)throws Exception {String s=input.trim();if(s.isEmpty())return s;URI u=new URI(s);if(s.length()>2048||!"https".equals(u.getScheme())||u.getHost()==null||u.getRawUserInfo()!=null||u.getRawFragment()!=null||u.getPort()==0||u.getPort()>65535||LobbyAssets.HOST.equalsIgnoreCase(u.getHost()))throw new IOException("请输入HTTPS留言地址");return s;}
    boolean visible(JSONObject m,Instant now){String expiry=m.optString("expireAt");return expiry.isEmpty()||now.isBefore(Instant.parse(expiry));}
    String due(JSONObject m,Instant now){if(!visible(m,now)||!m.optBoolean("tts"))return "";String time=m.optString("time");if(time.isEmpty())return "";ZonedDateTime local=now.atZone(zone);if(!time.substring(0,5).equals(local.toLocalTime().withSecond(0).withNano(0).toString()))return "";if("once".equals(m.optString("repeat"))&&!local.toLocalDate().toString().equals(m.optString("date")))return "";return m.optString("id")+":"+local.toLocalDate()+":"+time.substring(0,5);}
    JSONObject display(Instant now)throws Exception {JSONArray list=new JSONArray();for(JSONObject m:messages)if(visible(m,now))list.put(m);return new JSONObject().put("version",1).put("timezone",zone.getId()).put("messages",list);}
}
