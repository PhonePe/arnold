package com.phonepe.devx.arnold.core.services.copilot;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class CopilotTokenCipherTest {
    @Test
    void testCipher_shouldAuthenticateOwnerAndCiphertext_whenDecrypting() {
        final var cipher = new CopilotTokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
        final var encrypted = cipher.encrypt("alice:access", "secret");
        assertEquals("secret", cipher.decrypt("alice:access", encrypted));
        assertNotEquals(encrypted, cipher.encrypt("alice:access", "secret"));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("bob:access", encrypted));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("alice:refresh", encrypted));
        final var tampered = Base64.getDecoder().decode(encrypted);
        tampered[tampered.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("alice:access", Base64.getEncoder().encodeToString(tampered)));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("alice:access", "invalid"));
        assertThrows(IllegalArgumentException.class, () -> new CopilotTokenCipher("bad-key"));
    }
}
