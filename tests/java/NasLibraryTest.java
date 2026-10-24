package org.zhixiaoce.aperture;

import java.util.Base64;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.json.JSONArray;
import org.json.JSONObject;

/** Executes production library/cipher/navigation on the JVM; not AndroidKeyStore or TV evidence. */
public final class NasLibraryTest {
    interface Action { void run() throws Exception; }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void rejects(Action action,String message)throws Exception{try{action.run();}catch(Exception expected){return;}throw new AssertionError(message);}
    static JSONObject fixture(String name)throws Exception{return new JSONObject().put("name",name).put("root","smb://synthetic-nas/Family").put("brand","合成NAS").put("user","synthetic-user").put("password","synthetic-password-only").put("domain","");}
    public static void main(String[] args)throws Exception{
        JSONArray old=new JSONArray().put(fixture("客厅照片")).put(fixture("周末影片"));
        NasLibrary migrated=new NasLibrary(old.toString());check(migrated.upgraded,"v5 needs stable IDs");
        String first=migrated.connections().getJSONObject(0).getString("id"),second=migrated.connections().getJSONObject(1).getString("id");
        NasLibrary stable=new NasLibrary(migrated.json());check(!stable.upgraded&&stable.find(second)!=null,"migration remains stable");
        check(stable.route("movies")==null&&stable.route("series")==null,"legacy users keep household service defaults");
        stable.assign("movies",second,"电影/周末");stable.assign("series",second,"电视剧");stable.assign("photos",first,"");
        check(stable.route("movies").id.equals(second)&&stable.route("movies").path.equals("电影/周末"),"explicit selected folder");
        check(stable.find(second).getString("password").equals("synthetic-password-only"),"binding preserves original credentials");
        JSONObject detached=stable.route("movies").connection;detached.put("name","changed outside library");check(!stable.find(second).getString("name").equals("changed outside library"),"callers cannot mutate stored records");
        stable.remove(first);check(stable.route("photos")==null&&stable.route("movies").id.equals(second),"deleting earlier item cannot retarget a binding");
        JSONObject replacement=stable.add(fixture("新片库"));String third=replacement.getString("id");stable.assign("movies",third,"孩子的电影");
        check(!stable.find(second).getJSONObject("home").has("movies")&&stable.route("series").id.equals(second),"reassignment is unique and independent per role");
        stable.clear("movies");check(stable.route("movies")==null&&stable.route("series")!=null,"explicit restore to household service");
        stable.remove(second);check(stable.route("series")==null,"forgetting assigned connection clears its route");
        System.out.println("PASS migration / stable identity / binding / deletion / reassignment / restore");

        NasLibrary invalid=new NasLibrary(migrated.json());
        for(String path:new String[]{"../秘密","电影/../秘密","/绝对路径","电影\\秘密","电影/%2e%2e"})rejects(()->invalid.assign("movies",second,path),"reject traversal");
        rejects(()->invalid.assign("shell",second,""),"fixed categories only");rejects(()->invalid.assign("movies","missing", ""),"no unknown connection");
        JSONObject injected=fixture("传入的绑定").put("home",new JSONObject().put("movies","其他目录"));JSONObject added=invalid.add(injected);check(!added.has("home"),"adding a connection never imports route privileges");
        JSONArray duplicate=new JSONArray(migrated.json());duplicate.getJSONObject(0).put("id",second);rejects(()->new NasLibrary(duplicate.toString()),"duplicate identity");
        JSONArray ambiguous=new JSONArray(migrated.json());for(int i=0;i<2;i++)ambiguous.getJSONObject(i).put("home",new JSONObject().put("movies",""));rejects(()->new NasLibrary(ambiguous.toString()),"ambiguous home route fails closed");
        JSONArray malformed=new JSONArray(migrated.json());malformed.getJSONObject(0).put("home","not an object");rejects(()->new NasLibrary(malformed.toString()),"corrupt binding metadata");
        System.out.println("PASS path / category / missing-ID / malformed-or-ambiguous binding boundaries");

        NasNavigation navigation=new NasNavigation();navigation.reset("电影");navigation.save("电影",2,"电影/第四十部.mp4");navigation.save("电影/合集",0,"电影/合集/第一部.mp4");
        check(navigation.parent("电影")==null&&navigation.parent("电影/合集").equals("电影"),"selected folder is navigation root");
        check(!navigation.contains("电影私密")&&!navigation.contains("相册"),"no sibling-prefix escape");
        check(navigation.position("电影").page==2&&navigation.position("电影").focus.equals("电影/第四十部.mp4"),"back restores page and focused item");
        rejects(()->navigation.position("相册"),"cannot restore outside selected root");rejects(()->navigation.contains("电影/../秘密"),"navigation traversal");
        for(int i=0;i<70;i++)navigation.save("电影/目录"+i,0,"");check(navigation.position("电影").page==0,"bounded history evicts oldest entry");
        System.out.println("PASS selected-root navigation / focus-page restoration / bounded history");

        KeyGenerator generator=KeyGenerator.getInstance("AES");generator.init(256);SecretKey key=generator.generateKey();
        String encrypted=NasCipher.encrypt(migrated.json(),key),again=NasCipher.encrypt(migrated.json(),key);
        check(!encrypted.equals(again)&&!encrypted.contains("synthetic-user")&&!encrypted.contains("synthetic-password"),"fresh IV, no plaintext credential in storage");
        check(new NasLibrary(NasCipher.decrypt(encrypted,key)).find(second)!=null,"encrypted old-style array round trip");
        String[] parts=encrypted.split("\\.");byte[] ciphertext=Base64.getDecoder().decode(parts[1]);ciphertext[ciphertext.length-1]^=1;
        rejects(()->NasCipher.decrypt(parts[0]+"."+Base64.getEncoder().encodeToString(ciphertext),key),"tampered authenticated ciphertext");
        rejects(()->NasCipher.decrypt(encrypted,generator.generateKey()),"wrong key cannot expose credentials");rejects(()->NasCipher.decrypt("broken.record.more",key),"malformed storage");
        // v5 readers can still parse the updated array and find original names/credentials.
        JSONArray v5=new JSONArray(NasCipher.decrypt(encrypted,key));check(v5.getJSONObject(1).getString("name").equals("周末影片"),"v5-compatible plaintext structure");
        System.out.println("PASS authenticated encryption / fresh IV / tampering / wrong key / v5 record compatibility");

        DisplayLifecycle lifecycle=new DisplayLifecycle();lifecycle.resumed(true);lifecycle.focused(true);long pending=lifecycle.interruptions();
        lifecycle.focused(false);lifecycle.focused(true);check(lifecycle.active()&&pending!=lifecycle.interruptions(),"return from assistant must not revive pending autoplay");
        lifecycle.resumed(false);lifecycle.focused(false);lifecycle.focused(true);check(!lifecycle.active(),"late focus cannot activate a paused Activity");
        System.out.println("PASS assistant interruption / pending autoplay generation / late-focus ordering");
    }
}
