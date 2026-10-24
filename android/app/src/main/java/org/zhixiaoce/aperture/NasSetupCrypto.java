package org.zhixiaoce.aperture;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.json.JSONObject;

/** Ephemeral receiving key; only the native TV holds it. No persistent private key or QR secret. */
final class NasSetupCrypto {
    final String id = UUID.randomUUID().toString();
    final String secret, publicKey, fingerprint;
    private KeyPair keys;
    NasSetupCrypto() throws Exception {
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);keys=generator.generateKeyPair();
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);secret=encode(random);Arrays.fill(random,(byte)0);
        byte[] encoded=keys.getPublic().getEncoded();publicKey=encode(encoded);fingerprint=hash(encoded);
    }
    JSONObject hello(String label) throws Exception { return new JSONObject().put("id",id).put("label",label).put("publicKey",publicKey).put("secretHash",hash(secret.getBytes(StandardCharsets.UTF_8))); }
    JSONObject identify() throws Exception { return new JSONObject().put("id",id).put("secret",secret); }
    JSONObject open(JSONObject envelope) throws Exception {
        if(keys==null||envelope.length()!=4||envelope.getInt("version")!=1)throw new GeneralSecurityException("Invalid envelope");
        byte[] wrapped=decode(envelope.getString("key"),256,256),iv=decode(envelope.getString("iv"),12,12),encrypted=decode(envelope.getString("ciphertext"),18,8192);
        Cipher rsa=Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.DECRYPT_MODE,keys.getPrivate(),new OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA256,PSource.PSpecified.DEFAULT));
        byte[] raw=rsa.doFinal(wrapped),clear=null;
        try {
            if(raw.length!=32)throw new GeneralSecurityException("Invalid AES key");
            Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");aes.init(Cipher.DECRYPT_MODE,new SecretKeySpec(raw,"AES"),new GCMParameterSpec(128,iv));aes.updateAAD(("aperture.nas.setup.v1:"+id).getBytes(StandardCharsets.UTF_8));
            clear=aes.doFinal(encrypted);return new JSONObject(new String(clear,StandardCharsets.UTF_8));
        } finally { Arrays.fill(raw,(byte)0);if(clear!=null)Arrays.fill(clear,(byte)0); }
    }
    void close() { keys=null; }
    static String encode(byte[] value){return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
    static byte[] decode(String value,int min,int max)throws GeneralSecurityException{
        if(value==null||!value.matches("[A-Za-z0-9_-]+")||value.length()>max*2)throw new GeneralSecurityException("Invalid base64");
        byte[] bytes;try{bytes=Base64.getUrlDecoder().decode(value);}catch(IllegalArgumentException invalid){throw new GeneralSecurityException("Invalid base64");}
        if(bytes.length<min||bytes.length>max||!encode(bytes).equals(value))throw new GeneralSecurityException("Invalid size");return bytes;
    }
    static String hash(byte[] bytes)throws Exception{StringBuilder hex=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(bytes))hex.append(String.format(Locale.ROOT,"%02x",value&255));return hex.toString();}
}
