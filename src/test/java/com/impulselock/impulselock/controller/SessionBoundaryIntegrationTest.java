package com.impulselock.impulselock.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impulselock.impulselock.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Regression coverage for a class of bug the rest of the integration tier structurally cannot see.
 *
 * <p><b>Why this class exists.</b> Every other {@code *IntegrationTest} here is {@code @Transactional}.
 * That is fine for its own purposes - it rolls each test back and keeps the suite isolated - but it
 * also means Spring holds a persistence session open for the entire duration of the test method.
 * Any lazy association the controller touches after the service's own transaction has committed
 * therefore still finds a live session and initializes happily. Production has no such session:
 * {@code spring.jpa.open-in-view=false} closes it at the service boundary, so the same code throws
 * {@code LazyInitializationException} and the endpoint returns 500.
 *
 * <p>That gap let a bug reach production in which {@code POST /auth/login}, {@code POST /auth/refresh},
 * {@code GET /users/me}, {@code GET|POST /users/me/restricted-categories} and
 * {@code PUT /users/me/preferences} all returned 500 for any user loaded from the database, while
 * the full suite stayed green. Registration was the sole survivor, because a just-built entity
 * carries an already-initialized collection rather than a lazy proxy.
 *
 * <p><b>So this class is deliberately NOT {@code @Transactional}</b> - that omission is the entire
 * point of it, not an oversight. Each test drives the real request lifecycle, where every response
 * is serialized after the service transaction has closed. Usernames are randomized per run since
 * there is no rollback to clean up after them.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SessionBoundaryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Login re-loads the user from the database, unlike registration which still has the instance it
     * just built. Serializing that user into a {@code UserProfileResponse} reads
     * {@code User.restrictedCategories}; this asserts the collection is populated, not merely that
     * the request avoided a 500.
     */
    @Test
    void loginSerializesRestrictedCategoriesAfterTheTransactionCloses() throws Exception {
        String username = uniqueUsername("login");
        register(username);

        mockMvc.perform(post("/api/v2/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(username))
                .andExpect(jsonPath("$.user.restrictedCategories[0]").value("LUXURY"));
    }

    /**
     * The originally reported symptom: saving preferences returned 500. The response is built from
     * the saved entity after {@code UserService.updatePreferences}'s transaction has committed.
     */
    @Test
    void updatePreferencesReturnsTheUpdatedProfileAfterTheTransactionCloses() throws Exception {
        String username = uniqueUsername("prefs");
        String token = register(username);

        mockMvc.perform(put("/api/v2/users/me/preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dailyLimit", 2500,
                                "nightSpendingAllowed", true,
                                "sensitivityLevel", 8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyLimit").value(2500))
                .andExpect(jsonPath("$.sensitivityLevel").value(8))
                .andExpect(jsonPath("$.restrictedCategories[0]").value("LUXURY"));
    }

    @Test
    void meAndRestrictedCategoriesResolveAfterTheTransactionCloses() throws Exception {
        String username = uniqueUsername("me");
        String token = register(username);

        mockMvc.perform(get("/api/v2/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restrictedCategories[0]").value("LUXURY"));

        mockMvc.perform(get("/api/v2/users/me/restricted-categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("LUXURY"));
    }

    /**
     * Refresh failed for a second, distinct reason worth pinning separately: {@code RefreshToken.user}
     * is a lazy {@code @ManyToOne}, so rotation handed the controller an uninitialized <i>proxy</i>
     * rather than an uninitialized collection. The first property read off it - {@code getRoles()},
     * while signing the new access token - threw. Asserting the rotated cookie and the serialized
     * profile together covers both the proxy itself and the associations hanging off it.
     */
    @Test
    void refreshResolvesTheUserProxyAfterTheTransactionCloses() throws Exception {
        String username = uniqueUsername("refresh");
        Cookie refreshCookie = registerAndCaptureRefreshCookie(username);

        mockMvc.perform(post("/api/v2/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value(username))
                .andExpect(jsonPath("$.user.restrictedCategories[0]").value("LUXURY"));
    }

    private String uniqueUsername(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private String register(String username) throws Exception {
        return objectMapper.readValue(performRegister(username).getResponse().getContentAsString(), Map.class)
                .get("accessToken").toString();
    }

    private Cookie registerAndCaptureRefreshCookie(String username) throws Exception {
        Cookie cookie = performRegister(username).getResponse().getCookie("refreshToken");
        if (cookie == null) {
            throw new AssertionError("Registration did not set a refreshToken cookie");
        }
        return cookie;
    }

    private MvcResult performRegister(String username) throws Exception {
        return mockMvc.perform(post("/api/v2/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", username + "@example.com",
                                "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
    }
}
