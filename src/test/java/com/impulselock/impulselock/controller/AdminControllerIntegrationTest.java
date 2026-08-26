package com.impulselock.impulselock.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * New in Phase 3, covering AdminUserController, AdminRuleConfigController, and
 * AdminAuditLogController together since they share the same admin-setup boilerplate. Phase 1's
 * AuthIntegrationTest already proved the {@code /api/v2/admin/**} URL rule itself works against
 * a nonexistent path; these tests exercise the real controllers behind it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void nonAdminIsForbiddenFromListingUsers() throws Exception {
        String token = registerUser("alice");

        mockMvc.perform(get("/api/v2/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListAndUpdateUserStatus() throws Exception {
        String targetToken = registerUser("bob");
        Long targetId = userRepository.findByUsername("bob").orElseThrow().getId();

        String adminToken = registerUser("carol");
        MockMvcAuthHelper.promoteToAdmin(userRepository, roleRepository, "carol");

        mockMvc.perform(get("/api/v2/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(patch("/api/v2/admin/users/" + targetId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // JwtAuthenticationFilter re-checks isEnabled() via a fresh DB lookup on every request
        // (see SecurityUser/JwtAuthenticationFilter's Phase 1 design) - disabling must therefore
        // take effect on the target's very next request with their still-unexpired access token,
        // not just in the DB row.
        mockMvc.perform(get("/api/v2/users/me").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanListAndUpdateRuleConfigs() throws Exception {
        String adminToken = registerUser("dave");
        MockMvcAuthHelper.promoteToAdmin(userRepository, roleRepository, "dave");

        mockMvc.perform(get("/api/v2/admin/rule-configs").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));

        mockMvc.perform(put("/api/v2/admin/rule-configs/SENSITIVITY_LEVEL")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "weight", 25,
                                "enabled", true,
                                "params", Map.of("sensitivityThreshold", 9)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weight").value(25))
                .andExpect(jsonPath("$.params.sensitivityThreshold").value(9));
    }

    @Test
    void adminAuditLogsEndpointIsReachable() throws Exception {
        String adminToken = registerUser("erin");
        MockMvcAuthHelper.promoteToAdmin(userRepository, roleRepository, "erin");

        // Not asserting a specific totalElements value: audit writes commit via their own
        // REQUIRES_NEW transaction (see AuditLogService / docs/v2/architecture.md#audit-logging),
        // so how many rows are visible here depends on what else committed - and is visible under
        // this transaction's snapshot - before this test's own transaction started, which this
        // test has no control over. Just proves an admin can reach the endpoint and gets back the
        // paginated envelope shape.
        mockMvc.perform(get("/api/v2/admin/audit-logs").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists());
    }

    private String registerUser(String username) throws Exception {
        return MockMvcAuthHelper.registerAndGetAccessToken(mockMvc, objectMapper, username, username + "@example.com", "password123");
    }
}
