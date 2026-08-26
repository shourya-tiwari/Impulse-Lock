package com.impulselock.impulselock.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impulselock.impulselock.repository.RoleRepository;
import com.impulselock.impulselock.repository.UserRepository;
import com.impulselock.impulselock.support.AbstractIntegrationTest;
import com.impulselock.impulselock.support.MockMvcAuthHelper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * New in Phase 3 - see docs/v2/tasks.md, Phase 3's test bullet. A freshly registered user has
 * dailyLimit=0 (entity default), so any positive-amount evaluate call deterministically fires
 * HIGH_AMOUNT (weight 70 -> DELAY) without needing to set preferences first - used throughout as
 * a simple, reliable non-ALLOW fixture.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void evaluatePersistsTransactionAndReturnsRiskDetails() throws Exception {
        String token = registerUser("alice");

        mockMvc.perform(evaluateRequest(token, 50, "groceries", "Corner Store"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").isNotEmpty())
                .andExpect(jsonPath("$.decisionType").value("DELAY"))
                .andExpect(jsonPath("$.riskScore").value(70.0))
                .andExpect(jsonPath("$.explanation").value("Transaction exceeds daily limit; "))
                .andExpect(jsonPath("$.triggeredRules[0].ruleCode").value("HIGH_AMOUNT"));
    }

    @Test
    void getByPublicIdReturnsOwnTransaction() throws Exception {
        String token = registerUser("bob");
        String publicId = evaluateAndExtractPublicId(token, 50);

        mockMvc.perform(get("/api/v2/transactions/" + publicId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(publicId));
    }

    @Test
    void getByPublicIdReturns404ForAnotherUsersTransaction() throws Exception {
        String ownerToken = registerUser("carol");
        String publicId = evaluateAndExtractPublicId(ownerToken, 50);

        String otherToken = registerUser("dave");

        mockMvc.perform(get("/api/v2/transactions/" + publicId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByPublicIdReturns404ForUnknownId() throws Exception {
        String token = registerUser("erin");

        mockMvc.perform(get("/api/v2/transactions/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanViewAnotherUsersTransaction() throws Exception {
        String ownerToken = registerUser("frank");
        String publicId = evaluateAndExtractPublicId(ownerToken, 50);

        String adminToken = registerUser("grace");
        MockMvcAuthHelper.promoteToAdmin(userRepository, roleRepository, "grace");

        mockMvc.perform(get("/api/v2/transactions/" + publicId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(publicId));
    }

    @Test
    void historyFiltersByCategory() throws Exception {
        String token = registerUser("henry");
        mockMvc.perform(evaluateRequest(token, 10, "groceries", "Store A")).andExpect(status().isOk());
        mockMvc.perform(evaluateRequest(token, 20, "luxury", "Store B")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v2/transactions/history")
                        .header("Authorization", "Bearer " + token)
                        .param("category", "luxury"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].category").value("luxury"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void exportHistoryReturnsCsvWithTheEvaluatedTransaction() throws Exception {
        String token = registerUser("iris");
        String publicId = evaluateAndExtractPublicId(token, 50);

        MvcResult result = mockMvc.perform(get("/api/v2/transactions/history/export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String csv = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getContentType()).contains("text/csv");
        assertThat(csv).contains("publicId,amount,category,merchant,occurredAt,decisionType,riskScore,explanation");
        assertThat(csv).contains(publicId);
    }

    private String registerUser(String username) throws Exception {
        return MockMvcAuthHelper.registerAndGetAccessToken(mockMvc, objectMapper, username, username + "@example.com", "password123");
    }

    private String evaluateAndExtractPublicId(String token, double amount) throws Exception {
        MvcResult result = mockMvc.perform(evaluateRequest(token, amount, "groceries", "Corner Store"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("publicId");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder evaluateRequest(
            String token, double amount, String category, String merchant) throws Exception {
        // Fixed daytime timestamp - without this, a test run between 23:00-06:00 server time
        // would non-deterministically also trigger NightSpendingRule (new users default to
        // nightSpendingAllowed=false), throwing off the exact riskScore/explanation assertions.
        return post("/api/v2/transactions/evaluate")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "amount", amount,
                        "category", category,
                        "merchant", merchant,
                        "occurredAt", "2026-01-15T12:00:00")));
    }
}
