package com.hik.osp.dto.request;

import lombok.Data;

@Data
public class AgentUpdateRequest {
    private String name;
    private String description;
    private String systemPrompt;
    private String ontologyId;
    private String modelConfigId;
}
