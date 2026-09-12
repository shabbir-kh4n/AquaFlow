package com.shabbir.backend.service;

import java.util.Map;

public sealed interface GeminiTurnResult {

    /**
     * {@code rawPart} is the exact part Gemini returned (functionCall + any
     * accompanying fields such as thoughtSignature, required by newer
     * "thinking" models) - it must be echoed back verbatim in the next
     * turn's model-role content, not reconstructed from name/args alone.
     */
    record FunctionCall(String name, Map<String, Object> args, Map<String, Object> rawPart) implements GeminiTurnResult {
    }

    record FinalText(String text) implements GeminiTurnResult {
    }

    static GeminiTurnResult functionCall(String name, Map<String, Object> args, Map<String, Object> rawPart) {
        return new FunctionCall(name, args, rawPart);
    }

    static GeminiTurnResult finalText(String text) {
        return new FinalText(text);
    }
}
