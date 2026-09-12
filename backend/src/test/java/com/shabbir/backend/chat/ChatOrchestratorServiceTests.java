package com.shabbir.backend.chat;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shabbir.backend.dto.ChatRequest;
import com.shabbir.backend.dto.ChatResponse;
import com.shabbir.backend.exception.ApiException;
import com.shabbir.backend.service.ChatOrchestratorService;
import com.shabbir.backend.service.ChatPersona;
import com.shabbir.backend.service.ChatTool;
import com.shabbir.backend.service.ChatToolRegistry;
import com.shabbir.backend.service.GeminiClient;
import com.shabbir.backend.service.GeminiTurnResult;

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
                new ChatRequest("hi", null));

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
                new ChatRequest("what is my house number?", null));

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
                new ChatRequest("loop forever", null)))
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
                new ChatRequest("do the impossible", null));

        assertThat(response.reply()).isEqualTo("Sorry, I couldn't do that.");
    }

    @Test
    void resolvesToolsUsingThePrincipalPassedIn() {
        String principal = "resident-42";
        when(chatToolRegistry.toolsFor(any(), any())).thenReturn(Map.of());
        when(geminiClient.generateContent(anyString(), anyList(), anyList()))
                .thenReturn(GeminiTurnResult.finalText("ok"));

        orchestratorService.orchestrate(ChatPersona.RESIDENT, principal, new ChatRequest("hi", null));

        verify(chatToolRegistry, times(1)).toolsFor(ChatPersona.RESIDENT, principal);
    }
}
