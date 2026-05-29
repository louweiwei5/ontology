package com.hik.osp.service;

import com.hik.osp.dto.request.AgentCreateRequest;
import com.hik.osp.dto.request.AgentUpdateRequest;
import com.hik.osp.dto.response.AgentDetailResponse;
import com.hik.osp.dto.response.AgentListItem;
import com.hik.osp.entity.AgentEntity;
import com.hik.osp.entity.OntologyEntity;
import com.hik.osp.exception.ResourceNotFoundException;
import com.hik.osp.repository.AgentRepository;
import com.hik.osp.repository.OntologyRepository;
import com.hik.osp.entity.ModelConfigEntity;
import com.hik.osp.repository.ModelConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;
    private final OntologyRepository ontologyRepository;
    private final ModelConfigRepository modelConfigRepository;

    public List<AgentListItem> listAll() {
        return agentRepository.findAll().stream()
                .map(this::toListItem)
                .collect(Collectors.toList());
    }

    public AgentDetailResponse getById(String id) {
        AgentEntity entity = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", id));
        return toDetailResponse(entity);
    }

    public AgentDetailResponse create(AgentCreateRequest req) {
        AgentEntity entity = new AgentEntity();
        entity.setName(req.getName());
        entity.setDescription(req.getDescription());
        entity.setSystemPrompt(req.getSystemPrompt());
        entity.setOntologyId(req.getOntologyId());
        entity.setModelConfigId(req.getModelConfigId());
        entity = agentRepository.save(entity);
        return toDetailResponse(entity);
    }

    public AgentDetailResponse update(String id, AgentUpdateRequest req) {
        AgentEntity entity = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", id));
        if (req.getName() != null) entity.setName(req.getName());
        if (req.getDescription() != null) entity.setDescription(req.getDescription());
        if (req.getSystemPrompt() != null) entity.setSystemPrompt(req.getSystemPrompt());
        if (req.getOntologyId() != null) entity.setOntologyId(req.getOntologyId());
        if (req.getModelConfigId() != null) entity.setModelConfigId(req.getModelConfigId());
        entity = agentRepository.save(entity);
        return toDetailResponse(entity);
    }

    public void delete(String id) {
        if (!agentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Agent", id);
        }
        agentRepository.deleteById(id);
    }

    private AgentListItem toListItem(AgentEntity entity) {
        String ontologyName = null;
        if (entity.getOntologyId() != null) {
            ontologyName = ontologyRepository.findById(entity.getOntologyId())
                    .map(OntologyEntity::getName).orElse(null);
        }
        String modelConfigName = null;
        if (entity.getModelConfigId() != null) {
            modelConfigName = modelConfigRepository.findById(entity.getModelConfigId())
                    .map(ModelConfigEntity::getName).orElse(null);
        }
        return AgentListItem.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .ontologyId(entity.getOntologyId())
                .ontologyName(ontologyName)
                .modelConfigId(entity.getModelConfigId())
                .modelConfigName(modelConfigName)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AgentDetailResponse toDetailResponse(AgentEntity entity) {
        String ontologyName = null;
        if (entity.getOntologyId() != null) {
            ontologyName = ontologyRepository.findById(entity.getOntologyId())
                    .map(OntologyEntity::getName).orElse(null);
        }
        String modelConfigName = null;
        if (entity.getModelConfigId() != null) {
            modelConfigName = modelConfigRepository.findById(entity.getModelConfigId())
                    .map(ModelConfigEntity::getName).orElse(null);
        }
        return AgentDetailResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .systemPrompt(entity.getSystemPrompt())
                .ontologyId(entity.getOntologyId())
                .ontologyName(ontologyName)
                .modelConfigId(entity.getModelConfigId())
                .modelConfigName(modelConfigName)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
