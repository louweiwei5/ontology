package com.hik.osp.service;

import com.hik.osp.dto.request.ModelConfigCreateRequest;
import com.hik.osp.dto.request.ModelConfigUpdateRequest;
import com.hik.osp.dto.response.ModelConfigResponse;
import com.hik.osp.entity.ModelConfigEntity;
import com.hik.osp.exception.ResourceNotFoundException;
import com.hik.osp.repository.ModelConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModelConfigService {

    private final ModelConfigRepository repository;

    public List<ModelConfigResponse> listAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ModelConfigResponse getById(String id) {
        ModelConfigEntity entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ModelConfig", id));
        return toResponse(entity);
    }

    public ModelConfigResponse create(ModelConfigCreateRequest req) {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setName(req.getName());
        entity.setProvider(req.getProvider());
        entity.setBaseUrl(req.getBaseUrl());
        entity.setApiKey(req.getApiKey());
        entity.setModelName(req.getModelName());
        entity = repository.save(entity);
        return toResponse(entity);
    }

    public ModelConfigResponse update(String id, ModelConfigUpdateRequest req) {
        ModelConfigEntity entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ModelConfig", id));
        if (req.getName() != null) entity.setName(req.getName());
        if (req.getProvider() != null) entity.setProvider(req.getProvider());
        if (req.getBaseUrl() != null) entity.setBaseUrl(req.getBaseUrl());
        if (req.getApiKey() != null) entity.setApiKey(req.getApiKey());
        if (req.getModelName() != null) entity.setModelName(req.getModelName());
        entity = repository.save(entity);
        return toResponse(entity);
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("ModelConfig", id);
        }
        repository.deleteById(id);
    }

    private ModelConfigResponse toResponse(ModelConfigEntity entity) {
        String key = entity.getApiKey();
        String masked = key != null && key.length() > 4
                ? key.substring(0, Math.min(6, key.length() / 2)) + "****" + key.substring(key.length() - 4)
                : "****";
        return ModelConfigResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .provider(entity.getProvider())
                .baseUrl(entity.getBaseUrl())
                .apiKeyMasked(masked)
                .modelName(entity.getModelName())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
