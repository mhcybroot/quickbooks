package com.example.quickbooksimporter.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class QboCredentialsCryptoService {

    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;
    private static final String PREFIX = "v1:";

    private final byte[] keyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public QboCredentialsCryptoService(@Value("${app.quickbooks.credentials-master-key:}") String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            this.keyBytes = null;
        } else {
            this.keyBytes = Base64.getDecoder().decode(masterKey.trim());
            if (this.keyBytes.length != 32) {
                throw new IllegalStateException("app.quickbooks.credentials-master-key must be base64-encoded 32-byte key");
            }
        }
    }

    public String encrypt(String plainText) {
        ensureKey();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(nonce.length + ciphertext.length);
            buffer.put(nonce);
            buffer.put(ciphertext);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encrypt QuickBooks client secret", exception);
        }
    }

    public String decrypt(String encrypted) {
        ensureKey();
        if (encrypted == null || !encrypted.startsWith(PREFIX)) {
            throw new IllegalStateException("Unsupported encrypted secret format");
        }
        try {
            byte[] packed = Base64.getDecoder().decode(encrypted.substring(PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(packed);
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] cipherBytes = new byte[buffer.remaining()];
            buffer.get(cipherBytes);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decrypt QuickBooks client secret", exception);
        }
    }

    private void ensureKey() {
        if (keyBytes == null) {
            throw new IllegalStateException("app.quickbooks.credentials-master-key is required for company credential encryption");
        }
    }
}
