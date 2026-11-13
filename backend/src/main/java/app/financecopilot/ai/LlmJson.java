package app.financecopilot.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Extracts the JSON object from model output, tolerating ```json fences and leading prose. */
public final class LlmJson {

    private LlmJson() {
    }

    public static JsonNode parseObject(ObjectMapper mapper, String text) {
        if (text == null) {
            throw new LlmUnavailableException("Empty model output");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new LlmUnavailableException("Model output contained no JSON object");
        }
        try {
            return mapper.readTree(text.substring(start, end + 1));
        } catch (JsonProcessingException e) {
            throw new LlmUnavailableException("Model output was not valid JSON", e);
        }
    }
}
