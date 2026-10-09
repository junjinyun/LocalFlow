package com.localflow.domain.provider.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.DecisionRequest;
import com.localflow.domain.provider.domain.DecisionResult;
import com.localflow.domain.provider.domain.TaskScope;
import com.localflow.domain.provider.exception.ProviderCallException;
import com.localflow.domain.provider.port.DecisionProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class JevDecisionProvider implements DecisionProvider {
    private static final Map<String, String> ACTIONS = Map.of(
            "ANSWER_ONLY", "Answer without reading or changing project files.",
            "INSPECT_FILES", "Read or analyze existing project files without changing them.",
            "MODIFY_FILES", "Change or move one or more existing files.",
            "CREATE_FILES", "Create one or more new files.",
            "DELETE_FILES", "Delete one or more existing files.",
            "MIXED", "Use multiple kinds of file mutation in the same task.");

    private final AiProviderProperties.Jev properties;
    private final ProviderHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public JevDecisionProvider(AiProviderProperties properties,
                               ProviderHttpClient httpClient,
                               ObjectMapper objectMapper) {
        this.properties = properties.jev();
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override public AiProviderType providerType() { return AiProviderType.JEV; }
    @Override public boolean available() { return properties != null && properties.configured(); }
    @Override public String model() { return properties == null ? null : properties.model(); }

    @Override
    public DecisionResult decide(DecisionRequest request) {
        if (!available()) {
            throw new ProviderCallException(providerType(), "OPENROUTER_API_KEY가 설정되지 않았습니다.");
        }

        Map<String, String> targets = targets(request.candidateFiles());
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.model());
        body.put("state", request.state());
        ObjectNode questions = body.putObject("questions");
        choiceQuestion(questions, "action", "Choose the primary operation needed for the user request.", ACTIONS);
        choiceQuestion(questions, "target", "Choose the most relevant existing file, or none.", targets);
        scoreQuestion(questions, "risk", "Rate the risk of applying the requested file changes.",
                List.of("LOW", "MEDIUM", "HIGH"));
        ObjectNode approval = questions.putObject("needs_human");
        approval.put("type", "noul");
        approval.put("instructions", "Should a human explicitly approve before applying the requested file changes?");
        Map<String, String> tags = tags(request.candidateTags());
        for (int taskIndex = 0; taskIndex < Math.min(request.taskUnits().size(), 5); taskIndex++) {
            ObjectNode valid = questions.putObject(taskValidQuestion(taskIndex));
            valid.put("type", "noul");
            valid.put("instructions", "Is task '" + request.taskUnits().get(taskIndex)
                    + "' a necessary, independently actionable part of the user's request?");
            if (tags.size() > 1) {
                for (int slot = 0; slot < 2; slot++) {
                    choiceQuestion(questions, taskTagQuestion(taskIndex, slot),
                            "For task '" + request.taskUnits().get(taskIndex)
                                    + "', choose a relevant project file tag, or none. Prefer a different tag in each slot.",
                            tags);
                }
            }
        }

        JsonNode response = httpClient.post(providerType(), endpoint(), body,
                Map.of("Authorization", "Bearer " + properties.apiKey()));
        JsonNode answers = response.path("answers");
        String action = answerChoice(answers.path("action"), "ANSWER_ONLY");
        String targetKey = answerChoice(answers.path("target"), "none");
        double riskScore = answers.path("risk").path("score").asDouble(0.0);
        String risk = riskScore >= 1.5 ? "HIGH" : riskScore >= 0.75 ? "MEDIUM" : "LOW";
        boolean needsApproval = answers.path("needs_human").path("noul").asDouble(0.0) >= 0.5;
        if (!ACTIONS.containsKey(action)) action = "ANSWER_ONLY";
        String targetPath = "none".equals(targetKey) ? null : targets.get(targetKey);
        List<TaskScope> taskScopes = new ArrayList<>();
        Set<String> selectedTags = new LinkedHashSet<>();
        for (int taskIndex = 0; taskIndex < request.taskUnits().size(); taskIndex++) {
            JsonNode validAnswer = answers.path(taskValidQuestion(taskIndex));
            double validity = validAnswer.isMissingNode() ? 1.0 : validAnswer.path("noul").asDouble(0.0);
            if (validity < 0.55) continue;
            Set<String> taskTags = new LinkedHashSet<>();
            for (int slot = 0; slot < 2; slot++) {
                String tagKey = answerChoice(answers.path(taskTagQuestion(taskIndex, slot)), "none");
                String tag = tags.get(tagKey);
                if (tag != null && !"none".equals(tagKey)) taskTags.add(tag);
            }
            selectedTags.addAll(taskTags);
            taskScopes.add(new TaskScope(request.taskUnits().get(taskIndex), List.copyOf(taskTags)));
        }
        return new DecisionResult(action, targetPath, List.copyOf(selectedTags), taskScopes,
                risk, needsApproval,
                properties.model(), response.toString());
    }

    private Map<String, String> targets(List<String> files) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("none", "No specific existing file is the primary target.");
        if (files != null) {
            for (int index = 0; index < Math.min(files.size(), 18); index++) {
                values.put("f" + index, files.get(index));
            }
        }
        return values;
    }

    private Map<String, String> tags(List<String> candidateTags) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("none", "No additional file tag is relevant for this task.");
        for (int index = 0; index < Math.min(candidateTags.size(), 24); index++) {
            values.put("t" + index, candidateTags.get(index));
        }
        return values;
    }

    private String taskTagQuestion(int taskIndex, int slot) {
        return "task_" + taskIndex + "_tag_" + slot;
    }

    private String taskValidQuestion(int taskIndex) {
        return "task_" + taskIndex + "_valid";
    }

    private void choiceQuestion(ObjectNode questions, String id, String instructions,
                                Map<String, String> options) {
        ObjectNode question = questions.putObject(id);
        question.put("type", "choice");
        question.put("instructions", instructions);
        ObjectNode criteria = question.putObject("criteria");
        options.forEach(criteria::put);
    }

    private void scoreQuestion(ObjectNode questions, String id, String instructions, List<String> scores) {
        ObjectNode question = questions.putObject(id);
        question.put("type", "score");
        question.put("instructions", instructions);
        var values = question.putArray("criteria");
        scores.forEach(values::add);
    }

    private String answerChoice(JsonNode answer, String fallback) {
        String value = answer.path("choice").asText();
        return value.isBlank() ? fallback : value;
    }

    private String endpoint() {
        String baseUrl = properties.baseUrl().replaceAll("/+$", "");
        if (baseUrl.endsWith("/decisions")) return baseUrl;
        if (baseUrl.endsWith("/systemone")) return baseUrl + "/";
        return baseUrl + "/systemone/";
    }
}
