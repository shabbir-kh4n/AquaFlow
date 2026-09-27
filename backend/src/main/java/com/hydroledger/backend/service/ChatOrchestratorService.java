package com.hydroledger.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.hydroledger.backend.dto.ChatMessage;
import com.hydroledger.backend.dto.ChatRequest;
import com.hydroledger.backend.dto.ChatResponse;
import com.hydroledger.backend.exception.ApiException;

import lombok.RequiredArgsConstructor;

/**
 * Shared Gemini function-calling loop for all 4 chat personas. Tool
 * executors are resolved once per request (see ChatToolRegistry) and always
 * scoped to the already-authenticated principal - never to anything Gemini
 * supplies in a functionCall's args.
 */
@Service
@RequiredArgsConstructor
public class ChatOrchestratorService {

    private static final int MAX_TOOL_ROUNDTRIPS = 4;

    private final GeminiClient geminiClient;
    private final ChatToolRegistry chatToolRegistry;

    public ChatResponse orchestrate(ChatPersona persona, Object principal, ChatRequest request) {
        Map<String, ChatTool> tools = chatToolRegistry.toolsFor(persona, principal);
        List<Map<String, Object>> contents = buildInitialContents(request);
        List<Map<String, Object>> functionDeclarations = buildFunctionDeclarations(tools);
        String systemInstruction = ChatPersonaPrompts.systemInstructionFor(persona)
                + ReplyLanguage.instructionFor(request.language());

        for (int round = 0; round < MAX_TOOL_ROUNDTRIPS; round++) {
            GeminiTurnResult result = geminiClient.generateContent(systemInstruction, contents, functionDeclarations);

            if (result instanceof GeminiTurnResult.FinalText finalText) {
                return new ChatResponse(finalText.text());
            }

            GeminiTurnResult.FunctionCall functionCall = (GeminiTurnResult.FunctionCall) result;
            Object toolResult = executeTool(tools, functionCall);

            // rawPart is echoed back verbatim (see GeminiTurnResult) - it carries the
            // thoughtSignature newer Gemini "thinking" models require alongside functionCall.
            contents.add(Map.of("role", "model", "parts", List.of(functionCall.rawPart())));
            // Gemini's current API rejects a "function" role for functionResponse parts
            // (only SYSTEM/USER/MODEL/etc. are accepted) - "user" is the documented convention.
            contents.add(Map.of("role", "user", "parts", List.of(
                    Map.of("functionResponse", Map.of("name", functionCall.name(), "response",
                            Map.of("result", toolResult))))));
        }

        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "The assistant couldn't complete that request. Please try rephrasing it.");
    }

    private Object executeTool(Map<String, ChatTool> tools, GeminiTurnResult.FunctionCall functionCall) {
        ChatTool tool = tools.get(functionCall.name());
        if (tool == null) {
            return Map.of("error", "Unknown tool: " + functionCall.name());
        }
        try {
            return tool.execute(functionCall.args());
        } catch (Exception ex) {
            return Map.of("error", "That request couldn't be completed: " + ex.getMessage());
        }
    }

    private List<Map<String, Object>> buildInitialContents(ChatRequest request) {
        List<Map<String, Object>> contents = new ArrayList<>();
        if (request.history() != null) {
            for (ChatMessage message : request.history()) {
                contents.add(Map.of("role", message.role(), "parts", List.of(Map.of("text", message.content()))));
            }
        }
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", request.message()))));
        return contents;
    }

    private List<Map<String, Object>> buildFunctionDeclarations(Map<String, ChatTool> tools) {
        return tools.values().stream()
                .map(tool -> Map.<String, Object>of(
                        "name", tool.name(),
                        "description", tool.description(),
                        "parameters", tool.parametersSchema()))
                .toList();
    }
}
