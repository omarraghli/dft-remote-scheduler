package cires.dft.remotescheduler.service;

import java.security.SecureRandom;

/** Generates the one-time passwords handed out when an account is created or reset. */
public final class TemporaryPasswords {

    /**
     * No I, l, 1, O or 0. These get read aloud, written on paper and retyped, and the pairs
     * that look alike cause far more support than the lost entropy is worth.
     */
    private static final char[] ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".toCharArray();

    private static final int LENGTH = 14;

    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPasswords() {
    }

    /** Grouped in fours — {@code k7Rm-pQ2x-Tb9w} — because that is how people copy them. */
    public static String generate() {
        StringBuilder out = new StringBuilder(LENGTH + LENGTH / 4);

        for (int i = 0; i < LENGTH; i++) {
            if (i > 0 && i % 4 == 0) out.append('-');
            out.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }

        return out.toString();
    }
}
