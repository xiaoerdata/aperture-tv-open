package org.zhixiaoce.aperture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Independent format and size budget; changing media limits must not change NAS records. */
final class CinemaCipher {
    private static final byte[] AAD="aperture.cinema.v1".getBytes(StandardCharsets.UTF_8);
    static String encrypt(String json,SecretKey key) throws Exception {
        byte[] bytes=json.getBytes(StandardCharsets.UTF_8);if(bytes.length>CinemaCatalog.MAX_BYTES)throw new IOException("规范化配置超过512KB");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);cipher.updateAAD(AAD);
        return Base64.getEncoder().encodeToString(cipher.getIV())+"."+Base64.getEncoder().encodeToString(cipher.doFinal(bytes));
    }
    static String decrypt(String value,SecretKey key) throws Exception {
        if(value.length()>(CinemaCatalog.MAX_BYTES+16)*4/3+64)throw new IOException("来源记录过大");
        String[] parts=value.split("\\.",-1);if(parts.length!=2)throw new IOException("来源记录无效");byte[] iv=Base64.getDecoder().decode(parts[0]);if(iv.length!=12)throw new IOException("来源记录无效");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));cipher.updateAAD(AAD);
        byte[] bytes=cipher.doFinal(Base64.getDecoder().decode(parts[1]));if(bytes.length>CinemaCatalog.MAX_BYTES)throw new IOException("来源记录过大");return new String(bytes,StandardCharsets.UTF_8);
    }
}
