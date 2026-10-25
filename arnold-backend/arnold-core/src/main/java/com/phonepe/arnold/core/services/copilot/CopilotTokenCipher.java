package com.phonepe.arnold.core.services.copilot;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

public class CopilotTokenCipher {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CopilotTokenCipher(final String encodedKey) {
        try {
            final var bytes = Base64.getDecoder().decode(encodedKey == null ? "" : encodedKey);
            if (bytes.length != 32) throw new IllegalArgumentException();
            key = new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Copilot encryptionKey must be a base64-encoded 32-byte key");
        }
    }

    public String encrypt(final String owner, final String value) {
        if (value == null) return null;
        final var nonce = new byte[12];
        random.nextBytes(nonce);
        final var encrypted = crypt(Cipher.ENCRYPT_MODE, owner, nonce, value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                .put(nonce).put(encrypted).array());
    }

    public String decrypt(final String owner, final String value) {
        if (value == null) return null;
        try {
            final var bytes = Base64.getDecoder().decode(value);
            if (bytes.length < 28) throw new IllegalArgumentException();
            final var buffer = ByteBuffer.wrap(bytes);
            final var nonce = new byte[12];
            buffer.get(nonce);
            final var encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            return new String(crypt(Cipher.DECRYPT_MODE, owner, nonce, encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Cannot decrypt Copilot credential");
        }
    }

    private byte[] crypt(final int mode, final String owner, final byte[] nonce, final byte[] data) {
        try {
            final var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(owner.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot process Copilot credential");
        }
    }
}
