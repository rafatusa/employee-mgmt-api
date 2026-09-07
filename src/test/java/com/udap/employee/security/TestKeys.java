package com.udap.employee.security;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates throwaway HMAC signing material for the unit tests.
 *
 * <p>The keys are created at runtime and exist only for the lifetime of the test
 * JVM, so no key material is ever committed to the repository.
 */
final class TestKeys {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TestKeys() {
    }

    /** Returns a fresh, random signing key long enough for HMAC-SHA256. */
    static String signingKey() {
        final byte[] material = new byte[48];
        RANDOM.nextBytes(material);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(material);
    }
}
