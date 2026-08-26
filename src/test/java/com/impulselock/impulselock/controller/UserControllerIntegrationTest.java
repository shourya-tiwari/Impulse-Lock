package com.impulselock.impulselock.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/** New in Phase 3 - see docs/v2/tasks.md, Phase 3's test bullet. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void meReturnsTheAuthenticatedUsersProfile() throws Exception {
        String token = registerUser("alice");

        mockMvc.perform(get("/api/v2/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.restrictedCategories[0]").value("LUXURY"));
    }

    @Test
    void updatePreferencesAppliesNewValues() throws Exception {
        // restrictedCategories is deliberately not part of this request (see
        // UserPreferencesUpdateRequest's Phase 4 cleanup note) - restricted-category coverage
        // lives in the dedicated tests below instead. This user's registration-time default
        // ("LUXURY") is therefore expected to survive the preferences update untouched.
        String token = registerUser("bob");

        mockMvc.perform(put("/api/v2/users/me/preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dailyLimit", 2000,
                                "nightSpendingAllowed", true,
                                "sensitivityLevel", 7))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyLimit").value(2000))
                .andExpect(jsonPath("$.nightSpendingAllowed").value(true))
                .andExpect(jsonPath("$.sensitivityLevel").value(7))
                .andExpect(jsonPath("$.restrictedCategories[0]").value("LUXURY"));
    }

    @Test
    void updatePreferencesRejectsOutOfRangeSensitivityLevel() throws Exception {
        String token = registerUser("frank");

        mockMvc.perform(put("/api/v2/users/me/preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dailyLimit", 2000,
                                "nightSpendingAllowed", true,
                                "sensitivityLevel", 11))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sensitivityLevel"));
    }

    @Test
    void addRestrictedCategoryAppendsWithoutDuplicating() throws Exception {
        String token = registerUser("carol");

        mockMvc.perform(post("/api/v2/users/me/restricted-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("category", "gambling"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Adding the same category again (any case) must not create a duplicate entry.
        mockMvc.perform(post("/api/v2/users/me/restricted-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("category", "GAMBLING"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void removeRestrictedCategoryDeletesIt() throws Exception {
        String token = registerUser("dave");

        mockMvc.perform(delete("/api/v2/users/me/restricted-categories/LUXURY")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v2/users/me/restricted-categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private String registerUser(String username) throws Exception {
        return MockMvcAuthHelper.registerAndGetAccessToken(mockMvc, objectMapper, username, username + "@example.com", "password123");
    }
}
