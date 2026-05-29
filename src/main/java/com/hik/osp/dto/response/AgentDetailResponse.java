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
public class AgentDetailResponse {
    private String id;
    private String name;
    private String description;
    private String systemPrompt;
    private String ontologyId;
    private String ontologyName;
    private String modelConfigId;
    private String modelConfigName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
