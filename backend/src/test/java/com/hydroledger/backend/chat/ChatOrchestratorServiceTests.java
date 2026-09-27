package com.hydroledger.backend.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hydroledger.backend.dto.ChatRequest;
import com.hydroledger.backend.dto.ChatResponse;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.service.ChatOrchestratorService;
import com.hydroledger.backend.service.ChatPersona;
import com.hydroledger.backend.service.ChatTool;
import com.hydroledger.backend.service.ChatToolRegistry;
import com.hydroledger.backend.service.GeminiClient;
import com.hydroledger.backend.service.GeminiTurnResult;

@ExtendWith(MockitoExtension.class)
class ChatOrchestratorServiceTests {

    @Mock
    private GeminiClient geminiClient;

    @Mock
    private ChatToolRegistry chatToolRegistry;

    private ChatOrchestratorService orchestratorService;

    @BeforeEach
    void setUp() {
        orchestratorService = new ChatOrchestratorService(geminiClient, chatToolRegistry);
    }

    @Test
    void returnsImmediatelyWhenGeminiRepliesWithPlainText() {
        when(chatToolRegistry.toolsFor(any(), any())).thenReturn(Map.of());
        when(geminiClient.generateContent(anyString(), anyList(), anyList()))
                .thenReturn(GeminiTurnResult.finalText("Hello there!"));

        ChatResponse response = orchestratorService.orchestrate(ChatPersona.LANDING, null,
                new ChatRequest("hi", null, null));

        assertThat(response.reply()).isEqualTo("Hello there!");
        verify(geminiClient, times(1)).generateContent(anyString(), anyList(), anyList());
    }

    @Test
    void executesToolThenReturnsFinalTextOnNextRoundtrip() {
        ChatTool tool = mock(ChatTool.class);
        when(tool.name()).thenReturn("get_dashboard_summary");
        when(tool.description()).thenReturn("desc");
        when(tool.parametersSchema()).thenReturn(Map.of());
        when(tool.execute(any())).thenReturn(Map.of("houseNumber", "A-101"));
        when(chatToolRegistry.toolsFor(any(), any())).thenReturn(Map.of("get_dashboard_summary", tool));

        when(geminiClient.generateContent(anyString(), anyList(), anyList()))
                .thenReturn(GeminiTurnResult.functionCall("get_dashboard_summary", Map.of(), Map.of("functionCall", Map.of("name", "get_dashboard_summary", "args", Map.of()))))
                .thenReturn(GeminiTurnResult.finalText("You live in A-101."));

        ChatResponse response = orchestratorService.orchestrate(ChatPersona.RESIDENT, "someResident",
                new ChatRequest("what is my house number?", null, null));

        assertThat(response.reply()).isEqualTo("You live in A-101.");
        verify(tool, times(1)).execute(any());
        verify(geminiClient, times(2)).generateContent(anyString(), anyList(), anyList());
    }

    @Test
    void givesUpAfterMaxRoundtripsOfRepeatedFunctionCalls() {
        ChatTool tool = mock(ChatTool.class);
        when(tool.name()).thenReturn("get_dashboard_summary");
        when(tool.description()).thenReturn("desc");
        when(tool.parametersSchema()).thenReturn(Map.of());
        when(tool.execute(any())).thenReturn(Map.of());
        when(chatToolRegistry.toolsFor(any(), any())).thenReturn(Map.of("get_dashboard_summary", tool));

        when(geminiClient.generateContent(anyString(), anyList(), anyList()))
                .thenReturn(GeminiTurnResult.functionCall("get_dashboard_summary", Map.of(), Map.of("functionCall", Map.of("name", "get_dashboard_summary", "args", Map.of()))));

        assertThatThrownBy(() -> orchestratorService.orchestrate(ChatPersona.RESIDENT, "someResident",
                new ChatRequest("loop forever", null, null)))
                .isInstanceOf(ApiException.class);

        verify(geminiClient, times(4)).generateContent(anyString(), anyList(), anyList());
    }

    @Test
    void unknownToolNameDoesNotThrowAndIsReportedBackToGemini() {
        when(chatToolRegistry.toolsFor(any(), any())).thenReturn(Map.of());

        when(geminiClient.generateContent(anyString(), anyList(), anyList()))
                .thenReturn(GeminiTurnResult.functionCall("nonexistent_tool", Map.of(), Map.of("functionCall", Map.of("name", "nonexistent_tool", "args", Map.of()))))
                .thenReturn(GeminiTurnResult.finalText("Sorry, I couldn't do that."));

        ChatResponse response = orchestratorService.orchestrate(ChatPersona.RESIDENT, "someResident",
                new ChatRequest("do the impossible", null, null));

        assertThat(response.reply()).isEqualTo("Sorry, I couldn't do that.");
    }

    @Test
    void resolvesToolsUsingThePrincipalPassedIn() {
        String principal = "resident-42";
        when(chatToolRegistry.toolsFor(any(), any())).thenReturn(Map.of());
        when(geminiClient.generateContent(anyString(), anyList(), anyList()))
                .thenReturn(GeminiTurnResult.finalText("ok"));

        orchestratorService.orchestrate(ChatPersona.RESIDENT, principal, new ChatRequest("hi", null, null));

        verify(chatToolRegistry, times(1)).toolsFor(ChatPersona.RESIDENT, principal);
    }

    @Test
    void asksGeminiToReplyInTheRequestedLanguage() {
        when(chatToolRegistry.toolsFor(any(), any())).thenReturn(Map.of());
        when(geminiClient.generateContent(anyString(), anyList(), anyList()))
                .thenReturn(GeminiTurnResult.finalText("नमस्ते"));

        orchestratorService.orchestrate(ChatPersona.LANDING, null, new ChatRequest("hi", null, "hi"));

        ArgumentCaptor<String> instruction = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateContent(instruction.capture(), anyList(), anyList());
        assertThat(instruction.getValue()).contains("ISO 639 code is \"hi\"");
    }

    @Test
    void addsNoLanguageInstructionForEnglishOrMissingLanguage() {
        when(chatToolRegistry.toolsFor(any(), any())).thenReturn(Map.of());
        when(geminiClient.generateContent(anyString(), anyList(), anyList()))
                .thenReturn(GeminiTurnResult.finalText("Hello"));

        orchestratorService.orchestrate(ChatPersona.LANDING, null, new ChatRequest("hi", null, "en"));
        orchestratorService.orchestrate(ChatPersona.LANDING, null, new ChatRequest("hi", null, null));

        ArgumentCaptor<String> instruction = ArgumentCaptor.forClass(String.class);
        verify(geminiClient, times(2)).generateContent(instruction.capture(), anyList(), anyList());
        assertThat(instruction.getAllValues()).noneMatch(text -> text.contains("ISO 639"));
    }
}
