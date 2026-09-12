package com.shabbir.backend.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.shabbir.backend.exception.ApiException;

import lombok.extern.slf4j.Slf4j;

/**
 * Thin transport wrapper around Gemini's generateContent REST endpoint. Pure
 * request/response plumbing - no persona or tool-execution logic lives here.
 */
@Service
@Slf4j
public class GeminiClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GeminiClient(RestClient geminiRestClient,
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.model}") String model) {
        this.restClient = geminiRestClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    @SuppressWarnings("unchecked")
    public GeminiTurnResult generateContent(String systemInstruction, List<Map<String, Object>> contents,
            List<Map<String, Object>> functionDeclarations) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        if (systemInstruction != null) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
        }
        body.put("contents", contents);
        if (functionDeclarations != null && !functionDeclarations.isEmpty()) {
            body.put("tools", List.of(Map.of("functionDeclarations", functionDeclarations)));
        }

        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientException ex) {
            log.error("Gemini API call failed", ex);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI assistant is temporarily unavailable");
        }

        if (response == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI assistant returned an empty response");
        }

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI assistant returned no response");
        }

        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts = content != null
                ? (List<Map<String, Object>>) content.get("parts")
                : List.of();

        for (Map<String, Object> part : parts) {
            if (part.get("functionCall") != null) {
                Map<String, Object> functionCall = (Map<String, Object>) part.get("functionCall");
                String name = (String) functionCall.get("name");
                Map<String, Object> args = (Map<String, Object>) functionCall.getOrDefault("args", Map.of());
                return GeminiTurnResult.functionCall(name, args, part);
            }
        }

        StringBuilder text = new StringBuilder();
        for (Map<String, Object> part : parts) {
            Object partText = part.get("text");
            if (partText != null) {
                text.append(partText);
            }
        }
        return GeminiTurnResult.finalText(text.toString());
    }
}
