package app.financecopilot;

import app.financecopilot.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end import: parsing, dedupe, the categorisation pipeline (with cache) and subscription detection. */
class StatementImportIntegrationTest extends IntegrationTest {

    private static final Pattern ID = Pattern.compile("\\{\"id\":(\\d+),\"merchant\":\"[^\"]*\"");

    @Autowired
    JdbcTemplate jdbc;

    /** Stub model: every merchant it is asked about is "Groceries". */
    private void llmAnswersGroceries() {
        LLM_ANSWER.set(prompt -> {
            List<String> results = new ArrayList<>();
            Matcher m = ID.matcher(prompt);
            while (m.find()) {
                results.add("{\"id\":" + m.group(1) + ",\"category\":\"Groceries\"}");
            }
            return "{\"results\":[" + String.join(",", results) + "]}";
        });
    }

    @Test
    void importsCategorisesAndDetectsSubscriptions() throws Exception {
        jdbc.update("DELETE FROM merchant_category_cache");
        llmAnswersGroceries();
        String token = registerUser();

        upload(token, "hdfc-style-savings-may-oct-2026.csv")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imported").value(502))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.categorization.byRule").value(348))
                .andExpect(jsonPath("$.categorization.byAi").value(154))
                .andExpect(jsonPath("$.categorization.uncategorised").value(0));

        JsonNode subscriptions = getJson(token, "/api/subscriptions");
        assertThat(subscriptions).extracting(s -> s.get("merchant").asText())
                .contains("Netflix", "Spotify", "Jio", "Daily Fresh Dairy")
                .doesNotContain("BESCOM"); // variable utility bill is not a fixed subscription
    }

    @Test
    void secondUserBenefitsFromSharedCacheWithoutAnyAiCall() throws Exception {
        jdbc.update("DELETE FROM merchant_category_cache");
        llmAnswersGroceries();
        upload(registerUser(), "hdfc-style-savings-may-oct-2026.csv").andExpect(status().isCreated());

        LLM_ANSWER.set(prompt -> {
            throw new AssertionError("LLM must not be called when the cache knows every merchant");
        });
        upload(registerUser(), "hdfc-style-savings-may-oct-2026.csv")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categorization.byCache").value(154))
                .andExpect(jsonPath("$.categorization.byAi").value(0))
                .andExpect(jsonPath("$.categorization.withoutAiPercent").value(100.0));
    }

    @Test
    void reuploadOfTheIdenticalFileIsRejected() throws Exception {
        String token = registerUser();
        upload(token, "credit-card-sep-oct-2026.csv").andExpect(status().isCreated());
        upload(token, "credit-card-sep-oct-2026.csv").andExpect(status().isConflict());
    }

    @Test
    void aiOutageNeverFailsTheUpload() throws Exception {
        jdbc.update("DELETE FROM merchant_category_cache");
        LLM_ANSWER.set(prompt -> {
            throw new RuntimeException("429 RESOURCE_EXHAUSTED");
        });

        upload(registerUser(), "hdfc-style-savings-may-oct-2026.csv")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imported").value(502))
                .andExpect(jsonPath("$.categorization.uncategorised").value(154));
    }

    @Test
    void parsesPdfStatements() throws Exception {
        upload(registerUser(), "savings-statement-nov-2026.pdf")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileType").value("PDF"))
                .andExpect(jsonPath("$.imported").value(32))
                .andExpect(jsonPath("$.failed").value(0));
    }
}
