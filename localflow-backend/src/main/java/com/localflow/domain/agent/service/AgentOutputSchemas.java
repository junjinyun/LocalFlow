package com.localflow.domain.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.localflow.domain.provider.config.AiProviderProperties;
import org.springframework.stereotype.Component;

@Component
public class AgentOutputSchemas {
    private final JsonNode taskDecomposition;
    private final JsonNode filePlan;

    public AgentOutputSchemas(ObjectMapper objectMapper, AiProviderProperties properties) {
        this.taskDecomposition = parse(objectMapper, """
                {
                  "type":"object",
                  "additionalProperties":false,
                  "required":["summary","tasks"],
                  "properties":{
                    "summary":{"type":"string","minLength":1},
                    "tasks":{
                      "type":"array","minItems":1,"maxItems":5,
                      "items":{
                        "type":"object",
                        "additionalProperties":false,
                        "required":["id","instruction","dependsOn","suggestedTags","acceptanceCriteria"],
                        "properties":{
                          "id":{"type":"string","minLength":1},
                          "instruction":{"type":"string","minLength":1},
                          "dependsOn":{"type":"array","items":{"type":"string"}},
                          "suggestedTags":{"type":"array","items":{"type":"string"}},
                          "acceptanceCriteria":{"type":"array","items":{"type":"string"}}
                        }
                      }
                    }
                  }
                }
                """);
        ObjectNode plan = (ObjectNode) parse(objectMapper, """
                {
                  "type":"object",
                  "additionalProperties":false,
                  "required":["summary","response","operations"],
                  "properties":{
                    "summary":{"type":"string","minLength":1},
                    "response":{"type":"string","minLength":1},
                    "operations":{
                      "type":"array",
                      "items":{
                        "type":"object",
                        "additionalProperties":false,
                        "required":["action","path","destinationPath","content"],
                        "properties":{
                          "action":{"type":"string","enum":["CREATE","UPDATE","MOVE","DELETE"]},
                          "path":{"type":"string","minLength":1},
                          "destinationPath":{"type":["string","null"]},
                          "content":{"type":["string","null"]}
                        }
                      }
                    }
                  }
                }
                """);
        ((ObjectNode) plan.path("properties").path("operations"))
                .put("maxItems", properties.maxOperations());
        this.filePlan = plan;
    }

    public JsonNode taskDecomposition() {
        return taskDecomposition.deepCopy();
    }

    public JsonNode filePlan() {
        return filePlan.deepCopy();
    }

    private JsonNode parse(ObjectMapper objectMapper, String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException("에이전트 구조화 출력 스키마를 초기화하지 못했습니다.", exception);
        }
    }
}
