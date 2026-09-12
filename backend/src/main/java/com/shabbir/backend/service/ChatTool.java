package com.shabbir.backend.service;

import java.util.Map;

/**
 * One Gemini-callable "function" scoped to a single already-authenticated
 * principal. Implementations must never derive tenant scope (apartment /
 * household / resident identity) from {@code args} - only from state closed
 * over when the tool was built for this request's principal.
 */
public interface ChatTool {

    String name();

    String description();

    /** JSON-schema-shaped parameter description, Gemini's OBJECT/STRING/etc. convention. */
    Map<String, Object> parametersSchema();

    Object execute(Map<String, Object> args);
}
