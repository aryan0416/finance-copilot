package app.financecopilot.support;

import app.financecopilot.ai.LlmClient;
import app.financecopilot.ai.LlmClient.LlmResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for integration tests: the real Spring context, Flyway migrations and SQL against a throwaway
 * Postgres (Testcontainers), with the LLM replaced by a programmable stub. Skipped when Docker is absent.
 * The container is a JVM-wide singleton started by Spring Boot (@ServiceConnection), so the cached Spring
 * context stays valid across test classes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Import(IntegrationTest.StubLlmConfig.class)
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** What the stub LLM answers; tests swap it per scenario. */
    protected static final AtomicReference<Function<String, String>> LLM_ANSWER = new AtomicReference<>();

    @TestConfiguration
    public static class StubLlmConfig {
        @Bean
        @Primary
        LlmClient stubLlm() {
            return request -> new LlmResponse(LLM_ANSWER.get().apply(request.userPrompt()), 10, 10, 1);
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper mapper;

    @BeforeEach
    void defaultLlmAnswer() {
        LLM_ANSWER.set(prompt -> "{\"results\":[]}"); // AI knows nothing unless a test says otherwise
    }

    /** Registers a fresh user and returns its bearer token. */
    protected String registerUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("accessToken").asText();
    }

    protected ResultActions upload(String token, String sampleFile) throws Exception {
        byte[] content = Files.readAllBytes(Path.of("../sample-data", sampleFile));
        return mvc.perform(multipart("/api/statements")
                .file(new MockMultipartFile("file", sampleFile, "text/csv", content))
                .header("Authorization", "Bearer " + token));
    }

    protected JsonNode getJson(String token, String url) throws Exception {
        String body = mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }
}
