package com.hik.osp.repository;

import com.hik.osp.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, String> {
    List<ChatMessageEntity> findByAgentIdAndSessionIdOrderByCreatedAtAsc(String agentId, String sessionId);

    List<ChatMessageEntity> findByAgentIdOrderByCreatedAtAsc(String agentId);

    void deleteByAgentIdAndSessionId(String agentId, String sessionId);
}
