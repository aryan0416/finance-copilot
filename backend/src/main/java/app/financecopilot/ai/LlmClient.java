package app.financecopilot.ai;

/**
 * Provider-agnostic LLM port. Business code depends only on this; the Spring AI adapter behind it is
 * Gemini or Ollama depending on configuration ({@code AI_PROVIDER}), and is wrapped with retry/backoff.
 */
public interface LlmClient {

    LlmResponse complete(LlmRequest request);

    record LlmRequest(String systemPrompt, String userPrompt) {
    }

    /** Token counts are null when the provider doesn't report usage. */
    record LlmResponse(String text, Integer promptTokens, Integer completionTokens, long latencyMs) {
    }
}
