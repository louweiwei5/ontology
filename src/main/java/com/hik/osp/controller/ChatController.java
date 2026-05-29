package com.hik.osp.controller;

import com.hik.osp.dto.request.ChatRequest;
import com.hik.osp.dto.response.ChatMessageResponse;
import com.hik.osp.dto.response.ChatResponse;
import com.hik.osp.dto.response.ChatSessionResponse;
import com.hik.osp.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents/{agentId}")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@PathVariable String agentId,
                                              @Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.chat(agentId, request));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionResponse>> getSessions(@PathVariable String agentId) {
        return ResponseEntity.ok(chatService.getSessions(agentId));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<List<ChatMessageResponse>> getSessionMessages(
            @PathVariable String agentId, @PathVariable String sessionId) {
        return ResponseEntity.ok(chatService.getSessionMessages(agentId, sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String agentId,
                                               @PathVariable String sessionId) {
        chatService.deleteSession(agentId, sessionId);
        return ResponseEntity.noContent().build();
    }
}
