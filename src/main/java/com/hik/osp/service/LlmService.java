package com.hik.osp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hik.osp.entity.ModelConfigEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LlmService {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Call an OpenAI-compatible chat completion API.
     *
     * @param config   the model configuration (baseUrl, apiKey, modelName)
     * @param messages list of {role, content} maps
     * @return the response content string (JSON)
     */
    public String chatCompletion(ModelConfigEntity config, List<Map<String, String>> messages) {
        String url = config.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.getModelName());

        ArrayNode msgArray = body.putArray("messages");
        for (Map<String, String> msg : messages) {
            ObjectNode msgNode = msgArray.addObject();
            msgNode.put("role", msg.getOrDefault("role", "user"));
            msgNode.put("content", msg.getOrDefault("content", ""));
        }

        // Request JSON response format
        body.putObject("response_format").put("type", "json_object");

        HttpEntity<String> request;
        try {
            request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize LLM request", e);
        }

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(url, request, String.class);
        } catch (Exception e) {
            throw new RuntimeException("LLM API call failed: " + e.getMessage(), e);
        }

        String rawBody = response.getBody();
        if (rawBody == null || rawBody.isBlank()) {
            throw new RuntimeException("LLM returned empty response");
        }

        JsonNode responseBody;
        try {
            responseBody = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            String snippet = rawBody.length() > 200 ? rawBody.substring(0, 200) + "..." : rawBody;
            throw new RuntimeException("LLM returned non-JSON response (HTTP " + response.getStatusCode()
                    + "): " + snippet, e);
        }

        // Navigate: choices[0].message.content
        JsonNode choices = responseBody.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new RuntimeException("LLM returned no choices: " + responseBody);
        }

        JsonNode message = choices.get(0).get("message");
        if (message == null) {
            throw new RuntimeException("LLM response missing message field");
        }

        JsonNode content = message.get("content");
        if (content == null || content.isNull()) {
            throw new RuntimeException("LLM response content is null");
        }

        return content.asText();
    }
}
