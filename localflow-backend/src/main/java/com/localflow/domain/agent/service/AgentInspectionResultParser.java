package com.localflow.domain.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.agent.domain.AgentInspectionResult;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AgentInspectionResultParser {
    private final ObjectMapper objectMapper;

    public AgentInspectionResultParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentInspectionResult parse(String value) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(value));
            String summary = required(root, "summary");
            String response = required(root, "response");
            List<String> findings = new ArrayList<>();
            JsonNode nodes = root.path("findings");
            if (!nodes.isMissingNode() && !nodes.isArray()) throw invalidResult();
            if (nodes.isArray()) {
                for (JsonNode node : nodes) {
                    if (!node.isTextual() || node.asText().isBlank()) throw invalidResult();
                    findings.add(node.asText());
                }
            }
            return new AgentInspectionResult(summary, response, findings);
        } catch (CustomException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidResult();
        }
    }

    private String extractJson(String value) {
        if (value == null) throw invalidResult();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end < start) throw invalidResult();
        return value.substring(start, end + 1);
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) throw invalidResult();
        return value;
    }

    private CustomException invalidResult() {
        return new CustomException(ErrorCode.INVALID_AI_PLAN);
    }
}
