package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.agent.service.AgentOutputSchemas;
import com.localflow.domain.provider.config.AiProviderProperties;
import org.junit.jupiter.api.Test;

class AgentOutputSchemasTest {
    private final AgentOutputSchemas schemas = new AgentOutputSchemas(
            new ObjectMapper(), new AiProviderProperties(60_000, 2, null, null, null, null));

    @Test
    void taskDecompositionSchemaRequiresStableTaskFields() {
        JsonNode schema = schemas.taskDecomposition();
        JsonNode taskSchema = schema.path("properties").path("tasks").path("items");

        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("required")).extracting(JsonNode::asText)
                .containsExactly("summary", "tasks");
        assertThat(schema.path("properties").path("tasks").path("maxItems").asInt()).isEqualTo(5);
        assertThat(taskSchema.path("required")).extracting(JsonNode::asText)
                .containsExactly("id", "instruction", "dependsOn", "suggestedTags", "acceptanceCriteria");
    }

    @Test
    void filePlanSchemaUsesConfiguredOperationLimitAndActions() {
        JsonNode operations = schemas.filePlan().path("properties").path("operations");
        JsonNode operation = operations.path("items");

        assertThat(operations.path("maxItems").asInt()).isEqualTo(2);
        assertThat(operation.path("properties").path("action").path("enum"))
                .extracting(JsonNode::asText)
                .containsExactly("CREATE", "UPDATE", "MOVE", "DELETE");
        assertThat(operation.path("required")).extracting(JsonNode::asText)
                .containsExactly("action", "path", "destinationPath", "content");
    }
}
