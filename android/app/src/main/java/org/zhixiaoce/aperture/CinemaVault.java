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
    private final SharedPreferences preferences;
    CinemaVault(Context context){preferences=context.getSharedPreferences("cinema-vault",Context.MODE_PRIVATE);}
    private SecretKey key() throws Exception {
        String alias="aperture-cinema-v1";KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(alias)){KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generator.generateKey();}
        return (SecretKey)store.getKey(alias,null);
    }
    CinemaCatalog.Config read() throws Exception {
        String value=preferences.getString("config","");if(value.isEmpty())return new CinemaCatalog.Config(false);
        return CinemaCatalog.config(CinemaCipher.decrypt(value,key()),null,preferences.getBoolean("http",false));
    }
    void save(CinemaCatalog.Config config) throws Exception {
        String encrypted=CinemaCipher.encrypt(config.json(),key());if(!preferences.edit().putString("config",encrypted).putBoolean("http",config.allowHttp).commit())throw new java.io.IOException("来源保存失败");
    }
    void clear(){preferences.edit().clear().apply();}
}
