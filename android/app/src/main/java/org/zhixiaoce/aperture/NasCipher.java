package org.zhixiaoce.aperture;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Same AES-GCM record format/AAD as v5. AndroidKeyStore supplies the key in production. */
final class NasCipher {
    static final String ALIAS = "aperture.nas.v1";
    static String encrypt(String json, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key); cipher.updateAAD(ALIAS.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(cipher.getIV()) + "." + Base64.getEncoder().encodeToString(cipher.doFinal(json.getBytes(StandardCharsets.UTF_8)));
    }
    static String decrypt(String value, SecretKey key) throws Exception {
        if (value.length() > 131072) throw new IllegalArgumentException("连接记录过大");
        String[] parts = value.split("\\.", -1); if (parts.length != 2) throw new IllegalArgumentException("连接记录损坏");
        byte[] iv = Base64.getDecoder().decode(parts[0]); if (iv.length != 12) throw new IllegalArgumentException("连接记录损坏");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv)); cipher.updateAAD(ALIAS.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
    }
}
