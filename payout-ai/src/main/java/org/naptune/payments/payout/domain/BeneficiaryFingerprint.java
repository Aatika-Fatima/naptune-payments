package org.naptune.payments.payout.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

// SHA-256 of the beneficiary account reference, as 64 hex characters. The
// account number itself is never stored in this module.
public final class BeneficiaryFingerprint {

    private BeneficiaryFingerprint() {
    }

    // Case and whitespace are not part of an account reference, so
    // " bank-b-xxxx9931" and "BANK-B-XXXX9931" are the same beneficiary.
    public static String of(String beneficiaryReference) {
        String normalized = beneficiaryReference.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return sha256Hex(normalized);
    }

    static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Every JVM is required to provide SHA-256.
            throw new IllegalStateException(e);
        }
    }
}
