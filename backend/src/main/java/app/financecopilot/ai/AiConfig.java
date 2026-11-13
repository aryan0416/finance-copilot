package app.financecopilot.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /**
     * Wires the active Spring AI chat model behind {@link LlmClient}. With {@code AI_PROVIDER=none} (or no
     * model configured) every call fails fast with {@link LlmUnavailableException}, and the app keeps
     * working: categorisation falls back to "Uncategorised", Ask-AI returns 503.
     */
    @Bean
    LlmClient llmClient(ObjectProvider<ChatModel> chatModel,
                        @Value("${spring.ai.model.chat:none}") String provider,
                        @Value("${app.ai.retry.max-attempts:4}") int maxAttempts,
                        @Value("${app.ai.retry.initial-backoff:1s}") Duration initialBackoff,
                        @Value("${app.ai.retry.max-backoff:10s}") Duration maxBackoff) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            log.warn("No chat model configured (provider '{}'); AI features disabled", provider);
            return request -> {
                throw new LlmUnavailableException("AI provider is not configured");
            };
        }
        log.info("LLM provider: {} ({})", provider, model.getClass().getSimpleName());
        return new RetryingLlmClient(new SpringAiLlmClient(model), maxAttempts, initialBackoff, maxBackoff,
                d -> Thread.sleep(d.toMillis()));
    }
}
