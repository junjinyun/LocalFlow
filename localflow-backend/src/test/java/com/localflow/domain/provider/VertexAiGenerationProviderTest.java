package com.localflow.domain.provider;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.domain.provider.service.ProviderHttpClient;
import com.localflow.domain.provider.service.VertexAiGenerationProvider;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class VertexAiGenerationProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void reportsAvailableWhenWholeServiceAccountJsonIsBase64Encoded() throws Exception {
        String credentialsBase64 = serviceAccountJsonBase64();
        VertexAiGenerationProvider provider = provider(credentialsBase64);

        assertThat(provider.available()).isTrue();
    }

    @Test
    void reportsUnavailableWhenBase64ValueIsInvalid() {
        VertexAiGenerationProvider provider = provider("not-a-service-account-json");

        assertThat(provider.available()).isFalse();
    }

    private VertexAiGenerationProvider provider(String credentialsBase64) {
        AiProviderProperties properties = new AiProviderProperties(
                60_000,
                20,
                null,
                null,
                null,
                new AiProviderProperties.VertexAi(
                        credentialsBase64,
                        "test-project",
                        "us-central1",
                        "gemini-test",
                        java.util.List.of("gemini-test")));
        return new VertexAiGenerationProvider(
                properties,
                new ProviderHttpClient(objectMapper),
                objectMapper);
    }

    private String serviceAccountJsonBase64() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String privateKey = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(UTF_8))
                .encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        ObjectNode json = objectMapper.createObjectNode();
        json.put("type", "service_account");
        json.put("project_id", "test-project");
        json.put("private_key_id", "test-key-id");
        json.put("private_key", privateKey);
        json.put("client_email", "localflow@test-project.iam.gserviceaccount.com");
        json.put("client_id", "1234567890");
        json.put("auth_uri", "https://accounts.google.com/o/oauth2/auth");
        json.put("token_uri", "https://oauth2.googleapis.com/token");
        json.put("auth_provider_x509_cert_url", "https://www.googleapis.com/oauth2/v1/certs");
        json.put("client_x509_cert_url",
                "https://www.googleapis.com/robot/v1/metadata/x509/localflow%40test-project.iam.gserviceaccount.com");

        return Base64.getEncoder().encodeToString(
                objectMapper.writeValueAsString(json).getBytes(UTF_8));
    }
}
