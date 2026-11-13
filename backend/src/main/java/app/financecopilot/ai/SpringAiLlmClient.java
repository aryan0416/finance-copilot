package app.financecopilot.ai;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/** Adapter over whichever Spring AI {@link ChatModel} is active (Gemini via google-genai, or Ollama). */
public class SpringAiLlmClient implements LlmClient {

    private final ChatModel chatModel;

    public SpringAiLlmClient(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        long start = System.nanoTime();
        ChatResponse response = chatModel.call(new Prompt(List.of(
                new SystemMessage(request.systemPrompt()), new UserMessage(request.userPrompt()))));
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new LlmUnavailableException("Model returned no output");
        }
        String text = response.getResult().getOutput().getText();
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        Integer promptTokens = usage == null ? null : usage.getPromptTokens();
        Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
        return new LlmResponse(text == null ? "" : text, promptTokens, completionTokens, latencyMs);
    }
}
