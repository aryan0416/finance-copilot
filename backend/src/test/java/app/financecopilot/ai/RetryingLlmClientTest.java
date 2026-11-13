package app.financecopilot.ai;

import app.financecopilot.ai.LlmClient.LlmRequest;
import app.financecopilot.ai.LlmClient.LlmResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryingLlmClientTest {

    private static final LlmRequest REQUEST = new LlmRequest("system", "user");
    private static final LlmResponse OK = new LlmResponse("{}", 10, 5, 42);

    private final List<Duration> sleeps = new ArrayList<>();

    private RetryingLlmClient client(LlmClient delegate) {
        return new RetryingLlmClient(delegate, 4, Duration.ofSeconds(1), Duration.ofSeconds(10), sleeps::add);
    }

    @Test
    void retriesRateLimitWithExponentialBackoffThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        LlmClient flaky = request -> {
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException("429 RESOURCE_EXHAUSTED: quota exceeded for model");
            }
            return OK;
        };

        assertThat(client(flaky).complete(REQUEST)).isEqualTo(OK);
        assertThat(calls).hasValue(3);
        assertThat(sleeps).hasSize(2);
        // ~1s then ~2s, each with ±20% jitter
        assertThat(sleeps.get(0).toMillis()).isBetween(800L, 1200L);
        assertThat(sleeps.get(1).toMillis()).isBetween(1600L, 2400L);
    }

    @Test
    void givesUpAfterMaxAttempts() {
        AtomicInteger calls = new AtomicInteger();
        LlmClient alwaysLimited = request -> {
            calls.incrementAndGet();
            throw new RuntimeException("HTTP 429 Too Many Requests");
        };

        assertThatThrownBy(() -> client(alwaysLimited).complete(REQUEST))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("after retries");
        assertThat(calls).hasValue(4);
        assertThat(sleeps).hasSize(3);
    }

    @Test
    void doesNotRetryNonTransientErrors() {
        AtomicInteger calls = new AtomicInteger();
        LlmClient badRequest = request -> {
            calls.incrementAndGet();
            throw new IllegalArgumentException("400 INVALID_ARGUMENT: API key not valid");
        };

        assertThatThrownBy(() -> client(badRequest).complete(REQUEST)).isInstanceOf(LlmUnavailableException.class);
        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void detectsRateLimitInCauseChain() {
        RuntimeException wrapped = new RuntimeException("Chat call failed",
                new IllegalStateException("status 429 from upstream"));
        assertThat(RetryingLlmClient.isRetryable(wrapped)).isTrue();
    }

    @Test
    void backoffIsCapped() {
        RetryingLlmClient c = client(request -> OK);
        assertThat(c.backoffFor(10).toMillis()).isLessThanOrEqualTo(12_000L); // 10s cap + 20% jitter
    }
}
