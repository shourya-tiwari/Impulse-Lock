package com.impulselock.impulselock.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impulselock.impulselock.repository.RoleRepository;
import com.impulselock.impulselock.repository.UserRepository;
import com.impulselock.impulselock.support.AbstractIntegrationTest;
import com.impulselock.impulselock.support.MockMvcAuthHelper;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

/**
 * New in Phase 3. All fixture transactions use a fixed daytime {@code occurredAt} for the same
 * reason {@code TransactionControllerIntegrationTest} does - avoids NightSpendingRule
 * non-deterministically also firing depending on wall-clock test-run time.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void summaryAggregatesTheCallersOwnTransactions() throws Exception {
        String token = registerUser("alice");
        evaluate(token, 50, "groceries");
        evaluate(token, 30, "groceries");

        mockMvc.perform(get("/api/v2/dashboard/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionCount").value(2))
                .andExpect(jsonPath("$.totalSpend").value(80.0));
    }

    @Test
    void spendingByCategoryGroupsAndSumsCorrectly() throws Exception {
        String token = registerUser("bob");
        evaluate(token, 50, "groceries");
        evaluate(token, 30, "groceries");
        evaluate(token, 20, "luxury");

        mockMvc.perform(get("/api/v2/dashboard/spending-by-category").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].category").value("groceries"))
                .andExpect(jsonPath("$[0].totalAmount").value(80.0))
                .andExpect(jsonPath("$[0].transactionCount").value(2));
    }

    @Test
    void nonAdminCannotViewAnotherUsersDashboard() throws Exception {
        String ownerToken = registerUser("carol");
        evaluate(ownerToken, 50, "groceries");

        String otherToken = registerUser("dave");
        Long ownerId = userRepository.findByUsername("carol").orElseThrow().getId();

        mockMvc.perform(get("/api/v2/dashboard/summary")
                        .header("Authorization", "Bearer " + otherToken)
                        .param("userId", ownerId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanViewAnotherUsersDashboard() throws Exception {
        String ownerToken = registerUser("erin");
        evaluate(ownerToken, 50, "groceries");

        String adminToken = registerUser("frank");
        MockMvcAuthHelper.promoteToAdmin(userRepository, roleRepository, "frank");
        Long ownerId = userRepository.findByUsername("erin").orElseThrow().getId();

        mockMvc.perform(get("/api/v2/dashboard/summary")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("userId", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionCount").value(1));
    }

    private String registerUser(String username) throws Exception {
        return MockMvcAuthHelper.registerAndGetAccessToken(mockMvc, objectMapper, username, username + "@example.com", "password123");
    }

    private void evaluate(String token, double amount, String category) throws Exception {
        // Fixed daytime *yesterday* (not a hardcoded past date, and not "today") - dashboard/summary
        // filters occurredAt against an upper bound of the actual current instant, so a "today at
        // noon" timestamp would be in the future (and so excluded) whenever the suite happens to
        // run before noon; yesterday-at-noon is guaranteed to be in the past regardless of what
        // time it is right now, while still avoiding NightSpendingRule's 23:00-06:00 window.
        LocalDateTime occurredAt = LocalDateTime.now().minusDays(1).toLocalDate().atTime(12, 0);
        mockMvc.perform(post("/api/v2/transactions/evaluate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", amount,
                                "category", category,
                                "merchant", "Test Merchant",
                                "occurredAt", occurredAt.toString()))))
                .andExpect(status().isOk());
    }
}
