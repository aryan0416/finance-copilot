package app.financecopilot.ai;

/** The model could not produce an answer (not configured, rate-limited past retries, or malformed output). */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message) {
        super(message);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
