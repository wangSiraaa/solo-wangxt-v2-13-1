package com.tmhub.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Deterministic fingerprints and natural keys used for idempotency and dedupe. */
public final class Hashes {

    private Hashes() {}

    public static String sha256(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256(String content) {
        return sha256(content.getBytes(StandardCharsets.UTF_8));
    }

    /** Identity of a TM unit: same source sentence + language pair + product line. */
    public static String identityKey(String sourceLang, String targetLang, String productLine, String sourceText) {
        return sha256(sourceLang + "|" + targetLang + "|" + productLine + "|" + sourceText);
    }

    /** Identity of one concrete proposal; identical re-deliveries dedupe on this. */
    public static String proposalKey(String identityKey, String proposedTarget) {
        return sha256(identityKey + "|" + proposedTarget);
    }

    /** Idempotency key of a batch: binds file, baseline version, scope and vendor. */
    public static String batchKey(String tmxFingerprint, long sourceVersionId,
                                  String sourceLang, String targetLang, String productLine, String vendor) {
        return sha256(tmxFingerprint + "|" + sourceVersionId + "|" + sourceLang + "|"
                + targetLang + "|" + productLine + "|" + vendor);
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
