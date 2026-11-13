package app.financecopilot;

import app.financecopilot.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Proves one user can never see or change another user's data, through any endpoint, including Ask-AI. */
class UserIsolationIntegrationTest extends IntegrationTest {

    @Test
    void secondUserSeesNoneOfTheFirstUsersData() throws Exception {
        String alice = registerUser();
        String bob = registerUser();
        upload(alice, "hdfc-style-savings-may-oct-2026.csv").andExpect(status().isCreated());

        assertThat(getJson(alice, "/api/transactions").get("totalElements").asLong()).isEqualTo(502);
        assertThat(getJson(bob, "/api/transactions").get("totalElements").asLong()).isZero();
        assertThat(getJson(bob, "/api/statements")).isEmpty();
        assertThat(getJson(bob, "/api/subscriptions")).isEmpty();
        assertThat(getJson(bob, "/api/dashboard").get("availableMonths")).isEmpty();
    }

    @Test
    void cannotRecategoriseAnotherUsersTransaction() throws Exception {
        String alice = registerUser();
        String bob = registerUser();
        upload(alice, "hdfc-style-savings-may-oct-2026.csv").andExpect(status().isCreated());
        JsonNode aliceTxn = getJson(alice, "/api/transactions?size=1").get("content").get(0);
        long categoryId = getJson(alice, "/api/categories").get(0).get("id").asLong();

        mvc.perform(patch("/api/transactions/" + aliceTxn.get("id").asLong() + "/category")
                        .header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"createRule\":true}"))
                .andExpect(status().isNotFound()); // indistinguishable from "does not exist"

        JsonNode unchanged = getJson(alice, "/api/transactions?size=1").get("content").get(0);
        assertThat(unchanged.get("category")).isEqualTo(aliceTxn.get("category"));
    }

    @Test
    void askAiOnlyEverQueriesTheCallersRows() throws Exception {
        String alice = registerUser();
        String bob = registerUser();
        upload(alice, "hdfc-style-savings-may-oct-2026.csv").andExpect(status().isCreated());
        // Even an "all rows" query from the model is scoped to the caller by the executor's CTE.
        LLM_ANSWER.set(prompt -> "{\"sql\":\"SELECT COUNT(*) AS n FROM my_transactions\",\"chartType\":\"number\",\"explanation\":\"x\"}");

        mvc.perform(post("/api/ask").header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"How many transactions?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0][0]").value(0));
        mvc.perform(post("/api/ask").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"How many transactions?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0][0]").value(502));
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        mvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }
}
