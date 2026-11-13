package app.financecopilot.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Decorator adding retry with exponential backoff and jitter for rate limits (HTTP 429 /
 * RESOURCE_EXHAUSTED, common on the Gemini free tier) and transient 5xx / timeouts. Non-retryable errors
 * and exhausted retries surface as {@link LlmUnavailableException}, which callers turn into a graceful
 * fallback (e.g. "Uncategorised") rather than a failed request.
 */
public class RetryingLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(RetryingLlmClient.class);

    /** Abstracted so tests don't actually sleep. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final LlmClient delegate;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final Sleeper sleeper;

    public RetryingLlmClient(LlmClient delegate, int maxAttempts, Duration initialBackoff, Duration maxBackoff,
                             Sleeper sleeper) {
        this.delegate = delegate;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.sleeper = sleeper;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return delegate.complete(request);
            } catch (LlmUnavailableException e) {
                throw e;
            } catch (RuntimeException e) {
                last = e;
                if (!isRetryable(e) || attempt == maxAttempts) {
                    break;
                }
                Duration backoff = backoffFor(attempt);
                log.warn("LLM call failed ({}), retry {}/{} in {} ms", summary(e), attempt, maxAttempts - 1,
                        backoff.toMillis());
                try {
                    sleeper.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LlmUnavailableException("Interrupted while backing off", ie);
                }
            }
        }
        throw new LlmUnavailableException("LLM unavailable after retries: " + summary(last), last);
    }

    /** initial * 2^(attempt-1), capped, with ±20% jitter so concurrent clients don't retry in lockstep. */
    Duration backoffFor(int attempt) {
        long base = initialBackoff.toMillis() * (1L << Math.min(attempt - 1, 20));
        long capped = Math.min(base, maxBackoff.toMillis());
        double jitter = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4;
        return Duration.ofMillis((long) (capped * jitter));
    }

    static boolean isRetryable(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            String text = (t.getClass().getSimpleName() + " " + t.getMessage()).toLowerCase(Locale.ROOT);
            if (text.contains("429") || text.contains("resource_exhausted") || text.contains("rate limit")
                    || text.contains("too many requests")
                    || text.contains("503") || text.contains("unavailable")
                    || text.contains("timeout") || text.contains("timed out")
                    || t instanceof java.io.IOException) {
                return true;
            }
            if (t.getClass().getSimpleName().equals("TransientAiException")) {
                return true;
            }
        }
        return false;
    }

    private static String summary(Throwable e) {
        if (e == null) {
            return "unknown error";
        }
        String message = e.getMessage() == null ? "" : e.getMessage();
        return e.getClass().getSimpleName() + ": " + (message.length() > 160 ? message.substring(0, 160) + "…" : message);
    }
}
