package com.freezify.households.internal;

import java.security.SecureRandom;
import java.util.Locale;

/** Short codes meant to be read aloud or typed on a phone. */
final class InvitationCodes {

    static final int LENGTH = 8;

    // No 0/O, 1/I/L: they are confused when a code is dictated or copied by hand.
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    private InvitationCodes() {}

    static String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** Accepts what a person may type: lower case, spaces or dashes between groups. */
    static String normalize(String input) {
        return input.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }
}
