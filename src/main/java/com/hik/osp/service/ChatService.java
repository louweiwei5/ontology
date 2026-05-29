package com.hik.osp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hik.osp.dto.request.ChatRequest;
import com.hik.osp.dto.request.DslQueryRequest;
import com.hik.osp.dto.response.ChatMessageResponse;
import com.hik.osp.dto.response.ChatResponse;
import com.hik.osp.dto.response.ChatSessionResponse;
import com.hik.osp.entity.AgentEntity;
import com.hik.osp.entity.ChatMessageEntity;
import com.hik.osp.entity.ModelConfigEntity;
import com.hik.osp.entity.OntologyEntity;
import com.hik.osp.entity.PropertyEntity;
import com.hik.osp.exception.BadRequestException;
import com.hik.osp.exception.ResourceNotFoundException;
import com.hik.osp.repository.AgentRepository;
import com.hik.osp.repository.ChatMessageRepository;
import com.hik.osp.repository.ModelConfigRepository;
import com.hik.osp.repository.OntologyRepository;
import com.hik.osp.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int MAX_HISTORY = 20;

    private final AgentRepository agentRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final OntologyRepository ontologyRepository;
    private final OntologyService ontologyService;
    private final ChatMessageRepository chatMessageRepository;
    private final PropertyRepository propertyRepository;
    private final DslQueryService dslQueryService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public ChatResponse chat(String agentId, ChatRequest request) {
        // 1. Load agent
        AgentEntity agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", agentId));

        // 2. Validate model config
        if (agent.getModelConfigId() == null) {
            throw new BadRequestException("Agent has no model configuration assigned");
        }
        ModelConfigEntity modelConfig = modelConfigRepository.findById(agent.getModelConfigId())
                .orElseThrow(() -> new ResourceNotFoundException("ModelConfig", agent.getModelConfigId()));

        // 3. Resolve session
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        // 4. Build messages for LLM
        List<Map<String, String>> llmMessages = buildLlmMessages(agent, sessionId, request.getMessage());

        // 5. Call LLM
        String llmReply;
        try {
            llmReply = llmService.chatCompletion(modelConfig, llmMessages);
        } catch (Exception e) {
            throw new BadRequestException("LLM call failed: " + e.getMessage());
        }

        // 6. Parse LLM JSON response
        JsonNode replyJson;
        try {
            replyJson = objectMapper.readTree(llmReply);
        } catch (Exception e) {
            // LLM didn't return valid JSON — treat entire response as plain text
            saveMessage(agentId, sessionId, "user", request.getMessage(), null, null);
            String assistantMsgId = saveMessage(agentId, sessionId, "assistant", llmReply, null, null);
            return ChatResponse.builder()
                    .reply(llmReply)
                    .sessionId(sessionId)
                    .messageId(assistantMsgId)
                    .build();
        }

        // 7. Extract reply text
        String replyText = replyJson.has("reply") ? replyJson.get("reply").asText() : llmReply;

        // 8. Execute DSL if present
        Map<String, Object> dslExecuted = null;
        ChatResponse.QueryResult queryResult = null;
        if (replyJson.has("dsl") && !replyJson.get("dsl").isNull()) {
            JsonNode dslNode = replyJson.get("dsl");

            // Always capture the raw DSL for diagnostics
            try {
                dslExecuted = objectMapper.convertValue(dslNode,
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            } catch (Exception ignored) {}

            String rawSql = null;
            // Build field metadata (labels + relation types) from ontology
            Map<String, String> fieldLabels = Collections.emptyMap();
            Map<String, String> relationTypes = Collections.emptyMap();
            if (agent.getOntologyId() != null) {
                Map<String, String> labels = new LinkedHashMap<>();
                Map<String, String> relTypes = new LinkedHashMap<>();
                for (PropertyEntity prop : propertyRepository.findByOntologyId(agent.getOntologyId())) {
                    if (prop.getDescription() != null && !prop.getDescription().isBlank()) {
                        labels.put(prop.getName(), prop.getDescription());
                    }
                    if (prop.getPropertyType() == com.hik.osp.enums.PropertyType.OBJECT
                            && prop.getRelationType() != null) {
                        relTypes.put(prop.getName(), prop.getRelationType().getValue());
                    }
                }
                fieldLabels = labels;
                relationTypes = relTypes;
            }

            try {
                DslQueryRequest dslRequest = objectMapper.treeToValue(dslNode, DslQueryRequest.class);
                var dslResponse = dslQueryService.executeQuery(dslRequest);
                rawSql = dslResponse.getSql();

                List<Map<String, Object>> rows = dslResponse.getRows();
                if (rows == null) rows = dslResponse.getData();
                queryResult = ChatResponse.QueryResult.builder()
                        .columns(dslResponse.getColumns())
                        .rows(rows)
                        .total(dslResponse.getTotal())
                        .message(dslResponse.getMessage())
                        .sql(rawSql)
                        .fieldLabels(fieldLabels)
                        .relationTypes(relationTypes)
                        .build();
            } catch (Exception e) {
                queryResult = ChatResponse.QueryResult.builder()
                        .message("DSL 查询执行失败，请查看下方诊断信息")
                        .total(0)
                        .sql(rawSql)
                        .dslError(e.getMessage())
                        .build();
            }
        }

        // 9. Save messages
        String dslJson = dslExecuted != null ? toJsonString(dslExecuted) : null;
        String resultJson = queryResult != null ? toJsonString(queryResult) : null;
        saveMessage(agentId, sessionId, "user", request.getMessage(), null, null);
        String assistantMsgId = saveMessage(agentId, sessionId, "assistant", replyText, dslJson, resultJson);

        return ChatResponse.builder()
                .reply(replyText)
                .dslQuery(dslExecuted)
                .queryResult(queryResult)
                .sessionId(sessionId)
                .messageId(assistantMsgId)
                .build();
    }

    public List<ChatSessionResponse> getSessions(String agentId) {
        List<ChatMessageEntity> messages = chatMessageRepository.findByAgentIdOrderByCreatedAtAsc(agentId);
        Map<String, List<ChatMessageEntity>> grouped = messages.stream()
                .collect(Collectors.groupingBy(ChatMessageEntity::getSessionId));

        List<ChatSessionResponse> sessions = new ArrayList<>();
        for (Map.Entry<String, List<ChatMessageEntity>> entry : grouped.entrySet()) {
            List<ChatMessageEntity> msgs = entry.getValue();
            String preview = msgs.stream()
                    .filter(m -> "user".equals(m.getRole()))
                    .map(ChatMessageEntity::getContent)
                    .findFirst()
                    .orElse("");
            if (preview.length() > 80) preview = preview.substring(0, 80) + "...";
            sessions.add(ChatSessionResponse.builder()
                    .sessionId(entry.getKey())
                    .preview(preview)
                    .messageCount(msgs.size())
                    .createdAt(msgs.get(0).getCreatedAt())
                    .lastMessageAt(msgs.get(msgs.size() - 1).getCreatedAt())
                    .build());
        }
        sessions.sort((a, b) -> b.getLastMessageAt().compareTo(a.getLastMessageAt()));
        return sessions;
    }

    public List<ChatMessageResponse> getSessionMessages(String agentId, String sessionId) {
        return chatMessageRepository.findByAgentIdAndSessionIdOrderByCreatedAtAsc(agentId, sessionId)
                .stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteSession(String agentId, String sessionId) {
        chatMessageRepository.deleteByAgentIdAndSessionId(agentId, sessionId);
    }

    private List<Map<String, String>> buildLlmMessages(AgentEntity agent, String sessionId, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();

        // System prompt: agent prompt + ontology context
        StringBuilder systemBuilder = new StringBuilder();
        String agentPrompt = agent.getSystemPrompt();
        if (agentPrompt != null && !agentPrompt.isBlank()) {
            systemBuilder.append(agentPrompt);
        } else {
            systemBuilder.append("You are a helpful ontology query assistant. " +
                    "Help users query data using the ontology DSL.");
        }

        // Append ontology TBox context
        if (agent.getOntologyId() != null) {
            try {
                Map<String, Object> tboxJson = ontologyService.getTboxJson(agent.getOntologyId());
                systemBuilder.append("\n\n---\n## Ontology Context (TBox)\n").append(toJsonString(tboxJson));
            } catch (Exception e) {
                systemBuilder.append("\n\n(Ontology TBox unavailable: ").append(e.getMessage()).append(")");
            }
        }

        // DSL template instruction
        systemBuilder.append("\n\n---\n## Output Format\n" +
                "You MUST respond in JSON format ONLY:\n" +
                "{\n  \"reply\": \"Your natural language response to the user\",\n" +
                "  \"dsl\": { ... DSL query object matching the ontology. " +
                "Set to null if no query is needed. }\n}\n" +
                "The DSL query follows this structure:\n" +
                "- ontology: { name, namespace (optional), version (optional) }\n" +
                "- query: { target (class name), selection (array of field names or relation objects), " +
                "filter (optional), pagination (optional) }\n" +
                "Each relation object: { relation: \"relationName\", nested_fields: [field names or sub-relations] }\n" +
                "Filter: { logic: \"AND\"|\"OR\", conditions: [{ path: [\"relationName\"], field: \"fieldName\", " +
                "operator: \"EQ\"|\"GT\"|\"LIKE\"|\"CONTAINS\" etc., value: ... }] }\n" +
                "If the user's request cannot be expressed as a DSL query, set dsl to null and reply naturally.");

        messages.add(Map.of("role", "system", "content", systemBuilder.toString()));

        // Chat history (last N messages)
        List<ChatMessageEntity> history = chatMessageRepository
                .findByAgentIdAndSessionIdOrderByCreatedAtAsc(agent.getId(), sessionId);
        int start = Math.max(0, history.size() - MAX_HISTORY);
        for (int i = start; i < history.size(); i++) {
            ChatMessageEntity h = history.get(i);
            messages.add(Map.of("role", h.getRole(), "content", h.getContent()));
        }

        // Current user message
        messages.add(Map.of("role", "user", "content", userMessage));

        return messages;
    }

    private String saveMessage(String agentId, String sessionId, String role,
                                String content, String dslQuery, String queryResult) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setAgentId(agentId);
        entity.setSessionId(sessionId);
        entity.setRole(role);
        entity.setContent(content);
        entity.setDslQuery(dslQuery);
        entity.setQueryResult(queryResult);
        entity = chatMessageRepository.save(entity);
        return entity.getId();
    }

    private ChatMessageResponse toMessageResponse(ChatMessageEntity entity) {
        Map<String, Object> dslParsed = null;
        ChatResponse.QueryResult resultParsed = null;
        try {
            if (entity.getDslQuery() != null) {
                dslParsed = objectMapper.readValue(entity.getDslQuery(),
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            }
        } catch (Exception ignored) {}
        try {
            if (entity.getQueryResult() != null) {
                resultParsed = objectMapper.readValue(entity.getQueryResult(), ChatResponse.QueryResult.class);
            }
        } catch (Exception ignored) {}
        return ChatMessageResponse.builder()
                .id(entity.getId())
                .role(entity.getRole())
                .content(entity.getContent())
                .dslQuery(dslParsed)
                .queryResult(resultParsed)
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
