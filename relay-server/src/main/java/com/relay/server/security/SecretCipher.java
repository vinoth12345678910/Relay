// File: relay-server/src/main/java/com/relay/server/security/SecretCipher.java
package com.relay.server.security;

import com.relay.server.config.RelayProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Random;

/**
 * AES-256-GCM encryption for endpoint signing secrets.
 *
 * <p>The configured base64 key is run through SHA-256 to get a 256-bit AES key, so any key
 * length works. Stored layout is {@code iv (12 bytes) || ciphertext+tag}. Failures throw a
 * generic exception so no key or plaintext material leaks into logs.
 */
@Component
public class SecretCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final Random random;

    public SecretCipher(RelayProperties properties, Random random) {
        this.key = deriveKey(properties.getSecretEncryptionKey());
        this.random = random;
    }

    /** Encrypts the plaintext with a fresh random IV. */
    public byte[] encrypt(byte[] plaintext) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext);
            byte[] out = new byte[IV_BYTES + encrypted.length];
            System.arraycopy(iv, 0, out, 0, IV_BYTES);
            System.arraycopy(encrypted, 0, out, IV_BYTES, encrypted.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Secret encryption failed");
        }
    }

    /** Decrypts a value produced by {@link #encrypt(byte[])}. */
    public byte[] decrypt(byte[] stored) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES));
            return cipher.doFinal(stored, IV_BYTES, stored.length - IV_BYTES);
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IllegalStateException("Secret decryption failed");
        }
    }

    private static SecretKeySpec deriveKey(String base64Key) {
        try {
            byte[] raw = Base64.getDecoder().decode(base64Key);
            return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(raw), "AES");
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("relay.secret-encryption-key must be valid base64");
        }
    }
}
