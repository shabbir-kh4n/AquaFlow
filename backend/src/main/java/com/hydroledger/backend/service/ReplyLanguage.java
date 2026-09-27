package com.hydroledger.backend.service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The UI language a user picked in the frontend (an ISO 639 code, same codes as
 * frontend/src/i18n/languages.js), used to ask Gemini to reply in that language. The code
 * is shape-validated rather than trusted verbatim, since it ends up inside a system prompt.
 */
final class ReplyLanguage {

    static final String DEFAULT = "en";

    private static final Pattern CODE = Pattern.compile("^[a-z]{2,3}$");

    private ReplyLanguage() {
    }

    /** Lowercased code if well-formed, otherwise {@link #DEFAULT}. */
    static String normalize(String code) {
        if (code == null) {
            return DEFAULT;
        }
        String lowered = code.strip().toLowerCase(Locale.ROOT);
        return CODE.matcher(lowered).matches() ? lowered : DEFAULT;
    }

    /** Extra system-instruction text, or an empty string for English (the prompts' own language). */
    static String instructionFor(String code) {
        String normalized = normalize(code);
        if (DEFAULT.equals(normalized)) {
            return "";
        }
        return "\nWrite your entire reply in the language whose ISO 639 code is \"" + normalized
                + "\", using its native script. Keep numbers, currency amounts, units (kL), and proper nouns"
                + " such as names and \"HydroLedger\" unchanged.\n";
    }
}
