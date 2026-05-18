package com.mikunaigen.backend.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

public final class AesGcmPayloadCipher {

    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private AesGcmPayloadCipher() {
    }

    public static byte[] encryptUtf8(String plaintext, byte[] key32) throws Exception {
        if (key32 == null || key32.length != 32) {
            throw new IllegalArgumentException("Se requiere clave AES-256 de 32 bytes.");
        }
        byte[] iv = new byte[IV_LEN];
        SecureRandom rnd = new SecureRandom();
        rnd.nextBytes(iv);
        SecretKey key = new SecretKeySpec(key32, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ByteBuffer buf = ByteBuffer.allocate(iv.length + cipherBytes.length);
        buf.put(iv);
        buf.put(cipherBytes);
        return buf.array();
    }
}
