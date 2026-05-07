package com.sibanda.co.zw.janusgateway.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class KeyHashingService {

    private static final String ALGORITHM = "SHA-256";
    private static final int KEY_PREFIX_LENGTH = 8;  // "sk-abc123" prefix
    private static final int KEY_BODY_LENGTH = 32;   // 32 random bytes

    /**
     * Generates a new API key in format: sk-{random_base64}
     * Only the prefix "sk-" and first 8 chars are stored visibly.
     * The full key is returned once at creation time.
     */
    public String generateApiKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[KEY_BODY_LENGTH];
        random.nextBytes(bytes);
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return "sk-" + body;
    }

    /**
     * Hashes an API key using SHA-256.
     * The hash is what we store in Redis/PostgreSQL — never the raw key.
     */
    public String hashKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verifies a raw API key against a stored hash.
     */
    public boolean verifyKey(String rawKey, String storedHash) {
        String computedHash = hashKey(rawKey);
        return MessageDigest.isEqual(
                computedHash.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Returns a masked version of a key for display purposes.
     * Example: "sk-abc123..." → "sk-abc...xyz"
     */
    public String maskKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 12) {
            return "sk-****";
        }
        return apiKey.substring(0, 8) + "..." + apiKey.substring(apiKey.length() - 4);
    }
}