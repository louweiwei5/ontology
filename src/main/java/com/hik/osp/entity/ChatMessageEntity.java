package com.hik.osp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "chat_messages")
public class ChatMessageEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "agent_id", length = 36, nullable = false)
    private String agentId;

    @Column(name = "session_id", length = 64, nullable = false)
    private String sessionId;

    @Column(name = "role", length = 20, nullable = false)
    private String role;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "dsl_query", columnDefinition = "TEXT")
    private String dslQuery;

    @Column(name = "query_result", columnDefinition = "TEXT")
    private String queryResult;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        createdAt = LocalDateTime.now();
    }
}
