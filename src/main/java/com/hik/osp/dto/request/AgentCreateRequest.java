package com.hik.osp.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentCreateRequest {
    @NotBlank
    private String name;
    private String description;
    private String systemPrompt;
    private String ontologyId;
    private String modelConfigId;
}
