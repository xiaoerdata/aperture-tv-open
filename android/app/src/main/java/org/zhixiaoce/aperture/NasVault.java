package org.zhixiaoce.aperture;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.json.JSONArray;
import org.json.JSONObject;

/** Device-local encrypted connections. Never sent to WebView, household service or logs. */
final class NasVault {
    private static final String ALIAS = NasCipher.ALIAS;
    private final SharedPreferences preferences;
    NasVault(Context context) { preferences = context.getSharedPreferences("nas-vault", Context.MODE_PRIVATE); }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }
    NasLibrary library() throws Exception {
        String value = preferences.getString("connections", "");
        NasLibrary library = new NasLibrary(value.isEmpty() ? "[]" : NasCipher.decrypt(value, key()));
        if (library.upgraded) write(library); return library;
    }
    JSONArray read() throws Exception { return library().connections(); }
    private void write(NasLibrary library) throws Exception {
        String value = NasCipher.encrypt(library.json(), key());
        if (!preferences.edit().putString("connections", value).commit()) throw new IllegalStateException();
    }
    JSONObject add(JSONObject connection) throws Exception { NasLibrary library = library(); JSONObject saved = library.add(connection); write(library); return saved; }
    JSONObject addDraft(NasDraft draft) throws Exception { NasLibrary library=library();JSONObject saved=library.addDraft(draft);write(library);return saved; }
    void remove(String id) throws Exception { NasLibrary library = library(); library.remove(id); write(library); }
    void assign(String role, String id, String path) throws Exception { NasLibrary library = library(); library.assign(role, id, path); write(library); }
    void clear(String role) throws Exception { NasLibrary library = library(); library.clear(role); write(library); }
    void reset() { preferences.edit().clear().apply(); }
}
