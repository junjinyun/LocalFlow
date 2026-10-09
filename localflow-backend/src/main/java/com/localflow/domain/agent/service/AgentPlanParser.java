package com.localflow.domain.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.agent.domain.AgentPlan;
import com.localflow.domain.agent.domain.FileOperationAction;
import com.localflow.domain.agent.domain.FileOperationPlan;
import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AgentPlanParser {
    private final ObjectMapper objectMapper;
    private final AiProviderProperties properties;

    public AgentPlanParser(ObjectMapper objectMapper, AiProviderProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public AgentPlan parse(String value) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(value));
            List<FileOperationPlan> operations = new ArrayList<>();
            JsonNode operationNodes = root.path("operations");
            if (!operationNodes.isArray()) throw invalidPlan();
            if (operationNodes.size() > properties.maxOperations()) throw invalidPlan();
            for (JsonNode node : operationNodes) {
                FileOperationAction action = FileOperationAction.valueOf(required(node, "action").toUpperCase());
                String path = required(node, "path");
                String destination = optional(node, "destinationPath");
                String content = optional(node, "content");
                if (action == FileOperationAction.MOVE && (destination == null || destination.isBlank())) {
                    throw invalidPlan();
                }
                if ((action == FileOperationAction.CREATE || action == FileOperationAction.UPDATE)
                        && content == null) {
                    throw invalidPlan();
                }
                operations.add(new FileOperationPlan(action, path, destination, content));
            }
            return new AgentPlan(root.path("summary").asText("AI 작업 계획"),
                    root.path("response").asText("작업 계획을 생성했습니다."), operations);
        } catch (CustomException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidPlan();
        }
    }

    public String toJson(AgentPlan plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (Exception exception) {
            throw new IllegalStateException("작업 계획을 저장하지 못했습니다.", exception);
        }
    }

    private String extractJson(String value) {
        if (value == null) throw invalidPlan();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end < start) throw invalidPlan();
        return value.substring(start, end + 1);
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) throw invalidPlan();
        return value;
    }

    private String optional(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private CustomException invalidPlan() {
        return new CustomException(ErrorCode.INVALID_AI_PLAN);
    }
}
