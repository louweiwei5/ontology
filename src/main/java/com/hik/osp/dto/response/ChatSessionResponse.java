package com.hik.osp.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatSessionResponse {
    private String sessionId;
    private String preview;
    private int messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime lastMessageAt;
}
