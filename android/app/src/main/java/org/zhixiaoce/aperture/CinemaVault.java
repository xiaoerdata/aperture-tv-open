package org.zhixiaoce.aperture;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Separate device-local encrypted source configuration. Never exposed to the lobby WebView. */
final class CinemaVault {
    private static final Object SHELF_LOCK=new Object();
    private final SharedPreferences preferences;
    CinemaVault(Context context){preferences=context.getSharedPreferences("cinema-vault",Context.MODE_PRIVATE);}
    private SecretKey key() throws Exception {
        synchronized(SHELF_LOCK) {
        String alias="aperture-cinema-v1";KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(alias)){KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generator.generateKey();}
        return (SecretKey)store.getKey(alias,null);
        }
    }
    CinemaCatalog.Config read() throws Exception {
        String value=preferences.getString("config","");if(value.isEmpty())return new CinemaCatalog.Config(false);
        return CinemaCatalog.config(CinemaCipher.decrypt(value,key()),null,preferences.getBoolean("http",false));
    }
    void save(CinemaCatalog.Config config) throws Exception {importConfig(config);}
    boolean importConfig(CinemaCatalog.Config config) throws Exception {
        synchronized(SHELF_LOCK){CinemaCatalog.Config current;try{current=read();}catch(Exception unreadable){current=new CinemaCatalog.Config(false);}if(!CinemaResourcePack.shouldApply(current,config))return false;
        String encrypted=CinemaCipher.encrypt(config.json(),key());if(!preferences.edit().putString("config",encrypted).putBoolean("http",config.allowHttp).commit())throw new java.io.IOException("来源保存失败");return true;
        }
    }
    private CinemaShelfStore shelfStore(){return new CinemaShelfStore(SHELF_LOCK,new CinemaShelfStore.Storage(){
        public String read()throws Exception{String value=preferences.getString("shelf","");return value.isEmpty()?"[]":CinemaCipher.decrypt(value,key());}
        public void write(String json)throws Exception{String value=CinemaCipher.encrypt(json,key());if(!preferences.edit().putString("shelf",value).commit())throw new java.io.IOException("观看记录保存失败");}
    });}
    CinemaShelf shelf() throws Exception{return shelfStore().read();}
    CinemaShelf updateShelf(CinemaShelfStore.Edit edit)throws Exception{return shelfStore().update(edit);}
    synchronized org.json.JSONObject metadata()throws Exception{String value=preferences.getString("metadata","");return value.isEmpty()?new org.json.JSONObject():new org.json.JSONObject(CinemaCipher.decrypt(value,key()));}
    synchronized void metadata(org.json.JSONObject value)throws Exception{String raw=value.toString();if(raw.length()>262144)throw new java.io.IOException("资料设置过大");if(!preferences.edit().putString("metadata",CinemaCipher.encrypt(raw,key())).commit())throw new java.io.IOException("资料设置保存失败");}
    java.util.List<String> guideSources(java.util.List<String> imported)throws Exception{String stored=preferences.getString("guide_override","");if(stored.isEmpty())return new java.util.ArrayList<>(imported);org.json.JSONArray list=new org.json.JSONArray(CinemaCipher.decrypt(stored,key()));java.util.List<String> values=new java.util.ArrayList<>();for(int i=0;i<list.length();i++)values.add(list.getString(i));return values;}
    void guideSources(java.util.List<String> urls,boolean save)throws Exception{if(!preferences.edit().putString("guide_override",CinemaCipher.encrypt(new org.json.JSONArray(urls).toString(),key())).commit())throw new java.io.IOException("节目单地址未保存");}
    void clear(){preferences.edit().remove("config").remove("http").remove("guide_override").apply();}
}
